"""
Entity Disambiguation Service

Disambiguates between multiple candidate entities from external knowledge bases
using type matching, name similarity, and context analysis.
"""

import logging
import re
from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Optional, Any, Set

from rapidfuzz import fuzz

logger = logging.getLogger(__name__)


class EntityType(str, Enum):
    """Entity types supported for disambiguation"""
    PERSON = "person"
    ORGANIZATION = "organization"
    GOVERNMENT_ORG = "government_org"
    LOCATION = "location"
    EVENT = "event"


# Wikidata QID to entity type mapping for type matching
WIKIDATA_TYPE_MAPPINGS: Dict[str, Dict[str, float]] = {
    EntityType.PERSON: {
        "Q5": 1.0,          # human
        "Q215627": 0.8,     # person
        "Q36180": 0.7,      # writer (subtype of person)
        "Q82955": 0.7,      # politician
    },
    EntityType.ORGANIZATION: {
        "Q43229": 1.0,      # organization
        "Q4830453": 0.9,    # business
        "Q783794": 0.8,     # company
        "Q163740": 0.8,     # nonprofit organization
    },
    EntityType.GOVERNMENT_ORG: {
        "Q327333": 1.0,     # government agency
        "Q7210356": 0.9,    # political organization
        "Q43229": 0.7,      # organization (partial match)
        "Q7188": 0.8,       # government
    },
    EntityType.LOCATION: {
        "Q515": 1.0,        # city
        "Q6256": 1.0,       # country
        "Q82794": 0.9,      # geographic region
        "Q35657": 0.9,      # state
        "Q486972": 0.8,     # human settlement
    },
    EntityType.EVENT: {
        "Q1190554": 1.0,    # event
        "Q1656682": 0.9,    # occurrence
        "Q18669875": 0.8,   # recurring event
    },
}

# DBpedia ontology class to entity type mapping
DBPEDIA_TYPE_MAPPINGS: Dict[str, Dict[str, float]] = {
    EntityType.PERSON: {
        "Person": 1.0,
        "Artist": 0.8,
        "Politician": 0.8,
        "Writer": 0.8,
    },
    EntityType.ORGANIZATION: {
        "Organisation": 1.0,
        "Company": 0.9,
        "Non-ProfitOrganisation": 0.8,
    },
    EntityType.GOVERNMENT_ORG: {
        "GovernmentAgency": 1.0,
        "Government": 0.9,
        "Organisation": 0.7,
    },
    EntityType.LOCATION: {
        "Place": 1.0,
        "City": 1.0,
        "Country": 1.0,
        "PopulatedPlace": 0.9,
        "AdministrativeRegion": 0.8,
    },
    EntityType.EVENT: {
        "Event": 1.0,
        "SportsEvent": 0.9,
        "Election": 0.9,
    },
}

# Known ambiguous names that could be multiple types
AMBIGUOUS_NAMES: Dict[str, List[str]] = {
    "washington": ["person", "location", "government_org"],
    "johnson": ["person", "organization", "location"],
    "clinton": ["person", "location"],
    "jackson": ["person", "location"],
    "lincoln": ["person", "location"],
    "madison": ["person", "location"],
    "congress": ["government_org", "event"],
    "senate": ["government_org", "location"],
    "sec": ["government_org", "organization"],
    "doj": ["government_org"],
    "fbi": ["government_org"],
    "cia": ["government_org"],
}

# English stop words for context comparison
STOP_WORDS: Set[str] = {
    "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
    "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
    "be", "have", "has", "had", "do", "does", "did", "will", "would",
    "could", "should", "may", "might", "must", "shall", "can", "need",
    "that", "this", "these", "those", "it", "its", "they", "them", "their",
    "he", "she", "him", "her", "his", "we", "us", "our", "you", "your",
    "who", "which", "what", "when", "where", "why", "how", "all", "each",
    "every", "both", "few", "more", "most", "other", "some", "such", "no",
    "not", "only", "own", "same", "so", "than", "too", "very", "just",
}


@dataclass
class Candidate:
    """A candidate entity for disambiguation"""
    id: str  # QID or URI
    label: str
    description: Optional[str] = None
    types: List[str] = field(default_factory=list)
    aliases: List[str] = field(default_factory=list)
    source: str = "wikidata"  # "wikidata" or "dbpedia"
    original_confidence: float = 0.0

    # Disambiguation scores (filled during scoring)
    type_score: float = 0.0
    name_score: float = 0.0
    context_score: float = 0.0
    final_score: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        return {
            "id": self.id,
            "label": self.label,
            "description": self.description,
            "types": self.types,
            "aliases": self.aliases,
            "source": self.source,
            "scores": {
                "type": round(self.type_score, 3),
                "name": round(self.name_score, 3),
                "context": round(self.context_score, 3),
                "final": round(self.final_score, 3),
            }
        }


@dataclass
class DisambiguationResult:
    """Result of entity disambiguation"""
    entity_text: str
    entity_type: Optional[EntityType]
    match: Optional[Candidate] = None
    confidence: float = 0.0
    needs_review: bool = False
    is_ambiguous: bool = False
    all_candidates: List[Candidate] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        return {
            "entity_text": self.entity_text,
            "entity_type": self.entity_type.value if self.entity_type else None,
            "match": self.match.to_dict() if self.match else None,
            "confidence": round(self.confidence, 3),
            "needs_review": self.needs_review,
            "is_ambiguous": self.is_ambiguous,
            "candidate_count": len(self.all_candidates),
            "all_candidates": [c.to_dict() for c in self.all_candidates[:5]],
        }


class DisambiguationService:
    """Service for disambiguating entity candidates"""

    # Confidence threshold for automatic matching
    CONFIDENCE_THRESHOLD = 0.7

    # Score weights
    TYPE_WEIGHT = 0.4
    NAME_WEIGHT = 0.3
    CONTEXT_WEIGHT = 0.3

    # Weights without context
    TYPE_WEIGHT_NO_CONTEXT = 0.5
    NAME_WEIGHT_NO_CONTEXT = 0.5

    def __init__(self):
        """Initialize the disambiguation service"""
        logger.info("DisambiguationService initialized")

    def _normalize_text(self, text: str) -> str:
        """Normalize text for comparison"""
        # Lowercase
        text = text.lower()
        # Remove punctuation except hyphens
        text = re.sub(r"[^\w\s-]", "", text)
        # Normalize whitespace
        text = " ".join(text.split())
        return text

    def _is_acronym(self, text: str) -> bool:
        """Check if text is likely an acronym"""
        text = text.strip()
        # All caps, 2-6 letters
        return text.isupper() and 2 <= len(text) <= 6 and text.isalpha()

    # Minor words to skip when building acronym initials
    _ACRONYM_SKIP_WORDS = {"of", "the", "and", "for", "in", "on", "at", "to", "by", "a", "an"}

    def _matches_acronym(self, acronym: str, full_name: str) -> bool:
        """Check if acronym matches full name"""
        acronym = acronym.upper()
        words = full_name.split()

        # First-letter matching, skipping minor words (of, the, and, etc.)
        significant_words = [w for w in words if w.lower() not in self._ACRONYM_SKIP_WORDS]
        if len(significant_words) >= len(acronym):
            initials = "".join(w[0].upper() for w in significant_words if w)
            if acronym in initials:
                return True

        # Check if acronym appears in parentheses in name
        if f"({acronym})" in full_name.upper():
            return True

        return False

    def _score_type_match(
        self,
        expected_type: Optional[EntityType],
        candidate_types: List[str],
        source: str = "wikidata"
    ) -> float:
        """
        Score how well candidate types match expected entity type.

        Args:
            expected_type: Expected entity type
            candidate_types: Types from candidate (QIDs or class names)
            source: Source of candidate ("wikidata" or "dbpedia")

        Returns:
            Score between 0.0 and 1.0
        """
        if not expected_type or not candidate_types:
            return 0.5  # Neutral score

        # Select appropriate mapping
        type_mappings = (
            WIKIDATA_TYPE_MAPPINGS if source == "wikidata"
            else DBPEDIA_TYPE_MAPPINGS
        )

        if expected_type not in type_mappings:
            return 0.5

        expected_mapping = type_mappings[expected_type]

        # Find best matching type
        best_score = 0.0
        for candidate_type in candidate_types:
            # For Wikidata, extract QID from URI if needed
            if source == "wikidata" and "/" in candidate_type:
                candidate_type = candidate_type.split("/")[-1]

            # For DBpedia, extract class name from URI if needed
            if source == "dbpedia" and "/" in candidate_type:
                candidate_type = candidate_type.split("/")[-1]

            if candidate_type in expected_mapping:
                best_score = max(best_score, expected_mapping[candidate_type])

        return best_score

    def _score_name_similarity(
        self,
        entity_text: str,
        candidate_label: str,
        candidate_aliases: Optional[List[str]] = None
    ) -> float:
        """
        Score name similarity between entity and candidate.

        Args:
            entity_text: Original entity text
            candidate_label: Candidate's label
            candidate_aliases: Candidate's aliases

        Returns:
            Score between 0.0 and 1.0
        """
        n1 = self._normalize_text(entity_text)
        n2 = self._normalize_text(candidate_label)

        # Exact match
        if n1 == n2:
            return 1.0

        # Check acronym match
        if self._is_acronym(entity_text):
            if self._matches_acronym(entity_text, candidate_label):
                return 0.95

        # Check if entity text is in candidate label or vice versa
        if n1 in n2 or n2 in n1:
            return 0.85

        # Check aliases
        if candidate_aliases:
            for alias in candidate_aliases:
                normalized_alias = self._normalize_text(alias)
                if n1 == normalized_alias:
                    return 0.95
                if self._is_acronym(entity_text) and self._matches_acronym(entity_text, alias):
                    return 0.9

        # Use fuzzy matching
        ratio = fuzz.ratio(n1, n2) / 100.0
        token_ratio = fuzz.token_sort_ratio(n1, n2) / 100.0
        partial_ratio = fuzz.partial_ratio(n1, n2) / 100.0

        # Weighted combination
        return (ratio * 0.4) + (token_ratio * 0.4) + (partial_ratio * 0.2)

    def _score_context_similarity(
        self,
        context: str,
        description: Optional[str]
    ) -> float:
        """
        Score context similarity between article context and candidate description.

        Args:
            context: Article text or context around entity
            description: Candidate's description

        Returns:
            Score between 0.0 and 1.0
        """
        if not context or not description:
            return 0.5  # Neutral score

        # Tokenize and remove stop words
        context_words = set(self._normalize_text(context).split())
        desc_words = set(self._normalize_text(description).split())

        context_words -= STOP_WORDS
        desc_words -= STOP_WORDS

        if not desc_words:
            return 0.5

        # Calculate Jaccard-like overlap
        overlap = len(context_words & desc_words)

        # Normalize by smaller set size, cap at 1.0
        min_size = min(len(context_words), len(desc_words))
        if min_size == 0:
            return 0.5

        # Scale: 5+ overlapping words = 1.0
        return min(1.0, overlap / 5.0)

    def _is_ambiguous_name(self, name: str) -> bool:
        """Check if name is known to be ambiguous"""
        normalized = self._normalize_text(name)
        return normalized in AMBIGUOUS_NAMES

    def _get_ambiguous_types(self, name: str) -> List[str]:
        """Get possible types for ambiguous name"""
        normalized = self._normalize_text(name)
        return AMBIGUOUS_NAMES.get(normalized, [])

    def disambiguate(
        self,
        entity_text: str,
        entity_type: Optional[EntityType],
        candidates: List[Candidate],
        context: Optional[str] = None
    ) -> DisambiguationResult:
        """
        Disambiguate between candidate entities.

        Args:
            entity_text: Original entity text from extraction
            entity_type: Expected entity type
            candidates: List of candidate entities from KB lookup
            context: Optional surrounding text for context

        Returns:
            DisambiguationResult with best match and confidence
        """
        if not candidates:
            logger.info(f"No candidates for '{entity_text}'")
            return DisambiguationResult(
                entity_text=entity_text,
                entity_type=entity_type,
                confidence=0.0,
                needs_review=True
            )

        # Check if name is ambiguous
        is_ambiguous = self._is_ambiguous_name(entity_text)
        if is_ambiguous:
            logger.info(f"Ambiguous name detected: '{entity_text}'")

        has_context = context is not None and len(context.strip()) > 0

        # Score each candidate
        for candidate in candidates:
            # Type score
            candidate.type_score = self._score_type_match(
                entity_type,
                candidate.types,
                candidate.source
            )

            # Name score
            candidate.name_score = self._score_name_similarity(
                entity_text,
                candidate.label,
                candidate.aliases
            )

            # Context score
            candidate.context_score = self._score_context_similarity(
                context,  # type: ignore[arg-type]  # guarded by has_context check above
                candidate.description
            ) if has_context else 0.5

            # Combined score
            if has_context:
                candidate.final_score = (
                    (candidate.type_score * self.TYPE_WEIGHT) +
                    (candidate.name_score * self.NAME_WEIGHT) +
                    (candidate.context_score * self.CONTEXT_WEIGHT)
                )
            else:
                candidate.final_score = (
                    (candidate.type_score * self.TYPE_WEIGHT_NO_CONTEXT) +
                    (candidate.name_score * self.NAME_WEIGHT_NO_CONTEXT)
                )

            # Penalty for ambiguous names without context
            if is_ambiguous and not has_context:
                candidate.final_score *= 0.8

        # Sort by final score
        candidates.sort(key=lambda c: c.final_score, reverse=True)

        # Select best candidate
        best = candidates[0]
        confidence = best.final_score

        # Determine if needs review
        needs_review = confidence < self.CONFIDENCE_THRESHOLD

        # Additional checks for review
        if len(candidates) > 1:
            # If top two are very close, flag for review
            score_diff = best.final_score - candidates[1].final_score
            if score_diff < 0.1:
                needs_review = True
                logger.info(
                    f"Close scores for '{entity_text}': "
                    f"{best.label}={best.final_score:.2f}, "
                    f"{candidates[1].label}={candidates[1].final_score:.2f}"
                )

        logger.info(
            f"Disambiguation for '{entity_text}': "
            f"best='{best.label}' (confidence={confidence:.2f}, needs_review={needs_review})"
        )

        return DisambiguationResult(
            entity_text=entity_text,
            entity_type=entity_type,
            match=best if confidence >= 0.5 else None,  # Return match even if low confidence
            confidence=confidence,
            needs_review=needs_review,
            is_ambiguous=is_ambiguous,
            all_candidates=candidates
        )


# Singleton instance
_service_instance: Optional[DisambiguationService] = None


def get_disambiguation_service() -> DisambiguationService:
    """
    Get or create singleton DisambiguationService instance.

    Returns:
        DisambiguationService instance
    """
    global _service_instance
    if _service_instance is None:
        _service_instance = DisambiguationService()
    return _service_instance
