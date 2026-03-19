"""
EVAL Facts API — endpoints for manual inspection of fact extraction.

These endpoints let you query the KB fact extraction pipeline directly,
inspect the structured facts it produces, and verify data quality before
wiring up batch generation.
"""

from __future__ import annotations

from enum import Enum

from fastapi import APIRouter, HTTPException, Query

from app.clients.backend_client import BackendClient
from app.models.eval import FactSet, GovernmentBranch
from app.services.eval.fact_extractor import FactExtractor
from app.services.eval.fact_set_builder import FactSetBuilder

router = APIRouter()


class EntityType(str, Enum):
    """Supported entity types for fact extraction."""

    MEMBER = "member"
    PRESIDENCY = "presidency"
    JUDGE = "judge"


def _get_builder() -> FactSetBuilder:
    """Create a FactSetBuilder with a live BackendClient.

    Each request gets a fresh client. For a high-traffic service you'd
    use a connection pool or app-level singleton, but for an inspection
    endpoint this is fine.
    """
    client = BackendClient()
    extractor = FactExtractor(client)
    return FactSetBuilder(extractor, client)


@router.get(
    "/{entity_type}/{entity_id}",
    response_model=FactSet,
    summary="Extract facts for a specific entity",
    description="Returns a FactSet containing all structured facts "
    "extracted from the KB for the given entity.",
)
async def get_entity_facts(entity_type: EntityType, entity_id: str) -> FactSet:
    """GET /eval/facts/{entity_type}/{id}"""
    builder = _get_builder()
    try:
        match entity_type:
            case EntityType.MEMBER:
                result = await builder.build_legislative_set(entity_id)
            case EntityType.PRESIDENCY:
                result = await builder.build_executive_set(entity_id)
            case EntityType.JUDGE:
                result = await builder.build_judicial_set(entity_id)

        if result is None:
            raise HTTPException(
                status_code=404,
                detail=f"{entity_type.value} with id '{entity_id}' not found",
            )

        return result
    finally:
        await builder._client.close()


@router.get(
    "/sample",
    response_model=list[FactSet],
    summary="Extract facts for a random sample of entities",
    description="Returns FactSets for a random sample of KB entities, "
    "optionally filtered by government branch.",
)
async def get_sample_facts(
    branch: GovernmentBranch | None = Query(
        default=None,
        description="Filter by government branch (legislative, executive, judicial)",
    ),
    count: int = Query(
        default=5,
        ge=1,
        le=50,
        description="Number of entities to sample (1-50)",
    ),
) -> list[FactSet]:
    """GET /eval/facts/sample?branch=...&count=..."""
    builder = _get_builder()
    try:
        return await builder.build_entity_pool(branch=branch, count=count)
    finally:
        await builder._client.close()
