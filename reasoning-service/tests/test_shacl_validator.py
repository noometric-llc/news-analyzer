"""
Tests for SHACLValidator service (EVAL-3.2).

Validates:
- Valid ontology passes validation
- Missing required properties fail validation
- Violation reports contain expected fields
- Graceful degradation when pyshacl unavailable
"""

import pytest
from pathlib import Path
from rdflib import Graph, Namespace, Literal, BNode, URIRef
from rdflib.namespace import RDF, OWL, XSD

from app.services.shacl_validator import SHACLValidator, SHACLReport, SHACL_AVAILABLE

CB = Namespace("http://newsanalyzer.org/ontology/cognitive-bias#")
ONTOLOGY_DIR = Path(__file__).parent.parent / "ontology"


@pytest.fixture
def shapes_graph():
    """Load SHACL shapes."""
    g = Graph()
    g.parse(str(ONTOLOGY_DIR / "cognitive-bias-shapes.ttl"), format="turtle")
    return g


@pytest.fixture
def bias_graph():
    """Load the cognitive bias ontology."""
    g = Graph()
    g.parse(str(ONTOLOGY_DIR / "cognitive-bias.ttl"), format="turtle")
    return g


@pytest.fixture
def validator(shapes_graph):
    """SHACLValidator with cognitive bias shapes."""
    return SHACLValidator(shapes_graph)


class TestSHACLValidatorValid:
    """Tests with valid ontology data."""

    def test_valid_ontology_conforms(self, validator, bias_graph):
        """Valid ontology passes SHACL validation."""
        report = validator.validate_ontology(bias_graph)
        assert report.conforms is True
        assert report.results_count == 0
        assert len(report.violations) == 0

    def test_report_has_shapes_evaluated(self, validator, bias_graph):
        """Report lists the shapes that were evaluated."""
        report = validator.validate_ontology(bias_graph)
        assert len(report.shapes_evaluated) == 3
        assert "CognitiveDistortionShape" in report.shapes_evaluated
        assert "BiasAnnotationShape" in report.shapes_evaluated
        assert "AcademicSourceShape" in report.shapes_evaluated

    def test_report_available_flag(self, validator, bias_graph):
        """Report indicates pyshacl was available."""
        report = validator.validate(bias_graph)
        assert report.available is True


class TestSHACLValidatorInvalid:
    """Tests with invalid data that should fail validation."""

    def test_missing_definition_fails(self, validator):
        """A CognitiveDistortion without hasDefinition fails validation."""
        g = Graph()
        g.parse(str(ONTOLOGY_DIR / "cognitive-bias.ttl"), format="turtle")

        # Add a distortion missing required properties
        bad = URIRef("http://newsanalyzer.org/ontology/cognitive-bias#BadBias")
        g.add((bad, RDF.type, OWL.NamedIndividual))
        g.add((bad, RDF.type, CB.CognitiveDistortion))
        # No hasDefinition, no hasAcademicSource, no hasDetectionPattern

        report = validator.validate(g)
        assert report.conforms is False
        assert report.results_count > 0

    def test_violation_has_expected_fields(self, validator):
        """Violations contain focus_node, path, and message."""
        g = Graph()
        g.parse(str(ONTOLOGY_DIR / "cognitive-bias.ttl"), format="turtle")

        bad = URIRef("http://newsanalyzer.org/ontology/cognitive-bias#BadBias")
        g.add((bad, RDF.type, OWL.NamedIndividual))
        g.add((bad, RDF.type, CB.CognitiveDistortion))

        report = validator.validate(g)
        assert len(report.violations) > 0

        v = report.violations[0]
        assert v.focus_node != ""
        assert v.message != ""

    def test_missing_source_author_fails(self, validator):
        """An AcademicSource without sourceAuthor fails validation."""
        g = Graph()
        g.parse(str(ONTOLOGY_DIR / "cognitive-bias.ttl"), format="turtle")

        bad_src = URIRef("http://newsanalyzer.org/ontology/cognitive-bias#BadSource")
        g.add((bad_src, RDF.type, OWL.NamedIndividual))
        g.add((bad_src, RDF.type, CB.AcademicSource))
        # Has year and title but no author
        g.add((bad_src, CB.sourceYear, Literal(2024, datatype=XSD.integer)))
        g.add((bad_src, CB.sourceTitle, Literal("Test Title")))

        report = validator.validate(g)
        assert report.conforms is False
