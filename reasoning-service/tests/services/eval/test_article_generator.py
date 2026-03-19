"""
Tests for the ArticleGenerator.

All tests mock the Anthropic SDK — no real API calls are made.
Tests cover:
  - Prompt construction: facts embedded, style instructions correct per type
  - Dry-run mode: no API calls, returns prompt text with 0 tokens
  - Rate limiting: asyncio.sleep called after generation
  - Token counting: input + output tokens summed correctly
"""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4

import pytest

from app.models.eval import (
    ArticleType,
    Fact,
    FactPredicate,
    FactSet,
    GovernmentBranch,
)
from app.services.eval.article_generator import ArticleGenerator, _STYLE_INSTRUCTIONS


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def fact_set() -> FactSet:
    return FactSet(
        topic="Senator Jane Smith",
        primary_entity_id=uuid4(),
        branch=GovernmentBranch.LEGISLATIVE,
        facts=[
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.PARTY_AFFILIATION,
                object="Democratic",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.STATE,
                object="Pennsylvania",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
        ],
    )


@pytest.fixture
def generator() -> ArticleGenerator:
    return ArticleGenerator(api_key="test-key", model="test-model")


def _mock_response(text: str = "Generated article text.", input_tokens: int = 100, output_tokens: int = 200) -> MagicMock:
    """Create a mock Anthropic messages.create response."""
    response = MagicMock()
    content_block = MagicMock()
    content_block.text = text
    response.content = [content_block]
    response.usage = MagicMock()
    response.usage.input_tokens = input_tokens
    response.usage.output_tokens = output_tokens
    return response


# ---------------------------------------------------------------------------
# Prompt construction tests
# ---------------------------------------------------------------------------


class TestBuildPrompt:
    def test_contains_all_facts(self, generator, fact_set):
        prompt = generator._build_prompt(fact_set, ArticleType.NEWS_REPORT)
        assert "Jane Smith" in prompt
        assert "party_affiliation" in prompt
        assert "Democratic" in prompt
        assert "state" in prompt
        assert "Pennsylvania" in prompt

    def test_contains_style_instruction_news_report(self, generator, fact_set):
        prompt = generator._build_prompt(fact_set, ArticleType.NEWS_REPORT)
        assert "AP or Reuters" in prompt
        assert "dateline" in prompt

    def test_contains_style_instruction_breaking_news(self, generator, fact_set):
        prompt = generator._build_prompt(fact_set, ArticleType.BREAKING_NEWS)
        assert "breaking news" in prompt.lower()
        assert "urgent" in prompt.lower()

    def test_contains_style_instruction_opinion(self, generator, fact_set):
        prompt = generator._build_prompt(fact_set, ArticleType.OPINION)
        assert "opinion column" in prompt.lower()
        assert "perspective" in prompt.lower()

    def test_contains_style_instruction_analysis(self, generator, fact_set):
        prompt = generator._build_prompt(fact_set, ArticleType.ANALYSIS)
        assert "analysis" in prompt.lower()
        assert "context" in prompt.lower()

    def test_all_four_article_types_have_style_instructions(self):
        """Every ArticleType value must have a style instruction."""
        for article_type in ArticleType:
            assert article_type in _STYLE_INSTRUCTIONS

    def test_prompt_includes_requirements(self, generator, fact_set):
        prompt = generator._build_prompt(fact_set, ArticleType.NEWS_REPORT)
        assert "200-400 words" in prompt
        assert "Do NOT invent" in prompt

    def test_facts_formatted_as_pipe_delimited(self, generator, fact_set):
        prompt = generator._build_prompt(fact_set, ArticleType.NEWS_REPORT)
        assert "- Jane Smith | party_affiliation | Democratic" in prompt
        assert "- Jane Smith | state | Pennsylvania" in prompt


# ---------------------------------------------------------------------------
# Dry-run mode tests
# ---------------------------------------------------------------------------


class TestDryRun:
    @pytest.mark.asyncio
    async def test_dry_run_returns_prompt_with_zero_tokens(
        self, generator, fact_set
    ):
        with patch("app.services.eval.article_generator.settings") as mock_settings:
            mock_settings.eval_dry_run = True
            mock_settings.eval_rate_limit_rpm = 50

            text, tokens = await generator.generate(
                fact_set, ArticleType.NEWS_REPORT
            )
            assert text.startswith("[DRY RUN] Prompt:")
            assert tokens == 0

    @pytest.mark.asyncio
    async def test_dry_run_does_not_call_api(self, generator, fact_set):
        with patch("app.services.eval.article_generator.settings") as mock_settings:
            mock_settings.eval_dry_run = True
            mock_settings.eval_rate_limit_rpm = 50

            # Set up a mock client that should NOT be called
            mock_client = AsyncMock()
            generator._client = mock_client

            await generator.generate(fact_set, ArticleType.NEWS_REPORT)
            mock_client.messages.create.assert_not_called()

    @pytest.mark.asyncio
    async def test_dry_run_prompt_contains_facts(self, generator, fact_set):
        with patch("app.services.eval.article_generator.settings") as mock_settings:
            mock_settings.eval_dry_run = True
            mock_settings.eval_rate_limit_rpm = 50

            text, _ = await generator.generate(
                fact_set, ArticleType.ANALYSIS
            )
            assert "Jane Smith" in text
            assert "Democratic" in text


# ---------------------------------------------------------------------------
# API call tests (mocked)
# ---------------------------------------------------------------------------


class TestGenerate:
    @pytest.mark.asyncio
    async def test_returns_text_and_tokens(self, generator, fact_set):
        with patch("app.services.eval.article_generator.settings") as mock_settings:
            mock_settings.eval_dry_run = False
            mock_settings.eval_rate_limit_rpm = 60  # 1 call/sec for fast test

            mock_client = AsyncMock()
            mock_client.messages.create.return_value = _mock_response(
                text="A generated article.", input_tokens=150, output_tokens=250
            )
            generator._client = mock_client

            text, tokens = await generator.generate(
                fact_set, ArticleType.NEWS_REPORT
            )
            assert text == "A generated article."
            assert tokens == 400  # 150 + 250

    @pytest.mark.asyncio
    async def test_calls_anthropic_with_correct_params(
        self, generator, fact_set
    ):
        with patch("app.services.eval.article_generator.settings") as mock_settings:
            mock_settings.eval_dry_run = False
            mock_settings.eval_rate_limit_rpm = 60

            mock_client = AsyncMock()
            mock_client.messages.create.return_value = _mock_response()
            generator._client = mock_client

            await generator.generate(fact_set, ArticleType.NEWS_REPORT)

            call_kwargs = mock_client.messages.create.call_args.kwargs
            assert call_kwargs["model"] == "test-model"
            assert call_kwargs["max_tokens"] == 1500
            assert len(call_kwargs["messages"]) == 1
            assert call_kwargs["messages"][0]["role"] == "user"

    @pytest.mark.asyncio
    async def test_rate_limiting_sleeps(self, generator, fact_set):
        with patch("app.services.eval.article_generator.settings") as mock_settings:
            mock_settings.eval_dry_run = False
            mock_settings.eval_rate_limit_rpm = 60

            mock_client = AsyncMock()
            mock_client.messages.create.return_value = _mock_response()
            generator._client = mock_client

            with patch(
                "app.services.eval.article_generator.asyncio.sleep",
                new_callable=AsyncMock,
            ) as mock_sleep:
                await generator.generate(fact_set, ArticleType.NEWS_REPORT)
                mock_sleep.assert_called_once_with(1.0)  # 60 / 60 RPM

    @pytest.mark.asyncio
    async def test_prompt_passed_to_api(self, generator, fact_set):
        with patch("app.services.eval.article_generator.settings") as mock_settings:
            mock_settings.eval_dry_run = False
            mock_settings.eval_rate_limit_rpm = 60

            mock_client = AsyncMock()
            mock_client.messages.create.return_value = _mock_response()
            generator._client = mock_client

            await generator.generate(fact_set, ArticleType.OPINION)

            call_kwargs = mock_client.messages.create.call_args.kwargs
            prompt_text = call_kwargs["messages"][0]["content"]
            # Should contain the opinion style instruction
            assert "opinion column" in prompt_text.lower()
            # Should contain the facts
            assert "Jane Smith" in prompt_text
