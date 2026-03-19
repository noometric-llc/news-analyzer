"""
EVAL Track Data Models

Pydantic models and enums for the KB Fact Extraction and Synthetic Article
Generation pipeline (EVAL-1). These models are shared across the fact extractor,
perturbation engine, article generator, and batch orchestrator.
"""

from __future__ import annotations

from datetime import date
from enum import Enum
from uuid import UUID, uuid4

from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------


class GovernmentBranch(str, Enum):
    """Branch of the U.S. federal government."""

    LEGISLATIVE = "legislative"
    EXECUTIVE = "executive"
    JUDICIAL = "judicial"


class FactPredicate(str, Enum):
    """Constrained set of fact predicates.

    Enables type-safe perturbation logic — the perturbation engine can
    pattern-match on these values to know which facts are swappable.
    """

    # Legislative — Congressional Members
    PARTY_AFFILIATION = "party_affiliation"
    STATE = "state"
    DISTRICT = "district"
    CHAMBER = "chamber"
    COMMITTEE_MEMBERSHIP = "committee_membership"
    LEADERSHIP_ROLE = "leadership_role"
    TERM_START = "term_start"
    TERM_END = "term_end"

    # Executive — Presidencies & Administration
    PRESIDENCY_NUMBER = "presidency_number"
    VICE_PRESIDENT = "vice_president"
    CABINET_POSITION = "cabinet_position"
    EXECUTIVE_ORDER = "executive_order"
    CHIEF_OF_STAFF = "chief_of_staff"
    AGENCY_HEAD = "agency_head"

    # Judicial
    COURT = "court"
    APPOINTING_PRESIDENT = "appointing_president"
    CONFIRMATION_DATE = "confirmation_date"
    COURT_LEVEL = "court_level"

    # Cross-branch — Statutes (context only, no perturbation in EVAL-1)
    STATUTE_REFERENCE = "statute_reference"
    STATUTE_SUBJECT = "statute_subject"


class FactConfidence(str, Enum):
    """Confidence tier based on data source authority."""

    TIER_1 = "tier_1"  # Official government source
    TIER_2 = "tier_2"  # Enrichment / derived source


class PerturbationType(str, Enum):
    """Types of controlled factual perturbations for test article generation."""

    NONE = "none"  # Faithful — no perturbation
    WRONG_PARTY = "wrong_party"  # Swap D↔R
    WRONG_COMMITTEE = "wrong_committee"  # Assign to incorrect committee
    WRONG_STATE = "wrong_state"  # Incorrect state or district
    HALLUCINATE_ROLE = "hallucinate_role"  # Invent role not in KB
    OUTDATED_INFO = "outdated_info"  # Use superseded information
    CONFLATE_INDIVIDUALS = "conflate_individuals"  # Mix two people's attributes


class ArticleType(str, Enum):
    """Style of synthetic news article to generate."""

    NEWS_REPORT = "news_report"
    BREAKING_NEWS = "breaking_news"
    OPINION = "opinion"
    ANALYSIS = "analysis"


class Difficulty(str, Enum):
    """How hard the perturbation is to detect."""

    EASY = "easy"  # Obvious errors (wrong party for well-known figure)
    MEDIUM = "medium"  # Subtle errors (wrong committee, plausible but false)
    HARD = "hard"  # Requires specific knowledge (conflated individuals)
    ADVERSARIAL = "adversarial"  # Designed to fool (outdated but once-true)


# ---------------------------------------------------------------------------
# Core Models
# ---------------------------------------------------------------------------


class Fact(BaseModel):
    """A single verifiable statement extracted from the KB."""

    subject: str  # e.g. "John Fetterman"
    subject_id: UUID | None = None  # Backend entity UUID
    predicate: FactPredicate  # e.g. PARTY_AFFILIATION
    object: str  # e.g. "Democratic"
    entity_type: str  # e.g. "CongressionalMember"
    branch: GovernmentBranch  # e.g. LEGISLATIVE
    data_source: str  # e.g. "CONGRESS_GOV"
    confidence: FactConfidence = FactConfidence.TIER_1
    valid_from: date | None = None  # Temporal bounds
    valid_to: date | None = None


class FactSet(BaseModel):
    """A coherent collection of facts about an entity or topic."""

    topic: str  # e.g. "Senator John Fetterman"
    primary_entity_id: UUID | None = None
    branch: GovernmentBranch
    facts: list[Fact]
    related_entity_ids: list[UUID] = Field(default_factory=list)

    def get_fact(self, predicate: FactPredicate) -> Fact | None:
        """Get the first fact matching the given predicate."""
        return next(
            (f for f in self.facts if f.predicate == predicate),
            None,
        )

    def get_facts(self, predicate: FactPredicate) -> list[Fact]:
        """Get all facts matching the given predicate."""
        return [f for f in self.facts if f.predicate == predicate]


class PerturbedFactSet(BaseModel):
    """A FactSet with controlled modifications and a label of what changed."""

    original: FactSet
    perturbed: FactSet
    perturbation_type: PerturbationType
    changed_facts: list[dict]  # [{predicate, original_value, perturbed_value}]
    difficulty: Difficulty


class ArticleTestCase(BaseModel):
    """A generated article paired with its full ground-truth label."""

    model_config = {"protected_namespaces": ()}

    article_text: str
    article_type: ArticleType
    source_facts: FactSet  # Original (faithful) facts used
    is_faithful: bool  # True if no perturbation applied
    perturbation_type: PerturbationType = PerturbationType.NONE
    changed_facts: list[dict] = Field(default_factory=list)
    expected_findings: list[str] = Field(default_factory=list)
    difficulty: Difficulty = Difficulty.MEDIUM
    model_used: str = ""  # e.g. "claude-sonnet-4-20250514"
    tokens_used: int = 0


# ---------------------------------------------------------------------------
# Batch Configuration & Results
# ---------------------------------------------------------------------------


class BatchConfig(BaseModel):
    """Configuration for a batch generation run."""

    branch: GovernmentBranch | None = None  # None = all branches
    entity_count: int = 10  # Number of entities to generate articles for
    perturbation_types: list[PerturbationType] = Field(
        default_factory=lambda: [
            p for p in PerturbationType if p != PerturbationType.NONE
        ]
    )
    article_types: list[ArticleType] = Field(
        default_factory=lambda: [ArticleType.NEWS_REPORT]
    )
    model: str = "claude-sonnet-4-20250514"  # Default to Sonnet for cost
    dry_run: bool = False  # Log prompts without calling Claude API


class BatchResult(BaseModel):
    """Result summary of a batch generation run."""

    model_config = {"protected_namespaces": ()}

    batch_id: UUID = Field(default_factory=uuid4)
    articles_generated: int = 0
    faithful_count: int = 0
    perturbed_count: int = 0
    total_tokens: int = 0
    model_used: str = ""
    duration_seconds: float = 0.0
    errors: list[str] = Field(default_factory=list)
