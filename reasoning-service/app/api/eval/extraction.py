"""
LLM Entity Extraction API — EVAL-2.2

Endpoint for Claude-based entity extraction, producing output in the same
format as the existing spaCy /entities/extract endpoint for side-by-side
comparison via Promptfoo.
"""

import logging
from typing import Any, Dict, List

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

router = APIRouter()
logger = logging.getLogger(__name__)


class LLMExtractionRequest(BaseModel):
    """Request model for LLM entity extraction."""

    text: str = Field(..., description="Text to extract entities from")
    model: str | None = Field(
        None, description="Claude model override (default: eval_default_model)"
    )
    confidence_threshold: float = Field(
        0.0,
        ge=0.0,
        le=1.0,
        description="Minimum confidence score to include entity",
    )


class LLMEntity(BaseModel):
    """Extracted entity — matches spaCy Entity response shape."""

    text: str
    entity_type: str
    start: int
    end: int
    confidence: float
    schema_org_type: str = ""
    schema_org_data: Dict[str, Any] = Field(default_factory=dict)
    properties: Dict[str, Any] = Field(default_factory=dict)


class LLMExtractionResponse(BaseModel):
    """Response model — matches spaCy EntityExtractionResponse shape."""

    entities: List[LLMEntity]
    total_count: int


@router.post("/llm", response_model=LLMExtractionResponse)
async def extract_entities_llm(request: LLMExtractionRequest):
    """
    Extract entities from text using Claude LLM.

    Produces the same response shape as POST /entities/extract (spaCy),
    enabling side-by-side comparison in the EVAL-2 Promptfoo harness.

    Fields `schema_org_type`, `schema_org_data`, and `properties` are
    set to defaults — the Promptfoo scorer only uses `text`, `entity_type`,
    `start`, `end`, and `confidence`.
    """
    from app.services.eval.llm_entity_extractor import LLMEntityExtractor

    try:
        extractor = LLMEntityExtractor()
        raw_entities = await extractor.extract(
            text=request.text,
            model=request.model,
        )

        # Filter by confidence threshold
        filtered = [
            e for e in raw_entities if e["confidence"] >= request.confidence_threshold
        ]

        entities = [
            LLMEntity(
                text=e["text"],
                entity_type=e["entity_type"],
                start=e["start"],
                end=e["end"],
                confidence=e["confidence"],
            )
            for e in filtered
        ]

        return LLMExtractionResponse(
            entities=entities,
            total_count=len(entities),
        )

    except Exception as e:
        logger.error("LLM entity extraction failed: %s", e)
        raise HTTPException(
            status_code=500,
            detail=f"LLM entity extraction failed: {str(e)}",
        )
