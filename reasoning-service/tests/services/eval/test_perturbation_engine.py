"""
Tests for the PerturbationEngine.

All tests are pure unit tests — no LLM calls, no external dependencies.
Each perturbation type is tested for:
  - Correct modification of the target fact
  - Correct difficulty rating
  - Correct changed_facts metadata
  - Original FactSet immutability
Edge cases cover: missing predicates, incompatible branches, empty pools.
"""

from __future__ import annotations

from datetime import date
from uuid import uuid4

import pytest

from app.models.eval import (
    Difficulty,
    Fact,
    FactConfidence,
    FactPredicate,
    FactSet,
    GovernmentBranch,
    PerturbationType,
)
from app.services.eval.perturbation_engine import PerturbationEngine


# ---------------------------------------------------------------------------
# Fixtures — reusable test FactSets
# ---------------------------------------------------------------------------


@pytest.fixture
def engine() -> PerturbationEngine:
    return PerturbationEngine()


@pytest.fixture
def legislative_fact_set() -> FactSet:
    """A legislative FactSet with all the predicates needed for testing."""
    return FactSet(
        topic="Senator Jane Smith",
        primary_entity_id=uuid4(),
        branch=GovernmentBranch.LEGISLATIVE,
        facts=[
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.PARTY_AFFILIATION,
                object="Democratic",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.STATE,
                object="Pennsylvania",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
                object="Committee on Banking, Housing, and Urban Affairs",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.LEADERSHIP_ROLE,
                object="Ranking Member",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.CHAMBER,
                object="Senate",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
        ],
    )


@pytest.fixture
def legislative_fact_set_with_temporal() -> FactSet:
    """A legislative FactSet with temporal bounds on a fact."""
    return FactSet(
        topic="Senator Jane Smith",
        primary_entity_id=uuid4(),
        branch=GovernmentBranch.LEGISLATIVE,
        facts=[
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.PARTY_AFFILIATION,
                object="Democratic",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Jane Smith",
                predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
                object="Committee on Banking, Housing, and Urban Affairs",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
                valid_from=date(2023, 1, 3),
                valid_to=date(2025, 1, 3),
            ),
        ],
    )


@pytest.fixture
def judicial_fact_set() -> FactSet:
    """A judicial FactSet — no party, no committee, no state."""
    return FactSet(
        topic="Judge John Doe",
        primary_entity_id=uuid4(),
        branch=GovernmentBranch.JUDICIAL,
        facts=[
            Fact(
                subject="John Doe",
                predicate=FactPredicate.COURT,
                object="U.S. Court of Appeals for the Second Circuit",
                entity_type="Judge",
                branch=GovernmentBranch.JUDICIAL,
                data_source="STATIC",
            ),
            Fact(
                subject="John Doe",
                predicate=FactPredicate.APPOINTING_PRESIDENT,
                object="Barack Obama",
                entity_type="Judge",
                branch=GovernmentBranch.JUDICIAL,
                data_source="STATIC",
            ),
            Fact(
                subject="John Doe",
                predicate=FactPredicate.COURT_LEVEL,
                object="Circuit",
                entity_type="Judge",
                branch=GovernmentBranch.JUDICIAL,
                data_source="STATIC",
            ),
        ],
    )


@pytest.fixture
def donor_fact_set() -> FactSet:
    """A second legislative FactSet for CONFLATE_INDIVIDUALS testing."""
    return FactSet(
        topic="Senator Bob Johnson",
        primary_entity_id=uuid4(),
        branch=GovernmentBranch.LEGISLATIVE,
        facts=[
            Fact(
                subject="Bob Johnson",
                predicate=FactPredicate.PARTY_AFFILIATION,
                object="Republican",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Bob Johnson",
                predicate=FactPredicate.STATE,
                object="Texas",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
            Fact(
                subject="Bob Johnson",
                predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
                object="Committee on Armed Services",
                entity_type="CongressionalMember",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="CONGRESS_GOV",
            ),
        ],
    )


# ---------------------------------------------------------------------------
# WRONG_PARTY tests
# ---------------------------------------------------------------------------


class TestWrongParty:
    def test_swaps_party(self, engine, legislative_fact_set):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_PARTY
        )
        assert result is not None
        assert result.perturbation_type == PerturbationType.WRONG_PARTY
        assert result.difficulty == Difficulty.EASY

        # The perturbed party should differ from original
        original_party = legislative_fact_set.get_fact(
            FactPredicate.PARTY_AFFILIATION
        ).object
        perturbed_party = result.perturbed.get_fact(
            FactPredicate.PARTY_AFFILIATION
        ).object
        assert perturbed_party != original_party
        assert perturbed_party in PerturbationEngine.PARTIES

    def test_changed_facts_metadata(self, engine, legislative_fact_set):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_PARTY
        )
        assert len(result.changed_facts) == 1
        change = result.changed_facts[0]
        assert change["predicate"] == "party_affiliation"
        assert change["original_value"] == "Democratic"
        assert change["perturbed_value"] in ["Republican", "Independent"]

    def test_original_immutable(self, engine, legislative_fact_set):
        original_party = legislative_fact_set.get_fact(
            FactPredicate.PARTY_AFFILIATION
        ).object
        engine.perturb(legislative_fact_set, PerturbationType.WRONG_PARTY)
        # Original should be unchanged
        assert (
            legislative_fact_set.get_fact(FactPredicate.PARTY_AFFILIATION).object
            == original_party
        )

    def test_skips_when_no_party_fact(self, engine, judicial_fact_set):
        result = engine.perturb(
            judicial_fact_set, PerturbationType.WRONG_PARTY
        )
        assert result is None

    def test_original_stored_in_result(self, engine, legislative_fact_set):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_PARTY
        )
        assert result.original == legislative_fact_set


# ---------------------------------------------------------------------------
# WRONG_COMMITTEE tests
# ---------------------------------------------------------------------------


class TestWrongCommittee:
    def test_swaps_committee(self, engine, legislative_fact_set):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_COMMITTEE
        )
        assert result is not None
        assert result.perturbation_type == PerturbationType.WRONG_COMMITTEE
        assert result.difficulty == Difficulty.MEDIUM

        original_committee = legislative_fact_set.get_fact(
            FactPredicate.COMMITTEE_MEMBERSHIP
        ).object
        perturbed_committee = result.perturbed.get_fact(
            FactPredicate.COMMITTEE_MEMBERSHIP
        ).object
        assert perturbed_committee != original_committee

    def test_changed_facts_metadata(self, engine, legislative_fact_set):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_COMMITTEE
        )
        assert len(result.changed_facts) == 1
        assert result.changed_facts[0]["predicate"] == "committee_membership"
        assert (
            result.changed_facts[0]["original_value"]
            == "Committee on Banking, Housing, and Urban Affairs"
        )

    def test_skips_when_no_committee_fact(self, engine, judicial_fact_set):
        result = engine.perturb(
            judicial_fact_set, PerturbationType.WRONG_COMMITTEE
        )
        assert result is None


# ---------------------------------------------------------------------------
# WRONG_STATE tests
# ---------------------------------------------------------------------------


class TestWrongState:
    def test_swaps_state(self, engine, legislative_fact_set):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_STATE
        )
        assert result is not None
        assert result.perturbation_type == PerturbationType.WRONG_STATE
        assert result.difficulty == Difficulty.EASY

        perturbed_state = result.perturbed.get_fact(FactPredicate.STATE).object
        assert perturbed_state != "Pennsylvania"
        assert perturbed_state in PerturbationEngine.STATES

    def test_falls_back_to_district(self, engine):
        """When there's no STATE fact but there is DISTRICT, use DISTRICT."""
        fact_set = FactSet(
            topic="Rep. Test Person",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Test Person",
                    predicate=FactPredicate.DISTRICT,
                    object="5th",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="CONGRESS_GOV",
                ),
            ],
        )
        result = engine.perturb(fact_set, PerturbationType.WRONG_STATE)
        assert result is not None
        assert result.changed_facts[0]["predicate"] == "district"
        assert result.changed_facts[0]["original_value"] == "5th"

    def test_skips_when_no_state_or_district(self, engine, judicial_fact_set):
        result = engine.perturb(
            judicial_fact_set, PerturbationType.WRONG_STATE
        )
        assert result is None


# ---------------------------------------------------------------------------
# HALLUCINATE_ROLE tests
# ---------------------------------------------------------------------------


class TestHallucinateRole:
    def test_invents_role_for_legislative(self, engine, legislative_fact_set):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.HALLUCINATE_ROLE
        )
        assert result is not None
        assert result.perturbation_type == PerturbationType.HALLUCINATE_ROLE
        assert result.difficulty == Difficulty.MEDIUM
        assert (
            result.changed_facts[0]["perturbed_value"]
            in PerturbationEngine.HALLUCINATED_ROLES
        )

    def test_invents_role_for_judicial(self, engine, judicial_fact_set):
        """Judicial FactSet has COURT predicate, which is eligible."""
        result = engine.perturb(
            judicial_fact_set, PerturbationType.HALLUCINATE_ROLE
        )
        assert result is not None
        assert result.changed_facts[0]["predicate"] == "court"

    def test_skips_when_no_eligible_predicate(self, engine):
        """A FactSet with only non-eligible predicates should return None."""
        fact_set = FactSet(
            topic="Minimal entity",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Test",
                    predicate=FactPredicate.STATE,
                    object="Ohio",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="TEST",
                ),
            ],
        )
        result = engine.perturb(fact_set, PerturbationType.HALLUCINATE_ROLE)
        assert result is None


# ---------------------------------------------------------------------------
# OUTDATED_INFO tests
# ---------------------------------------------------------------------------


class TestOutdatedInfo:
    def test_uses_superseded_info(
        self, engine, legislative_fact_set_with_temporal
    ):
        result = engine.perturb(
            legislative_fact_set_with_temporal, PerturbationType.OUTDATED_INFO
        )
        assert result is not None
        assert result.perturbation_type == PerturbationType.OUTDATED_INFO
        assert result.difficulty == Difficulty.HARD
        assert result.changed_facts[0]["perturbed_value"].startswith("Former ")

    def test_clears_temporal_bounds(
        self, engine, legislative_fact_set_with_temporal
    ):
        """The perturbed fact should have temporal bounds cleared."""
        result = engine.perturb(
            legislative_fact_set_with_temporal, PerturbationType.OUTDATED_INFO
        )
        perturbed_committee = result.perturbed.get_fact(
            FactPredicate.COMMITTEE_MEMBERSHIP
        )
        assert perturbed_committee.valid_from is None
        assert perturbed_committee.valid_to is None

    def test_skips_when_no_temporal_facts(self, engine, legislative_fact_set):
        """FactSet without temporal bounds should return None."""
        result = engine.perturb(
            legislative_fact_set, PerturbationType.OUTDATED_INFO
        )
        assert result is None


# ---------------------------------------------------------------------------
# CONFLATE_INDIVIDUALS tests
# ---------------------------------------------------------------------------


class TestConflateIndividuals:
    def test_mixes_attributes(
        self, engine, legislative_fact_set, donor_fact_set
    ):
        result = engine.perturb(
            legislative_fact_set,
            PerturbationType.CONFLATE_INDIVIDUALS,
            entity_pool=[donor_fact_set],
        )
        assert result is not None
        assert (
            result.perturbation_type == PerturbationType.CONFLATE_INDIVIDUALS
        )
        assert result.difficulty == Difficulty.ADVERSARIAL

        # The changed value should come from the donor
        change = result.changed_facts[0]
        donor_values = [f.object for f in donor_fact_set.facts]
        assert change["perturbed_value"] in donor_values
        assert change["original_value"] != change["perturbed_value"]

    def test_raises_on_empty_pool(self, engine, legislative_fact_set):
        with pytest.raises(ValueError, match="entity_pool"):
            engine.perturb(
                legislative_fact_set,
                PerturbationType.CONFLATE_INDIVIDUALS,
                entity_pool=[],
            )

    def test_raises_on_none_pool(self, engine, legislative_fact_set):
        with pytest.raises(ValueError, match="entity_pool"):
            engine.perturb(
                legislative_fact_set,
                PerturbationType.CONFLATE_INDIVIDUALS,
                entity_pool=None,
            )

    def test_skips_when_no_overlapping_predicates(self, engine):
        """If donor has no predicates in common, return None."""
        target = FactSet(
            topic="Target",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Target",
                    predicate=FactPredicate.STATE,
                    object="Ohio",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="TEST",
                ),
            ],
        )
        donor = FactSet(
            topic="Donor",
            primary_entity_id=uuid4(),
            branch=GovernmentBranch.JUDICIAL,
            facts=[
                Fact(
                    subject="Donor",
                    predicate=FactPredicate.COURT,
                    object="Supreme Court",
                    entity_type="Judge",
                    branch=GovernmentBranch.JUDICIAL,
                    data_source="TEST",
                ),
            ],
        )
        result = engine.perturb(
            target,
            PerturbationType.CONFLATE_INDIVIDUALS,
            entity_pool=[donor],
        )
        assert result is None

    def test_skips_when_same_values(self, engine):
        """If the only overlapping predicate has the same value, skip."""
        target = FactSet(
            topic="Target",
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Target",
                    predicate=FactPredicate.STATE,
                    object="Ohio",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="TEST",
                ),
            ],
        )
        donor = FactSet(
            topic="Donor",
            primary_entity_id=uuid4(),
            branch=GovernmentBranch.LEGISLATIVE,
            facts=[
                Fact(
                    subject="Donor",
                    predicate=FactPredicate.STATE,
                    object="Ohio",
                    entity_type="CongressionalMember",
                    branch=GovernmentBranch.LEGISLATIVE,
                    data_source="TEST",
                ),
            ],
        )
        result = engine.perturb(
            target,
            PerturbationType.CONFLATE_INDIVIDUALS,
            entity_pool=[donor],
        )
        assert result is None


# ---------------------------------------------------------------------------
# General / dispatch tests
# ---------------------------------------------------------------------------


class TestPerturbDispatch:
    def test_none_perturbation_returns_none(self, engine, legislative_fact_set):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.NONE
        )
        assert result is None

    def test_all_six_perturbation_types_handled(self, engine):
        """Verify that the dispatch covers all non-NONE perturbation types."""
        perturbation_types = [
            p for p in PerturbationType if p != PerturbationType.NONE
        ]
        assert len(perturbation_types) == 6

    def test_perturbed_fact_set_preserves_topic(
        self, engine, legislative_fact_set
    ):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_PARTY
        )
        assert result.perturbed.topic == legislative_fact_set.topic

    def test_perturbed_fact_set_preserves_branch(
        self, engine, legislative_fact_set
    ):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_PARTY
        )
        assert result.perturbed.branch == legislative_fact_set.branch

    def test_perturbed_fact_set_preserves_entity_id(
        self, engine, legislative_fact_set
    ):
        result = engine.perturb(
            legislative_fact_set, PerturbationType.WRONG_PARTY
        )
        assert (
            result.perturbed.primary_entity_id
            == legislative_fact_set.primary_entity_id
        )


# ---------------------------------------------------------------------------
# _replace_fact helper tests
# ---------------------------------------------------------------------------


class TestReplaceFact:
    def test_replaces_first_matching_predicate(self):
        facts = [
            Fact(
                subject="Test",
                predicate=FactPredicate.STATE,
                object="Ohio",
                entity_type="Test",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="TEST",
            ),
            Fact(
                subject="Test",
                predicate=FactPredicate.STATE,
                object="Texas",
                entity_type="Test",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="TEST",
            ),
        ]
        result = PerturbationEngine._replace_fact(
            facts, FactPredicate.STATE, "California"
        )
        assert result[0].object == "California"
        assert result[1].object == "Texas"  # Second one untouched

    def test_does_not_mutate_original(self):
        facts = [
            Fact(
                subject="Test",
                predicate=FactPredicate.STATE,
                object="Ohio",
                entity_type="Test",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="TEST",
            ),
        ]
        PerturbationEngine._replace_fact(
            facts, FactPredicate.STATE, "California"
        )
        assert facts[0].object == "Ohio"

    def test_clear_temporal_removes_dates(self):
        facts = [
            Fact(
                subject="Test",
                predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
                object="Banking Committee",
                entity_type="Test",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="TEST",
                valid_from=date(2023, 1, 1),
                valid_to=date(2025, 1, 1),
            ),
        ]
        result = PerturbationEngine._replace_fact(
            facts,
            FactPredicate.COMMITTEE_MEMBERSHIP,
            "Former Banking Committee",
            clear_temporal=True,
        )
        assert result[0].valid_from is None
        assert result[0].valid_to is None
