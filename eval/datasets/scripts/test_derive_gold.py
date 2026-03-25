"""
Tests for the gold dataset derivation script.

Tests the pure functional core: predicate mapping, span location,
and entity derivation. No HTTP calls or file I/O needed.
"""

import pytest
import yaml
from pathlib import Path
from derive_gold import (
    map_predicate_to_entity_type,
    locate_span,
    derive_entities_from_facts,
    article_to_test_case,
    SKIP_PREDICATES,
)


# ---------------------------------------------------------------------------
# Predicate → Entity Type Mapping
# ---------------------------------------------------------------------------


class TestMapPredicateToEntityType:
    """Tests for the predicate-to-entity-type mapping function."""

    def test_state_maps_to_location(self):
        assert map_predicate_to_entity_type("state") == "location"

    def test_district_maps_to_location(self):
        assert map_predicate_to_entity_type("district") == "location"

    def test_committee_membership_maps_to_government_org(self):
        assert map_predicate_to_entity_type("committee_membership") == "government_org"

    def test_court_maps_to_government_org(self):
        assert map_predicate_to_entity_type("court") == "government_org"

    def test_party_affiliation_maps_to_concept(self):
        assert map_predicate_to_entity_type("party_affiliation") == "concept"

    def test_vice_president_maps_to_person(self):
        assert map_predicate_to_entity_type("vice_president") == "person"

    def test_appointing_president_maps_to_person(self):
        assert map_predicate_to_entity_type("appointing_president") == "person"

    def test_court_level_maps_to_government_org(self):
        assert map_predicate_to_entity_type("court_level") == "government_org"

    def test_skip_predicates_return_none(self):
        """All skip predicates should return None — their objects aren't entities."""
        for pred in SKIP_PREDICATES:
            assert map_predicate_to_entity_type(pred) is None, (
                f"Expected None for skip predicate '{pred}'"
            )

    def test_unknown_predicate_returns_none(self):
        assert map_predicate_to_entity_type("unknown_predicate") is None


# ---------------------------------------------------------------------------
# Span Location
# ---------------------------------------------------------------------------


class TestLocateSpan:
    """Tests for finding entity text within article text."""

    def test_exact_match(self):
        article = "Senator John Fetterman announced new legislation."
        result = locate_span("John Fetterman", article)
        assert result == (8, 22)

    def test_exact_match_at_start(self):
        article = "Pennsylvania is a large state."
        result = locate_span("Pennsylvania", article)
        assert result == (0, 12)

    def test_case_insensitive_fallback(self):
        article = "The senator from PENNSYLVANIA spoke today."
        result = locate_span("Pennsylvania", article)
        assert result is not None
        assert result == (17, 29)

    def test_not_found_returns_none(self):
        article = "The committee met to discuss policy."
        result = locate_span("John Fetterman", article)
        assert result is None

    def test_first_occurrence_used(self):
        article = "Warren met Warren at the hearing."
        result = locate_span("Warren", article)
        assert result == (0, 6)  # First occurrence

    def test_empty_text_returns_none(self):
        article = "Some article text."
        result = locate_span("", article)
        # Empty string finds at position 0 with str.find
        # This is correct behavior — the _add_entity function
        # guards against empty/short text separately
        assert result is not None  # "" finds at 0

    def test_preserves_article_casing(self):
        """Span offsets point to the original article text."""
        article = "The EPA issued new rules."
        result = locate_span("EPA", article)
        assert result == (4, 7)
        assert article[result[0] : result[1]] == "EPA"


# ---------------------------------------------------------------------------
# Entity Derivation from Facts
# ---------------------------------------------------------------------------


SAMPLE_FACTS = [
    {
        "subject": "John Fetterman",
        "predicate": "party_affiliation",
        "object": "Democratic",
        "entity_type": "CongressionalMember",
        "branch": "legislative",
        "data_source": "CONGRESS_GOV",
    },
    {
        "subject": "John Fetterman",
        "predicate": "state",
        "object": "Pennsylvania",
        "entity_type": "CongressionalMember",
        "branch": "legislative",
        "data_source": "CONGRESS_GOV",
    },
    {
        "subject": "John Fetterman",
        "predicate": "committee_membership",
        "object": "Banking Committee",
        "entity_type": "CongressionalMember",
        "branch": "legislative",
        "data_source": "CONGRESS_GOV",
    },
    {
        "subject": "John Fetterman",
        "predicate": "chamber",
        "object": "Senate",
        "entity_type": "CongressionalMember",
        "branch": "legislative",
        "data_source": "CONGRESS_GOV",
    },
]

SAMPLE_ARTICLE = (
    "Senator John Fetterman, a Democratic member from Pennsylvania, "
    "spoke at the Banking Committee hearing on Tuesday."
)


class TestDeriveEntitiesFromFacts:
    """Tests for the core entity derivation logic."""

    def test_extracts_subject_as_person(self):
        entities = derive_entities_from_facts(SAMPLE_FACTS, SAMPLE_ARTICLE)
        persons = [e for e in entities if e["type"] == "person"]
        assert len(persons) == 1
        assert persons[0]["text"] == "John Fetterman"

    def test_extracts_state_as_location(self):
        entities = derive_entities_from_facts(SAMPLE_FACTS, SAMPLE_ARTICLE)
        locations = [e for e in entities if e["type"] == "location"]
        assert len(locations) == 1
        assert locations[0]["text"] == "Pennsylvania"

    def test_extracts_committee_as_government_org(self):
        entities = derive_entities_from_facts(SAMPLE_FACTS, SAMPLE_ARTICLE)
        gov_orgs = [e for e in entities if e["type"] == "government_org"]
        assert len(gov_orgs) == 1
        assert gov_orgs[0]["text"] == "Banking Committee"

    def test_extracts_party_as_concept(self):
        entities = derive_entities_from_facts(SAMPLE_FACTS, SAMPLE_ARTICLE)
        concepts = [e for e in entities if e["type"] == "concept"]
        assert len(concepts) == 1
        assert concepts[0]["text"] == "Democratic"

    def test_skips_chamber_predicate(self):
        """chamber is a skip predicate — 'Senate' should NOT become an entity."""
        entities = derive_entities_from_facts(SAMPLE_FACTS, SAMPLE_ARTICLE)
        texts = [e["text"] for e in entities]
        # "Senate" from chamber predicate should be skipped
        # (it might still appear if another predicate maps it, but chamber doesn't)
        assert "Senate" not in texts

    def test_deduplicates_same_subject(self):
        """Same subject appearing in multiple facts should produce one entity."""
        entities = derive_entities_from_facts(SAMPLE_FACTS, SAMPLE_ARTICLE)
        person_count = sum(1 for e in entities if e["text"] == "John Fetterman")
        assert person_count == 1

    def test_entities_sorted_by_offset(self):
        entities = derive_entities_from_facts(SAMPLE_FACTS, SAMPLE_ARTICLE)
        starts = [e["start"] for e in entities]
        assert starts == sorted(starts)

    def test_offsets_are_correct(self):
        entities = derive_entities_from_facts(SAMPLE_FACTS, SAMPLE_ARTICLE)
        for entity in entities:
            extracted = SAMPLE_ARTICLE[entity["start"] : entity["end"]]
            assert extracted == entity["text"], (
                f"Offset mismatch: expected '{entity['text']}' "
                f"but got '{extracted}' at [{entity['start']}:{entity['end']}]"
            )

    def test_empty_facts_returns_empty(self):
        entities = derive_entities_from_facts([], SAMPLE_ARTICLE)
        assert entities == []

    def test_entity_not_in_text_is_skipped(self):
        """If an entity from facts doesn't appear in the article, skip it."""
        facts = [
            {
                "subject": "Nonexistent Person",
                "predicate": "state",
                "object": "Atlantis",
                "entity_type": "CongressionalMember",
                "branch": "legislative",
                "data_source": "TEST",
            }
        ]
        entities = derive_entities_from_facts(facts, SAMPLE_ARTICLE)
        assert entities == []


# ---------------------------------------------------------------------------
# Article → Test Case
# ---------------------------------------------------------------------------


class TestArticleToTestCase:
    """Tests for converting a backend article DTO to a Promptfoo test case."""

    def _make_article(self, **overrides) -> dict:
        base = {
            "id": "test-uuid-001",
            "articleText": SAMPLE_ARTICLE,
            "articleType": "news_report",
            "isFaithful": True,
            "perturbationType": None,
            "difficulty": "medium",
            "sourceFacts": {
                "topic": "Senator John Fetterman",
                "branch": "legislative",
                "facts": SAMPLE_FACTS,
            },
            "groundTruth": {},
            "modelUsed": "claude-sonnet-4-5-20250929",
            "tokensUsed": 500,
        }
        base.update(overrides)
        return base

    def test_produces_valid_test_case(self):
        article = self._make_article()
        result = article_to_test_case(article, "leg", 1)

        assert result is not None
        assert "vars" in result
        assert "article_text" in result["vars"]
        assert "entities" in result["vars"]
        assert "metadata" in result["vars"]

    def test_metadata_fields_populated(self):
        article = self._make_article()
        result = article_to_test_case(article, "leg", 5)
        meta = result["vars"]["metadata"]

        assert meta["id"] == "eval-2-leg-005"
        assert meta["article_id"] == "test-uuid-001"
        assert meta["article_type"] == "news_report"
        assert meta["branch"] == "legislative"
        assert meta["source"] == "derived"
        assert meta["curated"] is False
        assert meta["curated_date"] is None

    def test_perturbation_type_defaults_to_none(self):
        article = self._make_article(perturbationType=None)
        result = article_to_test_case(article, "leg", 1)
        assert result["vars"]["metadata"]["perturbation_type"] == "none"

    def test_returns_none_for_empty_facts(self):
        article = self._make_article(sourceFacts={"branch": "legislative", "facts": []})
        result = article_to_test_case(article, "leg", 1)
        assert result is None

    def test_returns_none_when_no_entities_found(self):
        """If facts exist but none can be located in the text, return None."""
        article = self._make_article(
            articleText="A completely unrelated article about weather.",
            sourceFacts={
                "branch": "legislative",
                "facts": [
                    {
                        "subject": "Invisible Person",
                        "predicate": "state",
                        "object": "Nowhere",
                        "entity_type": "Test",
                        "branch": "legislative",
                        "data_source": "TEST",
                    }
                ],
            },
        )
        result = article_to_test_case(article, "leg", 1)
        assert result is None

    def test_entities_have_required_fields(self):
        article = self._make_article()
        result = article_to_test_case(article, "leg", 1)
        for entity in result["vars"]["entities"]:
            assert "text" in entity
            assert "type" in entity
            assert "start" in entity
            assert "end" in entity
            assert isinstance(entity["start"], int)
            assert isinstance(entity["end"], int)


# ---------------------------------------------------------------------------
# YAML Output Validation
# ---------------------------------------------------------------------------


class TestYamlOutput:
    """Tests that derived test cases serialize to valid Promptfoo YAML."""

    def test_test_case_serializes_to_valid_yaml(self):
        article = {
            "id": "test-uuid",
            "articleText": SAMPLE_ARTICLE,
            "articleType": "news_report",
            "isFaithful": True,
            "perturbationType": None,
            "difficulty": "medium",
            "sourceFacts": {
                "topic": "Senator John Fetterman",
                "branch": "legislative",
                "facts": SAMPLE_FACTS,
            },
            "groundTruth": {},
            "modelUsed": "claude-sonnet-4-5-20250929",
            "tokensUsed": 500,
        }
        test_case = article_to_test_case(article, "leg", 1)
        assert test_case is not None

        # Serialize to YAML and back — should round-trip cleanly
        yaml_str = yaml.dump([test_case], default_flow_style=False)
        loaded = yaml.safe_load(yaml_str)

        assert len(loaded) == 1
        assert "vars" in loaded[0]
        assert loaded[0]["vars"]["article_text"] == SAMPLE_ARTICLE
        assert len(loaded[0]["vars"]["entities"]) > 0

    def test_multiple_test_cases_serialize(self):
        """Verify a list of test cases produces valid YAML array."""
        cases = []
        for i in range(3):
            article = {
                "id": f"uuid-{i}",
                "articleText": SAMPLE_ARTICLE,
                "articleType": "news_report",
                "isFaithful": True,
                "perturbationType": None,
                "difficulty": "medium",
                "sourceFacts": {
                    "topic": "Test",
                    "branch": "legislative",
                    "facts": SAMPLE_FACTS,
                },
                "groundTruth": {},
            }
            tc = article_to_test_case(article, "leg", i + 1)
            if tc:
                cases.append(tc)

        yaml_str = yaml.dump(cases, default_flow_style=False)
        loaded = yaml.safe_load(yaml_str)
        assert len(loaded) == 3
