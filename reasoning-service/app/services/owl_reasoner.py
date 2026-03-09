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
        self.graph.bind("owl", OWL)
        self.graph.bind("rdf", RDF)
        self.graph.bind("rdfs", RDFS)

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
                {str(var): str(row[var]) for var in variables}  # type: ignore[index]
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

    return _reasoner_instance
