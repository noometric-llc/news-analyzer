"""
EVAL Batches API — endpoints for batch generation operations.

POST /eval/batches          — kick off a batch generation run
GET  /eval/batches/{id}/status — check batch progress (stub for EVAL-1.3)
"""

from __future__ import annotations

from uuid import UUID

from fastapi import APIRouter, HTTPException

from app.clients.backend_client import BackendClient
from app.models.eval import BatchConfig, BatchResult
from app.services.eval.article_generator import ArticleGenerator
from app.services.eval.fact_extractor import FactExtractor
from app.services.eval.fact_set_builder import FactSetBuilder
from app.services.eval.batch_orchestrator import BatchOrchestrator
from app.services.eval.perturbation_engine import PerturbationEngine

router = APIRouter()


def _build_orchestrator() -> tuple[BatchOrchestrator, BackendClient]:
    """Create a BatchOrchestrator with all dependencies.

    Returns (orchestrator, client) so the caller can close the client.
    """
    client = BackendClient()
    extractor = FactExtractor(client)
    builder = FactSetBuilder(extractor, client)
    engine = PerturbationEngine()
    generator = ArticleGenerator()

    orchestrator = BatchOrchestrator(
        fact_set_builder=builder,
        perturbation_engine=engine,
        article_generator=generator,
        backend_client=client,
    )
    return orchestrator, client


@router.post(
    "",
    response_model=BatchResult,
    summary="Start a batch generation run",
    description="Kicks off a batch generation run that produces faithful and "
    "perturbed articles for the configured number of entities. "
    "Use dry_run=true to preview without API calls.",
)
async def run_batch(config: BatchConfig) -> BatchResult:
    """POST /eval/batches"""
    orchestrator, client = _build_orchestrator()
    try:
        return await orchestrator.run_batch(config)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
    finally:
        await client.close()


@router.get(
    "/{batch_id}/status",
    summary="Check batch progress",
    description="Stub — will be implemented in EVAL-1.3 when storage is added.",
)
async def get_batch_status(batch_id: UUID):
    """GET /eval/batches/{id}/status — stub for EVAL-1.3."""
    raise HTTPException(
        status_code=501,
        detail="Batch status tracking not yet implemented (EVAL-1.3)",
    )
