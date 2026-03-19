"""Tests for the BackendClient async API wrapper.

Uses httpx.MockTransport to simulate backend responses without
needing a running backend service.
"""

from __future__ import annotations

import json
from typing import Any
from uuid import uuid4

import httpx
import pytest
import pytest_asyncio

from app.clients.backend_client import BackendClient


# ---------------------------------------------------------------------------
# Helpers — build mock Spring Boot Page responses
# ---------------------------------------------------------------------------


def _spring_page(
    content: list[dict],
    page: int = 0,
    size: int = 100,
    total_elements: int | None = None,
) -> dict:
    """Build a Spring Data Page<T> response dict."""
    total = total_elements if total_elements is not None else len(content)
    total_pages = max(1, (total + size - 1) // size)
    return {
        "content": content,
        "totalElements": total,
        "totalPages": total_pages,
        "number": page,
        "size": size,
        "first": page == 0,
        "last": page >= total_pages - 1,
        "empty": len(content) == 0,
    }


def _member(bioguide_id: str = "F000479", name: str = "John Fetterman") -> dict:
    """Sample member DTO."""
    return {
        "bioguideId": bioguide_id,
        "fullName": name,
        "party": "Democratic",
        "state": "Pennsylvania",
        "district": None,
        "chamber": "Senate",
        "individualId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    }


def _presidency(id: str = "11111111-2222-3333-4444-555555555555") -> dict:
    """Sample presidency DTO."""
    return {
        "id": id,
        "presidentName": "Joe Biden",
        "presidencyNumber": 46,
        "party": "Democratic",
        "startDate": "2021-01-20",
        "endDate": None,
    }


def _judge() -> dict:
    """Sample judge DTO."""
    return {
        "id": "aabbccdd-eeff-1122-3344-556677889900",
        "firstName": "John",
        "lastName": "Roberts",
        "courtLevel": "SUPREME",
        "courtName": "Supreme Court of the United States",
        "appointingPresident": "George W. Bush",
        "confirmationDate": "2005-09-29",
        "status": "ACTIVE",
    }


# ---------------------------------------------------------------------------
# Mock transport factory
# ---------------------------------------------------------------------------


def _build_transport(routes: dict[str, Any]) -> httpx.MockTransport:
    """Build a MockTransport from a dict of path → response mappings.

    Values can be:
        - dict/list  → 200 JSON response
        - int         → status code with empty body
        - callable    → function(request) -> httpx.Response
    """

    def handler(request: httpx.Request) -> httpx.Response:
        path = request.url.path
        # Try exact match first, then prefix match for paginated routes
        response_spec = routes.get(path)
        if response_spec is None:
            # Try matching with query params stripped
            for route_path, spec in routes.items():
                if path.startswith(route_path) or path == route_path:
                    response_spec = spec
                    break

        if response_spec is None:
            return httpx.Response(404, json={"error": "not found"})

        if callable(response_spec):
            return response_spec(request)
        if isinstance(response_spec, int):
            return httpx.Response(response_spec)
        return httpx.Response(200, json=response_spec)

    return httpx.MockTransport(handler)


def _make_client(routes: dict[str, Any]) -> BackendClient:
    """Create a BackendClient backed by a mock transport."""
    transport = _build_transport(routes)
    client = BackendClient.__new__(BackendClient)
    client._base_url = "http://test-backend:8080"
    client._client = httpx.AsyncClient(
        transport=transport,
        base_url="http://test-backend:8080",
        timeout=5.0,
        headers={"Accept": "application/json"},
    )
    return client


# ---------------------------------------------------------------------------
# Legislative endpoint tests
# ---------------------------------------------------------------------------


class TestLegislativeEndpoints:
    """Tests for member and committee API methods."""

    @pytest.mark.asyncio
    async def test_get_members_returns_page(self):
        members = [_member(), _member("W000817", "Elizabeth Warren")]
        client = _make_client({"/api/members": _spring_page(members, total_elements=2)})

        async with client:
            result = await client.get_members(page=0, size=100)

        assert result["totalElements"] == 2
        assert len(result["content"]) == 2
        assert result["content"][0]["bioguideId"] == "F000479"

    @pytest.mark.asyncio
    async def test_get_all_members_fetches_multiple_pages(self):
        """Verify auto-pagination fetches all pages."""
        page1_members = [_member(f"M{i:06d}", f"Member {i}") for i in range(100)]
        page2_members = [_member(f"M{i:06d}", f"Member {i}") for i in range(100, 150)]

        def handler(request: httpx.Request) -> httpx.Response:
            page = int(request.url.params.get("page", "0"))
            if page == 0:
                return httpx.Response(
                    200,
                    json=_spring_page(page1_members, page=0, size=100, total_elements=150),
                )
            else:
                return httpx.Response(
                    200,
                    json=_spring_page(page2_members, page=1, size=100, total_elements=150),
                )

        client = _make_client({"/api/members": handler})

        async with client:
            result = await client.get_all_members()

        assert len(result) == 150

    @pytest.mark.asyncio
    async def test_get_member_returns_dto(self):
        member = _member()
        client = _make_client({"/api/members/F000479": member})

        async with client:
            result = await client.get_member("F000479")

        assert result is not None
        assert result["fullName"] == "John Fetterman"
        assert result["party"] == "Democratic"

    @pytest.mark.asyncio
    async def test_get_member_returns_none_on_404(self):
        client = _make_client({"/api/members/INVALID": 404})

        async with client:
            result = await client.get_member("INVALID")

        assert result is None

    @pytest.mark.asyncio
    async def test_get_member_committees(self):
        committees = [
            {"committeeName": "Banking", "rank": "MEMBER"},
            {"committeeName": "Agriculture", "rank": "MEMBER"},
        ]
        client = _make_client({
            "/api/members/F000479/committees": _spring_page(committees, total_elements=2)
        })

        async with client:
            result = await client.get_member_committees("F000479")

        assert len(result["content"]) == 2

    @pytest.mark.asyncio
    async def test_get_member_terms(self):
        terms = [
            {"chamber": "Senate", "startYear": 2023, "endYear": None},
        ]
        client = _make_client({"/api/members/F000479/terms": terms})

        async with client:
            result = await client.get_member_terms("F000479")

        assert isinstance(result, list)
        assert len(result) == 1
        assert result[0]["chamber"] == "Senate"

    @pytest.mark.asyncio
    async def test_get_committees(self):
        committees = [{"name": "Banking"}, {"name": "Agriculture"}]
        client = _make_client({
            "/api/committees": _spring_page(committees, total_elements=2)
        })

        async with client:
            result = await client.get_committees()

        assert result["totalElements"] == 2


# ---------------------------------------------------------------------------
# Executive endpoint tests
# ---------------------------------------------------------------------------


class TestExecutiveEndpoints:
    """Tests for presidency and administration API methods."""

    @pytest.mark.asyncio
    async def test_get_presidencies(self):
        client = _make_client({
            "/api/presidencies": _spring_page([_presidency()], total_elements=1)
        })

        async with client:
            result = await client.get_presidencies()

        assert result["totalElements"] == 1
        assert result["content"][0]["presidentName"] == "Joe Biden"

    @pytest.mark.asyncio
    async def test_get_current_presidency(self):
        client = _make_client({"/api/presidencies/current": _presidency()})

        async with client:
            result = await client.get_current_presidency()

        assert result is not None
        assert result["presidencyNumber"] == 46

    @pytest.mark.asyncio
    async def test_get_current_presidency_returns_none_on_404(self):
        client = _make_client({"/api/presidencies/current": 404})

        async with client:
            result = await client.get_current_presidency()

        assert result is None

    @pytest.mark.asyncio
    async def test_get_administration(self):
        admin = {
            "presidencyId": "11111111-2222-3333-4444-555555555555",
            "vicePresidents": [{"name": "Kamala Harris"}],
            "chiefsOfStaff": [{"name": "Jeff Zients"}],
            "cabinetMembers": [{"name": "Janet Yellen", "position": "Secretary of the Treasury"}],
        }
        client = _make_client({
            "/api/presidencies/11111111-2222-3333-4444-555555555555/administration": admin
        })

        async with client:
            result = await client.get_administration("11111111-2222-3333-4444-555555555555")

        assert result is not None
        assert len(result["vicePresidents"]) == 1
        assert result["vicePresidents"][0]["name"] == "Kamala Harris"

    @pytest.mark.asyncio
    async def test_get_executive_orders(self):
        eos = [{"eoNumber": "14001", "title": "Test EO"}]
        client = _make_client({
            "/api/presidencies/11111111-2222-3333-4444-555555555555/executive-orders": _spring_page(
                eos, total_elements=1
            )
        })

        async with client:
            result = await client.get_executive_orders("11111111-2222-3333-4444-555555555555")

        assert len(result["content"]) == 1

    @pytest.mark.asyncio
    async def test_get_cabinet(self):
        cabinet = [
            {"name": "Janet Yellen", "position": "Secretary of the Treasury"},
        ]
        client = _make_client({"/api/appointees/cabinet": cabinet})

        async with client:
            result = await client.get_cabinet()

        assert isinstance(result, list)
        assert len(result) == 1

    @pytest.mark.asyncio
    async def test_get_cabinet_departments(self):
        departments = [
            {"name": "Department of the Treasury"},
            {"name": "Department of Defense"},
        ]
        client = _make_client({
            "/api/government-organizations/cabinet-departments": departments
        })

        async with client:
            result = await client.get_cabinet_departments()

        assert isinstance(result, list)
        assert len(result) == 2


# ---------------------------------------------------------------------------
# Judicial endpoint tests
# ---------------------------------------------------------------------------


class TestJudicialEndpoints:
    """Tests for judge API methods."""

    @pytest.mark.asyncio
    async def test_get_judges(self):
        client = _make_client({
            "/api/judges": _spring_page([_judge()], total_elements=1)
        })

        async with client:
            result = await client.get_judges()

        assert result["totalElements"] == 1
        assert result["content"][0]["lastName"] == "Roberts"

    @pytest.mark.asyncio
    async def test_get_judges_with_filters(self):
        def handler(request: httpx.Request) -> httpx.Response:
            # Verify filter params are passed through
            assert request.url.params.get("courtLevel") == "SUPREME"
            assert request.url.params.get("status") == "ACTIVE"
            return httpx.Response(
                200, json=_spring_page([_judge()], total_elements=1)
            )

        client = _make_client({"/api/judges": handler})

        async with client:
            result = await client.get_judges(court_level="SUPREME", status="ACTIVE")

        assert result["totalElements"] == 1


# ---------------------------------------------------------------------------
# Error handling tests
# ---------------------------------------------------------------------------


class TestErrorHandling:
    """Verify error handling for various failure scenarios."""

    @pytest.mark.asyncio
    async def test_server_error_raises_exception(self):
        client = _make_client({"/api/members": 500})

        async with client:
            with pytest.raises(httpx.HTTPStatusError) as exc_info:
                await client.get_members()
            assert exc_info.value.response.status_code == 500

    @pytest.mark.asyncio
    async def test_timeout_raises_exception(self):
        """Verify timeout behaviour."""

        def slow_handler(request: httpx.Request) -> httpx.Response:
            raise httpx.ReadTimeout("Connection timed out")

        client = _make_client({"/api/members": slow_handler})

        async with client:
            with pytest.raises(httpx.ReadTimeout):
                await client.get_members()

    @pytest.mark.asyncio
    async def test_context_manager_closes_client(self):
        client = _make_client({})

        async with client:
            assert not client._client.is_closed

        assert client._client.is_closed


# ---------------------------------------------------------------------------
# EVAL dataset endpoint tests
# ---------------------------------------------------------------------------


class TestEvalDatasetEndpoints:
    """Tests for the post_batch method added in EVAL-1.3."""

    @pytest.mark.asyncio
    async def test_post_batch_sends_correct_payload(self):
        """Verify serialization of BatchConfig + ArticleTestCases."""
        from app.models.eval import (
            ArticleTestCase,
            ArticleType,
            BatchConfig,
            Difficulty,
            Fact,
            FactConfidence,
            FactPredicate,
            FactSet,
            GovernmentBranch,
            PerturbationType,
        )

        captured_body: dict = {}

        def handler(request: httpx.Request) -> httpx.Response:
            captured_body.update(json.loads(request.content))
            return httpx.Response(201, json={
                "id": captured_body.get("batchId"),
                "branch": "legislative",
                "modelUsed": "claude-sonnet-4-20250514",
                "articlesCount": 1,
            })

        client = _make_client({"/api/eval/datasets/batches": handler})

        batch_id = uuid4()
        config = BatchConfig(
            branch=GovernmentBranch.LEGISLATIVE,
            entity_count=1,
            model="claude-sonnet-4-20250514",
        )
        fact_set = FactSet(
            topic="Senator Test",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Sen. Test",
                    predicate=FactPredicate.PARTY_AFFILIATION,
                    object="Democratic",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="CONGRESS_GOV",
                )
            ],
        )
        test_case = ArticleTestCase(
            article_text="A test article.",
            article_type=ArticleType.NEWS_REPORT,
            source_facts=fact_set,
            is_faithful=True,
            model_used="claude-sonnet-4-20250514",
            tokens_used=250,
        )

        async with client:
            result = await client.post_batch(batch_id, config, [test_case])

        # Verify payload structure matches CreateBatchRequest
        assert captured_body["batchId"] == str(batch_id)
        assert captured_body["branch"] == "legislative"
        assert captured_body["modelUsed"] == "claude-sonnet-4-20250514"
        assert captured_body["articlesCount"] == 1
        assert captured_body["faithfulCount"] == 1
        assert captured_body["perturbedCount"] == 0
        assert captured_body["totalTokens"] == 250

        # Verify article entry
        articles = captured_body["articles"]
        assert len(articles) == 1
        article = articles[0]
        assert article["articleText"] == "A test article."
        assert article["articleType"] == "news_report"
        assert article["isFaithful"] is True
        assert article["perturbationType"] is None  # NONE → null
        assert article["difficulty"] == "MEDIUM"
        assert article["modelUsed"] == "claude-sonnet-4-20250514"
        assert article["tokensUsed"] == 250
        assert "sourceFacts" in article
        assert "groundTruth" in article

    @pytest.mark.asyncio
    async def test_post_batch_perturbed_article(self):
        """Verify perturbed articles serialize perturbation_type correctly."""
        from app.models.eval import (
            ArticleTestCase,
            ArticleType,
            BatchConfig,
            Difficulty,
            Fact,
            FactPredicate,
            FactSet,
            GovernmentBranch,
            PerturbationType,
        )

        captured_body: dict = {}

        def handler(request: httpx.Request) -> httpx.Response:
            captured_body.update(json.loads(request.content))
            return httpx.Response(201, json={"id": "test"})

        client = _make_client({"/api/eval/datasets/batches": handler})

        fact_set = FactSet(
            topic="Senator Test",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Sen. Test",
                    predicate=FactPredicate.PARTY_AFFILIATION,
                    object="Democratic",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="CONGRESS_GOV",
                )
            ],
        )
        test_case = ArticleTestCase(
            article_text="A perturbed article.",
            article_type=ArticleType.NEWS_REPORT,
            source_facts=fact_set,
            is_faithful=False,
            perturbation_type=PerturbationType.WRONG_PARTY,
            changed_facts=[{"predicate": "party_affiliation", "original_value": "Democratic", "perturbed_value": "Republican"}],
            expected_findings=["Incorrect party_affiliation: stated 'Republican', should be 'Democratic'"],
            difficulty=Difficulty.EASY,
            model_used="claude-sonnet-4-20250514",
            tokens_used=300,
        )

        config = BatchConfig(branch=GovernmentBranch.LEGISLATIVE)

        async with client:
            await client.post_batch(uuid4(), config, [test_case])

        article = captured_body["articles"][0]
        assert article["isFaithful"] is False
        assert article["perturbationType"] == "wrong_party"
        assert article["difficulty"] == "EASY"
        assert len(article["groundTruth"]["changed_facts"]) == 1
        assert len(article["groundTruth"]["expected_findings"]) == 1

    @pytest.mark.asyncio
    async def test_post_batch_server_error_raises(self):
        """Verify HTTP errors propagate correctly."""
        from app.models.eval import (
            ArticleTestCase,
            ArticleType,
            BatchConfig,
            Fact,
            FactPredicate,
            FactSet,
            GovernmentBranch,
        )

        client = _make_client({"/api/eval/datasets/batches": 500})

        fact_set = FactSet(
            topic="Test",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Test",
                    predicate=FactPredicate.PARTY_AFFILIATION,
                    object="D",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="TEST",
                )
            ],
        )
        tc = ArticleTestCase(
            article_text="Test",
            article_type=ArticleType.NEWS_REPORT,
            source_facts=fact_set,
            is_faithful=True,
        )
        config = BatchConfig()

        async with client:
            with pytest.raises(httpx.HTTPStatusError) as exc_info:
                await client.post_batch(uuid4(), config, [tc])
            assert exc_info.value.response.status_code == 500


# ---------------------------------------------------------------------------
# Config tests
# ---------------------------------------------------------------------------


class TestSettings:
    """Verify settings load with defaults."""

    def test_default_settings(self):
        from app.config import Settings

        s = Settings()
        assert s.backend_url == "http://localhost:8080"
        assert s.eval_default_model == "claude-sonnet-4-20250514"
        assert s.eval_rate_limit_rpm == 50
        assert s.eval_max_batch_size == 50
        assert s.eval_dry_run is False
