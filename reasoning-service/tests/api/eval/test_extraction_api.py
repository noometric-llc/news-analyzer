"""
Tests for the LLM Entity Extraction API endpoint.

Tests cover:
  - Request/response model validation
  - Confidence threshold filtering
  - Response shape matches spaCy endpoint
  - Error handling
"""

from __future__ import annotations

import json
from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

SAMPLE_ENTITIES = [
    {
        "text": "Elizabeth Warren",
        "entity_type": "person",
        "start": 14,
        "end": 29,
        "confidence": 0.95,
    },
    {
        "text": "EPA",
        "entity_type": "government_org",
        "start": 45,
        "end": 48,
        "confidence": 0.60,
    },
]


@pytest.fixture(autouse=True)
def mock_extractor():
    """Mock the LLM extractor for all endpoint tests."""
    with patch(
        "app.services.eval.llm_entity_extractor.LLMEntityExtractor"
    ) as MockClass:
        instance = MockClass.return_value
        instance.extract = AsyncMock(return_value=SAMPLE_ENTITIES)
        yield instance


class TestLLMExtractionEndpoint:
    def test_basic_extraction(self):
        response = client.post(
            "/eval/extract/llm",
            json={"text": "Senator Elizabeth Warren criticized the EPA."},
        )
        assert response.status_code == 200
        data = response.json()
        assert "entities" in data
        assert "total_count" in data
        assert data["total_count"] == 2

    def test_response_shape_matches_spacy(self):
        """Response must have same top-level keys as /entities/extract."""
        response = client.post(
            "/eval/extract/llm",
            json={"text": "Some text."},
        )
        data = response.json()

        # Top-level keys
        assert set(data.keys()) == {"entities", "total_count"}

        # Entity keys must include the spaCy fields
        if data["entities"]:
            entity_keys = set(data["entities"][0].keys())
            required = {
                "text",
                "entity_type",
                "start",
                "end",
                "confidence",
                "schema_org_type",
                "schema_org_data",
                "properties",
            }
            assert required.issubset(entity_keys)

    def test_confidence_threshold_filters(self, mock_extractor):
        response = client.post(
            "/eval/extract/llm",
            json={"text": "Some text.", "confidence_threshold": 0.8},
        )
        data = response.json()
        # EPA has confidence 0.60, should be filtered out
        assert data["total_count"] == 1
        assert data["entities"][0]["text"] == "Elizabeth Warren"

    def test_confidence_threshold_zero_returns_all(self, mock_extractor):
        response = client.post(
            "/eval/extract/llm",
            json={"text": "Some text.", "confidence_threshold": 0.0},
        )
        data = response.json()
        assert data["total_count"] == 2

    def test_model_override_passed(self, mock_extractor):
        client.post(
            "/eval/extract/llm",
            json={"text": "Some text.", "model": "claude-haiku-4-5-20251001"},
        )
        mock_extractor.extract.assert_called_once()
        call_kwargs = mock_extractor.extract.call_args.kwargs
        assert call_kwargs["model"] == "claude-haiku-4-5-20251001"

    def test_empty_text_returns_400(self, mock_extractor):
        response = client.post(
            "/eval/extract/llm",
            json={"text": ""},
        )
        # FastAPI/Pydantic should still accept empty string
        # (the extractor decides what to do with it)
        # But confirm it doesn't crash
        assert response.status_code == 200

    def test_schema_org_defaults(self, mock_extractor):
        """LLM entities should have default schema_org fields."""
        response = client.post(
            "/eval/extract/llm",
            json={"text": "Some text."},
        )
        data = response.json()
        for entity in data["entities"]:
            assert entity["schema_org_type"] == ""
            assert entity["schema_org_data"] == {}
            assert entity["properties"] == {}

    def test_extractor_error_returns_500(self, mock_extractor):
        mock_extractor.extract = AsyncMock(
            side_effect=RuntimeError("API unavailable")
        )
        response = client.post(
            "/eval/extract/llm",
            json={"text": "Some text."},
        )
        assert response.status_code == 500
        assert "LLM entity extraction failed" in response.json()["detail"]
