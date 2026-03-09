"""
Tests for Entity Disambiguation Service

Tests the DisambiguationService's ability to score and rank entity candidates
using type matching, name similarity, and context analysis.
"""

import pytest
from app.services.disambiguation import (
    DisambiguationService,
    Candidate,
    DisambiguationResult,
    EntityType,
    get_disambiguation_service,
    AMBIGUOUS_NAMES,
)


class TestCandidateDataclass:
    """Tests for Candidate dataclass"""

    def test_to_dict_basic(self):
        """Test basic candidate to_dict conversion"""
        candidate = Candidate(
            id="Q217173",
            label="Environmental Protection Agency",
            description="US government agency",
            types=["Q327333"],
            aliases=["EPA"],
            source="wikidata"
        )
        candidate.type_score = 0.9
        candidate.name_score = 0.8
        candidate.context_score = 0.7
        candidate.final_score = 0.85

        result = candidate.to_dict()

        assert result["id"] == "Q217173"
        assert result["label"] == "Environmental Protection Agency"
        assert result["scores"]["type"] == 0.9
        assert result["scores"]["final"] == 0.85

    def test_to_dict_minimal(self):
        """Test candidate with minimal data"""
        candidate = Candidate(id="Q5", label="human")

        result = candidate.to_dict()

        assert result["id"] == "Q5"
        assert result["label"] == "human"
        assert result["description"] is None
        assert result["types"] == []


class TestDisambiguationResult:
    """Tests for DisambiguationResult dataclass"""

    def test_to_dict_with_match(self):
        """Test result to_dict with match"""
        candidate = Candidate(
            id="Q217173",
            label="EPA",
            source="wikidata"
        )
        candidate.final_score = 0.9

        result = DisambiguationResult(
            entity_text="EPA",
            entity_type=EntityType.GOVERNMENT_ORG,
            match=candidate,
            confidence=0.9,
            needs_review=False,
            all_candidates=[candidate]
        )

        data = result.to_dict()

        assert data["entity_text"] == "EPA"
        assert data["entity_type"] == "government_org"
        assert data["match"]["id"] == "Q217173"
        assert data["confidence"] == 0.9
        assert data["needs_review"] is False

    def test_to_dict_no_match(self):
        """Test result to_dict without match"""
        result = DisambiguationResult(
            entity_text="Unknown Entity",
            entity_type=None,
            confidence=0.0,
            needs_review=True
        )

        data = result.to_dict()

        assert data["match"] is None
        assert data["needs_review"] is True


class TestDisambiguationServiceInit:
    """Tests for DisambiguationService initialization"""

    def test_service_initialization(self):
        """Test service initializes correctly"""
        service = DisambiguationService()
        assert service is not None

    def test_singleton_returns_same_instance(self):
        """Test singleton pattern"""
        import app.services.disambiguation as module
        module._service_instance = None

        service1 = get_disambiguation_service()
        service2 = get_disambiguation_service()

        assert service1 is service2

        module._service_instance = None


class TestTextNormalization:
    """Tests for text normalization"""

    def test_normalize_lowercase(self):
        """Test normalization lowercases text"""
        service = DisambiguationService()
        assert service._normalize_text("EPA") == "epa"

    def test_normalize_removes_punctuation(self):
        """Test normalization removes punctuation"""
        service = DisambiguationService()
        assert service._normalize_text("U.S.A.") == "usa"

    def test_normalize_whitespace(self):
        """Test normalization normalizes whitespace"""
        service = DisambiguationService()
        assert service._normalize_text("  New   York  ") == "new york"


class TestAcronymDetection:
    """Tests for acronym detection"""

    def test_is_acronym_true(self):
        """Test acronym detection returns true for acronyms"""
        service = DisambiguationService()
        assert service._is_acronym("EPA") is True
        assert service._is_acronym("FBI") is True
        assert service._is_acronym("NASA") is True

    def test_is_acronym_false(self):
        """Test acronym detection returns false for non-acronyms"""
        service = DisambiguationService()
        assert service._is_acronym("Environmental") is False
        assert service._is_acronym("agency") is False
        assert service._is_acronym("A") is False  # Too short
        assert service._is_acronym("TOOLONGFORACRONYM") is False  # Too long

    def test_matches_acronym_true(self):
        """Test acronym matching"""
        service = DisambiguationService()
        assert service._matches_acronym("EPA", "Environmental Protection Agency") is True
        assert service._matches_acronym("FBI", "Federal Bureau of Investigation") is True

    def test_matches_acronym_false(self):
        """Test acronym non-matching"""
        service = DisambiguationService()
        assert service._matches_acronym("EPA", "Department of Justice") is False
        assert service._matches_acronym("ABC", "Random Words Here") is False


class TestTypeScoring:
    """Tests for type matching scoring"""

    def test_exact_type_match(self):
        """Test exact type match returns 1.0"""
        service = DisambiguationService()

        score = service._score_type_match(
            EntityType.PERSON,
            ["Q5"],  # human
            "wikidata"
        )

        assert score == 1.0

    def test_partial_type_match(self):
        """Test partial type match returns lower score"""
        service = DisambiguationService()

        score = service._score_type_match(
            EntityType.GOVERNMENT_ORG,
            ["Q43229"],  # organization (partial match for gov_org)
            "wikidata"
        )

        assert 0.5 < score < 1.0

    def test_no_type_match(self):
        """Test no type match returns low score"""
        service = DisambiguationService()

        score = service._score_type_match(
            EntityType.PERSON,
            ["Q6256"],  # country - not a person type
            "wikidata"
        )

        assert score == 0.0 or score == 0.5

    def test_no_expected_type(self):
        """Test missing expected type returns neutral score"""
        service = DisambiguationService()

        score = service._score_type_match(
            None,
            ["Q5"],
            "wikidata"
        )

        assert score == 0.5

    def test_dbpedia_type_match(self):
        """Test DBpedia type matching"""
        service = DisambiguationService()

        score = service._score_type_match(
            EntityType.GOVERNMENT_ORG,
            ["GovernmentAgency"],
            "dbpedia"
        )

        assert score == 1.0


class TestNameScoring:
    """Tests for name similarity scoring"""

    def test_exact_name_match(self):
        """Test exact name match returns 1.0"""
        service = DisambiguationService()

        score = service._score_name_similarity(
            "Environmental Protection Agency",
            "Environmental Protection Agency"
        )

        assert score == 1.0

    def test_case_insensitive_match(self):
        """Test name matching is case insensitive"""
        service = DisambiguationService()

        score = service._score_name_similarity(
            "environmental protection agency",
            "Environmental Protection Agency"
        )

        assert score == 1.0

    def test_acronym_match(self):
        """Test acronym matches full name"""
        service = DisambiguationService()

        score = service._score_name_similarity(
            "EPA",
            "Environmental Protection Agency",
            candidate_aliases=[]
        )

        assert score >= 0.9

    def test_alias_match(self):
        """Test matching against aliases"""
        service = DisambiguationService()

        score = service._score_name_similarity(
            "EPA",
            "United States Environmental Protection Agency",
            candidate_aliases=["EPA", "US EPA"]
        )

        assert score >= 0.9

    def test_partial_match(self):
        """Test partial name match"""
        service = DisambiguationService()

        score = service._score_name_similarity(
            "Protection Agency",
            "Environmental Protection Agency"
        )

        assert 0.5 < score < 1.0

    def test_no_match(self):
        """Test no name match returns low score"""
        service = DisambiguationService()

        score = service._score_name_similarity(
            "Apple Inc",
            "Orange Corporation"
        )

        assert score < 0.5


class TestContextScoring:
    """Tests for context similarity scoring"""

    def test_context_with_overlap(self):
        """Test context with keyword overlap"""
        service = DisambiguationService()

        score = service._score_context_similarity(
            "The government agency responsible for environmental regulations",
            "United States government agency for environmental protection"
        )

        assert score > 0.5

    def test_context_no_overlap(self):
        """Test context with no keyword overlap"""
        service = DisambiguationService()

        score = service._score_context_similarity(
            "Sports team playing basketball",
            "Government agency for taxes"
        )

        assert score <= 0.5

    def test_context_none(self):
        """Test null context returns neutral score"""
        service = DisambiguationService()

        score = service._score_context_similarity(
            None,
            "Some description"
        )

        assert score == 0.5

    def test_description_none(self):
        """Test null description returns neutral score"""
        service = DisambiguationService()

        score = service._score_context_similarity(
            "Some context text",
            None
        )

        assert score == 0.5


class TestAmbiguousNames:
    """Tests for ambiguous name handling"""

    def test_washington_is_ambiguous(self):
        """Test Washington is detected as ambiguous"""
        service = DisambiguationService()
        assert service._is_ambiguous_name("Washington") is True
        assert service._is_ambiguous_name("washington") is True

    def test_normal_name_not_ambiguous(self):
        """Test normal names are not ambiguous"""
        service = DisambiguationService()
        assert service._is_ambiguous_name("Environmental Protection Agency") is False

    def test_get_ambiguous_types(self):
        """Test getting possible types for ambiguous name"""
        service = DisambiguationService()
        types = service._get_ambiguous_types("Washington")
        assert "person" in types
        assert "location" in types


class TestDisambiguation:
    """Tests for main disambiguation logic"""

    def test_disambiguate_no_candidates(self):
        """Test disambiguation with no candidates"""
        service = DisambiguationService()

        result = service.disambiguate(
            entity_text="Unknown Entity",
            entity_type=EntityType.ORGANIZATION,
            candidates=[]
        )

        assert result.match is None
        assert result.confidence == 0.0
        assert result.needs_review is True

    def test_disambiguate_single_candidate(self):
        """Test disambiguation with single good candidate"""
        service = DisambiguationService()

        candidate = Candidate(
            id="Q217173",
            label="Environmental Protection Agency",
            description="US government agency for environmental protection",
            types=["Q327333"],
            source="wikidata"
        )

        result = service.disambiguate(
            entity_text="Environmental Protection Agency",
            entity_type=EntityType.GOVERNMENT_ORG,
            candidates=[candidate],
            context="The government agency announced new regulations"
        )

        assert result.match is not None
        assert result.match.id == "Q217173"
        assert result.confidence > 0.7
        assert result.needs_review is False

    def test_disambiguate_multiple_candidates(self):
        """Test disambiguation selects best candidate"""
        service = DisambiguationService()

        candidates = [
            Candidate(
                id="Q1",
                label="Wrong Entity",
                types=["Q5"],  # person
                source="wikidata"
            ),
            Candidate(
                id="Q217173",
                label="Environmental Protection Agency",
                types=["Q327333"],  # government agency
                source="wikidata"
            ),
        ]

        result = service.disambiguate(
            entity_text="EPA",
            entity_type=EntityType.GOVERNMENT_ORG,
            candidates=candidates
        )

        # Should select the government agency, not the person
        assert result.match.id == "Q217173"

    def test_disambiguate_low_confidence_flagged(self):
        """Test low confidence match is flagged for review"""
        service = DisambiguationService()

        candidate = Candidate(
            id="Q1",
            label="Completely Different Name",
            types=["Q5"],
            source="wikidata"
        )

        result = service.disambiguate(
            entity_text="EPA",
            entity_type=EntityType.GOVERNMENT_ORG,
            candidates=[candidate]
        )

        assert result.needs_review is True
        assert result.confidence < 0.7

    def test_disambiguate_close_scores_flagged(self):
        """Test close scores trigger review flag"""
        service = DisambiguationService()

        candidates = [
            Candidate(
                id="Q1",
                label="EPA One",
                types=["Q327333"],
                source="wikidata"
            ),
            Candidate(
                id="Q2",
                label="EPA Two",
                types=["Q327333"],
                source="wikidata"
            ),
        ]

        result = service.disambiguate(
            entity_text="EPA",
            entity_type=EntityType.GOVERNMENT_ORG,
            candidates=candidates
        )

        # Both candidates are very similar, should flag for review
        assert result.needs_review is True

    def test_disambiguate_ambiguous_name_penalty(self):
        """Test ambiguous names without context get penalty"""
        service = DisambiguationService()

        candidate = Candidate(
            id="Q1",
            label="Washington",
            types=["Q515"],  # city
            source="wikidata"
        )

        # Without context
        result_no_context = service.disambiguate(
            entity_text="Washington",
            entity_type=EntityType.LOCATION,
            candidates=[candidate],
            context=None
        )

        # With context
        result_with_context = service.disambiguate(
            entity_text="Washington",
            entity_type=EntityType.LOCATION,
            candidates=[candidate],
            context="The city of Washington DC is the capital"
        )

        # Score should be lower without context for ambiguous names
        assert result_no_context.confidence < result_with_context.confidence

    def test_disambiguate_context_helps(self):
        """Test context improves disambiguation"""
        service = DisambiguationService()

        candidates = [
            Candidate(
                id="Q1",
                label="Washington",
                description="Capital city of the United States",
                types=["Q515"],
                source="wikidata"
            ),
            Candidate(
                id="Q2",
                label="George Washington",
                description="First president of the United States",
                types=["Q5"],
                source="wikidata"
            ),
        ]

        # Context about the city
        result = service.disambiguate(
            entity_text="Washington",
            entity_type=None,  # No type hint
            candidates=candidates,
            context="The capital city announced new traffic regulations"
        )

        # Should prefer the city due to context
        assert result.match.id == "Q1"

    def test_disambiguate_result_has_all_candidates(self):
        """Test result includes all scored candidates"""
        service = DisambiguationService()

        candidates = [
            Candidate(id="Q1", label="A", source="wikidata"),
            Candidate(id="Q2", label="B", source="wikidata"),
            Candidate(id="Q3", label="C", source="wikidata"),
        ]

        result = service.disambiguate(
            entity_text="Test",
            entity_type=None,
            candidates=candidates
        )

        assert len(result.all_candidates) == 3
        # Should be sorted by score
        scores = [c.final_score for c in result.all_candidates]
        assert scores == sorted(scores, reverse=True)


class TestWeightedScoring:
    """Tests for weighted score combination"""

    def test_weights_sum_to_one_with_context(self):
        """Test weights sum to 1.0 with context"""
        service = DisambiguationService()
        total = (
            service.TYPE_WEIGHT +
            service.NAME_WEIGHT +
            service.CONTEXT_WEIGHT
        )
        assert total == 1.0

    def test_weights_sum_to_one_without_context(self):
        """Test weights sum to 1.0 without context"""
        service = DisambiguationService()
        total = (
            service.TYPE_WEIGHT_NO_CONTEXT +
            service.NAME_WEIGHT_NO_CONTEXT
        )
        assert total == 1.0

    def test_type_has_highest_weight(self):
        """Test type matching has highest individual weight"""
        service = DisambiguationService()
        assert service.TYPE_WEIGHT >= service.NAME_WEIGHT
        assert service.TYPE_WEIGHT >= service.CONTEXT_WEIGHT
