"""
Tests for OntologyGroundedBiasDetector (EVAL-3.3).

Tests prompt building, response parsing, RDF conversion, SHACL validation,
and dry-run mode. Claude API calls are mocked.
"""

import json
import pytest
from pathlib import Path
from unittest.mock import AsyncMock, patch, MagicMock

from rdflib import Graph, Namespace
from rdflib.namespace import RDF

from app.services.eval.bias_detector import (
    OntologyGroundedBiasDetector,
    BiasDetectionOutput,
    _uri_to_snake,
    _snake_to_pascal,
    _SYSTEM_PROMPT,
)
from app.services.owl_reasoner import OWLReasoner, CB

ONTOLOGY_DIR = Path(__file__).parent.parent.parent / "ontology"

# -------------------------------------------------------------------
#  Mock data
# -------------------------------------------------------------------

MOCK_CLAUDE_RESPONSE = json.dumps([
    {
        "distortion_type": "framing_effect",
        "category": "cognitive_bias",
        "excerpt": "reckless policy",
        "explanation": "Uses loaded language to frame negatively",
        "confidence": 0.82,
    },
    {
        "distortion_type": "ad_hominem",
        "category": "logical_fallacy",
        "excerpt": "the senator's incompetence",
        "explanation": "Attacks the person rather than the argument",
        "confidence": 0.75,
    },
])

MOCK_CLAUDE_RESPONSE_FENCED = f"```json\n{MOCK_CLAUDE_RESPONSE}\n```"

MOCK_CLAUDE_RESPONSE_INVALID_TYPE = json.dumps([
    {
        "distortion_type": "made_up_bias",
        "category": "cognitive_bias",
        "excerpt": "some text",
        "explanation": "explanation",
        "confidence": 0.9,
    },
])

MOCK_CLAUDE_RESPONSE_EMPTY = "[]"

MOCK_CLAUDE_RESPONSE_MALFORMED = "This is not valid JSON at all"


# -------------------------------------------------------------------
#  Fixtures
# -------------------------------------------------------------------


@pytest.fixture
def reasoner_with_bias():
    """OWLReasoner with bias ontology loaded."""
    r = OWLReasoner()
    r.load_bias_ontology()
    return r


@pytest.fixture
def detector(reasoner_with_bias):
    """Detector with mocked reasoner singleton."""
    with patch("app.services.eval.bias_detector.get_reasoner", return_value=reasoner_with_bias):
        d = OntologyGroundedBiasDetector()
        # Pre-populate caches
        d._get_known_types()
        d._get_category_map()
        yield d


# -------------------------------------------------------------------
#  Naming conversion tests
# -------------------------------------------------------------------


class TestNamingConversion:
    def test_uri_to_snake(self):
        assert _uri_to_snake("ConfirmationBias") == "confirmation_bias"
        assert _uri_to_snake("AdHominem") == "ad_hominem"
        assert _uri_to_snake("AffirmingTheConsequent") == "affirming_the_consequent"
        assert _uri_to_snake("RedHerring") == "red_herring"

    def test_snake_to_pascal(self):
        assert _snake_to_pascal("confirmation_bias") == "ConfirmationBias"
        assert _snake_to_pascal("ad_hominem") == "AdHominem"
        assert _snake_to_pascal("affirming_the_consequent") == "AffirmingTheConsequent"


# -------------------------------------------------------------------
#  Prompt builder tests
# -------------------------------------------------------------------


class TestBuildPrompt:
    def test_contains_definitions(self, detector, reasoner_with_bias):
        """Grounded prompt contains ontology definitions."""
        with patch("app.services.eval.bias_detector.get_reasoner", return_value=reasoner_with_bias):
            definitions = reasoner_with_bias.get_all_distortion_definitions()
            prompt = detector._build_prompt("Some article text.", definitions)

        assert "DEFINITIONS:" in prompt
        assert "TEXT TO ANALYZE:" in prompt
        assert "[confirmation_bias]" in prompt
        assert "Nickerson" in prompt
        assert "Some article text." in prompt

    def test_contains_all_14_types(self, detector, reasoner_with_bias):
        """Prompt contains all 14 distortion definitions."""
        with patch("app.services.eval.bias_detector.get_reasoner", return_value=reasoner_with_bias):
            definitions = reasoner_with_bias.get_all_distortion_definitions()
            prompt = detector._build_prompt("text", definitions)

        assert "[framing_effect]" in prompt
        assert "[ad_hominem]" in prompt
        assert "[affirming_the_consequent]" in prompt

    def test_filtered_definitions(self, detector, reasoner_with_bias):
        """Filtering definitions limits what's in the prompt."""
        with patch("app.services.eval.bias_detector.get_reasoner", return_value=reasoner_with_bias):
            all_defs = reasoner_with_bias.get_all_distortion_definitions()
            # Filter to just framing_effect
            filtered = [d for d in all_defs if _uri_to_snake(str(d["uri"]).split("#")[-1]) == "framing_effect"]
            prompt = detector._build_prompt("text", filtered)

        assert "[framing_effect]" in prompt
        assert "[confirmation_bias]" not in prompt

    def test_ungrounded_prompt(self, detector):
        """Ungrounded prompt has no definitions section."""
        prompt = detector._build_ungrounded_prompt("Some article text.")
        assert "DEFINITIONS:" not in prompt
        assert "TEXT TO ANALYZE:" in prompt
        assert "Some article text." in prompt


# -------------------------------------------------------------------
#  Response parser tests
# -------------------------------------------------------------------


class TestParseResponse:
    def test_well_formed_json(self, detector):
        """Parses well-formed JSON array."""
        result = detector._parse_response(MOCK_CLAUDE_RESPONSE)
        assert len(result) == 2
        assert result[0]["distortion_type"] == "framing_effect"
        assert result[1]["distortion_type"] == "ad_hominem"

    def test_markdown_fenced(self, detector):
        """Parses markdown-fenced JSON."""
        result = detector._parse_response(MOCK_CLAUDE_RESPONSE_FENCED)
        assert len(result) == 2

    def test_invalid_distortion_type_skipped(self, detector):
        """Unknown distortion_type is skipped with warning."""
        result = detector._parse_response(MOCK_CLAUDE_RESPONSE_INVALID_TYPE)
        assert len(result) == 0

    def test_empty_array(self, detector):
        """Empty array returns empty list."""
        result = detector._parse_response(MOCK_CLAUDE_RESPONSE_EMPTY)
        assert result == []

    def test_malformed_json(self, detector):
        """Malformed JSON returns empty list."""
        result = detector._parse_response(MOCK_CLAUDE_RESPONSE_MALFORMED)
        assert result == []

    def test_confidence_clamped(self, detector):
        """Confidence values are clamped to [0, 1]."""
        response = json.dumps([{
            "distortion_type": "framing_effect",
            "category": "cognitive_bias",
            "excerpt": "text",
            "explanation": "reason",
            "confidence": 1.5,
        }])
        result = detector._parse_response(response)
        assert len(result) == 1
        assert result[0]["confidence"] == 1.0

    def test_negative_confidence_clamped(self, detector):
        """Negative confidence clamped to 0."""
        response = json.dumps([{
            "distortion_type": "framing_effect",
            "category": "cognitive_bias",
            "excerpt": "text",
            "explanation": "reason",
            "confidence": -0.5,
        }])
        result = detector._parse_response(response)
        assert result[0]["confidence"] == 0.0


# -------------------------------------------------------------------
#  Category resolution tests
# -------------------------------------------------------------------


class TestCategoryResolution:
    def test_bias_category(self, detector):
        """CognitiveBias types resolve to 'cognitive_bias'."""
        assert detector._resolve_category("confirmation_bias") == "cognitive_bias"
        assert detector._resolve_category("framing_effect") == "cognitive_bias"

    def test_fallacy_category(self, detector):
        """LogicalFallacy types resolve to 'logical_fallacy'."""
        assert detector._resolve_category("ad_hominem") == "logical_fallacy"
        assert detector._resolve_category("affirming_the_consequent") == "logical_fallacy"

    def test_unknown_category(self, detector):
        """Unknown type returns 'unknown'."""
        assert detector._resolve_category("made_up_thing") == "unknown"


# -------------------------------------------------------------------
#  RDF conversion tests
# -------------------------------------------------------------------


class TestConvertToRdf:
    def test_produces_valid_triples(self, detector):
        """Annotations are converted to valid BiasAnnotation RDF triples."""
        annotations = [
            {
                "distortion_type": "framing_effect",
                "excerpt": "reckless policy",
                "explanation": "Uses loaded language",
                "confidence": 0.82,
            }
        ]
        g = detector._convert_to_rdf(annotations)

        # Should have triples for: type, hasDistortionType, detectedIn, hasConfidence, hasExplanation
        assert len(g) == 5

        # Check the annotation type
        bias_annotations = list(g.subjects(RDF.type, CB.BiasAnnotation))
        assert len(bias_annotations) == 1


# -------------------------------------------------------------------
#  SHACL validation tests
# -------------------------------------------------------------------


class TestShaclValidation:
    def test_valid_annotations_pass(self, detector):
        """Well-formed annotations pass SHACL validation."""
        annotations = [
            {
                "distortion_type": "framing_effect",
                "excerpt": "reckless policy",
                "explanation": "Uses loaded language",
                "confidence": 0.82,
            }
        ]
        g = detector._convert_to_rdf(annotations)
        assert detector._validate_annotations(g) is True


# -------------------------------------------------------------------
#  Dry-run test
# -------------------------------------------------------------------


class TestDryRun:
    @pytest.mark.asyncio
    async def test_dry_run_returns_empty(self, detector):
        """Dry-run mode returns empty output without API call."""
        with patch("app.services.eval.bias_detector.settings") as mock_settings:
            mock_settings.eval_dry_run = True
            mock_settings.anthropic_api_key = ""
            mock_settings.eval_default_model = "test"
            mock_settings.eval_rate_limit_rpm = 50

            result = await detector.detect("Some article text")

        assert isinstance(result, BiasDetectionOutput)
        assert result.total_count == 0
        assert result.annotations == []
        assert result.shacl_valid is True
        assert len(result.distortions_checked) == 14


# -------------------------------------------------------------------
#  Full detect flow (mocked Claude)
# -------------------------------------------------------------------


class TestDetectFlow:
    @pytest.mark.asyncio
    async def test_detect_with_mocked_claude(self, detector, reasoner_with_bias):
        """Full detect flow with mocked Claude response."""
        mock_response = MagicMock()
        mock_content = MagicMock()
        mock_content.text = MOCK_CLAUDE_RESPONSE
        mock_response.content = [mock_content]

        mock_client = AsyncMock()
        mock_client.messages.create = AsyncMock(return_value=mock_response)
        detector._client = mock_client

        with patch("app.services.eval.bias_detector.get_reasoner", return_value=reasoner_with_bias):
            with patch("app.services.eval.bias_detector.settings") as mock_settings:
                mock_settings.eval_dry_run = False
                mock_settings.anthropic_api_key = "test"
                mock_settings.eval_default_model = "claude-sonnet-4-20250514"
                mock_settings.eval_rate_limit_rpm = 50

                result = await detector.detect("The senator's reckless policy...")

        assert isinstance(result, BiasDetectionOutput)
        assert result.total_count == 2
        assert result.annotations[0].distortion_type == "framing_effect"
        assert result.annotations[0].category == "cognitive_bias"
        assert result.annotations[1].distortion_type == "ad_hominem"
        assert result.annotations[1].category == "logical_fallacy"
        assert len(result.distortions_checked) == 14
