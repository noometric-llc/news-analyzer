"""
Fact Extractor — transforms backend API responses into structured Fact tuples.

This is the core data extraction layer for the EVAL pipeline. Each extraction
method maps raw DTO fields from the backend API into typed Fact objects with
the correct FactPredicate, GovernmentBranch, and data_source attribution.
"""

from __future__ import annotations

import asyncio
import logging
import random
from uuid import UUID

from app.clients.backend_client import BackendClient
from app.models.eval import (
    Fact,
    FactConfidence,
    FactPredicate,
    FactSet,
    GovernmentBranch,
)

logger = logging.getLogger(__name__)


class FactExtractor:
    """Extracts structured facts from backend KB data.

    Each ``extract_*`` method queries the BackendClient, maps response
    fields to Fact tuples, and returns a FactSet.
    """

    def __init__(self, client: BackendClient):
        self._client = client

    # ------------------------------------------------------------------
    # Legislative — Congressional Members
    # ------------------------------------------------------------------

    async def extract_member_facts(self, bioguide_id: str) -> FactSet | None:
        """Extract all facts about a congressional member.

        Makes concurrent API calls for member details, committees, and terms.
        Returns None if the member is not found.
        """
        # Concurrent fetch — async I/O payoff
        member, committees_page, terms = await asyncio.gather(
            self._client.get_member(bioguide_id),
            self._client.get_member_committees(bioguide_id),
            self._client.get_member_terms(bioguide_id),
        )

        if member is None:
            return None

        individual_id = _parse_uuid(member.get("id"))
        subject = member.get("fullName", "Unknown")
        data_source = member.get("dataSource", "CONGRESS_GOV")

        facts: list[Fact] = []

        # Party affiliation
        if member.get("party"):
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.PARTY_AFFILIATION,
                object=member["party"],
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source=data_source,
            ))

        # State
        if member.get("state"):
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.STATE,
                object=member["state"],
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source=data_source,
            ))

        # Chamber (Senate / House)
        if member.get("chamber"):
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.CHAMBER,
                object=member["chamber"],
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source=data_source,
            ))

        # Committee memberships
        committees = committees_page.get("content", []) if committees_page else []
        for cm in committees:
            committee_name = None
            # Handle both nested object and flat field patterns
            if isinstance(cm.get("committee"), dict):
                committee_name = cm["committee"].get("name")
            elif cm.get("committeeName"):
                committee_name = cm["committeeName"]

            if committee_name:
                facts.append(Fact(
                    subject=subject,
                    subject_id=individual_id,
                    predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
                    object=committee_name,
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source=data_source,
                ))

        # Terms — extract temporal info for the most recent term
        if terms:
            latest_term = terms[0]  # Backend returns most recent first
            if latest_term.get("startDate"):
                facts.append(Fact(
                    subject=subject,
                    subject_id=individual_id,
                    predicate=FactPredicate.TERM_START,
                    object=str(latest_term["startDate"]),
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source=data_source,
                ))

        return FactSet(
            topic=f"{member.get('chamber', 'Congress')} member {subject}",
            primary_entity_id=individual_id,
            branch=GovernmentBranch.LEGISLATIVE,
            facts=facts,
        )

    # ------------------------------------------------------------------
    # Executive — Presidencies & Administration
    # ------------------------------------------------------------------

    async def extract_presidency_facts(self, presidency_id: str) -> FactSet | None:
        """Extract all facts about a presidency and its administration.

        Makes concurrent API calls for presidency lookup and administration.
        Returns None if the presidency is not found.
        """
        # Concurrent fetch — presidency lookup and administration details
        presidency, admin = await asyncio.gather(
            self._find_presidency_by_id(presidency_id),
            self._client.get_administration(presidency_id),
        )

        if presidency is None or admin is None:
            return None

        individual_id = _parse_uuid(presidency.get("individualId"))
        subject = presidency.get("presidentFullName", "Unknown")

        facts: list[Fact] = []

        # Presidency number
        if presidency.get("number") is not None:
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.PRESIDENCY_NUMBER,
                object=str(presidency["number"]),
                entity_type="Presidency",
                branch=GovernmentBranch.EXECUTIVE,
                data_source="STATIC_SEED",
            ))

        # Party
        if presidency.get("party"):
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.PARTY_AFFILIATION,
                object=presidency["party"],
                entity_type="Presidency",
                branch=GovernmentBranch.EXECUTIVE,
                data_source="STATIC_SEED",
            ))

        # Vice Presidents (from administration)
        for vp in admin.get("vicePresidents", []):
            vp_name = vp.get("fullName")
            if vp_name:
                facts.append(Fact(
                    subject=subject,
                    subject_id=individual_id,
                    predicate=FactPredicate.VICE_PRESIDENT,
                    object=vp_name,
                    entity_type="Presidency",
                    branch=GovernmentBranch.EXECUTIVE,
                    data_source="STATIC_SEED",
                ))

        # Chiefs of Staff
        for cos in admin.get("chiefsOfStaff", []):
            cos_name = cos.get("fullName")
            if cos_name:
                facts.append(Fact(
                    subject=subject,
                    subject_id=individual_id,
                    predicate=FactPredicate.CHIEF_OF_STAFF,
                    object=cos_name,
                    entity_type="Presidency",
                    branch=GovernmentBranch.EXECUTIVE,
                    data_source="STATIC_SEED",
                ))

        # Cabinet members
        for cab in admin.get("cabinetMembers", []):
            position = cab.get("positionTitle", "")
            name = cab.get("fullName", "")
            if position and name:
                facts.append(Fact(
                    subject=name,
                    subject_id=_parse_uuid(cab.get("individualId")),
                    predicate=FactPredicate.CABINET_POSITION,
                    object=position,
                    entity_type="Presidency",
                    branch=GovernmentBranch.EXECUTIVE,
                    data_source="STATIC_SEED",
                ))

        return FactSet(
            topic=f"Presidency of {subject}",
            primary_entity_id=individual_id,
            branch=GovernmentBranch.EXECUTIVE,
            facts=facts,
        )

    async def _find_presidency_by_id(self, presidency_id: str) -> dict | None:
        """Find a presidency by UUID from the paginated list."""
        all_presidencies = await self._client.get_all_presidencies()
        for p in all_presidencies:
            if str(p.get("id")) == presidency_id:
                return p
        return None

    # ------------------------------------------------------------------
    # Judicial — Judges
    # ------------------------------------------------------------------

    async def extract_judge_facts(self, judge_id: str) -> FactSet | None:
        """Extract all facts about a federal judge.

        Currently judges are fetched from the list endpoint since there
        is no single-judge GET endpoint. We find by ID in the paginated list.
        """
        judge = await self._find_judge_by_id(judge_id)
        if judge is None:
            return None

        individual_id = _parse_uuid(judge.get("id"))
        subject = judge.get("fullName", "Unknown")

        facts: list[Fact] = []

        # Court name
        if judge.get("courtName"):
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.COURT,
                object=judge["courtName"],
                entity_type="Judge",
                branch=GovernmentBranch.JUDICIAL,
                data_source="STATIC_SEED",
            ))

        # Court level (SUPREME, APPEALS, DISTRICT)
        if judge.get("courtType"):
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.COURT_LEVEL,
                object=judge["courtType"],
                entity_type="Judge",
                branch=GovernmentBranch.JUDICIAL,
                data_source="STATIC_SEED",
            ))

        # Appointing president
        if judge.get("appointingPresident"):
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.APPOINTING_PRESIDENT,
                object=judge["appointingPresident"],
                entity_type="Judge",
                branch=GovernmentBranch.JUDICIAL,
                data_source="STATIC_SEED",
            ))

        # Confirmation date
        if judge.get("confirmationDate"):
            facts.append(Fact(
                subject=subject,
                subject_id=individual_id,
                predicate=FactPredicate.CONFIRMATION_DATE,
                object=str(judge["confirmationDate"]),
                entity_type="Judge",
                branch=GovernmentBranch.JUDICIAL,
                data_source="STATIC_SEED",
            ))

        return FactSet(
            topic=f"Judge {subject}",
            primary_entity_id=individual_id,
            branch=GovernmentBranch.JUDICIAL,
            facts=facts,
        )

    async def _find_judge_by_id(self, judge_id: str) -> dict | None:
        """Find a judge by UUID from the paginated list."""
        all_judges = await self._client.get_all_judges()
        for j in all_judges:
            if str(j.get("id")) == judge_id:
                return j
        return None

    # ------------------------------------------------------------------
    # Random sampling — for batch generation
    # ------------------------------------------------------------------

    async def extract_random_sample(
        self,
        branch: GovernmentBranch | None = None,
        count: int = 10,
    ) -> list[FactSet]:
        """Extract FactSets for a random sample of entities.

        If branch is None, samples across all three branches.

        Args:
            branch: Limit to a specific government branch, or None for all.
            count: Number of FactSets to return.
        """
        fact_sets: list[FactSet] = []

        if branch is None or branch == GovernmentBranch.LEGISLATIVE:
            legislative = await self._sample_members(count)
            fact_sets.extend(legislative)

        if branch is None or branch == GovernmentBranch.EXECUTIVE:
            executive = await self._sample_presidencies(count)
            fact_sets.extend(executive)

        if branch is None or branch == GovernmentBranch.JUDICIAL:
            judicial = await self._sample_judges(count)
            fact_sets.extend(judicial)

        # If sampling across all branches, shuffle and trim to count
        if branch is None:
            random.shuffle(fact_sets)

        return fact_sets[:count]

    async def _sample_members(self, count: int) -> list[FactSet]:
        """Sample random congressional members and extract their facts."""
        page = await self._client.get_members(page=0, size=200)
        members = page.get("content", [])
        if not members:
            return []

        sampled = random.sample(members, min(count, len(members)))
        results = await asyncio.gather(
            *(self.extract_member_facts(m["bioguideId"]) for m in sampled)
        )
        return [fs for fs in results if fs is not None]

    async def _sample_presidencies(self, count: int) -> list[FactSet]:
        """Sample random presidencies and extract their facts."""
        all_presidencies = await self._client.get_all_presidencies()
        if not all_presidencies:
            return []

        sampled = random.sample(all_presidencies, min(count, len(all_presidencies)))
        results = await asyncio.gather(
            *(self.extract_presidency_facts(str(p["id"])) for p in sampled)
        )
        return [fs for fs in results if fs is not None]

    async def _sample_judges(self, count: int) -> list[FactSet]:
        """Sample random judges and extract their facts."""
        page = await self._client.get_judges(page=0, size=200)
        judges = page.get("content", [])
        if not judges:
            return []

        sampled = random.sample(judges, min(count, len(judges)))
        results = await asyncio.gather(
            *(self.extract_judge_facts(str(j["id"])) for j in sampled)
        )
        return [fs for fs in results if fs is not None]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _parse_uuid(value: str | None) -> UUID | None:
    """Safely parse a UUID string, returning None on failure."""
    if value is None:
        return None
    try:
        return UUID(str(value))
    except (ValueError, AttributeError):
        return None
