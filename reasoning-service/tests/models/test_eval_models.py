"""Tests for EVAL track Pydantic models and enums."""

import json
from datetime import date
from uuid import UUID, uuid4

import pytest
from pydantic import ValidationError

from app.models.eval import (
    ArticleTestCase,
    ArticleType,
    BatchConfig,
    BatchResult,
    Difficulty,
    Fact,
    FactConfidence,
    FactPredicate,
    FactSet,
    GovernmentBranch,
    PerturbationType,
    PerturbedFactSet,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


@pytest.fixture
def sample_fact() -> Fact:
    """A single legislative fact for testing."""
    return Fact(
        subject="John Fetterman",
        subject_id=uuid4(),
        predicate=FactPredicate.PARTY_AFFILIATION,
        object="Democratic",
        entity_type="CongressionalMember",
        branch=GovernmentBranch.LEGISLATIVE,
        data_source="CONGRESS_GOV",
        confidence=FactConfidence.TIER_1,
    )


@pytest.fixture
def sample_fact_set(sample_fact: Fact) -> FactSet:
    """A FactSet with multiple facts about a senator."""
    state_fact = Fact(
        subject="John Fetterman",
        subject_id=sample_fact.subject_id,
        predicate=FactPredicate.STATE,
        object="Pennsylvania",
        entity_type="CongressionalMember",
        branch=GovernmentBranch.LEGISLATIVE,
        data_source="CONGRESS_GOV",
    )
    committee_fact_1 = Fact(
        subject="John Fetterman",
        subject_id=sample_fact.subject_id,
        predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
        object="Banking, Housing, and Urban Affairs",
        entity_type="CongressionalMember",
        branch=GovernmentBranch.LEGISLATIVE,
        data_source="CONGRESS_GOV",
    )
    committee_fact_2 = Fact(
        subject="John Fetterman",
        subject_id=sample_fact.subject_id,
        predicate=FactPredicate.COMMITTEE_MEMBERSHIP,
        object="Agriculture, Nutrition, and Forestry",
        entity_type="CongressionalMember",
        branch=GovernmentBranch.LEGISLATIVE,
        data_source="CONGRESS_GOV",
    )
    return FactSet(
        topic="Senator John Fetterman",
        primary_entity_id=sample_fact.subject_id,
        branch=GovernmentBranch.LEGISLATIVE,
        facts=[sample_fact, state_fact, committee_fact_1, committee_fact_2],
    )


# ---------------------------------------------------------------------------
# Enum Tests
# ---------------------------------------------------------------------------


class TestEnums:
    """Verify enums serialize as plain strings for JSONB storage."""

    def test_government_branch_values(self):
        assert GovernmentBranch.LEGISLATIVE == "legislative"
        assert GovernmentBranch.EXECUTIVE == "executive"
        assert GovernmentBranch.JUDICIAL == "judicial"

    def test_government_branch_serializes_to_string(self):
        """str(Enum) behaviour — critical for JSONB storage."""
        assert GovernmentBranch.LEGISLATIVE.value == "legislative"

    def test_fact_predicate_legislative_predicates_exist(self):
        legislative = [
            FactPredicate.PARTY_AFFILIATION,
            FactPredicate.STATE,
            FactPredicate.DISTRICT,
            FactPredicate.CHAMBER,
            FactPredicate.COMMITTEE_MEMBERSHIP,
            FactPredicate.LEADERSHIP_ROLE,
            FactPredicate.TERM_START,
            FactPredicate.TERM_END,
        ]
        assert len(legislative) == 8

    def test_fact_predicate_executive_predicates_exist(self):
        executive = [
            FactPredicate.PRESIDENCY_NUMBER,
            FactPredicate.VICE_PRESIDENT,
            FactPredicate.CABINET_POSITION,
            FactPredicate.EXECUTIVE_ORDER,
            FactPredicate.CHIEF_OF_STAFF,
            FactPredicate.AGENCY_HEAD,
        ]
        assert len(executive) == 6

    def test_fact_predicate_judicial_predicates_exist(self):
        judicial = [
            FactPredicate.COURT,
            FactPredicate.APPOINTING_PRESIDENT,
            FactPredicate.CONFIRMATION_DATE,
            FactPredicate.COURT_LEVEL,
        ]
        assert len(judicial) == 4

    def test_fact_predicate_cross_branch_predicates_exist(self):
        cross = [
            FactPredicate.STATUTE_REFERENCE,
            FactPredicate.STATUTE_SUBJECT,
        ]
        assert len(cross) == 2

    def test_perturbation_types_count(self):
        """All 6 perturbation types + NONE."""
        assert len(PerturbationType) == 7

    def test_difficulty_ordering(self):
        """Verify all difficulty levels exist."""
        levels = [Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD, Difficulty.ADVERSARIAL]
        assert len(levels) == 4

    def test_article_types_count(self):
        assert len(ArticleType) == 4


# ---------------------------------------------------------------------------
# Fact Model Tests
# ---------------------------------------------------------------------------


class TestFact:
    """Tests for the Fact model."""

    def test_create_minimal_fact(self):
        """Fact with only required fields."""
        fact = Fact(
            subject="Elizabeth Warren",
            predicate=FactPredicate.PARTY_AFFILIATION,
            object="Democratic",
            entity_type="CongressionalMember",
            branch=GovernmentBranch.LEGISLATIVE,
            data_source="CONGRESS_GOV",
        )
        assert fact.subject == "Elizabeth Warren"
        assert fact.subject_id is None
        assert fact.confidence == FactConfidence.TIER_1  # default
        assert fact.valid_from is None
        assert fact.valid_to is None

    def test_create_fact_with_temporal_bounds(self):
        fact = Fact(
            subject="Joe Biden",
            predicate=FactPredicate.PRESIDENCY_NUMBER,
            object="46",
            entity_type="Presidency",
            branch=GovernmentBranch.EXECUTIVE,
            data_source="STATIC_SEED",
            valid_from=date(2021, 1, 20),
        )
        assert fact.valid_from == date(2021, 1, 20)
        assert fact.valid_to is None

    def test_fact_json_serialization(self, sample_fact: Fact):
        """Fact round-trips through JSON — critical for JSONB storage."""
        json_str = sample_fact.model_dump_json()
        parsed = json.loads(json_str)

        assert parsed["predicate"] == "party_affiliation"
        assert parsed["branch"] == "legislative"
        assert parsed["confidence"] == "tier_1"

    def test_fact_json_deserialization(self):
        """Reconstruct a Fact from JSON dict."""
        data = {
            "subject": "John Roberts",
            "predicate": "court",
            "object": "Supreme Court of the United States",
            "entity_type": "Judge",
            "branch": "judicial",
            "data_source": "STATIC_SEED",
            "confidence": "tier_1",
        }
        fact = Fact.model_validate(data)
        assert fact.predicate == FactPredicate.COURT
        assert fact.branch == GovernmentBranch.JUDICIAL

    def test_fact_rejects_invalid_predicate(self):
        with pytest.raises(ValidationError):
            Fact(
                subject="Test",
                predicate="not_a_real_predicate",
                object="value",
                entity_type="Test",
                branch=GovernmentBranch.LEGISLATIVE,
                data_source="TEST",
            )

    def test_fact_rejects_invalid_branch(self):
        with pytest.raises(ValidationError):
            Fact(
                subject="Test",
                predicate=FactPredicate.STATE,
                object="value",
                entity_type="Test",
                branch="not_a_branch",
                data_source="TEST",
            )


# ---------------------------------------------------------------------------
# FactSet Tests
# ---------------------------------------------------------------------------


class TestFactSet:
    """Tests for FactSet model and helper methods."""

    def test_get_fact_returns_matching_fact(self, sample_fact_set: FactSet):
        result = sample_fact_set.get_fact(FactPredicate.PARTY_AFFILIATION)
        assert result is not None
        assert result.object == "Democratic"

    def test_get_fact_returns_none_for_missing_predicate(self, sample_fact_set: FactSet):
        result = sample_fact_set.get_fact(FactPredicate.DISTRICT)
        assert result is None

    def test_get_facts_returns_all_matching(self, sample_fact_set: FactSet):
        committees = sample_fact_set.get_facts(FactPredicate.COMMITTEE_MEMBERSHIP)
        assert len(committees) == 2
        names = {c.object for c in committees}
        assert "Banking, Housing, and Urban Affairs" in names
        assert "Agriculture, Nutrition, and Forestry" in names

    def test_get_facts_returns_empty_for_no_match(self, sample_fact_set: FactSet):
        result = sample_fact_set.get_facts(FactPredicate.EXECUTIVE_ORDER)
        assert result == []

    def test_fact_set_json_round_trip(self, sample_fact_set: FactSet):
        json_str = sample_fact_set.model_dump_json()
        restored = FactSet.model_validate_json(json_str)
        assert restored.topic == sample_fact_set.topic
        assert len(restored.facts) == len(sample_fact_set.facts)
        assert restored.get_fact(FactPredicate.STATE).object == "Pennsylvania"

    def test_empty_fact_set(self):
        fs = FactSet(
            topic="Empty",
            branch=GovernmentBranch.JUDICIAL,
            facts=[],
        )
        assert fs.get_fact(FactPredicate.COURT) is None
        assert fs.get_facts(FactPredicate.COURT) == []

    def test_related_entity_ids_default_empty(self, sample_fact_set: FactSet):
        assert sample_fact_set.related_entity_ids == []


# ---------------------------------------------------------------------------
# PerturbedFactSet Tests
# ---------------------------------------------------------------------------


class TestPerturbedFactSet:
    """Tests for PerturbedFactSet model."""

    def test_create_perturbed_fact_set(self, sample_fact_set: FactSet):
        # Create a perturbed copy with wrong party
        perturbed_facts = []
        for f in sample_fact_set.facts:
            if f.predicate == FactPredicate.PARTY_AFFILIATION:
                perturbed_facts.append(f.model_copy(update={"object": "Republican"}))
            else:
                perturbed_facts.append(f)

        perturbed_set = FactSet(
            topic=sample_fact_set.topic,
            primary_entity_id=sample_fact_set.primary_entity_id,
            branch=sample_fact_set.branch,
            facts=perturbed_facts,
        )

        pfs = PerturbedFactSet(
            original=sample_fact_set,
            perturbed=perturbed_set,
            perturbation_type=PerturbationType.WRONG_PARTY,
            changed_facts=[{
                "predicate": "party_affiliation",
                "original_value": "Democratic",
                "perturbed_value": "Republican",
            }],
            difficulty=Difficulty.EASY,
        )

        assert pfs.perturbation_type == PerturbationType.WRONG_PARTY
        assert pfs.difficulty == Difficulty.EASY
        assert len(pfs.changed_facts) == 1
        assert pfs.original.get_fact(FactPredicate.PARTY_AFFILIATION).object == "Democratic"
        assert pfs.perturbed.get_fact(FactPredicate.PARTY_AFFILIATION).object == "Republican"


# ---------------------------------------------------------------------------
# ArticleTestCase Tests
# ---------------------------------------------------------------------------


class TestArticleTestCase:
    """Tests for ArticleTestCase model."""

    def test_create_faithful_article(self, sample_fact_set: FactSet):
        article = ArticleTestCase(
            article_text="Senator Fetterman announced...",
            article_type=ArticleType.NEWS_REPORT,
            source_facts=sample_fact_set,
            is_faithful=True,
        )
        assert article.is_faithful is True
        assert article.perturbation_type == PerturbationType.NONE
        assert article.changed_facts == []
        assert article.expected_findings == []
        assert article.difficulty == Difficulty.MEDIUM  # default

    def test_create_perturbed_article(self, sample_fact_set: FactSet):
        article = ArticleTestCase(
            article_text="Senator Fetterman (R-PA)...",
            article_type=ArticleType.NEWS_REPORT,
            source_facts=sample_fact_set,
            is_faithful=False,
            perturbation_type=PerturbationType.WRONG_PARTY,
            changed_facts=[{
                "predicate": "party_affiliation",
                "original_value": "Democratic",
                "perturbed_value": "Republican",
            }],
            expected_findings=[
                "Incorrect party_affiliation: stated 'Republican', should be 'Democratic'"
            ],
            difficulty=Difficulty.EASY,
            model_used="claude-sonnet-4-20250514",
            tokens_used=450,
        )
        assert article.is_faithful is False
        assert len(article.changed_facts) == 1
        assert article.tokens_used == 450


# ---------------------------------------------------------------------------
# BatchConfig Tests
# ---------------------------------------------------------------------------


class TestBatchConfig:
    """Tests for BatchConfig model."""

    def test_default_config(self):
        config = BatchConfig()
        assert config.branch is None  # all branches
        assert config.entity_count == 10
        assert config.dry_run is False
        assert config.model == "claude-sonnet-4-20250514"
        # Default perturbation types = all except NONE
        assert PerturbationType.NONE not in config.perturbation_types
        assert len(config.perturbation_types) == 6

    def test_custom_config(self):
        config = BatchConfig(
            branch=GovernmentBranch.LEGISLATIVE,
            entity_count=5,
            perturbation_types=[PerturbationType.WRONG_PARTY, PerturbationType.WRONG_STATE],
            article_types=[ArticleType.NEWS_REPORT, ArticleType.ANALYSIS],
            dry_run=True,
        )
        assert config.branch == GovernmentBranch.LEGISLATIVE
        assert config.entity_count == 5
        assert len(config.perturbation_types) == 2
        assert len(config.article_types) == 2
        assert config.dry_run is True


# ---------------------------------------------------------------------------
# BatchResult Tests
# ---------------------------------------------------------------------------


class TestBatchResult:
    """Tests for BatchResult model."""

    def test_default_result(self):
        result = BatchResult()
        assert isinstance(result.batch_id, UUID)
        assert result.articles_generated == 0
        assert result.errors == []

    def test_populated_result(self):
        bid = uuid4()
        result = BatchResult(
            batch_id=bid,
            articles_generated=70,
            faithful_count=10,
            perturbed_count=60,
            total_tokens=35000,
            model_used="claude-sonnet-4-20250514",
            duration_seconds=120.5,
            errors=["Error for entity X"],
        )
        assert result.batch_id == bid
        assert result.articles_generated == 70
        assert result.faithful_count == 10
        assert result.perturbed_count == 60
        assert len(result.errors) == 1
