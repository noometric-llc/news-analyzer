"""
Perturbation Engine — applies controlled factual modifications to FactSets.

All logic is deterministic and testable without an LLM. The engine takes a
faithful FactSet and introduces exactly one controlled error, producing a
PerturbedFactSet that records what changed and the expected difficulty of
detecting the error.

Perturbation-Predicate Compatibility:
    WRONG_PARTY        → PARTY_AFFILIATION (Legislative, Executive)
    WRONG_COMMITTEE    → COMMITTEE_MEMBERSHIP (Legislative)
    WRONG_STATE        → STATE, DISTRICT (Legislative)
    HALLUCINATE_ROLE   → LEADERSHIP_ROLE, CABINET_POSITION, COURT (All)
    OUTDATED_INFO      → Any fact with valid_from/valid_to set (All)
    CONFLATE_INDIVIDUALS → Any (mixes facts from two entities) (All)

When a perturbation is incompatible with the given FactSet the engine
returns None and logs a warning.
"""

from __future__ import annotations

import logging
import random
from copy import deepcopy
from datetime import date

from app.models.eval import (
    Difficulty,
    Fact,
    FactPredicate,
    FactSet,
    PerturbationType,
    PerturbedFactSet,
)

logger = logging.getLogger(__name__)


class PerturbationEngine:
    """Applies controlled factual perturbations to FactSets."""

    PARTIES = ["Democratic", "Republican", "Independent"]

    STATES = [
        "Alabama", "Alaska", "Arizona", "Arkansas", "California",
        "Colorado", "Connecticut", "Delaware", "Florida", "Georgia",
        "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa",
        "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland",
        "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri",
        "Montana", "Nebraska", "Nevada", "New Hampshire", "New Jersey",
        "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio",
        "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina",
        "South Dakota", "Tennessee", "Texas", "Utah", "Vermont",
        "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming",
    ]

    # Plausible but fabricated roles for HALLUCINATE_ROLE
    HALLUCINATED_ROLES = [
        "Chair of the Select Committee on Emerging Technologies",
        "Deputy Majority Whip",
        "Vice Chair of the Congressional Budget Task Force",
        "Senior Advisor to the Intelligence Committee",
        "Chair of the Bipartisan Infrastructure Caucus",
        "Ranking Member of the Ethics Subcommittee",
        "Special Envoy for Trade Relations",
        "Director of the Office of Legislative Affairs",
        "Chair of the Joint Economic Advisory Panel",
        "Deputy Secretary of Intergovernmental Affairs",
    ]

    # Predicates eligible for HALLUCINATE_ROLE by branch
    HALLUCINATE_PREDICATES = {
        FactPredicate.LEADERSHIP_ROLE,
        FactPredicate.CABINET_POSITION,
        FactPredicate.COURT,
    }

    def perturb(
        self,
        fact_set: FactSet,
        perturbation_type: PerturbationType,
        entity_pool: list[FactSet] | None = None,
    ) -> PerturbedFactSet | None:
        """Apply a single perturbation to a FactSet.

        Returns None if the perturbation is incompatible with the FactSet.
        Raises ValueError only for CONFLATE_INDIVIDUALS when entity_pool
        is missing (a programming error, not a data incompatibility).
        """
        match perturbation_type:
            case PerturbationType.WRONG_PARTY:
                return self._wrong_party(fact_set)
            case PerturbationType.WRONG_COMMITTEE:
                return self._wrong_committee(fact_set)
            case PerturbationType.WRONG_STATE:
                return self._wrong_state(fact_set)
            case PerturbationType.HALLUCINATE_ROLE:
                return self._hallucinate_role(fact_set)
            case PerturbationType.OUTDATED_INFO:
                return self._outdated_info(fact_set)
            case PerturbationType.CONFLATE_INDIVIDUALS:
                if not entity_pool:
                    raise ValueError(
                        "CONFLATE_INDIVIDUALS requires a non-empty entity_pool"
                    )
                return self._conflate(fact_set, entity_pool)
            case PerturbationType.NONE:
                return None
            case _:
                logger.warning("Unknown perturbation type: %s", perturbation_type)
                return None

    # ------------------------------------------------------------------
    # Individual perturbation methods
    # ------------------------------------------------------------------

    def _wrong_party(self, fact_set: FactSet) -> PerturbedFactSet | None:
        """Swap party affiliation to a different party. Difficulty: EASY."""
        party_fact = fact_set.get_fact(FactPredicate.PARTY_AFFILIATION)
        if not party_fact:
            logger.warning(
                "WRONG_PARTY skipped: no PARTY_AFFILIATION fact in '%s'",
                fact_set.topic,
            )
            return None

        other_parties = [p for p in self.PARTIES if p != party_fact.object]
        new_party = random.choice(other_parties)

        perturbed_facts = self._replace_fact(
            fact_set.facts, FactPredicate.PARTY_AFFILIATION, new_party
        )
        return PerturbedFactSet(
            original=fact_set,
            perturbed=FactSet(
                topic=fact_set.topic,
                primary_entity_id=fact_set.primary_entity_id,
                branch=fact_set.branch,
                facts=perturbed_facts,
                related_entity_ids=fact_set.related_entity_ids,
            ),
            perturbation_type=PerturbationType.WRONG_PARTY,
            changed_facts=[
                {
                    "predicate": FactPredicate.PARTY_AFFILIATION.value,
                    "original_value": party_fact.object,
                    "perturbed_value": new_party,
                }
            ],
            difficulty=Difficulty.EASY,
        )

    def _wrong_committee(self, fact_set: FactSet) -> PerturbedFactSet | None:
        """Assign to an incorrect committee. Difficulty: MEDIUM."""
        committee_fact = fact_set.get_fact(FactPredicate.COMMITTEE_MEMBERSHIP)
        if not committee_fact:
            logger.warning(
                "WRONG_COMMITTEE skipped: no COMMITTEE_MEMBERSHIP fact in '%s'",
                fact_set.topic,
            )
            return None

        # Pick a plausible but different committee name
        fake_committees = [
            "Committee on Armed Services",
            "Committee on Foreign Relations",
            "Committee on the Judiciary",
            "Committee on Finance",
            "Committee on Appropriations",
            "Committee on Commerce, Science, and Transportation",
            "Committee on Health, Education, Labor, and Pensions",
            "Committee on Homeland Security and Governmental Affairs",
            "Committee on Small Business and Entrepreneurship",
            "Committee on Veterans' Affairs",
        ]
        alternatives = [c for c in fake_committees if c != committee_fact.object]
        new_committee = random.choice(alternatives)

        perturbed_facts = self._replace_fact(
            fact_set.facts, FactPredicate.COMMITTEE_MEMBERSHIP, new_committee
        )
        return PerturbedFactSet(
            original=fact_set,
            perturbed=FactSet(
                topic=fact_set.topic,
                primary_entity_id=fact_set.primary_entity_id,
                branch=fact_set.branch,
                facts=perturbed_facts,
                related_entity_ids=fact_set.related_entity_ids,
            ),
            perturbation_type=PerturbationType.WRONG_COMMITTEE,
            changed_facts=[
                {
                    "predicate": FactPredicate.COMMITTEE_MEMBERSHIP.value,
                    "original_value": committee_fact.object,
                    "perturbed_value": new_committee,
                }
            ],
            difficulty=Difficulty.MEDIUM,
        )

    def _wrong_state(self, fact_set: FactSet) -> PerturbedFactSet | None:
        """Swap state or district to an incorrect value. Difficulty: EASY."""
        # Try STATE first, fall back to DISTRICT
        target_predicate = FactPredicate.STATE
        state_fact = fact_set.get_fact(FactPredicate.STATE)
        if not state_fact:
            target_predicate = FactPredicate.DISTRICT
            state_fact = fact_set.get_fact(FactPredicate.DISTRICT)
        if not state_fact:
            logger.warning(
                "WRONG_STATE skipped: no STATE or DISTRICT fact in '%s'",
                fact_set.topic,
            )
            return None

        other_states = [s for s in self.STATES if s != state_fact.object]
        new_state = random.choice(other_states)

        perturbed_facts = self._replace_fact(
            fact_set.facts, target_predicate, new_state
        )
        return PerturbedFactSet(
            original=fact_set,
            perturbed=FactSet(
                topic=fact_set.topic,
                primary_entity_id=fact_set.primary_entity_id,
                branch=fact_set.branch,
                facts=perturbed_facts,
                related_entity_ids=fact_set.related_entity_ids,
            ),
            perturbation_type=PerturbationType.WRONG_STATE,
            changed_facts=[
                {
                    "predicate": target_predicate.value,
                    "original_value": state_fact.object,
                    "perturbed_value": new_state,
                }
            ],
            difficulty=Difficulty.EASY,
        )

    def _hallucinate_role(self, fact_set: FactSet) -> PerturbedFactSet | None:
        """Invent a plausible role not in the KB. Difficulty: MEDIUM."""
        # Find the first eligible predicate in this FactSet
        target_fact: Fact | None = None
        for fact in fact_set.facts:
            if fact.predicate in self.HALLUCINATE_PREDICATES:
                target_fact = fact
                break

        if not target_fact:
            logger.warning(
                "HALLUCINATE_ROLE skipped: no eligible predicate in '%s'",
                fact_set.topic,
            )
            return None

        fake_role = random.choice(self.HALLUCINATED_ROLES)

        perturbed_facts = self._replace_fact(
            fact_set.facts, target_fact.predicate, fake_role
        )
        return PerturbedFactSet(
            original=fact_set,
            perturbed=FactSet(
                topic=fact_set.topic,
                primary_entity_id=fact_set.primary_entity_id,
                branch=fact_set.branch,
                facts=perturbed_facts,
                related_entity_ids=fact_set.related_entity_ids,
            ),
            perturbation_type=PerturbationType.HALLUCINATE_ROLE,
            changed_facts=[
                {
                    "predicate": target_fact.predicate.value,
                    "original_value": target_fact.object,
                    "perturbed_value": fake_role,
                }
            ],
            difficulty=Difficulty.MEDIUM,
        )

    def _outdated_info(self, fact_set: FactSet) -> PerturbedFactSet | None:
        """Use superseded information from a fact with temporal bounds.

        Difficulty: HARD — the info was once true, requiring temporal awareness.
        """
        # Find a fact that has valid_to set (meaning it has been superseded)
        target_fact: Fact | None = None
        for fact in fact_set.facts:
            if fact.valid_from is not None or fact.valid_to is not None:
                target_fact = fact
                break

        if not target_fact:
            logger.warning(
                "OUTDATED_INFO skipped: no temporally-bounded facts in '%s'",
                fact_set.topic,
            )
            return None

        # Construct an outdated version: prepend "Former " to the value
        # and remove temporal bounds to make it look current
        outdated_value = f"Former {target_fact.object}"

        perturbed_facts = self._replace_fact(
            fact_set.facts,
            target_fact.predicate,
            outdated_value,
            clear_temporal=True,
        )
        return PerturbedFactSet(
            original=fact_set,
            perturbed=FactSet(
                topic=fact_set.topic,
                primary_entity_id=fact_set.primary_entity_id,
                branch=fact_set.branch,
                facts=perturbed_facts,
                related_entity_ids=fact_set.related_entity_ids,
            ),
            perturbation_type=PerturbationType.OUTDATED_INFO,
            changed_facts=[
                {
                    "predicate": target_fact.predicate.value,
                    "original_value": target_fact.object,
                    "perturbed_value": outdated_value,
                }
            ],
            difficulty=Difficulty.HARD,
        )

    def _conflate(
        self, fact_set: FactSet, entity_pool: list[FactSet]
    ) -> PerturbedFactSet | None:
        """Mix attributes from two different entities. Difficulty: ADVERSARIAL.

        Picks a random second entity from the pool and swaps one of their
        facts into the target FactSet — creating a realistic "mixing up
        two people" error.
        """
        # Find a different entity in the pool
        candidates = [
            fs for fs in entity_pool
            if fs.primary_entity_id != fact_set.primary_entity_id
            or fs.topic != fact_set.topic
        ]
        if not candidates:
            logger.warning(
                "CONFLATE_INDIVIDUALS skipped: no different entity in pool "
                "for '%s'",
                fact_set.topic,
            )
            return None

        donor = random.choice(candidates)

        # Find a fact in the donor that has a matching predicate in the target
        swappable_pairs: list[tuple[Fact, Fact]] = []
        for donor_fact in donor.facts:
            target_fact = fact_set.get_fact(donor_fact.predicate)
            if target_fact and target_fact.object != donor_fact.object:
                swappable_pairs.append((target_fact, donor_fact))

        if not swappable_pairs:
            logger.warning(
                "CONFLATE_INDIVIDUALS skipped: no overlapping predicates "
                "between '%s' and '%s'",
                fact_set.topic,
                donor.topic,
            )
            return None

        target_fact, donor_fact = random.choice(swappable_pairs)

        perturbed_facts = self._replace_fact(
            fact_set.facts, target_fact.predicate, donor_fact.object
        )
        return PerturbedFactSet(
            original=fact_set,
            perturbed=FactSet(
                topic=fact_set.topic,
                primary_entity_id=fact_set.primary_entity_id,
                branch=fact_set.branch,
                facts=perturbed_facts,
                related_entity_ids=fact_set.related_entity_ids,
            ),
            perturbation_type=PerturbationType.CONFLATE_INDIVIDUALS,
            changed_facts=[
                {
                    "predicate": target_fact.predicate.value,
                    "original_value": target_fact.object,
                    "perturbed_value": donor_fact.object,
                }
            ],
            difficulty=Difficulty.ADVERSARIAL,
        )

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _replace_fact(
        facts: list[Fact],
        predicate: FactPredicate,
        new_value: str,
        *,
        clear_temporal: bool = False,
    ) -> list[Fact]:
        """Deep-copy the facts list and replace the first matching predicate's
        object value. Returns a new list — the original is not mutated.

        If clear_temporal is True, also clears valid_from and valid_to on
        the replaced fact (used by OUTDATED_INFO to make the value look current).
        """
        new_facts: list[Fact] = []
        replaced = False
        for fact in facts:
            copied = deepcopy(fact)
            if not replaced and copied.predicate == predicate:
                copied.object = new_value
                if clear_temporal:
                    copied.valid_from = None
                    copied.valid_to = None
                replaced = True
            new_facts.append(copied)
        return new_facts
