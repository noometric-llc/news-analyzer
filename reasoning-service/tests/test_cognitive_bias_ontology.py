"""
Tests for the Cognitive Bias OWL Ontology (EVAL-3.1).

These are pure ontology validation tests — they load .ttl files directly
with rdflib and run SPARQL queries. No FastAPI service required.

Validates:
- Ontology parses without errors
- Correct number of distortion individuals (14)
- Class hierarchy (5 CognitiveBias, 7 InformalFallacy, 2 FormalFallacy)
- Required properties present on all individuals
- Academic sources have bibliographic metadata
- Combined graph (newsanalyzer.ttl + cognitive-bias.ttl) loads
- SHACL validation passes (skipped until pyshacl added in EVAL-3.2)
"""

import pytest
from pathlib import Path
from rdflib import Graph, Namespace, URIRef
from rdflib.namespace import RDF, RDFS, OWL, XSD

CB = Namespace("http://newsanalyzer.org/ontology/cognitive-bias#")
ONTOLOGY_DIR = Path(__file__).parent.parent / "ontology"


@pytest.fixture
def bias_graph():
    """Load the cognitive bias ontology into an rdflib Graph."""
    g = Graph()
    g.parse(str(ONTOLOGY_DIR / "cognitive-bias.ttl"), format="turtle")
    return g


@pytest.fixture
def combined_graph():
    """Load both ontologies into a single merged Graph."""
    g = Graph()
    g.parse(str(ONTOLOGY_DIR / "newsanalyzer.ttl"), format="turtle")
    g.parse(str(ONTOLOGY_DIR / "cognitive-bias.ttl"), format="turtle")
    return g


def _count_individuals_of_class(g: Graph, cls_uri: str) -> int:
    """Count NamedIndividual instances of a class (direct type only)."""
    q = f"""
    SELECT (COUNT(?d) AS ?count) WHERE {{
        ?d rdf:type owl:NamedIndividual .
        ?d rdf:type <{cls_uri}> .
    }}
    """
    for row in g.query(q):
        return int(row[0])
    return 0


def _count_distortions(g: Graph) -> int:
    """Count all NamedIndividual instances that are CognitiveDistortions (via subClassOf path)."""
    q = f"""
    SELECT (COUNT(?d) AS ?count) WHERE {{
        ?d rdf:type owl:NamedIndividual .
        ?d rdf:type/rdfs:subClassOf* <{CB}CognitiveDistortion> .
    }}
    """
    for row in g.query(q):
        return int(row[0])
    return 0


# -------------------------------------------------------------------
# AC1: Ontology loads without errors
# -------------------------------------------------------------------


class TestOntologyLoading:
    """Tests that ontology files parse correctly."""

    def test_bias_ontology_loads(self, bias_graph):
        """cognitive-bias.ttl loads into rdflib without errors."""
        assert len(bias_graph) > 0

    def test_combined_graph_loads(self, combined_graph):
        """Both ontologies load into a single graph without errors."""
        assert len(combined_graph) > len(Graph().parse(
            str(ONTOLOGY_DIR / "cognitive-bias.ttl"), format="turtle"
        ))


# -------------------------------------------------------------------
# AC2: SPARQL query returns all 14 distortions with definitions
# -------------------------------------------------------------------


class TestDistortionCount:
    """Tests that the correct number of distortions are present."""

    def test_total_distortions(self, bias_graph):
        """Exactly 14 CognitiveDistortion individuals exist."""
        assert _count_distortions(bias_graph) == 14

    def test_all_distortions_have_definitions(self, bias_graph):
        """Every distortion has a hasDefinition property."""
        q = f"""
        SELECT ?d WHERE {{
            ?d rdf:type owl:NamedIndividual .
            ?d rdf:type/rdfs:subClassOf* <{CB}CognitiveDistortion> .
            FILTER NOT EXISTS {{ ?d <{CB}hasDefinition> ?def }}
        }}
        """
        missing = list(bias_graph.query(q))
        assert len(missing) == 0, f"Distortions missing definitions: {missing}"


# -------------------------------------------------------------------
# AC3: Every distortion has at least one academic source
# -------------------------------------------------------------------


class TestAcademicSources:
    """Tests that academic sources are properly linked."""

    def test_all_distortions_have_source(self, bias_graph):
        """Every distortion has at least one hasAcademicSource."""
        q = f"""
        SELECT ?d WHERE {{
            ?d rdf:type owl:NamedIndividual .
            ?d rdf:type/rdfs:subClassOf* <{CB}CognitiveDistortion> .
            FILTER NOT EXISTS {{ ?d <{CB}hasAcademicSource> ?src }}
        }}
        """
        missing = list(bias_graph.query(q))
        assert len(missing) == 0, f"Distortions missing sources: {missing}"

    def test_four_academic_sources(self, bias_graph):
        """Exactly 4 AcademicSource individuals exist."""
        count = _count_individuals_of_class(bias_graph, f"{CB}AcademicSource")
        assert count == 4

    def test_sources_have_author_year_title(self, bias_graph):
        """Every AcademicSource has author, year, and title."""
        for prop in ["sourceAuthor", "sourceYear", "sourceTitle"]:
            q = f"""
            SELECT ?s WHERE {{
                ?s rdf:type owl:NamedIndividual .
                ?s rdf:type <{CB}AcademicSource> .
                FILTER NOT EXISTS {{ ?s <{CB}{prop}> ?val }}
            }}
            """
            missing = list(bias_graph.query(q))
            assert len(missing) == 0, f"Sources missing {prop}: {missing}"


# -------------------------------------------------------------------
# AC4: Every distortion has a detection pattern
# -------------------------------------------------------------------


class TestDetectionPatterns:
    """Tests that detection patterns are properly linked."""

    def test_all_distortions_have_pattern(self, bias_graph):
        """Every distortion has at least one hasDetectionPattern."""
        q = f"""
        SELECT ?d WHERE {{
            ?d rdf:type owl:NamedIndividual .
            ?d rdf:type/rdfs:subClassOf* <{CB}CognitiveDistortion> .
            FILTER NOT EXISTS {{ ?d <{CB}hasDetectionPattern> ?pat }}
        }}
        """
        missing = list(bias_graph.query(q))
        assert len(missing) == 0, f"Distortions missing patterns: {missing}"

    def test_patterns_have_description(self, bias_graph):
        """Every DetectionPattern has a patternDescription."""
        q = f"""
        SELECT ?p WHERE {{
            ?p rdf:type owl:NamedIndividual .
            ?p rdf:type <{CB}DetectionPattern> .
            FILTER NOT EXISTS {{ ?p <{CB}patternDescription> ?desc }}
        }}
        """
        missing = list(bias_graph.query(q))
        assert len(missing) == 0, f"Patterns missing description: {missing}"


# -------------------------------------------------------------------
# AC5: SHACL validation passes (skipped until EVAL-3.2)
# -------------------------------------------------------------------


class TestSHACLValidation:
    """SHACL validation tests — skipped until pyshacl is added in EVAL-3.2."""

    def test_ontology_conforms_to_shacl(self, bias_graph):
        """Bias ontology passes SHACL validation against shapes."""
        from pyshacl import validate

        shapes = Graph()
        shapes.parse(
            str(ONTOLOGY_DIR / "cognitive-bias-shapes.ttl"), format="turtle"
        )

        conforms, _, results_text = validate(
            data_graph=bias_graph,
            shacl_graph=shapes,
            inference="none",
            abort_on_first=False,
        )
        assert conforms, f"SHACL validation failed:\n{results_text}"


# -------------------------------------------------------------------
# AC6: Class hierarchy correct
# -------------------------------------------------------------------


class TestClassHierarchy:
    """Tests that the class hierarchy matches the specification."""

    def test_five_cognitive_biases(self, bias_graph):
        """Exactly 5 CognitiveBias individuals."""
        count = _count_individuals_of_class(bias_graph, f"{CB}CognitiveBias")
        assert count == 5

    def test_seven_informal_fallacies(self, bias_graph):
        """Exactly 7 InformalFallacy individuals."""
        count = _count_individuals_of_class(bias_graph, f"{CB}InformalFallacy")
        assert count == 7

    def test_two_formal_fallacies(self, bias_graph):
        """Exactly 2 FormalFallacy individuals."""
        count = _count_individuals_of_class(bias_graph, f"{CB}FormalFallacy")
        assert count == 2

    def test_cognitive_bias_is_subclass_of_distortion(self, bias_graph):
        """CognitiveBias rdfs:subClassOf CognitiveDistortion."""
        assert (CB.CognitiveBias, RDFS.subClassOf, CB.CognitiveDistortion) in bias_graph

    def test_logical_fallacy_is_subclass_of_distortion(self, bias_graph):
        """LogicalFallacy rdfs:subClassOf CognitiveDistortion."""
        assert (CB.LogicalFallacy, RDFS.subClassOf, CB.CognitiveDistortion) in bias_graph

    def test_formal_fallacy_is_subclass_of_logical_fallacy(self, bias_graph):
        """FormalFallacy rdfs:subClassOf LogicalFallacy."""
        assert (CB.FormalFallacy, RDFS.subClassOf, CB.LogicalFallacy) in bias_graph

    def test_informal_fallacy_is_subclass_of_logical_fallacy(self, bias_graph):
        """InformalFallacy rdfs:subClassOf LogicalFallacy."""
        assert (CB.InformalFallacy, RDFS.subClassOf, CB.LogicalFallacy) in bias_graph


# -------------------------------------------------------------------
# AC7: Imports newsanalyzer.ttl successfully
# -------------------------------------------------------------------


class TestCombinedGraph:
    """Tests that both ontologies work together."""

    def test_combined_graph_has_more_triples(self, bias_graph, combined_graph):
        """Combined graph has triples from both ontologies."""
        assert len(combined_graph) > len(bias_graph)

    def test_newsanalyzer_classes_in_combined(self, combined_graph):
        """newsanalyzer.ttl classes are accessible in the combined graph."""
        NA = Namespace("http://newsanalyzer.org/ontology#")
        # LegislativeBody is defined in newsanalyzer.ttl
        assert (NA.LegislativeBody, RDF.type, OWL.Class) in combined_graph

    def test_bias_classes_in_combined(self, combined_graph):
        """cognitive-bias.ttl classes are accessible in the combined graph."""
        assert (CB.CognitiveDistortion, RDF.type, OWL.Class) in combined_graph
