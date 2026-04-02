"""
OWL Reasoning Service for NewsAnalyzer

This module provides OWL-based reasoning capabilities for entity classification,
relationship inference, and consistency checking using RDFLib and OWL-RL.
"""

from typing import Dict, List, Optional, Any
from pathlib import Path
import logging

try:
    from rdflib import Graph, Namespace, URIRef, Literal
    from rdflib.namespace import RDF, RDFS, OWL
    import owlrl
    DEPENDENCIES_AVAILABLE = True
except ImportError:
    DEPENDENCIES_AVAILABLE = False
    logging.warning("RDFLib or owlrl not installed. OWL reasoning features disabled.")

logger = logging.getLogger(__name__)


# Namespaces
SCHEMA = Namespace("http://schema.org/")
NA = Namespace("http://newsanalyzer.org/ontology#")
CB = Namespace("http://newsanalyzer.org/ontology/cognitive-bias#")


# -------------------------------------------------------------------
#  SPARQL query constants for cognitive bias ontology (EVAL-3.2)
#  Defined at module level for testability and auditability.
# -------------------------------------------------------------------

SPARQL_LIST_DISTORTIONS = """
SELECT ?d ?label ?def WHERE {
    ?d rdf:type owl:NamedIndividual .
    ?d rdf:type/rdfs:subClassOf* cb:CognitiveDistortion .
    ?d rdfs:label ?label .
    ?d cb:hasDefinition ?def .
}
"""

SPARQL_GET_DEFINITION = """
SELECT ?def ?patternDesc ?patternExample ?author ?year ?title WHERE {
    ?distortion cb:hasDefinition ?def .
    ?distortion cb:hasDetectionPattern ?pattern .
    ?pattern cb:patternDescription ?patternDesc .
    OPTIONAL { ?pattern cb:patternExample ?patternExample }
    ?distortion cb:hasAcademicSource ?src .
    ?src cb:sourceAuthor ?author .
    ?src cb:sourceYear ?year .
    ?src cb:sourceTitle ?title .
}
"""

SPARQL_GET_ALL_DEFINITIONS = """
SELECT ?d ?label ?def ?patternDesc ?patternExample ?author ?year ?title WHERE {
    ?d rdf:type owl:NamedIndividual .
    ?d rdf:type/rdfs:subClassOf* cb:CognitiveDistortion .
    ?d rdfs:label ?label .
    ?d cb:hasDefinition ?def .
    ?d cb:hasDetectionPattern ?pattern .
    ?pattern cb:patternDescription ?patternDesc .
    OPTIONAL { ?pattern cb:patternExample ?patternExample }
    ?d cb:hasAcademicSource ?src .
    ?src cb:sourceAuthor ?author .
    ?src cb:sourceYear ?year .
    ?src cb:sourceTitle ?title .
}
"""

SPARQL_COUNT_BY_CLASS = """
SELECT ?class (COUNT(?d) AS ?count) WHERE {
    VALUES ?class { cb:CognitiveBias cb:FormalFallacy cb:InformalFallacy }
    ?d rdf:type ?class .
    ?d rdf:type owl:NamedIndividual .
} GROUP BY ?class
"""


class OWLReasoner:
    """
    OWL-based reasoner for entity classification and relationship inference.

    Features:
    - Load custom NewsAnalyzer ontology
    - Classify entities based on OWL restrictions
    - Infer relationships between entities
    - Validate consistency
    - Generate inferred Schema.org data
    """

    def __init__(self, ontology_path: Optional[str] = None):
        """
        Initialize the OWL reasoner.

        Args:
            ontology_path: Path to the .ttl ontology file. If None, uses default.
        """
        if not DEPENDENCIES_AVAILABLE:
            raise ImportError(
                "RDFLib and owlrl are required for OWL reasoning. "
                "Install with: pip install rdflib==7.0.0 owlrl==6.0.2"
            )

        self.graph = Graph()

        # Bind common namespaces
        self.graph.bind("schema", SCHEMA)
        self.graph.bind("na", NA)
        self.graph.bind("cb", CB)
        self.graph.bind("owl", OWL)
        self.graph.bind("rdf", RDF)
        self.graph.bind("rdfs", RDFS)

        # Bias ontology state (EVAL-3.2)
        self._bias_ontology_loaded = False

        # Load ontology
        if ontology_path is None:
            resolved_path = Path(__file__).parent.parent.parent / "ontology" / "newsanalyzer.ttl"
        else:
            resolved_path = Path(ontology_path)

        self._load_ontology(resolved_path)

    def _load_ontology(self, ontology_path: Path):
        """Load the NewsAnalyzer ontology from a .ttl file."""
        try:
            self.graph.parse(str(ontology_path), format="turtle")
            logger.info(f"Loaded ontology from {ontology_path}")
            logger.info(f"Ontology contains {len(self.graph)} triples")
        except Exception as e:
            logger.error(f"Failed to load ontology: {e}")
            raise

    def add_entity(
        self,
        entity_uri: str,
        entity_type: str,
        properties: Dict[str, Any]
    ) -> None:
        """
        Add an entity to the graph with its properties.

        Args:
            entity_uri: Unique URI for the entity (e.g., "http://newsanalyzer.org/entity/epa")
            entity_type: Schema.org or NewsAnalyzer type
            properties: Dictionary of property-value pairs
        """
        entity = URIRef(entity_uri)

        # Add type
        if entity_type.startswith("http://"):
            type_uri = URIRef(entity_type)
        else:
            # Assume Schema.org type
            type_uri = SCHEMA[entity_type]

        self.graph.add((entity, RDF.type, type_uri))

        # Add properties
        for prop, value in properties.items():
            prop_uri = self._get_property_uri(prop)

            if isinstance(value, str) and value.startswith("http://"):
                # Object property (URI reference)
                self.graph.add((entity, prop_uri, URIRef(value)))
            else:
                # Data property (literal)
                self.graph.add((entity, prop_uri, Literal(value)))

    def _get_property_uri(self, prop: str) -> URIRef:
        """Convert property name to URI."""
        if prop.startswith("http://"):
            return URIRef(prop)
        elif ":" in prop:
            prefix, local = prop.split(":", 1)
            if prefix == "schema":
                return SCHEMA[local]
            elif prefix == "na":
                return NA[local]
        return SCHEMA[prop]  # Default to Schema.org

    def infer(self) -> int:
        """
        Run OWL-RL reasoning to infer new triples.

        Returns:
            Number of triples after inference
        """
        initial_size = len(self.graph)

        try:
            # Run OWL-RL reasoner
            owlrl.DeductiveClosure(owlrl.OWLRL_Semantics).expand(self.graph)

            final_size = len(self.graph)
            inferred_count = final_size - initial_size

            logger.info(f"Inference complete: {inferred_count} new triples inferred")
            logger.info(f"Total triples: {final_size}")

            return final_size
        except Exception as e:
            logger.error(f"Inference failed: {e}")
            raise

    def classify_entity(self, entity_uri: str) -> List[str]:
        """
        Get all inferred types for an entity.

        Args:
            entity_uri: URI of the entity

        Returns:
            List of type URIs
        """
        entity = URIRef(entity_uri)
        types = []

        for _, _, type_uri in self.graph.triples((entity, RDF.type, None)):
            types.append(str(type_uri))

        return types

    def get_entity_properties(self, entity_uri: str) -> Dict[str, List[Any]]:
        """
        Get all properties and values for an entity.

        Args:
            entity_uri: URI of the entity

        Returns:
            Dictionary mapping property URIs to lists of values
        """
        entity = URIRef(entity_uri)
        properties: Dict[str, List[Any]] = {}

        for _, prop, value in self.graph.triples((entity, None, None)):
            if prop == RDF.type:
                continue  # Skip type (handled separately)

            prop_str = str(prop)
            if prop_str not in properties:
                properties[prop_str] = []

            if isinstance(value, URIRef):
                properties[prop_str].append(str(value))
            else:
                properties[prop_str].append(str(value))

        return properties

    def check_consistency(self) -> List[str]:
        """
        Check for consistency violations in the graph.

        Returns:
            List of error messages (empty if consistent)
        """
        errors = []

        # Check cardinality constraints
        # Example: Legislators can only be affiliated with one political party
        query = """
        PREFIX na: <http://newsanalyzer.org/ontology#>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

        SELECT ?entity (COUNT(?party) as ?count)
        WHERE {
            ?entity rdf:type na:Legislator .
            ?entity na:affiliatedWith ?party .
        }
        GROUP BY ?entity
        HAVING (COUNT(?party) > 1)
        """

        try:
            results = self.graph.query(query)
            for row in results:
                errors.append(
                    f"Legislator {row.entity} is affiliated with {row.count} parties (max 1 allowed)"  # type: ignore[union-attr]
                )
        except Exception as e:
            logger.error(f"Consistency check failed: {e}")

        return errors

    def enrich_entity_data(
        self,
        entity_text: str,
        entity_type: str,
        confidence: float,
        base_properties: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Enrich entity data using OWL reasoning.

        Args:
            entity_text: Entity name/text
            entity_type: Detected entity type
            confidence: Confidence score
            base_properties: Initial properties

        Returns:
            Enriched entity data with inferred types and properties
        """
        # Create temporary entity URI
        entity_uri = f"http://newsanalyzer.org/entity/{entity_text.replace(' ', '_').lower()}"

        # Create a temporary graph for this entity
        temp_graph = Graph()
        temp_graph += self.graph  # Copy ontology

        # Add entity to temporary graph
        entity = URIRef(entity_uri)

        # Map internal type to Schema.org type
        type_mapping = {
            "person": "Person",
            "government_org": "GovernmentOrganization",
            "organization": "Organization",
            "location": "Place",
            "event": "Event",
            "concept": "Thing",
            "legislation": str(NA.Legislation),
            "political_party": str(NA.PoliticalParty),
            "news_media": str(NA.NewsMedia),
        }

        schema_type = type_mapping.get(entity_type, "Thing")
        if schema_type.startswith("http://"):
            type_uri = URIRef(schema_type)
        else:
            type_uri = SCHEMA[schema_type]

        temp_graph.add((entity, RDF.type, type_uri))
        temp_graph.add((entity, SCHEMA.name, Literal(entity_text)))

        # Add base properties
        for prop, value in base_properties.items():
            prop_uri = self._get_property_uri(prop)
            if isinstance(value, str) and value.startswith("http://"):
                temp_graph.add((entity, prop_uri, URIRef(value)))
            else:
                temp_graph.add((entity, prop_uri, Literal(value)))

        # Run inference
        try:
            owlrl.DeductiveClosure(owlrl.OWLRL_Semantics).expand(temp_graph)
        except Exception as e:
            logger.warning(f"Inference failed for entity {entity_text}: {e}")

        # Extract inferred types
        inferred_types: List[str] = []
        for _, _, type_uri in temp_graph.triples((entity, RDF.type, None)):  # type: ignore[assignment]
            type_str = str(type_uri)
            if type_str not in inferred_types:
                inferred_types.append(type_str)

        # Extract inferred properties
        inferred_properties: Dict[str, str] = {}
        for _, prop, value in temp_graph.triples((entity, None, None)):  # type: ignore[assignment]
            if prop == RDF.type:
                continue

            prop_str = str(prop)
            # Convert URIs to friendly names
            if prop_str.startswith(str(SCHEMA)):
                prop_name = prop_str.replace(str(SCHEMA), "schema:")
            elif prop_str.startswith(str(NA)):
                prop_name = prop_str.replace(str(NA), "na:")
            else:
                prop_name = prop_str

            if isinstance(value, URIRef):
                inferred_properties[prop_name] = str(value)
            else:
                inferred_properties[prop_name] = str(value)

        return {
            "text": entity_text,
            "entity_type": entity_type,
            "confidence": confidence,
            "schema_org_types": inferred_types,
            "inferred_properties": inferred_properties,
            "reasoning_applied": True
        }

    def query_sparql(self, sparql_query: str) -> List[Dict[str, Any]]:
        """
        Execute a SPARQL query on the graph.

        Args:
            sparql_query: SPARQL query string

        Returns:
            List of result dictionaries
        """
        try:
            results = self.graph.query(sparql_query)
            variables = results.vars or []
            return [
                {str(var): str(row[var]) for var in variables}  # type: ignore[index,call-overload]
                for row in results
            ]
        except Exception as e:
            logger.error(f"SPARQL query failed: {e}")
            raise

    def export_graph(self, format: str = "turtle") -> str:
        """
        Export the graph to a string in the specified format.

        Args:
            format: Output format (turtle, xml, n3, json-ld)

        Returns:
            Serialized graph
        """
        return self.graph.serialize(format=format)

    def get_ontology_stats(self) -> Dict[str, Any]:
        """Get statistics about the loaded ontology."""
        stats = {
            "total_triples": len(self.graph),
            "classes": len(list(self.graph.subjects(RDF.type, OWL.Class))),
            "properties": len(
                list(self.graph.subjects(RDF.type, OWL.ObjectProperty)) +
                list(self.graph.subjects(RDF.type, OWL.DatatypeProperty))
            ),
            "individuals": len(list(self.graph.subjects(RDF.type, None))) -
                          len(list(self.graph.subjects(RDF.type, OWL.Class)))
        }
        return stats

    # -------------------------------------------------------------------
    #  Cognitive Bias Ontology Methods (EVAL-3.2)
    # -------------------------------------------------------------------

    def load_bias_ontology(self, path: Optional[str] = None) -> None:
        """
        Load the cognitive bias ontology alongside the base ontology.

        On failure: logs a warning and leaves _bias_ontology_loaded as False.
        Existing reasoner functionality continues working regardless.

        Args:
            path: Path to cognitive-bias.ttl. Defaults to ontology/cognitive-bias.ttl.
        """
        if path is None:
            resolved = Path(__file__).parent.parent.parent / "ontology" / "cognitive-bias.ttl"
        else:
            resolved = Path(path)

        try:
            before = len(self.graph)
            self.graph.parse(str(resolved), format="turtle")
            after = len(self.graph)
            self._bias_ontology_loaded = True
            logger.info(
                f"Loaded bias ontology from {resolved} "
                f"({after - before} new triples, {after} total)"
            )
        except Exception as e:
            logger.warning(f"Failed to load bias ontology: {e}")
            self._bias_ontology_loaded = False

    def get_distortion_definition(self, distortion_uri: str) -> Dict[str, Any]:
        """
        Get definition, detection pattern, and academic source for a distortion.

        Uses initBindings for safe SPARQL parameterization.

        Args:
            distortion_uri: Full URI of the distortion individual.

        Returns:
            Dict with definition, detection_pattern, pattern_example, academic_source.
            Empty dict if not found.
        """
        results = self.graph.query(
            SPARQL_GET_DEFINITION,
            initBindings={"distortion": URIRef(distortion_uri)},
        )

        for row in results:
            return {
                "definition": str(row[0]),
                "detection_pattern": str(row[1]),
                "pattern_example": str(row[2]) if row[2] else None,
                "academic_source": {
                    "author": str(row[3]),
                    "year": int(row[4]),
                    "title": str(row[5]),
                },
            }

        return {}

    def list_distortions(self, category: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        List all cognitive distortions, optionally filtered by category.

        Args:
            category: "bias", "fallacy", or None for all.

        Returns:
            List of dicts with uri, label, definition.
        """
        results = self.graph.query(SPARQL_LIST_DISTORTIONS)
        distortions = []

        for row in results:
            uri_str = str(row[0])
            uri_ref = URIRef(uri_str)
            label = str(row[1])
            definition = str(row[2])

            if category == "bias":
                if (uri_ref, RDF.type, CB.CognitiveBias) not in self.graph:
                    continue
            elif category == "fallacy":
                if (
                    (uri_ref, RDF.type, CB.InformalFallacy) not in self.graph
                    and (uri_ref, RDF.type, CB.FormalFallacy) not in self.graph
                ):
                    continue

            distortions.append({
                "uri": uri_str,
                "label": label,
                "definition": definition,
            })

        return distortions

    def get_bias_ontology_stats(self) -> Dict[str, Any]:
        """
        Get statistics about the cognitive bias ontology.

        Returns:
            Counts of distortions by type, academic sources, detection patterns.
        """
        # Count by class
        counts: Dict[str, int] = {}
        for row in self.graph.query(SPARQL_COUNT_BY_CLASS):
            cls_name = str(row[0]).split("#")[-1]
            counts[cls_name] = int(row[1])

        cognitive_biases = counts.get("CognitiveBias", 0)
        formal_fallacies = counts.get("FormalFallacy", 0)
        informal_fallacies = counts.get("InformalFallacy", 0)
        total = cognitive_biases + formal_fallacies + informal_fallacies

        # Count academic sources
        src_query = """
        SELECT (COUNT(?s) AS ?count) WHERE {
            ?s rdf:type owl:NamedIndividual .
            ?s rdf:type cb:AcademicSource .
        }
        """
        academic_sources = 0
        for row in self.graph.query(src_query):
            academic_sources = int(row[0])

        # Count detection patterns
        pat_query = """
        SELECT (COUNT(?p) AS ?count) WHERE {
            ?p rdf:type owl:NamedIndividual .
            ?p rdf:type cb:DetectionPattern .
        }
        """
        detection_patterns = 0
        for row in self.graph.query(pat_query):
            detection_patterns = int(row[0])

        return {
            "total_distortions": total,
            "cognitive_biases": cognitive_biases,
            "logical_fallacies": formal_fallacies + informal_fallacies,
            "formal_fallacies": formal_fallacies,
            "informal_fallacies": informal_fallacies,
            "academic_sources": academic_sources,
            "detection_patterns": detection_patterns,
        }

    def get_all_distortion_definitions(self) -> List[Dict[str, Any]]:
        """
        Get all distortion definitions with sources and patterns in one SPARQL call.

        More efficient than calling get_distortion_definition() per distortion.
        Used by EVAL-3.3's bias detector to build grounded prompts.

        Returns:
            List of dicts with uri, label, definition, detection_pattern,
            pattern_example, academic_source {author, year, title}.
        """
        results = self.graph.query(SPARQL_GET_ALL_DEFINITIONS)
        definitions = []

        for row in results:
            definitions.append({
                "uri": str(row[0]),
                "label": str(row[1]),
                "definition": str(row[2]),
                "detection_pattern": str(row[3]),
                "pattern_example": str(row[4]) if row[4] else None,
                "academic_source": {
                    "author": str(row[5]),
                    "year": int(row[6]),
                    "title": str(row[7]),
                },
            })

        return definitions


# Singleton instance
_reasoner_instance: Optional[OWLReasoner] = None


def get_reasoner() -> OWLReasoner:
    """Get or create the singleton OWL reasoner instance."""
    global _reasoner_instance

    if _reasoner_instance is None:
        if not DEPENDENCIES_AVAILABLE:
            raise ImportError(
                "RDFLib and owlrl are required. "
                "Install with: pip install rdflib==7.0.0 owlrl==6.0.2"
            )
        _reasoner_instance = OWLReasoner()

        # Load bias ontology (optional — failure doesn't break existing functionality)
        try:
            _reasoner_instance.load_bias_ontology()
        except Exception as e:
            logger.warning(f"Bias ontology not loaded: {e}")

    return _reasoner_instance
