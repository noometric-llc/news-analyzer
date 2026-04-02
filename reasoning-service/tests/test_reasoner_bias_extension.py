"""
Tests for OWLReasoner bias ontology extension methods (EVAL-3.2).

Validates:
- load_bias_ontology() increases triple count
- list_distortions() returns correct counts
- get_distortion_definition() returns expected fields
- get_bias_ontology_stats() returns expected structure
- get_all_distortion_definitions() returns all definitions
- Existing methods still work after bias ontology loaded
- Graceful degradation on missing ontology file
"""

import pytest
from pathlib import Path

from app.services.owl_reasoner import OWLReasoner, CB


ONTOLOGY_DIR = Path(__file__).parent.parent / "ontology"


@pytest.fixture
def reasoner():
    """Fresh OWLReasoner with only newsanalyzer.ttl loaded."""
    return OWLReasoner()


@pytest.fixture
def reasoner_with_bias():
    """OWLReasoner with both ontologies loaded."""
    r = OWLReasoner()
    r.load_bias_ontology()
    return r


class TestLoadBiasOntology:
    """Tests for load_bias_ontology()."""

    def test_increases_triple_count(self, reasoner):
        """Loading bias ontology adds triples to the graph."""
        before = len(reasoner.graph)
        reasoner.load_bias_ontology()
        after = len(reasoner.graph)
        assert after > before

    def test_sets_loaded_flag(self, reasoner):
        """Sets _bias_ontology_loaded to True on success."""
        assert reasoner._bias_ontology_loaded is False
        reasoner.load_bias_ontology()
        assert reasoner._bias_ontology_loaded is True

    def test_missing_file_does_not_crash(self, reasoner):
        """Missing file logs warning, doesn't raise."""
        reasoner.load_bias_ontology(path="/nonexistent/path.ttl")
        assert reasoner._bias_ontology_loaded is False

    def test_custom_path(self, reasoner):
        """Can load from explicit path."""
        path = str(ONTOLOGY_DIR / "cognitive-bias.ttl")
        reasoner.load_bias_ontology(path=path)
        assert reasoner._bias_ontology_loaded is True


class TestListDistortions:
    """Tests for list_distortions()."""

    def test_returns_all_14(self, reasoner_with_bias):
        """Returns all 14 distortions when no filter."""
        result = reasoner_with_bias.list_distortions()
        assert len(result) == 14

    def test_filter_bias_returns_5(self, reasoner_with_bias):
        """Filtering by 'bias' returns 5 CognitiveBias individuals."""
        result = reasoner_with_bias.list_distortions(category="bias")
        assert len(result) == 5

    def test_filter_fallacy_returns_9(self, reasoner_with_bias):
        """Filtering by 'fallacy' returns 9 LogicalFallacy individuals."""
        result = reasoner_with_bias.list_distortions(category="fallacy")
        assert len(result) == 9

    def test_each_has_required_fields(self, reasoner_with_bias):
        """Each distortion dict has uri, label, definition."""
        result = reasoner_with_bias.list_distortions()
        for d in result:
            assert "uri" in d
            assert "label" in d
            assert "definition" in d
            assert len(d["definition"]) > 10


class TestGetDistortionDefinition:
    """Tests for get_distortion_definition()."""

    def test_confirmation_bias(self, reasoner_with_bias):
        """ConfirmationBias returns definition, source, pattern."""
        uri = str(CB.ConfirmationBias)
        result = reasoner_with_bias.get_distortion_definition(uri)
        assert result != {}
        assert "definition" in result
        assert "Nickerson" in result["academic_source"]["author"]
        assert result["academic_source"]["year"] == 1998
        assert "detection_pattern" in result
        assert len(result["detection_pattern"]) > 10

    def test_nonexistent_returns_empty(self, reasoner_with_bias):
        """Unknown URI returns empty dict."""
        uri = str(CB.NonexistentBias)
        result = reasoner_with_bias.get_distortion_definition(uri)
        assert result == {}

    def test_framing_effect(self, reasoner_with_bias):
        """FramingEffect returns Tversky & Kahneman 1981 source."""
        uri = str(CB.FramingEffect)
        result = reasoner_with_bias.get_distortion_definition(uri)
        assert result != {}
        assert result["academic_source"]["year"] == 1981


class TestGetBiasOntologyStats:
    """Tests for get_bias_ontology_stats()."""

    def test_returns_expected_counts(self, reasoner_with_bias):
        """Stats match the ontology specification."""
        stats = reasoner_with_bias.get_bias_ontology_stats()
        assert stats["total_distortions"] == 14
        assert stats["cognitive_biases"] == 5
        assert stats["logical_fallacies"] == 9
        assert stats["formal_fallacies"] == 2
        assert stats["informal_fallacies"] == 7
        assert stats["academic_sources"] == 4
        assert stats["detection_patterns"] == 14


class TestGetAllDistortionDefinitions:
    """Tests for get_all_distortion_definitions()."""

    def test_returns_14_definitions(self, reasoner_with_bias):
        """Returns all 14 definitions in one call."""
        result = reasoner_with_bias.get_all_distortion_definitions()
        assert len(result) == 14

    def test_each_has_full_fields(self, reasoner_with_bias):
        """Each definition has all required fields."""
        result = reasoner_with_bias.get_all_distortion_definitions()
        for d in result:
            assert "uri" in d
            assert "label" in d
            assert "definition" in d
            assert "detection_pattern" in d
            assert "academic_source" in d
            assert "author" in d["academic_source"]
            assert "year" in d["academic_source"]
            assert "title" in d["academic_source"]


class TestExistingMethodsStillWork:
    """Regression: existing methods work after bias ontology is loaded."""

    def test_infer_works(self, reasoner_with_bias):
        """infer() runs without error on combined graph."""
        result = reasoner_with_bias.infer()
        assert result > 0

    def test_check_consistency_works(self, reasoner_with_bias):
        """check_consistency() returns a list (possibly empty)."""
        result = reasoner_with_bias.check_consistency()
        assert isinstance(result, list)

    def test_query_sparql_works(self, reasoner_with_bias):
        """query_sparql() still works for base ontology queries."""
        result = reasoner_with_bias.query_sparql(
            "SELECT (COUNT(*) AS ?c) WHERE { ?s ?p ?o }"
        )
        assert len(result) == 1
        assert int(result[0]["c"]) > 0

    def test_get_ontology_stats_works(self, reasoner_with_bias):
        """get_ontology_stats() returns expected structure."""
        stats = reasoner_with_bias.get_ontology_stats()
        assert "total_triples" in stats
        assert stats["total_triples"] > 0
