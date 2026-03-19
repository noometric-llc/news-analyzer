"""Tests for the EVAL facts API endpoints.

Uses unittest.mock to patch the FactSetBuilder so we test the HTTP
layer without hitting the backend API.
"""

from __future__ import annotations

from unittest.mock import AsyncMock, patch
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app.models.eval import (
    Fact,
    FactPredicate,
    FactSet,
    GovernmentBranch,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _sample_fact_set(
    topic: str = "Senator Test",
    branch: GovernmentBranch = GovernmentBranch.LEGISLATIVE,
) -> FactSet:
    return FactSet(
        topic=topic,
        primary_entity_id=uuid4(),
        branch=branch,
        facts=[
            Fact(
                subject="Test Subject",
                predicate=FactPredicate.PARTY_AFFILIATION,
                object="Democratic",
                entity_type="CongressionalMember",
                branch=branch,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Test Subject",
                predicate=FactPredicate.STATE,
                object="Pennsylvania",
                entity_type="CongressionalMember",
                branch=branch,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Test Subject",
                predicate=FactPredicate.CHAMBER,
                object="Senate",
                entity_type="CongressionalMember",
                branch=branch,
                data_source="CONGRESS_GOV",
            ),
        ],
    )


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def mock_builder() -> AsyncMock:
    """Mock FactSetBuilder for endpoint tests."""
    builder = AsyncMock()
    builder._client = AsyncMock()
    return builder


@pytest.fixture
def client(mock_builder: AsyncMock) -> TestClient:
    """TestClient with patched builder."""
    with patch(
        "app.api.eval.facts._get_builder", return_value=mock_builder
    ):
        from app.main import app

        yield TestClient(app)


# ---------------------------------------------------------------------------
# GET /eval/facts/{entity_type}/{id} tests
# ---------------------------------------------------------------------------


class TestGetEntityFacts:
    """Tests for the entity facts endpoint."""

    def test_get_member_facts_success(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        mock_builder.build_legislative_set.return_value = _sample_fact_set()

        response = client.get("/eval/facts/member/F000479")

        assert response.status_code == 200
        data = response.json()
        assert data["topic"] == "Senator Test"
        assert data["branch"] == "legislative"
        assert len(data["facts"]) == 3

    def test_get_presidency_facts_success(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        mock_builder.build_executive_set.return_value = _sample_fact_set(
            "Presidency of Biden", GovernmentBranch.EXECUTIVE
        )

        response = client.get("/eval/facts/presidency/some-uuid")

        assert response.status_code == 200
        assert response.json()["topic"] == "Presidency of Biden"

    def test_get_judge_facts_success(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        mock_builder.build_judicial_set.return_value = _sample_fact_set(
            "Judge Roberts", GovernmentBranch.JUDICIAL
        )

        response = client.get("/eval/facts/judge/some-uuid")

        assert response.status_code == 200
        assert response.json()["topic"] == "Judge Roberts"

    def test_entity_not_found_returns_404(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        mock_builder.build_legislative_set.return_value = None

        response = client.get("/eval/facts/member/INVALID")

        assert response.status_code == 404
        assert "not found" in response.json()["detail"]

    def test_invalid_entity_type_returns_422(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        response = client.get("/eval/facts/invalid_type/some-id")

        assert response.status_code == 422

    def test_client_closed_after_success(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        """Resource cleanup: client.close() must be called even on success."""
        mock_builder.build_legislative_set.return_value = _sample_fact_set()

        client.get("/eval/facts/member/F000479")

        mock_builder._client.close.assert_called_once()

    def test_client_closed_after_not_found(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        """Resource cleanup: client.close() must be called even on 404."""
        mock_builder.build_legislative_set.return_value = None

        client.get("/eval/facts/member/INVALID")

        mock_builder._client.close.assert_called_once()

    def test_builder_exception_returns_500(
        self, mock_builder: AsyncMock
    ):
        """Unhandled service errors should propagate as 500.

        TestClient re-raises exceptions by default. Setting
        raise_server_exceptions=False lets us see the actual 500 response
        that production ASGI would return.
        """
        mock_builder.build_legislative_set.side_effect = RuntimeError("connection lost")

        with patch(
            "app.api.eval.facts._get_builder", return_value=mock_builder
        ):
            from app.main import app

            with TestClient(app, raise_server_exceptions=False) as c:
                response = c.get("/eval/facts/member/F000479")

        assert response.status_code == 500

    def test_client_closed_after_exception(
        self, mock_builder: AsyncMock
    ):
        """Resource cleanup: client.close() must be called even on exception."""
        mock_builder.build_legislative_set.side_effect = RuntimeError("boom")

        with patch(
            "app.api.eval.facts._get_builder", return_value=mock_builder
        ):
            from app.main import app

            with TestClient(app, raise_server_exceptions=False) as c:
                c.get("/eval/facts/member/F000479")

        mock_builder._client.close.assert_called_once()


# ---------------------------------------------------------------------------
# GET /eval/facts/sample tests
# ---------------------------------------------------------------------------


class TestGetSampleFacts:
    """Tests for the sample facts endpoint."""

    def test_sample_returns_list(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        mock_builder.build_entity_pool.return_value = [
            _sample_fact_set("Entity 1"),
            _sample_fact_set("Entity 2"),
        ]

        response = client.get("/eval/facts/sample?count=2")

        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) == 2

    def test_sample_with_branch_filter(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        mock_builder.build_entity_pool.return_value = [
            _sample_fact_set("Member 1"),
        ]

        response = client.get("/eval/facts/sample?branch=legislative&count=1")

        assert response.status_code == 200
        assert len(response.json()) == 1
        call_args = mock_builder.build_entity_pool.call_args
        assert call_args.kwargs["branch"] == GovernmentBranch.LEGISLATIVE

    def test_sample_default_count(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        mock_builder.build_entity_pool.return_value = []

        response = client.get("/eval/facts/sample")

        assert response.status_code == 200
        call_args = mock_builder.build_entity_pool.call_args
        assert call_args.kwargs["count"] == 5

    def test_sample_count_validation(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        """Count must be between 1 and 50."""
        response = client.get("/eval/facts/sample?count=0")
        assert response.status_code == 422

        response = client.get("/eval/facts/sample?count=51")
        assert response.status_code == 422

    def test_sample_empty_result(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        mock_builder.build_entity_pool.return_value = []

        response = client.get("/eval/facts/sample")

        assert response.status_code == 200
        assert response.json() == []

    def test_sample_client_closed_after_success(
        self, client: TestClient, mock_builder: AsyncMock
    ):
        """Resource cleanup on sample endpoint."""
        mock_builder.build_entity_pool.return_value = []

        client.get("/eval/facts/sample")

        mock_builder._client.close.assert_called_once()

    def test_sample_builder_exception_returns_500(
        self, mock_builder: AsyncMock
    ):
        """Unhandled service errors on sample endpoint propagate as 500."""
        mock_builder.build_entity_pool.side_effect = RuntimeError("timeout")

        with patch(
            "app.api.eval.facts._get_builder", return_value=mock_builder
        ):
            from app.main import app

            with TestClient(app, raise_server_exceptions=False) as c:
                response = c.get("/eval/facts/sample")

        assert response.status_code == 500
        mock_builder._client.close.assert_called_once()
