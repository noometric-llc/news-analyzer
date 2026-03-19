"""Tests for the FactSetBuilder service."""

from __future__ import annotations

from unittest.mock import AsyncMock
from uuid import uuid4

import pytest

from app.models.eval import (
    Fact,
    FactConfidence,
    FactPredicate,
    FactSet,
    GovernmentBranch,
)
from app.services.eval.fact_set_builder import FactSetBuilder


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _make_fact_set(
    topic: str = "Test Entity",
    branch: GovernmentBranch = GovernmentBranch.LEGISLATIVE,
    num_facts: int = 4,
) -> FactSet:
    """Build a FactSet with the given number of facts."""
    facts = []
    predicates = [
        FactPredicate.PARTY_AFFILIATION,
        FactPredicate.STATE,
        FactPredicate.CHAMBER,
        FactPredicate.COMMITTEE_MEMBERSHIP,
        FactPredicate.TERM_START,
    ]
    for i in range(num_facts):
        facts.append(Fact(
            subject=topic,
            predicate=predicates[i % len(predicates)],
            object=f"value_{i}",
            entity_type="CongressionalMember",
            branch=branch,
            data_source="TEST",
        ))
    return FactSet(
        topic=topic,
        branch=branch,
        facts=facts,
    )


def _spring_page(content: list[dict], total: int | None = None) -> dict:
    total = total if total is not None else len(content)
    return {
        "content": content,
        "totalElements": total,
        "totalPages": 1,
    }


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def mock_extractor() -> AsyncMock:
    return AsyncMock()


@pytest.fixture
def mock_client() -> AsyncMock:
    return AsyncMock()


@pytest.fixture
def builder(mock_extractor: AsyncMock, mock_client: AsyncMock) -> FactSetBuilder:
    return FactSetBuilder(mock_extractor, mock_client)


# ---------------------------------------------------------------------------
# Branch builder tests
# ---------------------------------------------------------------------------


class TestBranchBuilders:
    """Tests for build_legislative_set, build_executive_set, build_judicial_set."""

    @pytest.mark.asyncio
    async def test_build_legislative_set_delegates_to_extractor(
        self, builder: FactSetBuilder, mock_extractor: AsyncMock
    ):
        expected = _make_fact_set("Senator Test")
        mock_extractor.extract_member_facts.return_value = expected

        result = await builder.build_legislative_set("T000001")

        mock_extractor.extract_member_facts.assert_called_once_with("T000001")
        assert result == expected

    @pytest.mark.asyncio
    async def test_build_executive_set_delegates_to_extractor(
        self, builder: FactSetBuilder, mock_extractor: AsyncMock
    ):
        expected = _make_fact_set(
            "President Test", GovernmentBranch.EXECUTIVE, 3
        )
        mock_extractor.extract_presidency_facts.return_value = expected

        result = await builder.build_executive_set("some-id")

        mock_extractor.extract_presidency_facts.assert_called_once_with("some-id")
        assert result == expected

    @pytest.mark.asyncio
    async def test_build_judicial_set_delegates_to_extractor(
        self, builder: FactSetBuilder, mock_extractor: AsyncMock
    ):
        expected = _make_fact_set(
            "Judge Test", GovernmentBranch.JUDICIAL, 3
        )
        mock_extractor.extract_judge_facts.return_value = expected

        result = await builder.build_judicial_set("judge-id")

        mock_extractor.extract_judge_facts.assert_called_once_with("judge-id")
        assert result == expected

    @pytest.mark.asyncio
    async def test_build_legislative_returns_none_when_not_found(
        self, builder: FactSetBuilder, mock_extractor: AsyncMock
    ):
        mock_extractor.extract_member_facts.return_value = None

        result = await builder.build_legislative_set("INVALID")
        assert result is None


# ---------------------------------------------------------------------------
# Topic-based assembly tests (AC4)
# ---------------------------------------------------------------------------


class TestCommitteeTopic:
    """Tests for topic-based FactSet assembly."""

    @pytest.mark.asyncio
    async def test_build_committee_topic_finds_matching_members(
        self, builder: FactSetBuilder, mock_extractor: AsyncMock, mock_client: AsyncMock
    ):
        # Setup: 2 members, one on Banking, one not
        mock_client.get_members.return_value = _spring_page([
            {"bioguideId": "M001", "fullName": "Member A"},
            {"bioguideId": "M002", "fullName": "Member B"},
        ])

        banking_member = FactSet(
            topic="Member A",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Member A",
                    predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
                    object="Banking, Housing, and Urban Affairs",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="CONGRESS_GOV",
                ),
                Fact(
                    subject="Member A",
                    predicate=FactPredicate.PARTY_AFFILIATION,
                    object="Democratic",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="CONGRESS_GOV",
                ),
            ],
        )
        non_banking_member = FactSet(
            topic="Member B",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Member B",
                    predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
                    object="Agriculture",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="CONGRESS_GOV",
                ),
            ],
        )

        mock_extractor.extract_member_facts.side_effect = [
            banking_member,
            non_banking_member,
        ]

        result = await builder.build_committee_topic("Banking")

        assert result is not None
        assert "Banking" in result.topic
        # Should contain facts from Member A (banking member) but not Member B
        subjects = {f.subject for f in result.facts}
        assert "Member A" in subjects

    @pytest.mark.asyncio
    async def test_build_committee_topic_returns_none_when_no_members(
        self, builder: FactSetBuilder, mock_client: AsyncMock
    ):
        mock_client.get_members.return_value = _spring_page([])

        result = await builder.build_committee_topic("Nonexistent Committee")
        assert result is None


# ---------------------------------------------------------------------------
# Entity pool tests
# ---------------------------------------------------------------------------


class TestEntityPool:
    """Tests for build_entity_pool."""

    @pytest.mark.asyncio
    async def test_pool_requests_extra_for_conflation(
        self, builder: FactSetBuilder, mock_extractor: AsyncMock
    ):
        """Pool should request count+5 entities for CONFLATE_INDIVIDUALS."""
        mock_extractor.extract_random_sample.return_value = [
            _make_fact_set(f"Entity {i}") for i in range(15)
        ]

        result = await builder.build_entity_pool(
            branch=GovernmentBranch.LEGISLATIVE, count=10
        )

        # Verify it requested count+5
        call_args = mock_extractor.extract_random_sample.call_args
        assert call_args.kwargs["count"] == 15

    @pytest.mark.asyncio
    async def test_pool_filters_sparse_fact_sets(
        self, builder: FactSetBuilder, mock_extractor: AsyncMock
    ):
        """FactSets with fewer than 3 facts should be excluded."""
        mock_extractor.extract_random_sample.return_value = [
            _make_fact_set("Rich Entity", num_facts=5),
            _make_fact_set("Sparse Entity", num_facts=1),  # too few
            _make_fact_set("Also Rich", num_facts=4),
        ]

        result = await builder.build_entity_pool(count=5)

        assert len(result) == 2
        topics = {fs.topic for fs in result}
        assert "Sparse Entity" not in topics

    @pytest.mark.asyncio
    async def test_pool_handles_empty_results(
        self, builder: FactSetBuilder, mock_extractor: AsyncMock
    ):
        mock_extractor.extract_random_sample.return_value = []

        result = await builder.build_entity_pool(count=10)
        assert result == []
