"""
SHACL Validator Service for NewsAnalyzer (EVAL-3.2)

Validates RDF data against SHACL shape constraints. Dual purpose:
1. Validate the cognitive bias ontology entries at startup
2. Validate LLM-produced bias annotations at runtime (EVAL-3.3)

Uses pyshacl with graceful degradation if not installed.
"""

import logging
from dataclasses import dataclass, field
from typing import List, Optional

try:
    from pyshacl import validate as shacl_validate
    from rdflib import Graph
    from rdflib.namespace import SH, RDF

    SHACL_AVAILABLE = True
except ImportError:
    SHACL_AVAILABLE = False
    logging.warning("pyshacl not installed. SHACL validation features disabled.")

logger = logging.getLogger(__name__)


@dataclass
class SHACLViolation:
    """A single SHACL constraint violation."""

    focus_node: str
    path: str
    message: str
    severity: str = "Violation"


@dataclass
class SHACLReport:
    """Structured result of a SHACL validation run."""

    conforms: bool
    results_count: int
    violations: List[SHACLViolation] = field(default_factory=list)
    shapes_evaluated: List[str] = field(default_factory=list)
    available: bool = True


class SHACLValidator:
    """
    Validates RDF data against SHACL shapes using pyshacl.

    Gracefully degrades if pyshacl is not installed — returns conforms=True
    with available=False so callers know validation wasn't actually performed.
    """

    def __init__(self, shapes_graph: "Graph"):
        """
        Args:
            shapes_graph: An rdflib Graph containing SHACL shape definitions.
        """
        self._shapes_graph = shapes_graph
        self._shapes_evaluated = self._extract_shape_names()

    def _extract_shape_names(self) -> List[str]:
        """Extract shape names from the shapes graph for reporting."""
        if not SHACL_AVAILABLE:
            return []
        names = []
        for s in self._shapes_graph.subjects(
            RDF.type, SH.NodeShape  # type: ignore[arg-type]
        ):
            names.append(str(s).split("#")[-1] if "#" in str(s) else str(s))
        return sorted(names)

    def validate(self, data_graph: "Graph") -> SHACLReport:
        """
        Validate an RDF data graph against the loaded SHACL shapes.

        Args:
            data_graph: The RDF graph to validate.

        Returns:
            SHACLReport with conformance status and any violations.
        """
        if not SHACL_AVAILABLE:
            logger.warning("SHACL validation skipped — pyshacl not installed")
            return SHACLReport(
                conforms=True,
                results_count=0,
                shapes_evaluated=self._shapes_evaluated,
                available=False,
            )

        try:
            conforms, results_graph, results_text = shacl_validate(
                data_graph=data_graph,
                shacl_graph=self._shapes_graph,
                inference="none",
                abort_on_first=False,
            )

            violations = self._parse_violations(results_graph)

            return SHACLReport(
                conforms=conforms,
                results_count=len(violations),
                violations=violations,
                shapes_evaluated=self._shapes_evaluated,
                available=True,
            )
        except Exception as e:
            logger.error(f"SHACL validation failed: {e}")
            return SHACLReport(
                conforms=False,
                results_count=1,
                violations=[
                    SHACLViolation(
                        focus_node="N/A",
                        path="N/A",
                        message=f"SHACL validation error: {e}",
                        severity="Error",
                    )
                ],
                shapes_evaluated=self._shapes_evaluated,
                available=True,
            )

    def validate_ontology(self, ontology_graph: "Graph") -> SHACLReport:
        """Convenience method — validate an ontology file against shapes."""
        return self.validate(ontology_graph)

    def _parse_violations(self, results_graph: "Graph") -> List[SHACLViolation]:
        """Parse pyshacl results graph into structured violation objects."""
        violations = []

        query = """
        PREFIX sh: <http://www.w3.org/ns/shacl#>
        SELECT ?focus ?path ?message ?severity WHERE {
            ?result a sh:ValidationResult .
            OPTIONAL { ?result sh:focusNode ?focus }
            OPTIONAL { ?result sh:resultPath ?path }
            OPTIONAL { ?result sh:resultMessage ?message }
            OPTIONAL { ?result sh:resultSeverity ?severity }
        }
        """

        try:
            for row in results_graph.query(query):
                violations.append(
                    SHACLViolation(
                        focus_node=str(row.focus) if row.focus else "N/A",
                        path=str(row.path) if row.path else "N/A",
                        message=str(row.message) if row.message else "No message",
                        severity=(
                            str(row.severity).split("#")[-1]
                            if row.severity
                            else "Violation"
                        ),
                    )
                )
        except Exception as e:
            logger.error(f"Failed to parse SHACL violations: {e}")

        return violations
