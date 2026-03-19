"""Tests for the FactExtractor service.

Uses a mock BackendClient to verify that API responses are correctly
mapped to Fact tuples with proper predicates, branches, and sources.
"""

from __future__ import annotations

from unittest.mock import AsyncMock, patch
from uuid import uuid4

import pytest

from app.models.eval import (
    Fact,
    FactPredicate,
    FactSet,
    GovernmentBranch,
)
from app.services.eval.fact_extractor import FactExtractor


# ---------------------------------------------------------------------------
# Mock data builders
# ---------------------------------------------------------------------------

MEMBER_ID = str(uuid4())
PRESIDENCY_ID = str(uuid4())
JUDGE_ID = str(uuid4())


def _mock_member(bioguide_id: str = "F000479") -> dict:
    return {
        "id": MEMBER_ID,
        "bioguideId": bioguide_id,
        "fullName": "John Fetterman",
        "party": "Democratic",
        "state": "Pennsylvania",
        "chamber": "Senate",
        "dataSource": "CONGRESS_GOV",
    }


def _mock_committees_page() -> dict:
    return {
        "content": [
            {
                "committee": {"name": "Banking, Housing, and Urban Affairs"},
                "role": "MEMBER",
            },
            {
                "committee": {"name": "Agriculture, Nutrition, and Forestry"},
                "role": "MEMBER",
            },
        ],
        "totalElements": 2,
        "totalPages": 1,
    }


def _mock_terms() -> list[dict]:
    return [
        {"startDate": "2023-01-03", "endDate": None, "chamber": "Senate"},
    ]


def _mock_presidency() -> dict:
    return {
        "id": PRESIDENCY_ID,
        "individualId": str(uuid4()),
        "presidentFullName": "Joe Biden",
        "number": 46,
        "party": "Democratic",
        "startDate": "2021-01-20",
        "endDate": None,
    }


def _mock_administration() -> dict:
    return {
        "presidencyId": PRESIDENCY_ID,
        "presidencyNumber": 46,
        "vicePresidents": [
            {"fullName": "Kamala Harris", "individualId": str(uuid4())},
        ],
        "chiefsOfStaff": [
            {"fullName": "Jeff Zients", "individualId": str(uuid4())},
        ],
        "cabinetMembers": [
            {
                "fullName": "Janet Yellen",
                "individualId": str(uuid4()),
                "positionTitle": "Secretary of the Treasury",
                "departmentName": "Department of the Treasury",
            },
            {
                "fullName": "Lloyd Austin",
                "individualId": str(uuid4()),
                "positionTitle": "Secretary of Defense",
                "departmentName": "Department of Defense",
            },
        ],
    }


def _mock_judge() -> dict:
    return {
        "id": JUDGE_ID,
        "fullName": "John Roberts",
        "firstName": "John",
        "lastName": "Roberts",
        "courtName": "Supreme Court of the United States",
        "courtType": "SUPREME",
        "appointingPresident": "George W. Bush",
        "confirmationDate": "2005-09-29",
        "judicialStatus": "ACTIVE",
    }


def _spring_page(content: list[dict], total: int | None = None) -> dict:
    total = total if total is not None else len(content)
    return {
        "content": content,
        "totalElements": total,
        "totalPages": max(1, (total + 99) // 100),
        "number": 0,
        "size": 100,
    }


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def mock_client() -> AsyncMock:
    """Create a fully mocked BackendClient."""
    client = AsyncMock()
    return client


@pytest.fixture
def extractor(mock_client: AsyncMock) -> FactExtractor:
    return FactExtractor(mock_client)


# ---------------------------------------------------------------------------
# Legislative extraction tests
# ---------------------------------------------------------------------------


class TestExtractMemberFacts:
    """Tests for congressional member fact extraction."""

    @pytest.mark.asyncio
    async def test_extracts_party_state_chamber(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_member.return_value = _mock_member()
        mock_client.get_member_committees.return_value = _spring_page([])
        mock_client.get_member_terms.return_value = []

        result = await extractor.extract_member_facts("F000479")

        assert result is not None
        assert result.branch == GovernmentBranch.LEGISLATIVE
        assert result.get_fact(FactPredicate.PARTY_AFFILIATION).object == "Democratic"
        assert result.get_fact(FactPredicate.STATE).object == "Pennsylvania"
        assert result.get_fact(FactPredicate.CHAMBER).object == "Senate"

    @pytest.mark.asyncio
    async def test_extracts_committee_memberships(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_member.return_value = _mock_member()
        mock_client.get_member_committees.return_value = _mock_committees_page()
        mock_client.get_member_terms.return_value = []

        result = await extractor.extract_member_facts("F000479")

        committees = result.get_facts(FactPredicate.COMMITTEE_MEMBERSHIP)
        assert len(committees) == 2
        names = {c.object for c in committees}
        assert "Banking, Housing, and Urban Affairs" in names
        assert "Agriculture, Nutrition, and Forestry" in names

    @pytest.mark.asyncio
    async def test_extracts_term_start(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_member.return_value = _mock_member()
        mock_client.get_member_committees.return_value = _spring_page([])
        mock_client.get_member_terms.return_value = _mock_terms()

        result = await extractor.extract_member_facts("F000479")

        term_fact = result.get_fact(FactPredicate.TERM_START)
        assert term_fact is not None
        assert term_fact.object == "2023-01-03"

    @pytest.mark.asyncio
    async def test_all_facts_have_data_source(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        """AC2: Every fact must have data_source populated."""
        mock_client.get_member.return_value = _mock_member()
        mock_client.get_member_committees.return_value = _mock_committees_page()
        mock_client.get_member_terms.return_value = _mock_terms()

        result = await extractor.extract_member_facts("F000479")

        for fact in result.facts:
            assert fact.data_source, f"Missing data_source on {fact.predicate}"

    @pytest.mark.asyncio
    async def test_returns_none_for_missing_member(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_member.return_value = None
        mock_client.get_member_committees.return_value = _spring_page([])
        mock_client.get_member_terms.return_value = []

        result = await extractor.extract_member_facts("INVALID")
        assert result is None

    @pytest.mark.asyncio
    async def test_topic_includes_chamber_and_name(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_member.return_value = _mock_member()
        mock_client.get_member_committees.return_value = _spring_page([])
        mock_client.get_member_terms.return_value = []

        result = await extractor.extract_member_facts("F000479")
        assert "Senate" in result.topic
        assert "John Fetterman" in result.topic


# ---------------------------------------------------------------------------
# Executive extraction tests
# ---------------------------------------------------------------------------


class TestExtractPresidencyFacts:
    """Tests for presidency/administration fact extraction."""

    @pytest.mark.asyncio
    async def test_extracts_presidency_number_and_party(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_presidencies.return_value = [_mock_presidency()]
        mock_client.get_administration.return_value = _mock_administration()

        result = await extractor.extract_presidency_facts(PRESIDENCY_ID)

        assert result is not None
        assert result.branch == GovernmentBranch.EXECUTIVE
        assert result.get_fact(FactPredicate.PRESIDENCY_NUMBER).object == "46"
        assert result.get_fact(FactPredicate.PARTY_AFFILIATION).object == "Democratic"

    @pytest.mark.asyncio
    async def test_extracts_vice_president(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_presidencies.return_value = [_mock_presidency()]
        mock_client.get_administration.return_value = _mock_administration()

        result = await extractor.extract_presidency_facts(PRESIDENCY_ID)

        vp_facts = result.get_facts(FactPredicate.VICE_PRESIDENT)
        assert len(vp_facts) == 1
        assert vp_facts[0].object == "Kamala Harris"

    @pytest.mark.asyncio
    async def test_extracts_chief_of_staff(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_presidencies.return_value = [_mock_presidency()]
        mock_client.get_administration.return_value = _mock_administration()

        result = await extractor.extract_presidency_facts(PRESIDENCY_ID)

        cos_facts = result.get_facts(FactPredicate.CHIEF_OF_STAFF)
        assert len(cos_facts) == 1
        assert cos_facts[0].object == "Jeff Zients"

    @pytest.mark.asyncio
    async def test_extracts_cabinet_members(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_presidencies.return_value = [_mock_presidency()]
        mock_client.get_administration.return_value = _mock_administration()

        result = await extractor.extract_presidency_facts(PRESIDENCY_ID)

        cabinet_facts = result.get_facts(FactPredicate.CABINET_POSITION)
        assert len(cabinet_facts) == 2
        positions = {f.object for f in cabinet_facts}
        assert "Secretary of the Treasury" in positions
        assert "Secretary of Defense" in positions

    @pytest.mark.asyncio
    async def test_returns_none_for_missing_presidency(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_presidencies.return_value = []
        mock_client.get_administration.return_value = None

        result = await extractor.extract_presidency_facts("nonexistent-id")
        assert result is None

    @pytest.mark.asyncio
    async def test_all_facts_have_data_source(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_presidencies.return_value = [_mock_presidency()]
        mock_client.get_administration.return_value = _mock_administration()

        result = await extractor.extract_presidency_facts(PRESIDENCY_ID)

        for fact in result.facts:
            assert fact.data_source, f"Missing data_source on {fact.predicate}"


# ---------------------------------------------------------------------------
# Judicial extraction tests
# ---------------------------------------------------------------------------


class TestExtractJudgeFacts:
    """Tests for judge fact extraction."""

    @pytest.mark.asyncio
    async def test_extracts_court_and_level(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_judges.return_value = [_mock_judge()]

        result = await extractor.extract_judge_facts(JUDGE_ID)

        assert result is not None
        assert result.branch == GovernmentBranch.JUDICIAL
        assert result.get_fact(FactPredicate.COURT).object == "Supreme Court of the United States"
        assert result.get_fact(FactPredicate.COURT_LEVEL).object == "SUPREME"

    @pytest.mark.asyncio
    async def test_extracts_appointing_president(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_judges.return_value = [_mock_judge()]

        result = await extractor.extract_judge_facts(JUDGE_ID)

        ap = result.get_fact(FactPredicate.APPOINTING_PRESIDENT)
        assert ap is not None
        assert ap.object == "George W. Bush"

    @pytest.mark.asyncio
    async def test_extracts_confirmation_date(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_judges.return_value = [_mock_judge()]

        result = await extractor.extract_judge_facts(JUDGE_ID)

        cd = result.get_fact(FactPredicate.CONFIRMATION_DATE)
        assert cd is not None
        assert cd.object == "2005-09-29"

    @pytest.mark.asyncio
    async def test_returns_none_for_missing_judge(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_judges.return_value = []

        result = await extractor.extract_judge_facts("nonexistent-id")
        assert result is None

    @pytest.mark.asyncio
    async def test_all_facts_have_data_source(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_all_judges.return_value = [_mock_judge()]

        result = await extractor.extract_judge_facts(JUDGE_ID)

        for fact in result.facts:
            assert fact.data_source, f"Missing data_source on {fact.predicate}"


# ---------------------------------------------------------------------------
# Random sampling tests
# ---------------------------------------------------------------------------


class TestRandomSample:
    """Tests for the random sampling method."""

    @pytest.mark.asyncio
    async def test_sample_legislative_only(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_members.return_value = _spring_page(
            [_mock_member(f"M{i:06d}") for i in range(5)],
            total=5,
        )
        # Mock the individual member calls
        mock_client.get_member.return_value = _mock_member()
        mock_client.get_member_committees.return_value = _spring_page([])
        mock_client.get_member_terms.return_value = []

        result = await extractor.extract_random_sample(
            branch=GovernmentBranch.LEGISLATIVE, count=3
        )

        assert len(result) <= 3
        for fs in result:
            assert fs.branch == GovernmentBranch.LEGISLATIVE

    @pytest.mark.asyncio
    async def test_sample_returns_empty_on_no_data(
        self, extractor: FactExtractor, mock_client: AsyncMock
    ):
        mock_client.get_members.return_value = _spring_page([], total=0)

        result = await extractor.extract_random_sample(
            branch=GovernmentBranch.LEGISLATIVE, count=5
        )

        assert result == []
