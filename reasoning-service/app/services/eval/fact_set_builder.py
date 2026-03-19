"""
Fact Set Builder — higher-level assembly of related facts across entities.

The FactExtractor pulls facts about a single entity. The FactSetBuilder
composes richer FactSets by combining related entities and builds entity
pools for batch generation.
"""

from __future__ import annotations

import logging
import random

from app.clients.backend_client import BackendClient
from app.models.eval import (
    FactPredicate,
    FactSet,
    GovernmentBranch,
)
from app.services.eval.fact_extractor import FactExtractor

logger = logging.getLogger(__name__)


class FactSetBuilder:
    """Builds rich FactSets by composing facts across related entities.

    Sits on top of FactExtractor to provide:
    - Branch-specific builders with enrichment hooks
    - Topic-based assembly (e.g., all members of a specific committee)
    - Entity pool construction for batch generation
    """

    def __init__(self, extractor: FactExtractor, client: BackendClient):
        self._extractor = extractor
        self._client = client

    # ------------------------------------------------------------------
    # Branch-specific builders
    # ------------------------------------------------------------------

    async def build_legislative_set(self, bioguide_id: str) -> FactSet | None:
        """Build a FactSet for a congressional member.

        Currently delegates directly to the extractor. This is the
        extension point for adding statute context or cross-referencing
        committee data in future stories.
        """
        return await self._extractor.extract_member_facts(bioguide_id)

    async def build_executive_set(self, presidency_id: str) -> FactSet | None:
        """Build a FactSet for a presidency including administration."""
        return await self._extractor.extract_presidency_facts(presidency_id)

    async def build_judicial_set(self, judge_id: str) -> FactSet | None:
        """Build a FactSet for a federal judge."""
        return await self._extractor.extract_judge_facts(judge_id)

    # ------------------------------------------------------------------
    # Topic-based assembly (AC4)
    # ------------------------------------------------------------------

    async def build_committee_topic(self, committee_name: str) -> FactSet | None:
        """Build a FactSet about a committee and its members.

        Finds all members who serve on the given committee and assembles
        their committee-related facts into a single topic-based FactSet.
        """
        # Get all members and filter for those on this committee
        all_members_page = await self._client.get_members(page=0, size=200)
        all_members = all_members_page.get("content", [])

        if not all_members:
            return None

        # Extract facts for a sample of members and filter for committee match
        matching_facts = []
        # Check up to 50 members to find committee matches
        sample_size = min(50, len(all_members))
        for member in random.sample(all_members, sample_size):
            member_facts = await self._extractor.extract_member_facts(
                member["bioguideId"]
            )
            if member_facts is None:
                continue

            committee_facts = member_facts.get_facts(
                FactPredicate.COMMITTEE_MEMBERSHIP
            )
            if any(committee_name.lower() in f.object.lower() for f in committee_facts):
                matching_facts.extend(member_facts.facts)

        if not matching_facts:
            return None

        return FactSet(
            topic=f"{committee_name} Committee membership",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=matching_facts,
        )

    # ------------------------------------------------------------------
    # Entity pool for batch generation
    # ------------------------------------------------------------------

    async def build_entity_pool(
        self,
        branch: GovernmentBranch | None = None,
        count: int = 10,
    ) -> list[FactSet]:
        """Build a pool of FactSets for batch generation.

        The pool is intentionally larger than ``count`` to provide
        extra entities for the CONFLATE_INDIVIDUALS perturbation type,
        which needs a second entity to mix attributes with.

        Args:
            branch: Limit to a specific branch, or None for all.
            count: Minimum number of FactSets to return.

        Returns:
            List of FactSets, potentially larger than ``count``.
        """
        # Request extra for CONFLATE_INDIVIDUALS
        pool_size = count + 5

        fact_sets = await self._extractor.extract_random_sample(
            branch=branch,
            count=pool_size,
        )

        # Filter out any FactSets with too few facts to be useful
        MIN_FACTS = 3
        viable = [fs for fs in fact_sets if len(fs.facts) >= MIN_FACTS]

        if len(viable) < count:
            logger.warning(
                "Entity pool has %d viable FactSets, requested %d. "
                "Some entities may have sparse KB data.",
                len(viable),
                count,
            )

        return viable
