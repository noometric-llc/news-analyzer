"""
Bias Ontology & Detection API Endpoints (EVAL-3.2 + EVAL-3.3)

Provides ontology statistics, SHACL validation, and bias detection
for the cognitive bias ontology.
"""

import logging
from pathlib import Path
from typing import List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services.owl_reasoner import get_reasoner

logger = logging.getLogger(__name__)

router = APIRouter()

ONTOLOGY_DIR = Path(__file__).parent.parent.parent / "ontology"

# -------------------------------------------------------------------
#  Response Models
# -------------------------------------------------------------------


class SHACLViolationResponse(BaseModel):
    focus_node: str
    path: str
    message: str
    severity: str


class OntologyStatsResponse(BaseModel):
    total_distortions: int
    cognitive_biases: int
    logical_fallacies: int
    formal_fallacies: int
    informal_fallacies: int
    academic_sources: int
    detection_patterns: int
    shacl_valid: bool
    shacl_violations: List[SHACLViolationResponse]


class ValidateRequest(BaseModel):
    validate_ontology: bool = True


class ValidateResponse(BaseModel):
    conforms: bool
    results_count: int
    violations: List[SHACLViolationResponse]
    shapes_evaluated: List[str]


class BiasDetectRequest(BaseModel):
    text: str
    distortion_types: Optional[List[str]] = None
    confidence_threshold: float = 0.0
    include_ontology_metadata: bool = True
    grounded: bool = True


class OntologyMetadataResponse(BaseModel):
    definition: str
    academic_source: str
    detection_pattern: str


class BiasAnnotationResponse(BaseModel):
    distortion_type: str
    category: str
    excerpt: str
    explanation: str
    confidence: float
    ontology_metadata: Optional[OntologyMetadataResponse] = None


class BiasDetectResponse(BaseModel):
    annotations: List[BiasAnnotationResponse]
    total_count: int
    distortions_checked: List[str]
    shacl_valid: bool


# -------------------------------------------------------------------
#  Cached SHACL validation (ontology doesn't change at runtime)
# -------------------------------------------------------------------

_cached_shacl_report: Optional[dict] = None


def _get_shacl_report() -> dict:
    """Run SHACL validation once and cache the result."""
    global _cached_shacl_report

    if _cached_shacl_report is not None:
        return _cached_shacl_report

    try:
        from rdflib import Graph
        from app.services.shacl_validator import SHACLValidator

        shapes_path = ONTOLOGY_DIR / "cognitive-bias-shapes.ttl"
        if not shapes_path.exists():
            _cached_shacl_report = {
                "conforms": False,
                "results_count": 1,
                "violations": [{"focus_node": "N/A", "path": "N/A", "message": "Shapes file not found", "severity": "Error"}],
                "shapes_evaluated": [],
            }
            return _cached_shacl_report

        shapes = Graph()
        shapes.parse(str(shapes_path), format="turtle")
        validator = SHACLValidator(shapes)

        reasoner = get_reasoner()
        report = validator.validate_ontology(reasoner.graph)

        _cached_shacl_report = {
            "conforms": report.conforms,
            "results_count": report.results_count,
            "violations": [
                {"focus_node": v.focus_node, "path": v.path, "message": v.message, "severity": v.severity}
                for v in report.violations
            ],
            "shapes_evaluated": report.shapes_evaluated,
        }
    except Exception as e:
        logger.error(f"SHACL validation failed: {e}")
        _cached_shacl_report = {
            "conforms": False,
            "results_count": 1,
            "violations": [{"focus_node": "N/A", "path": "N/A", "message": str(e), "severity": "Error"}],
            "shapes_evaluated": [],
        }

    return _cached_shacl_report


# -------------------------------------------------------------------
#  Endpoints
# -------------------------------------------------------------------


@router.get("/ontology/stats", response_model=OntologyStatsResponse)
async def get_ontology_stats():
    """Get cognitive bias ontology statistics and SHACL validation status."""
    reasoner = get_reasoner()

    if not reasoner._bias_ontology_loaded:
        raise HTTPException(status_code=503, detail="Bias ontology not loaded")

    stats = reasoner.get_bias_ontology_stats()
    shacl = _get_shacl_report()

    return OntologyStatsResponse(
        **stats,
        shacl_valid=shacl["conforms"],
        shacl_violations=[SHACLViolationResponse(**v) for v in shacl["violations"]],
    )


@router.post("/ontology/validate", response_model=ValidateResponse)
async def validate_ontology(request: ValidateRequest):
    """Run SHACL validation against the loaded bias ontology."""
    reasoner = get_reasoner()

    if not reasoner._bias_ontology_loaded:
        raise HTTPException(status_code=503, detail="Bias ontology not loaded")

    # Force re-validation if requested
    global _cached_shacl_report
    if request.validate_ontology:
        _cached_shacl_report = None

    shacl = _get_shacl_report()

    return ValidateResponse(
        conforms=shacl["conforms"],
        results_count=shacl["results_count"],
        violations=[SHACLViolationResponse(**v) for v in shacl["violations"]],
        shapes_evaluated=shacl["shapes_evaluated"],
    )


@router.post("/detect", response_model=BiasDetectResponse)
async def detect_biases(request: BiasDetectRequest):
    """
    Detect cognitive biases and logical fallacies in text.

    When grounded=True (default), uses ontology-grounded neuro-symbolic analysis:
    SPARQL retrieves formal definitions, which are included in the LLM prompt.
    When grounded=False, uses a generic prompt without ontology definitions
    (for A/B comparison in EVAL-3.5).
    """
    reasoner = get_reasoner()
    if not reasoner._bias_ontology_loaded:
        raise HTTPException(status_code=503, detail="Bias ontology not loaded")

    # Lazy import — avoid taking down the service if bias modules have issues
    from app.services.eval.bias_detector import OntologyGroundedBiasDetector

    try:
        detector = OntologyGroundedBiasDetector()
        output = await detector.detect(
            text=request.text,
            distortion_types=request.distortion_types,
            confidence_threshold=request.confidence_threshold,
            grounded=request.grounded,
        )
    except Exception as e:
        logger.error("Bias detection failed: %s", e)
        raise HTTPException(status_code=500, detail=f"Bias detection failed: {e}")

    # Enrich with ontology metadata if requested
    annotations = []
    for a in output.annotations:
        metadata = None
        if request.include_ontology_metadata and request.grounded:
            from app.services.eval.bias_detector import _snake_to_pascal
            from app.services.owl_reasoner import CB

            pascal = _snake_to_pascal(a.distortion_type)
            uri = str(CB[pascal])
            defn = reasoner.get_distortion_definition(uri)
            if defn:
                source = defn["academic_source"]
                metadata = OntologyMetadataResponse(
                    definition=defn["definition"],
                    academic_source=f"{source['author']}, {source['year']}",
                    detection_pattern=defn["detection_pattern"],
                )

        annotations.append(
            BiasAnnotationResponse(
                distortion_type=a.distortion_type,
                category=a.category,
                excerpt=a.excerpt,
                explanation=a.explanation,
                confidence=a.confidence,
                ontology_metadata=metadata,
            )
        )

    return BiasDetectResponse(
        annotations=annotations,
        total_count=output.total_count,
        distortions_checked=output.distortions_checked,
        shacl_valid=output.shacl_valid,
    )
