"""
Typed async HTTP client for the NewsAnalyzer backend API.

Wraps all backend REST endpoints needed for EVAL fact extraction.
Uses httpx.AsyncClient for non-blocking I/O.
"""

from __future__ import annotations

import logging
from typing import Any
from uuid import UUID

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

# Default pagination size — large enough to minimize round-trips,
# small enough to avoid memory issues.
_DEFAULT_PAGE_SIZE = 100


class BackendClient:
    """Async client for the NewsAnalyzer backend REST API.

    Usage::

        async with BackendClient() as client:
            member = await client.get_member("F000479")
    """

    def __init__(self, base_url: str | None = None, timeout: float = 30.0):
        self._base_url = base_url or settings.backend_url
        self._client = httpx.AsyncClient(
            base_url=self._base_url,
            timeout=timeout,
            headers={"Accept": "application/json"},
        )

    async def close(self) -> None:
        """Close the underlying HTTP client."""
        await self._client.aclose()

    async def __aenter__(self) -> BackendClient:
        return self

    async def __aexit__(self, *exc: object) -> None:
        await self.close()

    # ------------------------------------------------------------------
    # Pagination helper
    # ------------------------------------------------------------------

    async def _get_page(
        self, path: str, page: int = 0, size: int = _DEFAULT_PAGE_SIZE, **params: Any
    ) -> dict:
        """Fetch a single page from a paginated Spring Boot endpoint.

        Returns the full Spring Page response dict with keys:
        content, totalElements, totalPages, number, size, etc.
        """
        params.update({"page": page, "size": size})
        resp = await self._client.get(path, params=params)
        resp.raise_for_status()
        return resp.json()

    async def _get_all_pages(
        self, path: str, size: int = _DEFAULT_PAGE_SIZE, **params: Any
    ) -> list[dict]:
        """Auto-fetch all pages from a paginated endpoint.

        Collects all items from content arrays across pages.
        Use with care on large datasets — prefer single-page
        fetches when you only need a sample.
        """
        first = await self._get_page(path, page=0, size=size, **params)
        items = list(first.get("content", []))
        total_pages = first.get("totalPages", 1)

        for page_num in range(1, total_pages):
            page_data = await self._get_page(path, page=page_num, size=size, **params)
            items.extend(page_data.get("content", []))

        return items

    # ------------------------------------------------------------------
    # Legislative endpoints
    # ------------------------------------------------------------------

    async def get_members(
        self, page: int = 0, size: int = _DEFAULT_PAGE_SIZE
    ) -> dict:
        """GET /api/members — paginated list of congressional members."""
        return await self._get_page("/api/members", page=page, size=size)

    async def get_all_members(self) -> list[dict]:
        """Fetch all congressional members across all pages."""
        return await self._get_all_pages("/api/members")

    async def get_member(self, bioguide_id: str) -> dict | None:
        """GET /api/members/{bioguideId} — single member by BioGuide ID.

        Returns None if the member is not found (404).
        """
        return await self._get_or_none(f"/api/members/{bioguide_id}")

    async def get_member_committees(
        self,
        bioguide_id: str,
        congress: int | None = None,
        page: int = 0,
        size: int = _DEFAULT_PAGE_SIZE,
    ) -> dict:
        """GET /api/members/{bioguideId}/committees — committee assignments."""
        params: dict[str, Any] = {}
        if congress is not None:
            params["congress"] = congress
        return await self._get_page(
            f"/api/members/{bioguide_id}/committees",
            page=page,
            size=size,
            **params,
        )

    async def get_member_terms(self, bioguide_id: str) -> list[dict]:
        """GET /api/members/{bioguideId}/terms — term history.

        Returns a plain list (not paginated).
        """
        resp = await self._client.get(f"/api/members/{bioguide_id}/terms")
        resp.raise_for_status()
        return resp.json()

    async def get_committees(
        self, page: int = 0, size: int = _DEFAULT_PAGE_SIZE
    ) -> dict:
        """GET /api/committees — paginated list of committees."""
        return await self._get_page("/api/committees", page=page, size=size)

    async def get_all_committees(self) -> list[dict]:
        """Fetch all committees across all pages."""
        return await self._get_all_pages("/api/committees")

    # ------------------------------------------------------------------
    # Executive endpoints
    # ------------------------------------------------------------------

    async def get_presidencies(
        self, page: int = 0, size: int = _DEFAULT_PAGE_SIZE
    ) -> dict:
        """GET /api/presidencies — paginated list (most recent first)."""
        return await self._get_page("/api/presidencies", page=page, size=size)

    async def get_all_presidencies(self) -> list[dict]:
        """Fetch all presidencies across all pages."""
        return await self._get_all_pages("/api/presidencies")

    async def get_current_presidency(self) -> dict | None:
        """GET /api/presidencies/current — current presidency."""
        return await self._get_or_none("/api/presidencies/current")

    async def get_administration(self, presidency_id: str) -> dict | None:
        """GET /api/presidencies/{id}/administration — VP, cabinet, CoS."""
        return await self._get_or_none(
            f"/api/presidencies/{presidency_id}/administration"
        )

    async def get_executive_orders(
        self,
        presidency_id: str,
        page: int = 0,
        size: int = _DEFAULT_PAGE_SIZE,
    ) -> dict:
        """GET /api/presidencies/{id}/executive-orders — paginated EOs."""
        return await self._get_page(
            f"/api/presidencies/{presidency_id}/executive-orders",
            page=page,
            size=size,
        )

    async def get_cabinet(self) -> list[dict]:
        """GET /api/appointees/cabinet — current cabinet members.

        Returns a plain list (not paginated).
        """
        resp = await self._client.get("/api/appointees/cabinet")
        resp.raise_for_status()
        return resp.json()

    async def get_cabinet_departments(self) -> list[dict]:
        """GET /api/government-organizations/cabinet-departments.

        Returns a plain list of the 15 cabinet-level departments.
        """
        resp = await self._client.get(
            "/api/government-organizations/cabinet-departments"
        )
        resp.raise_for_status()
        return resp.json()

    # ------------------------------------------------------------------
    # Judicial endpoints
    # ------------------------------------------------------------------

    async def get_judges(
        self,
        page: int = 0,
        size: int = _DEFAULT_PAGE_SIZE,
        court_level: str | None = None,
        status: str | None = None,
    ) -> dict:
        """GET /api/judges — paginated list with optional filters.

        Args:
            court_level: SUPREME, APPEALS, or DISTRICT
            status: ACTIVE, SENIOR, or ALL
        """
        params: dict[str, Any] = {}
        if court_level is not None:
            params["courtLevel"] = court_level
        if status is not None:
            params["status"] = status
        return await self._get_page(
            "/api/judges", page=page, size=size, **params
        )

    async def get_all_judges(self, **filters: Any) -> list[dict]:
        """Fetch all judges across all pages with optional filters."""
        return await self._get_all_pages("/api/judges", **filters)

    # ------------------------------------------------------------------
    # EVAL dataset endpoints
    # ------------------------------------------------------------------

    async def post_batch(
        self,
        batch_id: UUID,
        config: Any,
        test_cases: list[Any],
    ) -> dict:
        """POST /api/eval/datasets/batches — store a completed generation batch.

        Serializes BatchConfig + ArticleTestCases into the CreateBatchRequest
        shape expected by the backend controller.

        Args:
            batch_id: Unique identifier for this batch run.
            config: BatchConfig instance with generation parameters.
            test_cases: List of ArticleTestCase instances to persist.

        Returns:
            The created GenerationBatchDTO response from the backend.
        """
        faithful_count = sum(1 for tc in test_cases if tc.is_faithful)
        total_tokens = sum(tc.tokens_used for tc in test_cases)

        articles = []
        for tc in test_cases:
            articles.append({
                "articleText": tc.article_text,
                "articleType": tc.article_type.value,
                "isFaithful": tc.is_faithful,
                "perturbationType": tc.perturbation_type.value
                if tc.perturbation_type.value != "none"
                else None,
                "difficulty": tc.difficulty.value.upper(),
                "sourceFacts": tc.source_facts.model_dump(mode="json"),
                "groundTruth": {
                    "changed_facts": tc.changed_facts,
                    "expected_findings": tc.expected_findings,
                },
                "modelUsed": tc.model_used,
                "tokensUsed": tc.tokens_used,
            })

        payload = {
            "batchId": str(batch_id),
            "branch": config.branch.value if config.branch else None,
            "modelUsed": config.model,
            "configJson": config.model_dump(mode="json"),
            "articlesCount": len(test_cases),
            "faithfulCount": faithful_count,
            "perturbedCount": len(test_cases) - faithful_count,
            "totalTokens": total_tokens,
            "durationSeconds": None,  # Set by caller if needed
            "errors": [],
            "articles": articles,
        }

        resp = await self._client.post(
            "/api/eval/datasets/batches",
            json=payload,
        )
        resp.raise_for_status()
        return resp.json()

    # ------------------------------------------------------------------
    # Private helpers
    # ------------------------------------------------------------------

    async def _get_or_none(self, path: str) -> dict | None:
        """GET a single resource, returning None on 404."""
        try:
            resp = await self._client.get(path)
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            return resp.json()
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code == 404:
                return None
            raise
