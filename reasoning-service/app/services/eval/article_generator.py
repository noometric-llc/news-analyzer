"""
Article Generator — produces synthetic news articles from FactSets via Claude API.

Constructs prompts with style-specific instructions per ArticleType and calls
the Anthropic SDK. Supports dry-run mode (no API calls) and configurable
rate limiting.
"""

from __future__ import annotations

import asyncio
import logging

from anthropic import AsyncAnthropic

from app.config import settings
from app.models.eval import ArticleType, FactSet

logger = logging.getLogger(__name__)

# Style instructions keyed by ArticleType
_STYLE_INSTRUCTIONS: dict[ArticleType, str] = {
    ArticleType.NEWS_REPORT: (
        "Write in the style of an AP or Reuters news report. "
        "Neutral, factual tone. Include a dateline."
    ),
    ArticleType.BREAKING_NEWS: (
        "Write as a breaking news alert. Urgent tone, concise, "
        "lead with the most important fact."
    ),
    ArticleType.OPINION: (
        "Write as a newspaper opinion column. Include the author's "
        "perspective, but embed the provided facts."
    ),
    ArticleType.ANALYSIS: (
        "Write as a political analysis piece. Provide context and "
        "interpretation around the facts."
    ),
}


class ArticleGenerator:
    """Generates synthetic news articles from FactSets via Claude API."""

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

    async def generate(
        self,
        fact_set: FactSet,
        article_type: ArticleType,
    ) -> tuple[str, int]:
        """Generate a single article.

        Returns (article_text, tokens_used).
        In dry-run mode returns the prompt text with 0 tokens.
        """
        prompt = self._build_prompt(fact_set, article_type)

        if settings.eval_dry_run:
            logger.info(
                "[DRY RUN] Would generate %s article for '%s'",
                article_type.value,
                fact_set.topic,
            )
            return f"[DRY RUN] Prompt:\n{prompt}", 0

        client = self._get_client()
        response = await client.messages.create(
            model=self._model,
            max_tokens=1500,
            messages=[{"role": "user", "content": prompt}],
        )
        content_block = response.content[0]
        text = content_block.text  # type: ignore[union-attr]
        tokens = response.usage.input_tokens + response.usage.output_tokens

        # Rate limiting — sleep between calls
        await asyncio.sleep(60 / settings.eval_rate_limit_rpm)

        return text, tokens

    def _build_prompt(
        self,
        fact_set: FactSet,
        article_type: ArticleType,
    ) -> str:
        """Construct the generation prompt from facts and article type."""
        facts_text = "\n".join(
            f"- {f.subject} | {f.predicate.value} | {f.object}"
            for f in fact_set.facts
        )

        style_instruction = _STYLE_INSTRUCTIONS[article_type]

        return (
            "You are a news article generator for an AI evaluation system. "
            "Generate a realistic news article using ONLY the facts provided "
            "below. Do not add any facts not listed here. The article should "
            "read like a real published article — natural prose, not a fact "
            "list.\n\n"
            f"{style_instruction}\n\n"
            f"FACTS:\n{facts_text}\n\n"
            "REQUIREMENTS:\n"
            "- Use ALL provided facts naturally in the article\n"
            "- Do NOT invent any additional factual claims\n"
            "- Article length: 200-400 words\n"
            "- Include realistic but fictional quotes if appropriate "
            "for the article type\n"
            "- The article should feel like it could appear in a major "
            "news outlet"
        )
