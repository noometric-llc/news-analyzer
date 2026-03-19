"""
Tests for the EVAL Articles API endpoints.

Tests mock the ArticleGenerator to avoid real API calls.
Covers: POST /eval/articles/generate, GET /eval/articles/{id} (stub).
"""

from __future__ import annotations

from unittest.mock import AsyncMock, patch
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.models.eval import (
    ArticleType,
    Fact,
    FactPredicate,
    FactSet,
    GovernmentBranch,
)


@pytest.fixture
def client():
    return TestClient(app)


def _fact_set_payload() -> dict:
    """A FactSet serialized as JSON for request bodies."""
    return {
        "topic": "Senator Test",
        "primary_entity_id": str(uuid4()),
        "branch": "legislative",
        "facts": [
            {
                "subject": "Test Person",
                "predicate": "party_affiliation",
                "object": "Democratic",
                "entity_type": "CongressionalMember",
                "branch": "legislative",
                "data_source": "CONGRESS_GOV",
                "confidence": "tier_1",
            },
            {
                "subject": "Test Person",
                "predicate": "state",
                "object": "Ohio",
                "entity_type": "CongressionalMember",
                "branch": "legislative",
                "data_source": "CONGRESS_GOV",
                "confidence": "tier_1",
            },
        ],
    }


class TestGenerateArticle:
    @patch("app.api.eval.articles.ArticleGenerator")
    def test_generate_returns_article(self, mock_gen_class, client):
        mock_gen = AsyncMock()
        mock_gen.generate.return_value = ("A generated article.", 300)
        mock_gen_class.return_value = mock_gen

        with patch("app.api.eval.articles.settings") as mock_settings:
            mock_settings.eval_dry_run = False

            response = client.post(
                "/eval/articles/generate",
                json={
                    "fact_set": _fact_set_payload(),
                    "article_type": "news_report",
                },
            )

        assert response.status_code == 200
        data = response.json()
        assert data["article_text"] == "A generated article."
        assert data["tokens_used"] == 300
        assert data["article_type"] == "news_report"

    @patch("app.api.eval.articles.ArticleGenerator")
    def test_generate_dry_run(self, mock_gen_class, client):
        mock_gen = AsyncMock()
        mock_gen.generate.return_value = ("[DRY RUN] Prompt: ...", 0)
        mock_gen_class.return_value = mock_gen

        with patch("app.api.eval.articles.settings") as mock_settings:
            mock_settings.eval_dry_run = True

            response = client.post(
                "/eval/articles/generate",
                json={
                    "fact_set": _fact_set_payload(),
                    "article_type": "opinion",
                },
            )

        assert response.status_code == 200
        data = response.json()
        assert data["dry_run"] is True
        assert data["tokens_used"] == 0

    @patch("app.api.eval.articles.ArticleGenerator")
    def test_generate_defaults_to_news_report(self, mock_gen_class, client):
        mock_gen = AsyncMock()
        mock_gen.generate.return_value = ("Article.", 100)
        mock_gen_class.return_value = mock_gen

        with patch("app.api.eval.articles.settings") as mock_settings:
            mock_settings.eval_dry_run = False

            response = client.post(
                "/eval/articles/generate",
                json={"fact_set": _fact_set_payload()},
            )

        assert response.status_code == 200
        assert response.json()["article_type"] == "news_report"

    def test_generate_invalid_article_type(self, client):
        response = client.post(
            "/eval/articles/generate",
            json={
                "fact_set": _fact_set_payload(),
                "article_type": "invalid_type",
            },
        )
        assert response.status_code == 422

    def test_generate_missing_fact_set(self, client):
        response = client.post(
            "/eval/articles/generate",
            json={"article_type": "news_report"},
        )
        assert response.status_code == 422

    @patch("app.api.eval.articles.ArticleGenerator")
    def test_generate_error_returns_500(self, mock_gen_class):
        mock_gen = AsyncMock()
        mock_gen.generate.side_effect = RuntimeError("API error")
        mock_gen_class.return_value = mock_gen

        error_client = TestClient(app, raise_server_exceptions=False)
        response = error_client.post(
            "/eval/articles/generate",
            json={
                "fact_set": _fact_set_payload(),
                "article_type": "news_report",
            },
        )
        assert response.status_code == 500
        assert "API error" in response.json()["detail"]


class TestGetArticle:
    def test_stub_returns_501(self, client):
        article_id = str(uuid4())
        response = client.get(f"/eval/articles/{article_id}")
        assert response.status_code == 501
        assert "EVAL-1.3" in response.json()["detail"]

    def test_invalid_uuid_returns_422(self, client):
        response = client.get("/eval/articles/not-a-uuid")
        assert response.status_code == 422
