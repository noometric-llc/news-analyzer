"""
EVAL Articles API — endpoints for article generation inspection.

POST /eval/articles/generate  — generate a single article for testing
GET  /eval/articles/{id}      — retrieve a stored article (stub for EVAL-1.3)
"""

from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.config import settings
from app.models.eval import ArticleType, FactSet
from app.services.eval.article_generator import ArticleGenerator

router = APIRouter()


class GenerateRequest(BaseModel):
    """Request body for single article generation."""

    fact_set: FactSet
    article_type: ArticleType = ArticleType.NEWS_REPORT


class GenerateResponse(BaseModel):
    """Response from single article generation."""

    article_text: str
    tokens_used: int
    article_type: ArticleType
    dry_run: bool


@router.post(
    "/generate",
    response_model=GenerateResponse,
    summary="Generate a single article from a FactSet",
    description="Generates a single synthetic news article from the provided "
    "FactSet. Useful for manual inspection and prompt iteration.",
)
async def generate_article(request: GenerateRequest) -> GenerateResponse:
    """POST /eval/articles/generate"""
    generator = ArticleGenerator()
    try:
        text, tokens = await generator.generate(
            request.fact_set, request.article_type
        )
        return GenerateResponse(
            article_text=text,
            tokens_used=tokens,
            article_type=request.article_type,
            dry_run=settings.eval_dry_run,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get(
    "/{article_id}",
    summary="Retrieve a generated article",
    description="Stub — will be implemented in EVAL-1.3 when storage is added.",
)
async def get_article(article_id: UUID):
    """GET /eval/articles/{id} — stub for EVAL-1.3."""
    raise HTTPException(
        status_code=501,
        detail="Article storage not yet implemented (EVAL-1.3)",
    )
