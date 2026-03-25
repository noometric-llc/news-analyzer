"""
LLM Entity Extractor — extracts named entities from text via Claude API.

Produces output compatible with the existing spaCy extractor response shape,
enabling side-by-side comparison in the Promptfoo evaluation harness (EVAL-2).

Uses the same lazy-client, dry-run, and rate-limiting patterns as ArticleGenerator.
"""

from __future__ import annotations

import asyncio
import json
import logging
import re

from anthropic import AsyncAnthropic

from app.config import settings

logger = logging.getLogger(__name__)

# The 7 entity types matching the internal taxonomy
ENTITY_TYPES = frozenset(
    {
        "person",
        "government_org",
        "organization",
        "location",
        "event",
        "concept",
        "legislation",
    }
)

_SYSTEM_PROMPT = (
    "You are a named entity extraction system for government news articles.\n\n"
    "Extract ALL named entities from the following text. For each entity, provide:\n"
    '- "text": the exact text span as it appears in the article\n'
    '- "type": one of [person, government_org, organization, location, '
    "event, concept, legislation]\n"
    '- "confidence": your confidence in the extraction and classification '
    "(0.0 to 1.0)\n\n"
    "Classification rules:\n"
    "- person: Named individuals (politicians, officials, journalists, "
    "quoted sources)\n"
    "- government_org: Any government body, agency, committee, department, "
    "branch, or court (e.g., Senate, EPA, Supreme Court)\n"
    "- organization: Non-government organizations (companies, NGOs, "
    "universities, media outlets)\n"
    "- location: Countries, states, cities, geographic features, dateline "
    "locations\n"
    "- concept: Political parties, nationalities, ideologies, policies\n"
    "- legislation: Laws, statutes, bills, executive orders by name\n"
    "- event: Named events (elections, hearings, summits, wars)\n\n"
    "Important:\n"
    "- Extract ALL named entities, not just the most prominent\n"
    "- Use the EXACT text as it appears — do not paraphrase or normalize\n"
    "- Only annotate the first occurrence of each entity\n"
    "- Return ONLY a JSON array. No explanation, no markdown fences."
)


class LLMEntityExtractor:
    """Extracts named entities from text via Claude API."""

    def __init__(
        self,
        api_key: str | None = None,
        model: str | None = None,
    ) -> None:
        self._api_key = api_key or settings.anthropic_api_key
        self._model = model or settings.eval_default_model
        self._client: AsyncAnthropic | None = None

    def _get_client(self) -> AsyncAnthropic:
        """Lazily initialise the Anthropic client."""
        if self._client is None:
            self._client = AsyncAnthropic(api_key=self._api_key)
        return self._client

    async def extract(
        self,
        text: str,
        model: str | None = None,
    ) -> list[dict]:
        """Extract entities from text.

        Returns list of dicts with keys: text, entity_type, start, end, confidence.
        In dry-run mode returns an empty list.
        """
        if settings.eval_dry_run:
            logger.info("[DRY RUN] Would extract entities via LLM")
            return []

        prompt = self._build_prompt(text)
        model_to_use = model or self._model

        client = self._get_client()
        response = await client.messages.create(
            model=model_to_use,
            max_tokens=4096,
            system=_SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )
        response_text = response.content[0].text

        # Rate limiting
        await asyncio.sleep(60 / settings.eval_rate_limit_rpm)

        return self._parse_response(response_text, text)

    def _build_prompt(self, text: str) -> str:
        """Construct the user message containing the article text."""
        return f"TEXT:\n{text}"

    def _parse_response(
        self, response_text: str, original_text: str
    ) -> list[dict]:
        """Parse Claude's JSON response and compute character offsets.

        Args:
            response_text: Raw text from Claude (expected: JSON array).
            original_text: The original article text for offset computation.

        Returns:
            List of entity dicts with text, entity_type, start, end, confidence.
        """
        raw_entities = self._extract_json(response_text)
        if raw_entities is None:
            return []

        entities = []
        seen_texts: set[str] = set()

        for raw in raw_entities:
            entity_text = raw.get("text", "")
            entity_type = raw.get("type", "")
            confidence = raw.get("confidence", 0.0)

            # Skip empty text
            if not entity_text:
                continue

            # Skip duplicate text (first occurrence only)
            if entity_text.lower() in seen_texts:
                continue

            # Validate entity type
            if entity_type not in ENTITY_TYPES:
                logger.warning(
                    "Unknown entity type '%s' for '%s', skipping",
                    entity_type,
                    entity_text,
                )
                continue

            # Clamp confidence to 0.0–1.0
            try:
                confidence = float(confidence)
            except (TypeError, ValueError):
                confidence = 0.5
            confidence = max(0.0, min(1.0, confidence))

            # Locate span in original text
            start = original_text.find(entity_text)
            if start == -1:
                # Case-insensitive fallback
                start = original_text.lower().find(entity_text.lower())
            if start == -1:
                logger.warning(
                    "Entity '%s' not found in text, skipping", entity_text
                )
                continue
            end = start + len(entity_text)

            seen_texts.add(entity_text.lower())
            entities.append(
                {
                    "text": original_text[start:end],
                    "entity_type": entity_type,
                    "start": start,
                    "end": end,
                    "confidence": confidence,
                }
            )

        return entities

    @staticmethod
    def _extract_json(response_text: str) -> list[dict] | None:
        """Extract a JSON array from Claude's response text.

        Handles markdown fences and leading/trailing text gracefully.
        """
        # Try direct parse first
        text = response_text.strip()
        try:
            result = json.loads(text)
            if isinstance(result, list):
                return result
        except json.JSONDecodeError:
            pass

        # Try stripping markdown code fences
        fence_match = re.search(
            r"```(?:json)?\s*\n?(.*?)\n?\s*```", text, re.DOTALL
        )
        if fence_match:
            try:
                result = json.loads(fence_match.group(1))
                if isinstance(result, list):
                    return result
            except json.JSONDecodeError:
                pass

        # Try finding a JSON array anywhere in the text
        bracket_match = re.search(r"\[.*\]", text, re.DOTALL)
        if bracket_match:
            try:
                result = json.loads(bracket_match.group(0))
                if isinstance(result, list):
                    return result
            except json.JSONDecodeError:
                pass

        logger.error("Failed to parse JSON from response: %s", text[:200])
        return None
