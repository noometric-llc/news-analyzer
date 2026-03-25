"""
Tests for the LLM Entity Extractor.

All tests mock the Anthropic SDK — no real API calls are made.
Tests cover:
  - Prompt construction: system prompt contains all 7 entity types
  - Response parsing: valid JSON, offset computation, deduplication
  - Edge cases: entity not found, unknown type, bad confidence, malformed JSON
  - Dry-run mode: no API calls, returns empty list
"""

from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.services.eval.llm_entity_extractor import (
    ENTITY_TYPES,
    LLMEntityExtractor,
    _SYSTEM_PROMPT,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

SAMPLE_TEXT = (
    "WASHINGTON — Senator Elizabeth Warren criticized the EPA's "
    "new regulations at a Senate hearing on Tuesday."
)

SAMPLE_RESPONSE = json.dumps(
    [
        {"text": "Elizabeth Warren", "type": "person", "confidence": 0.95},
        {"text": "EPA", "type": "government_org", "confidence": 0.90},
        {"text": "Senate", "type": "government_org", "confidence": 0.85},
        {"text": "WASHINGTON", "type": "location", "confidence": 0.80},
    ]
)


@pytest.fixture
def extractor() -> LLMEntityExtractor:
    return LLMEntityExtractor(api_key="test-key", model="test-model")


# ---------------------------------------------------------------------------
# Prompt construction tests
# ---------------------------------------------------------------------------


class TestBuildPrompt:
    def test_prompt_contains_article_text(self, extractor):
        prompt = extractor._build_prompt(SAMPLE_TEXT)
        assert SAMPLE_TEXT in prompt

    def test_prompt_starts_with_text_label(self, extractor):
        prompt = extractor._build_prompt("Some article.")
        assert prompt.startswith("TEXT:\n")

    def test_system_prompt_contains_all_entity_types(self):
        for entity_type in ENTITY_TYPES:
            assert entity_type in _SYSTEM_PROMPT, (
                f"Entity type '{entity_type}' missing from system prompt"
            )

    def test_system_prompt_requests_json_output(self):
        assert "JSON array" in _SYSTEM_PROMPT


# ---------------------------------------------------------------------------
# Response parsing tests
# ---------------------------------------------------------------------------


class TestParseResponse:
    def test_standard_case(self, extractor):
        entities = extractor._parse_response(SAMPLE_RESPONSE, SAMPLE_TEXT)

        assert len(entities) == 4
        names = [e["text"] for e in entities]
        assert "Elizabeth Warren" in names
        assert "EPA" in names
        assert "Senate" in names
        assert "WASHINGTON" in names

    def test_offsets_are_correct(self, extractor):
        entities = extractor._parse_response(SAMPLE_RESPONSE, SAMPLE_TEXT)

        for e in entities:
            assert SAMPLE_TEXT[e["start"] : e["end"]] == e["text"], (
                f"Offset mismatch for '{e['text']}': "
                f"text[{e['start']}:{e['end']}] = "
                f"'{SAMPLE_TEXT[e['start']:e['end']]}'"
            )

    def test_entity_type_preserved(self, extractor):
        entities = extractor._parse_response(SAMPLE_RESPONSE, SAMPLE_TEXT)

        ew = next(e for e in entities if e["text"] == "Elizabeth Warren")
        assert ew["entity_type"] == "person"

        epa = next(e for e in entities if e["text"] == "EPA")
        assert epa["entity_type"] == "government_org"

    def test_confidence_preserved(self, extractor):
        entities = extractor._parse_response(SAMPLE_RESPONSE, SAMPLE_TEXT)

        ew = next(e for e in entities if e["text"] == "Elizabeth Warren")
        assert ew["confidence"] == 0.95

    def test_entity_not_found_skipped(self, extractor):
        response = json.dumps(
            [{"text": "Nonexistent Entity", "type": "person", "confidence": 0.9}]
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert len(entities) == 0

    def test_unknown_type_skipped(self, extractor):
        response = json.dumps(
            [{"text": "Elizabeth Warren", "type": "alien", "confidence": 0.9}]
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert len(entities) == 0

    def test_confidence_clamped_high(self, extractor):
        response = json.dumps(
            [{"text": "EPA", "type": "government_org", "confidence": 1.5}]
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert entities[0]["confidence"] == 1.0

    def test_confidence_clamped_low(self, extractor):
        response = json.dumps(
            [{"text": "EPA", "type": "government_org", "confidence": -0.3}]
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert entities[0]["confidence"] == 0.0

    def test_confidence_non_numeric_defaults(self, extractor):
        response = json.dumps(
            [{"text": "EPA", "type": "government_org", "confidence": "high"}]
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert entities[0]["confidence"] == 0.5

    def test_duplicate_text_uses_first_only(self, extractor):
        response = json.dumps(
            [
                {"text": "EPA", "type": "government_org", "confidence": 0.95},
                {"text": "EPA", "type": "organization", "confidence": 0.80},
            ]
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert len(entities) == 1
        assert entities[0]["entity_type"] == "government_org"

    def test_case_insensitive_span_location(self, extractor):
        text = "The senate held a hearing."
        response = json.dumps(
            [{"text": "Senate", "type": "government_org", "confidence": 0.9}]
        )
        entities = extractor._parse_response(response, text)
        # Should find "senate" via case-insensitive fallback
        assert len(entities) == 1
        assert entities[0]["text"] == "senate"  # Uses original text casing
        assert text[entities[0]["start"] : entities[0]["end"]] == "senate"

    def test_empty_json_array(self, extractor):
        entities = extractor._parse_response("[]", SAMPLE_TEXT)
        assert entities == []

    def test_malformed_json_returns_empty(self, extractor):
        entities = extractor._parse_response("not json at all", SAMPLE_TEXT)
        assert entities == []

    def test_json_in_markdown_fences(self, extractor):
        response = (
            "```json\n"
            + json.dumps(
                [{"text": "EPA", "type": "government_org", "confidence": 0.9}]
            )
            + "\n```"
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert len(entities) == 1

    def test_json_with_surrounding_text(self, extractor):
        response = (
            "Here are the entities:\n"
            + json.dumps(
                [{"text": "EPA", "type": "government_org", "confidence": 0.9}]
            )
            + "\nDone."
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert len(entities) == 1

    def test_empty_text_field_skipped(self, extractor):
        response = json.dumps(
            [{"text": "", "type": "person", "confidence": 0.9}]
        )
        entities = extractor._parse_response(response, SAMPLE_TEXT)
        assert len(entities) == 0

    def test_output_fields_present(self, extractor):
        entities = extractor._parse_response(SAMPLE_RESPONSE, SAMPLE_TEXT)
        required_keys = {"text", "entity_type", "start", "end", "confidence"}
        for e in entities:
            assert set(e.keys()) == required_keys


# ---------------------------------------------------------------------------
# Dry-run mode tests
# ---------------------------------------------------------------------------


class TestDryRun:
    @pytest.mark.asyncio
    async def test_dry_run_returns_empty_list(self, extractor):
        with patch("app.services.eval.llm_entity_extractor.settings") as mock_settings:
            mock_settings.eval_dry_run = True
            result = await extractor.extract(SAMPLE_TEXT)
            assert result == []

    @pytest.mark.asyncio
    async def test_dry_run_no_api_call(self, extractor):
        with patch("app.services.eval.llm_entity_extractor.settings") as mock_settings:
            mock_settings.eval_dry_run = True
            # Client should never be created
            extractor._client = MagicMock()
            extractor._client.messages.create = AsyncMock()

            await extractor.extract(SAMPLE_TEXT)
            extractor._client.messages.create.assert_not_called()


# ---------------------------------------------------------------------------
# Full extract() integration (mocked API)
# ---------------------------------------------------------------------------


class TestExtract:
    @pytest.mark.asyncio
    async def test_extract_calls_api_and_parses(self, extractor):
        mock_response = MagicMock()
        mock_response.content = [MagicMock(text=SAMPLE_RESPONSE)]
        mock_response.usage.input_tokens = 100
        mock_response.usage.output_tokens = 50

        mock_client = AsyncMock()
        mock_client.messages.create = AsyncMock(return_value=mock_response)

        with (
            patch("app.services.eval.llm_entity_extractor.settings") as mock_settings,
            patch.object(extractor, "_get_client", return_value=mock_client),
        ):
            mock_settings.eval_dry_run = False
            mock_settings.eval_rate_limit_rpm = 6000  # Fast for tests

            entities = await extractor.extract(SAMPLE_TEXT)

        assert len(entities) == 4
        mock_client.messages.create.assert_called_once()

    @pytest.mark.asyncio
    async def test_extract_uses_system_prompt(self, extractor):
        mock_response = MagicMock()
        mock_response.content = [MagicMock(text="[]")]

        mock_client = AsyncMock()
        mock_client.messages.create = AsyncMock(return_value=mock_response)

        with (
            patch("app.services.eval.llm_entity_extractor.settings") as mock_settings,
            patch.object(extractor, "_get_client", return_value=mock_client),
        ):
            mock_settings.eval_dry_run = False
            mock_settings.eval_rate_limit_rpm = 6000

            await extractor.extract(SAMPLE_TEXT)

        call_kwargs = mock_client.messages.create.call_args.kwargs
        assert call_kwargs["system"] == _SYSTEM_PROMPT

    @pytest.mark.asyncio
    async def test_extract_model_override(self, extractor):
        mock_response = MagicMock()
        mock_response.content = [MagicMock(text="[]")]

        mock_client = AsyncMock()
        mock_client.messages.create = AsyncMock(return_value=mock_response)

        with (
            patch("app.services.eval.llm_entity_extractor.settings") as mock_settings,
            patch.object(extractor, "_get_client", return_value=mock_client),
        ):
            mock_settings.eval_dry_run = False
            mock_settings.eval_rate_limit_rpm = 6000

            await extractor.extract(SAMPLE_TEXT, model="claude-haiku-4-5-20251001")

        call_kwargs = mock_client.messages.create.call_args.kwargs
        assert call_kwargs["model"] == "claude-haiku-4-5-20251001"
