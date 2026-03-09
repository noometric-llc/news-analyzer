"""
Entity Extraction API

Extracts government entities, persons, organizations, locations, events, and concepts
from news articles using spaCy and transformers.
Also provides entity linking to external knowledge bases (Wikidata, DBpedia).
"""

import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional
from enum import Enum

router = APIRouter()
logger = logging.getLogger(__name__)


class EntityExtractionRequest(BaseModel):
    """Request model for entity extraction"""
    text: str = Field(..., description="Text to extract entities from")
    confidence_threshold: float = Field(0.7, ge=0.0, le=1.0, description="Minimum confidence score")


class Entity(BaseModel):
    """Extracted entity model"""
    text: str
    entity_type: str
    start: int
    end: int
    confidence: float
    schema_org_type: str
    schema_org_data: Dict[str, Any]
    properties: Dict[str, Any] = Field(default_factory=dict)


class EntityExtractionResponse(BaseModel):
    """Response model for entity extraction"""
    entities: List[Entity]
    total_count: int


@router.post("/extract", response_model=EntityExtractionResponse)
async def extract_entities(request: EntityExtractionRequest):
    """
    Extract entities from text using spaCy NLP.

    Identifies:
    - Government organizations (Congress, Senate, agencies)
    - Persons (politicians, officials, public figures)
    - Organizations (companies, NGOs, political parties)
    - Locations (countries, states, cities)
    - Events (elections, hearings, protests)
    - Concepts (policies, laws, ideologies)

    Example:
        POST /entities/extract
        {
            "text": "Senator Elizabeth Warren criticized the EPA's new regulations...",
            "confidence_threshold": 0.7
        }

        Response:
        {
            "entities": [
                {
                    "text": "Elizabeth Warren",
                    "entity_type": "person",
                    "start": 8,
                    "end": 24,
                    "confidence": 0.85,
                    "schema_org_type": "Person",
                    "schema_org_data": {
                        "@context": "https://schema.org",
                        "@type": "Person",
                        "name": "Elizabeth Warren"
                    }
                }
            ],
            "total_count": 1
        }
    """
    from app.services.entity_extractor import get_entity_extractor

    # Get extractor instance
    extractor = get_entity_extractor()

    # Extract entities
    result = extractor.extract_with_context(
        text=request.text,
        confidence_threshold=request.confidence_threshold
    )

    # Convert to response model
    entities = [
        Entity(
            text=e["text"],
            entity_type=e["entity_type"],
            start=e["start"],
            end=e["end"],
            confidence=e["confidence"],
            schema_org_type=e["schema_org_type"],
            schema_org_data=e["schema_org_data"],
            properties=e.get("properties", {})
        )
        for e in result["entities"]
    ]

    return EntityExtractionResponse(
        entities=entities,
        total_count=result["total_count"]
    )


# =====================================================
# Entity Linking Models
# =====================================================

class LinkingSource(str, Enum):
    """Source selection for entity linking"""
    WIKIDATA = "wikidata"
    DBPEDIA = "dbpedia"
    BOTH = "both"


class EntityLinkRequest(BaseModel):
    """Single entity to link"""
    text: str = Field(..., description="Entity text to link", min_length=1)
    entity_type: str = Field(
        ...,
        description="Entity type (person, organization, government_org, location, event)"
    )
    context: Optional[str] = Field(
        None,
        description="Surrounding text for disambiguation"
    )


class LinkingOptions(BaseModel):
    """Options for entity linking"""
    sources: LinkingSource = Field(
        LinkingSource.BOTH,
        description="Which knowledge bases to query"
    )
    min_confidence: float = Field(
        0.7,
        ge=0.0,
        le=1.0,
        description="Minimum confidence for automatic linking"
    )
    max_candidates: int = Field(
        5,
        ge=1,
        le=10,
        description="Maximum candidates to return per entity"
    )


class LinkRequest(BaseModel):
    """Request for entity linking"""
    entities: List[EntityLinkRequest] = Field(
        ...,
        max_length=100,
        description="Entities to link (max 100)"
    )
    options: LinkingOptions = Field(
        default_factory=LinkingOptions,  # type: ignore[arg-type]
        description="Linking options"
    )


class SingleLinkRequest(BaseModel):
    """Request for single entity linking - combines entity and options"""
    text: str = Field(..., description="Entity text to link", min_length=1)
    entity_type: str = Field(
        ...,
        description="Entity type (person, organization, government_org, location, event)"
    )
    context: Optional[str] = Field(
        None,
        description="Surrounding text for disambiguation"
    )
    sources: LinkingSource = Field(
        LinkingSource.BOTH,
        description="Which knowledge bases to query"
    )
    min_confidence: float = Field(
        0.7,
        ge=0.0,
        le=1.0,
        description="Minimum confidence for automatic linking"
    )
    max_candidates: int = Field(
        5,
        ge=1,
        le=10,
        description="Maximum candidates to return per entity"
    )


class CandidateResponse(BaseModel):
    """A candidate match from external KB"""
    id: str = Field(..., description="External ID (QID or URI)")
    label: str = Field(..., description="Entity label")
    description: Optional[str] = Field(None, description="Entity description")
    source: str = Field(..., description="Source KB (wikidata or dbpedia)")
    confidence: float = Field(..., description="Match confidence score")


class LinkedEntityResponse(BaseModel):
    """Linked entity result"""
    text: str
    entity_type: str
    wikidata_id: Optional[str] = None
    wikidata_url: Optional[str] = None
    dbpedia_uri: Optional[str] = None
    linking_confidence: float
    linking_source: Optional[str] = None
    linking_status: str
    needs_review: bool = False
    is_ambiguous: bool = False
    candidates: List[Dict[str, Any]] = Field(default_factory=list)
    error: Optional[str] = None


class LinkingStatsResponse(BaseModel):
    """Statistics for linking operation"""
    total: int
    linked: int
    needs_review: int
    not_found: int
    errors: int
    success_rate: float


class LinkResponse(BaseModel):
    """Response for entity linking"""
    linked_entities: List[LinkedEntityResponse]
    statistics: LinkingStatsResponse


@router.post("/link", response_model=LinkResponse)
async def link_entities(request: LinkRequest):
    """
    Link extracted entities to external knowledge bases (Wikidata, DBpedia).

    This endpoint takes a list of entities (text + type) and attempts to match
    them with authoritative records in Wikidata and/or DBpedia.

    **Process:**
    1. Query Wikidata for matching entities
    2. If no Wikidata results, query DBpedia as fallback
    3. Disambiguate between candidates using type matching, name similarity, and context
    4. Return linked entities with external IDs and confidence scores

    **Linking Status:**
    - `linked`: Confident match found (confidence >= min_confidence)
    - `needs_review`: Match found but confidence below threshold
    - `not_found`: No matching candidates in external KBs
    - `error`: Error during linking process

    **Example Request:**
    ```json
    {
        "entities": [
            {
                "text": "Environmental Protection Agency",
                "entity_type": "government_org",
                "context": "The EPA announced new environmental regulations"
            },
            {
                "text": "Elizabeth Warren",
                "entity_type": "person"
            }
        ],
        "options": {
            "sources": "both",
            "min_confidence": 0.7,
            "max_candidates": 5
        }
    }
    ```

    **Example Response:**
    ```json
    {
        "linked_entities": [
            {
                "text": "Environmental Protection Agency",
                "entity_type": "government_org",
                "wikidata_id": "Q217173",
                "wikidata_url": "https://www.wikidata.org/wiki/Q217173",
                "linking_confidence": 0.95,
                "linking_source": "wikidata",
                "linking_status": "linked",
                "needs_review": false
            }
        ],
        "statistics": {
            "total": 2,
            "linked": 2,
            "needs_review": 0,
            "not_found": 0,
            "errors": 0,
            "success_rate": 1.0
        }
    }
    ```
    """
    try:
        from app.services.entity_linker import get_entity_linker, LinkingSource as LinkerSource

        logger.info(f"Entity linking request: {len(request.entities)} entities")

        linker = get_entity_linker()

        # Map source enum
        source_map = {
            LinkingSource.WIKIDATA: LinkerSource.WIKIDATA,
            LinkingSource.DBPEDIA: LinkerSource.DBPEDIA,
            LinkingSource.BOTH: LinkerSource.BOTH,
        }

        # Build entities list for linker
        entities_to_link = [
            {
                "text": e.text,
                "entity_type": e.entity_type,
                "context": e.context,
            }
            for e in request.entities
        ]

        # Perform linking
        result = linker.link_batch(
            entities=entities_to_link,
            sources=source_map[request.options.sources],
            min_confidence=request.options.min_confidence,
            max_candidates=request.options.max_candidates
        )

        # Convert to response models
        linked_entities = [
            LinkedEntityResponse(
                text=e.text,
                entity_type=e.entity_type,
                wikidata_id=e.wikidata_id,
                wikidata_url=e.wikidata_url,
                dbpedia_uri=e.dbpedia_uri,
                linking_confidence=e.linking_confidence,
                linking_source=e.linking_source,
                linking_status=e.linking_status.value,
                needs_review=e.needs_review,
                is_ambiguous=e.is_ambiguous,
                candidates=e.candidates,
                error=e.error,
            )
            for e in result.linked_entities
        ]

        stats = result.statistics
        statistics = LinkingStatsResponse(
            total=stats.total,
            linked=stats.linked,
            needs_review=stats.needs_review,
            not_found=stats.not_found,
            errors=stats.errors,
            success_rate=stats.linked / stats.total if stats.total > 0 else 0.0,
        )

        logger.info(
            f"Entity linking complete: {stats.linked}/{stats.total} linked, "
            f"{stats.needs_review} need review"
        )

        return LinkResponse(
            linked_entities=linked_entities,
            statistics=statistics
        )

    except ImportError as e:
        logger.error(f"Entity linking not available: {e}")
        raise HTTPException(
            status_code=503,
            detail=f"Entity linking service not available: {str(e)}"
        )
    except Exception as e:
        logger.error(f"Entity linking failed: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"Entity linking failed: {str(e)}"
        )


@router.post("/link/single", response_model=LinkedEntityResponse)
async def link_single_entity(request: SingleLinkRequest):
    """
    Link a single entity to external knowledge bases.

    Convenience endpoint for linking one entity without wrapping in a list.

    **Example Request:**
    ```json
    {
        "text": "EPA",
        "entity_type": "government_org",
        "context": "The EPA issued new regulations on emissions"
    }
    ```
    """
    try:
        from app.services.entity_linker import get_entity_linker, LinkingSource as LinkerSource

        linker = get_entity_linker()

        source_map = {
            LinkingSource.WIKIDATA: LinkerSource.WIKIDATA,
            LinkingSource.DBPEDIA: LinkerSource.DBPEDIA,
            LinkingSource.BOTH: LinkerSource.BOTH,
        }

        result = linker.link_entity(
            text=request.text,
            entity_type=request.entity_type,
            context=request.context,
            sources=source_map[request.sources],
            min_confidence=request.min_confidence,
            max_candidates=request.max_candidates
        )

        return LinkedEntityResponse(
            text=result.text,
            entity_type=result.entity_type,
            wikidata_id=result.wikidata_id,
            wikidata_url=result.wikidata_url,
            dbpedia_uri=result.dbpedia_uri,
            linking_confidence=result.linking_confidence,
            linking_source=result.linking_source,
            linking_status=result.linking_status.value,
            needs_review=result.needs_review,
            is_ambiguous=result.is_ambiguous,
            candidates=result.candidates,
            error=result.error,
        )

    except ImportError as e:
        raise HTTPException(
            status_code=503,
            detail=f"Entity linking service not available: {str(e)}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Entity linking failed: {str(e)}"
        )


class ReasoningRequest(BaseModel):
    """Request model for OWL reasoning"""
    entities: List[Dict[str, Any]] = Field(..., description="Entities to reason about")
    enable_inference: bool = Field(True, description="Enable OWL inference")


class ReasoningResponse(BaseModel):
    """Response model for OWL reasoning"""
    enriched_entities: List[Dict[str, Any]]
    inferred_triples: int
    consistency_errors: List[str]


@router.post("/reason", response_model=ReasoningResponse)
async def reason_entities(request: ReasoningRequest):
    """
    Apply OWL reasoning to enrich entities with inferred types and properties.

    Uses the NewsAnalyzer ontology to:
    - Infer additional entity types based on properties
    - Validate consistency constraints
    - Add inferred relationships

    Example:
        POST /entities/reason
        {
            "entities": [
                {
                    "text": "EPA",
                    "entity_type": "government_org",
                    "confidence": 0.9,
                    "properties": {"regulates": "environmental_policy"}
                }
            ],
            "enable_inference": true
        }

        Response:
        {
            "enriched_entities": [
                {
                    "text": "EPA",
                    "entity_type": "government_org",
                    "confidence": 0.9,
                    "schema_org_types": [
                        "http://schema.org/GovernmentOrganization",
                        "http://newsanalyzer.org/ontology#ExecutiveAgency"
                    ],
                    "inferred_properties": {...},
                    "reasoning_applied": true
                }
            ],
            "inferred_triples": 5,
            "consistency_errors": []
        }
    """
    try:
        from app.services.owl_reasoner import get_reasoner

        reasoner = get_reasoner()
        enriched = []

        # Track initial triple count
        initial_triples = len(reasoner.graph)

        # Process each entity
        for entity_data in request.entities:
            enriched_entity = reasoner.enrich_entity_data(
                entity_text=entity_data["text"],
                entity_type=entity_data["entity_type"],
                confidence=entity_data.get("confidence", 1.0),
                base_properties=entity_data.get("properties", {})
            )
            enriched.append(enriched_entity)

        # Run inference if enabled
        if request.enable_inference:
            final_triples = reasoner.infer()
            inferred_count = final_triples - initial_triples
        else:
            inferred_count = 0

        # Check consistency
        consistency_errors = reasoner.check_consistency()

        return ReasoningResponse(
            enriched_entities=enriched,
            inferred_triples=inferred_count,
            consistency_errors=consistency_errors
        )

    except ImportError as e:
        raise HTTPException(
            status_code=503,
            detail=f"OWL reasoning not available: {str(e)}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Reasoning failed: {str(e)}"
        )


class OntologyStatsResponse(BaseModel):
    """Response model for ontology statistics"""
    total_triples: int
    classes: int
    properties: int
    individuals: int


@router.get("/ontology/stats", response_model=OntologyStatsResponse)
async def get_ontology_stats():
    """
    Get statistics about the loaded NewsAnalyzer ontology.

    Returns counts of classes, properties, and individuals defined in the ontology.
    """
    try:
        from app.services.owl_reasoner import get_reasoner

        reasoner = get_reasoner()
        stats = reasoner.get_ontology_stats()

        return OntologyStatsResponse(**stats)

    except ImportError as e:
        raise HTTPException(
            status_code=503,
            detail=f"OWL reasoning not available: {str(e)}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to get ontology stats: {str(e)}"
        )


class SPARQLRequest(BaseModel):
    """Request model for SPARQL queries"""
    query: str = Field(..., description="SPARQL query to execute")


@router.post("/query/sparql")
async def query_sparql(request: SPARQLRequest):
    """
    Execute a SPARQL query against the loaded ontology and entity data.

    Example:
        POST /entities/query/sparql
        {
            "query": "PREFIX na: <http://newsanalyzer.org/ontology#>
                      SELECT ?org WHERE { ?org a na:ExecutiveAgency }"
        }
    """
    try:
        from app.services.owl_reasoner import get_reasoner

        reasoner = get_reasoner()
        results = reasoner.query_sparql(request.query)

        return {"results": results, "count": len(results)}

    except ImportError as e:
        raise HTTPException(
            status_code=503,
            detail=f"OWL reasoning not available: {str(e)}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=400,
            detail=f"SPARQL query failed: {str(e)}"
        )
