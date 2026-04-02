"""
Ontology-Grounded Bias Detector (EVAL-3.3)

Core neuro-symbolic component: queries the OWL ontology via SPARQL for formal
definitions, builds grounded LLM prompts from those definitions, parses the
response, and validates output via SHACL.

The key innovation: instead of asking an LLM "does this text contain bias?"
(unauditable, relies on training data), we retrieve formal definitions from
the ontology and ground the prompt in those definitions — making the analysis
auditable and traceable to cited academic sources.

Follows the LLMEntityExtractor pattern (lazy client, dry-run, rate limiting).
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from pathlib import Path
from typing import Optional

from anthropic import AsyncAnthropic
from pydantic import BaseModel
from rdflib import Graph, Literal, BNode, Namespace
from rdflib.namespace import RDF, XSD

from app.config import settings
from app.services.owl_reasoner import get_reasoner, CB

logger = logging.getLogger(__name__)

ONTOLOGY_DIR = Path(__file__).parent.parent.parent / "ontology"


# -------------------------------------------------------------------
#  Pydantic output models (Task 4)
# -------------------------------------------------------------------


class OntologyMetadata(BaseModel):
    definition: str
    academic_source: str
    detection_pattern: str


class BiasAnnotation(BaseModel):
    distortion_type: str
    category: str  # "cognitive_bias" or "logical_fallacy"
    excerpt: str
    explanation: str
    confidence: float
    ontology_metadata: Optional[OntologyMetadata] = None


class BiasDetectionOutput(BaseModel):
    annotations: list[BiasAnnotation]
    total_count: int
    distortions_checked: list[str]
    shacl_valid: bool


# -------------------------------------------------------------------
#  Naming convention helpers
# -------------------------------------------------------------------


def _uri_to_snake(uri_local_name: str) -> str:
    """Convert PascalCase URI local name to snake_case for JSON API.

    ConfirmationBias → confirmation_bias
    AffirmingTheConsequent → affirming_the_consequent
    """
    return re.sub(r"(?<!^)(?=[A-Z])", "_", uri_local_name).lower()


def _snake_to_pascal(snake: str) -> str:
    """Convert snake_case back to PascalCase for ontology URI lookup.

    confirmation_bias → ConfirmationBias
    """
    return "".join(word.capitalize() for word in snake.split("_"))


# -------------------------------------------------------------------
#  System prompt (Task 2)
# -------------------------------------------------------------------

_SYSTEM_PROMPT = (
    "You are a cognitive bias and logical fallacy detection system for news articles.\n\n"
    "Analyze the provided text for cognitive biases and logical fallacies.\n"
    "Use ONLY the definitions provided below — do not rely on your own knowledge\n"
    "of biases/fallacies. Each detection must be traceable to the provided definition.\n\n"
    "For each detected bias or fallacy, provide:\n"
    '- "distortion_type": the exact type identifier from the definitions below\n'
    '- "category": "cognitive_bias" or "logical_fallacy"\n'
    '- "excerpt": the exact text excerpt demonstrating the distortion\n'
    '- "explanation": why this excerpt demonstrates this specific distortion, '
    "referencing the definition\n"
    '- "confidence": your confidence (0.0 to 1.0)\n\n'
    "Important:\n"
    "- Only flag distortions you are confident about (>0.5 confidence)\n"
    "- Quote the EXACT text from the article in excerpt\n"
    "- If no distortions are found, return an empty array []\n"
    "- Return ONLY a JSON array. No explanation, no markdown fences."
)

_UNGROUNDED_SYSTEM_PROMPT = (
    "You are a cognitive bias and logical fallacy detection system for news articles.\n\n"
    "Analyze the provided text for cognitive biases and logical fallacies.\n\n"
    "For each detected bias or fallacy, provide:\n"
    '- "distortion_type": a snake_case identifier (e.g., "confirmation_bias", "ad_hominem")\n'
    '- "category": "cognitive_bias" or "logical_fallacy"\n'
    '- "excerpt": the exact text excerpt demonstrating the distortion\n'
    '- "explanation": why this excerpt demonstrates this specific distortion\n'
    '- "confidence": your confidence (0.0 to 1.0)\n\n'
    "Important:\n"
    "- Only flag distortions you are confident about (>0.5 confidence)\n"
    "- Quote the EXACT text from the article in excerpt\n"
    "- If no distortions are found, return an empty array []\n"
    "- Return ONLY a JSON array. No explanation, no markdown fences."
)


# -------------------------------------------------------------------
#  Main detector class (Tasks 1-3)
# -------------------------------------------------------------------


class OntologyGroundedBiasDetector:
    """
    Detects cognitive biases and logical fallacies using ontology-grounded prompts.

    Neuro-symbolic pipeline:
    1. SPARQL → retrieve formal definitions from ontology
    2. Build prompt grounded in those definitions
    3. Claude analyzes text against formal definitions
    4. Parse JSON response into annotations
    5. SHACL-validate annotations
    6. Return validated results
    """

    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
    ) -> None:
        self._api_key = api_key or settings.anthropic_api_key
        self._model = model or settings.eval_default_model
        self._client: AsyncAnthropic | None = None
        self._category_map: dict[str, str] | None = None
        self._known_types: set[str] | None = None

    def _get_client(self) -> AsyncAnthropic:
        """Lazily initialise the Anthropic client."""
        if self._client is None:
            self._client = AsyncAnthropic(api_key=self._api_key)
        return self._client

    def _get_known_types(self) -> set[str]:
        """Get the set of known snake_case distortion type identifiers."""
        if self._known_types is None:
            reasoner = get_reasoner()
            definitions = reasoner.get_all_distortion_definitions()
            self._known_types = set()
            for d in definitions:
                local_name = str(d["uri"]).split("#")[-1]
                self._known_types.add(_uri_to_snake(local_name))
        return self._known_types

    def _get_category_map(self) -> dict[str, str]:
        """Build a map from snake_case distortion_type to category.

        Derives category from the ontology class hierarchy, NOT from LLM output.
        """
        if self._category_map is not None:
            return self._category_map

        reasoner = get_reasoner()
        self._category_map = {}

        # Get biases
        for d in reasoner.list_distortions(category="bias"):
            local_name = str(d["uri"]).split("#")[-1]
            self._category_map[_uri_to_snake(local_name)] = "cognitive_bias"

        # Get fallacies
        for d in reasoner.list_distortions(category="fallacy"):
            local_name = str(d["uri"]).split("#")[-1]
            self._category_map[_uri_to_snake(local_name)] = "logical_fallacy"

        return self._category_map

    async def detect(
        self,
        text: str,
        distortion_types: list[str] | None = None,
        confidence_threshold: float = 0.0,
        model: str | None = None,
        grounded: bool = True,
    ) -> BiasDetectionOutput:
        """
        Detect cognitive biases and logical fallacies in text.

        Args:
            text: Article text to analyze.
            distortion_types: Optional filter — only check these types.
            confidence_threshold: Minimum confidence to include in results.
            model: Override the default Claude model.
            grounded: If True, use ontology definitions in prompt. If False, generic prompt.

        Returns:
            BiasDetectionOutput with annotations, counts, and SHACL status.
        """
        # Step 0: Dry-run check
        if settings.eval_dry_run:
            logger.info("[DRY RUN] Would detect biases via LLM")
            return BiasDetectionOutput(
                annotations=[],
                total_count=0,
                distortions_checked=list(self._get_known_types()),
                shacl_valid=True,
            )

        # Step 1: Retrieve definitions from ontology (if grounded)
        definitions = None
        if grounded:
            reasoner = get_reasoner()
            definitions = reasoner.get_all_distortion_definitions()
            if distortion_types:
                definitions = [
                    d for d in definitions
                    if _uri_to_snake(str(d["uri"]).split("#")[-1]) in distortion_types
                ]

        # Build the distortions_checked list
        if definitions:
            distortions_checked = [
                _uri_to_snake(str(d["uri"]).split("#")[-1]) for d in definitions
            ]
        elif distortion_types:
            distortions_checked = distortion_types
        else:
            distortions_checked = list(self._get_known_types())

        # Step 2: Build prompt
        if grounded and definitions:
            system_prompt = _SYSTEM_PROMPT
            user_prompt = self._build_prompt(text, definitions)
        else:
            system_prompt = _UNGROUNDED_SYSTEM_PROMPT
            user_prompt = self._build_ungrounded_prompt(text)

        logger.debug("Grounded prompt:\n%s", user_prompt)

        # Step 3: Call Claude
        model_to_use = model or self._model
        try:
            client = self._get_client()
            response = await client.messages.create(
                model=model_to_use,
                max_tokens=4096,
                system=system_prompt,
                messages=[{"role": "user", "content": user_prompt}],
            )
            content_block = response.content[0]
            response_text = content_block.text  # type: ignore[union-attr]
        except Exception as e:
            logger.error("Claude API call failed: %s", e)
            raise

        # Rate limiting
        await asyncio.sleep(60 / settings.eval_rate_limit_rpm)

        # Step 4: Parse response
        raw_annotations = self._parse_response(response_text)

        # Step 5: SHACL validate
        shacl_valid = True
        if raw_annotations:
            annotations_graph = self._convert_to_rdf(raw_annotations)
            shacl_valid = self._validate_annotations(annotations_graph)

        # Step 6: Build output, filter by confidence
        annotations = []
        category_map = self._get_category_map()

        for raw in raw_annotations:
            dt = raw["distortion_type"]
            category = category_map.get(dt, raw.get("category", "unknown"))

            if raw.get("category") and raw["category"] != category:
                logger.warning(
                    "LLM category '%s' for '%s' disagrees with ontology '%s' — using ontology",
                    raw["category"], dt, category,
                )

            if raw["confidence"] < confidence_threshold:
                continue

            annotations.append(
                BiasAnnotation(
                    distortion_type=dt,
                    category=category,
                    excerpt=raw["excerpt"],
                    explanation=raw["explanation"],
                    confidence=raw["confidence"],
                )
            )

        return BiasDetectionOutput(
            annotations=annotations,
            total_count=len(annotations),
            distortions_checked=distortions_checked,
            shacl_valid=shacl_valid,
        )

    # -------------------------------------------------------------------
    #  Prompt builders (Task 2)
    # -------------------------------------------------------------------

    def _build_prompt(self, text: str, definitions: list[dict]) -> str:
        """Build grounded user prompt with ontology definitions + article text."""
        parts = ["DEFINITIONS:\n"]

        for d in definitions:
            local_name = str(d["uri"]).split("#")[-1]
            snake_id = _uri_to_snake(local_name)
            source = d["academic_source"]
            source_short = f"{source['author'].split(',')[0]}, {source['year']}"

            parts.append(f"[{snake_id}] {d['label']} ({source_short})")
            parts.append(f"Definition: {d['definition']}")
            parts.append(f"Detection pattern: {d['detection_pattern']}")
            parts.append("")

        parts.append("TEXT TO ANALYZE:")
        parts.append(text)

        return "\n".join(parts)

    def _build_ungrounded_prompt(self, text: str) -> str:
        """Build ungrounded user prompt — no ontology definitions."""
        return (
            "Analyze the following text for cognitive biases and logical fallacies. "
            "For each one found, provide distortion_type, category, excerpt, "
            "explanation, and confidence.\n\n"
            f"TEXT TO ANALYZE:\n{text}"
        )

    # -------------------------------------------------------------------
    #  Response parser (Task 3)
    # -------------------------------------------------------------------

    def _parse_response(self, response_text: str) -> list[dict]:
        """Parse Claude's JSON response into annotation dicts.

        Validates distortion_type against known types and clamps confidence.
        """
        raw_list = self._extract_json(response_text)
        if raw_list is None:
            return []

        known = self._get_known_types()
        annotations = []

        for raw in raw_list:
            dt = raw.get("distortion_type", "")
            excerpt = raw.get("excerpt", "")
            explanation = raw.get("explanation", "")
            confidence = raw.get("confidence", 0.0)

            if not dt or not excerpt:
                logger.warning("Skipping annotation missing distortion_type or excerpt")
                continue

            # Validate against known types
            if dt not in known:
                logger.warning("Unknown distortion_type '%s', skipping", dt)
                continue

            # Clamp confidence
            try:
                confidence = float(confidence)
            except (TypeError, ValueError):
                confidence = 0.5
            confidence = max(0.0, min(1.0, confidence))

            annotations.append({
                "distortion_type": dt,
                "category": raw.get("category", ""),
                "excerpt": excerpt,
                "explanation": explanation,
                "confidence": confidence,
            })

        return annotations

    @staticmethod
    def _extract_json(response_text: str) -> list[dict] | None:
        """Extract a JSON array from Claude's response (same pattern as LLMEntityExtractor)."""
        text = response_text.strip()

        # Try direct parse
        try:
            result = json.loads(text)
            if isinstance(result, list):
                return result
        except json.JSONDecodeError:
            pass

        # Try stripping markdown fences
        fence_match = re.search(r"```(?:json)?\s*\n?(.*?)\n?\s*```", text, re.DOTALL)
        if fence_match:
            try:
                result = json.loads(fence_match.group(1))
                if isinstance(result, list):
                    return result
            except json.JSONDecodeError:
                pass

        # Try finding a JSON array anywhere
        bracket_match = re.search(r"\[.*\]", text, re.DOTALL)
        if bracket_match:
            try:
                result = json.loads(bracket_match.group(0))
                if isinstance(result, list):
                    return result
            except json.JSONDecodeError:
                pass

        logger.error("Failed to parse JSON from bias detection response: %s", text[:200])
        return None

    def _resolve_category(self, distortion_type: str) -> str:
        """Resolve category from ontology class hierarchy, not LLM response."""
        return self._get_category_map().get(distortion_type, "unknown")

    # -------------------------------------------------------------------
    #  SHACL validation (Task 3)
    # -------------------------------------------------------------------

    def _convert_to_rdf(self, annotations: list[dict]) -> Graph:
        """Convert parsed annotations to RDF BiasAnnotation triples for SHACL validation."""
        g = Graph()
        g.bind("cb", CB)

        for annotation in annotations:
            ann = BNode()
            pascal_name = _snake_to_pascal(annotation["distortion_type"])
            g.add((ann, RDF.type, CB.BiasAnnotation))
            g.add((ann, CB.hasDistortionType, CB[pascal_name]))
            g.add((ann, CB.detectedIn, Literal(annotation["excerpt"])))
            g.add((ann, CB.hasConfidence, Literal(annotation["confidence"], datatype=XSD.float)))
            g.add((ann, CB.hasExplanation, Literal(annotation["explanation"])))

        return g

    def _validate_annotations(self, annotations_graph: Graph) -> bool:
        """SHACL-validate annotations against BiasAnnotationShape."""
        try:
            from app.services.shacl_validator import SHACLValidator

            shapes_path = ONTOLOGY_DIR / "cognitive-bias-shapes.ttl"
            if not shapes_path.exists():
                logger.warning("SHACL shapes file not found, skipping validation")
                return True

            shapes = Graph()
            shapes.parse(str(shapes_path), format="turtle")

            # Merge ontology classes into annotations graph so SHACL can
            # resolve cb:CognitiveDistortion class references
            ontology_path = ONTOLOGY_DIR / "cognitive-bias.ttl"
            if ontology_path.exists():
                annotations_graph.parse(str(ontology_path), format="turtle")

            validator = SHACLValidator(shapes)
            report = validator.validate(annotations_graph)

            if not report.conforms:
                for v in report.violations:
                    logger.warning("SHACL violation: %s — %s", v.focus_node, v.message)

            return report.conforms
        except Exception as e:
            logger.warning("SHACL validation error: %s", e)
            return True  # Don't block on validation failures
