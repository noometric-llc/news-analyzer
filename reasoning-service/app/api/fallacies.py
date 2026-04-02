"""
Logical Fallacy Detection API

Detects common logical fallacies and cognitive biases in text using
ontology-grounded neuro-symbolic analysis (EVAL-3.3).
"""

import logging

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional

logger = logging.getLogger(__name__)

router = APIRouter()


class FallacyDetectionRequest(BaseModel):
    """Request model for fallacy detection"""
    text: str = Field(..., description="Text to analyze for fallacies")
    context: Optional[str] = Field(None, description="Additional context")


class Fallacy(BaseModel):
    """Detected fallacy model"""
    type: str = Field(..., description="Type of fallacy (e.g., ad_hominem, strawman)")
    excerpt: str = Field(..., description="Text excerpt containing the fallacy")
    explanation: str = Field(..., description="Explanation of why this is a fallacy")
    confidence: float = Field(..., ge=0.0, le=1.0)


class BiasDetection(BaseModel):
    """Detected cognitive bias"""
    type: str = Field(..., description="Type of bias (e.g., confirmation_bias, framing)")
    excerpt: str = Field(..., description="Text excerpt showing the bias")
    explanation: str = Field(..., description="Explanation of the bias")
    confidence: float = Field(..., ge=0.0, le=1.0)


class FallacyDetectionResponse(BaseModel):
    """Response model for fallacy detection"""
    fallacies: List[Fallacy]
    biases: List[BiasDetection]
    overall_quality_score: float = Field(..., ge=0.0, le=1.0)


@router.post("/detect", response_model=FallacyDetectionResponse)
async def detect_fallacies(request: FallacyDetectionRequest):
    """
    Detect logical fallacies and cognitive biases in text.

    Uses ontology-grounded neuro-symbolic analysis: queries formal definitions
    from the cognitive bias OWL ontology via SPARQL, grounds the LLM prompt
    in those definitions, and validates output via SHACL.
    """
    # Lazy imports — avoid taking down the whole service if bias modules have issues
    from app.services.owl_reasoner import get_reasoner
    from app.services.eval.bias_detector import OntologyGroundedBiasDetector

    reasoner = get_reasoner()
    if not reasoner._bias_ontology_loaded:
        raise HTTPException(status_code=503, detail="Bias ontology not loaded")

    try:
        detector = OntologyGroundedBiasDetector()
        output = await detector.detect(text=request.text)
    except Exception as e:
        logger.error("Bias detection failed: %s", e)
        raise HTTPException(status_code=500, detail=f"Bias detection failed: {e}")

    # Map BiasDetectionOutput → FallacyDetectionResponse
    fallacies = [
        Fallacy(
            type=a.distortion_type,
            excerpt=a.excerpt,
            explanation=a.explanation,
            confidence=a.confidence,
        )
        for a in output.annotations
        if a.category == "logical_fallacy"
    ]

    biases = [
        BiasDetection(
            type=a.distortion_type,
            excerpt=a.excerpt,
            explanation=a.explanation,
            confidence=a.confidence,
        )
        for a in output.annotations
        if a.category == "cognitive_bias"
    ]

    # Quality score heuristic: fewer biases detected = higher quality.
    # NOTE: This is a rough heuristic, not a rigorous metric. The score depends
    # on how many distortion types were checked, not just how many were found.
    # A well-written article about a biased topic may still flag legitimately.
    if output.distortions_checked:
        quality = 1.0 - min(
            len(output.annotations) / len(output.distortions_checked), 1.0
        )
    else:
        quality = 0.0

    return FallacyDetectionResponse(
        fallacies=fallacies,
        biases=biases,
        overall_quality_score=round(quality, 2),
    )
