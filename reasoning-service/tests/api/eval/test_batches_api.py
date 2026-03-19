"""
Tests for the EVAL Batches API endpoints.

Tests mock the BatchOrchestrator to avoid real API/DB calls.
Covers: POST /eval/batches, GET /eval/batches/{id}/status (stub).
"""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.models.eval import BatchConfig, BatchResult


@pytest.fixture
def client():
    return TestClient(app)


def _mock_batch_result() -> BatchResult:
    return BatchResult(
        batch_id=uuid4(),
        articles_generated=7,
        faithful_count=1,
        perturbed_count=6,
        total_tokens=2100,
        model_used="test-model",
        duration_seconds=5.0,
        errors=[],
    )


class TestRunBatch:
    @patch("app.api.eval.batches._build_orchestrator")
    def test_run_batch_returns_result(self, mock_build, client):
        mock_orchestrator = AsyncMock()
        mock_orchestrator.run_batch.return_value = _mock_batch_result()
        mock_client = AsyncMock()
        mock_build.return_value = (mock_orchestrator, mock_client)

        response = client.post(
            "/eval/batches",
            json={
                "entity_count": 1,
                "perturbation_types": ["wrong_party"],
                "article_types": ["news_report"],
                "model": "test-model",
                "dry_run": True,
            },
        )

        assert response.status_code == 200
        data = response.json()
        assert data["articles_generated"] == 7
        assert data["faithful_count"] == 1
        assert data["perturbed_count"] == 6
        assert data["total_tokens"] == 2100

    @patch("app.api.eval.batches._build_orchestrator")
    def test_run_batch_closes_client(self, mock_build, client):
        mock_orchestrator = AsyncMock()
        mock_orchestrator.run_batch.return_value = _mock_batch_result()
        mock_client = AsyncMock()
        mock_build.return_value = (mock_orchestrator, mock_client)

        client.post(
            "/eval/batches",
            json={"entity_count": 1, "dry_run": True},
        )

        mock_client.close.assert_called_once()

    @patch("app.api.eval.batches._build_orchestrator")
    def test_run_batch_closes_client_on_error(self, mock_build):
        mock_orchestrator = AsyncMock()
        mock_orchestrator.run_batch.side_effect = RuntimeError("Boom")
        mock_client = AsyncMock()
        mock_build.return_value = (mock_orchestrator, mock_client)

        error_client = TestClient(app, raise_server_exceptions=False)
        response = error_client.post(
            "/eval/batches",
            json={"entity_count": 1, "dry_run": True},
        )

        assert response.status_code == 500
        mock_client.close.assert_called_once()

    @patch("app.api.eval.batches._build_orchestrator")
    def test_run_batch_default_config(self, mock_build, client):
        mock_orchestrator = AsyncMock()
        mock_orchestrator.run_batch.return_value = _mock_batch_result()
        mock_client = AsyncMock()
        mock_build.return_value = (mock_orchestrator, mock_client)

        response = client.post("/eval/batches", json={})
        assert response.status_code == 200

        # Verify defaults were used
        call_args = mock_orchestrator.run_batch.call_args[0][0]
        assert call_args.entity_count == 10
        assert call_args.dry_run is False

    def test_invalid_perturbation_type_returns_422(self, client):
        response = client.post(
            "/eval/batches",
            json={"perturbation_types": ["nonexistent_type"]},
        )
        assert response.status_code == 422

    def test_invalid_article_type_returns_422(self, client):
        response = client.post(
            "/eval/batches",
            json={"article_types": ["invalid"]},
        )
        assert response.status_code == 422


class TestGetBatchStatus:
    def test_stub_returns_501(self, client):
        batch_id = str(uuid4())
        response = client.get(f"/eval/batches/{batch_id}/status")
        assert response.status_code == 501
        assert "EVAL-1.3" in response.json()["detail"]

    def test_invalid_uuid_returns_422(self, client):
        response = client.get("/eval/batches/not-a-uuid/status")
        assert response.status_code == 422
