"""
Entity Extractor Service

Uses spaCy NLP to extract named entities from news article text.
"""

import spacy
from typing import List, Dict, Any, Optional
from spacy.tokens import Doc

from app.services.schema_mapper import SchemaMapper


class ExtractedEntity:
    """Represents an extracted entity with metadata"""

    def __init__(
        self,
        text: str,
        entity_type: str,
        start: int,
        end: int,
        confidence: float,
        label: str,
        schema_org_type: str,
        schema_org_data: Dict[str, Any]
    ):
        self.text = text
        self.entity_type = entity_type
        self.start = start
        self.end = end
        self.confidence = confidence
        self.label = label  # Original spaCy label
        self.schema_org_type = schema_org_type
        self.schema_org_data = schema_org_data

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization"""
        return {
            "text": self.text,
            "entity_type": self.entity_type,
            "start": self.start,
            "end": self.end,
            "confidence": self.confidence,
            "schema_org_type": self.schema_org_type,
            "schema_org_data": self.schema_org_data,
            "properties": {}  # Can be enriched later
        }


class EntityExtractor:
    """Extracts entities from text using spaCy NLP"""

    def __init__(self, model_name: str = "en_core_web_lg"):
        """
        Initialize the entity extractor with spaCy model

        Args:
            model_name: spaCy model to use (default: en_core_web_lg)
        """
        try:
            self.nlp = spacy.load(model_name)
        except OSError:
            # Model not found, provide helpful error
            raise RuntimeError(
                f"spaCy model '{model_name}' not found. "
                f"Install it with: python -m spacy download {model_name}"
            )

        self.schema_mapper = SchemaMapper()

    def extract(
        self,
        text: str,
        confidence_threshold: float = 0.7
    ) -> List[ExtractedEntity]:
        """
        Extract entities from text

        Args:
            text: Text to extract entities from
            confidence_threshold: Minimum confidence score (0.0 to 1.0)

        Returns:
            List of ExtractedEntity objects
        """
        # Process text with spaCy
        doc: Doc = self.nlp(text)

        entities: List[ExtractedEntity] = []

        for ent in doc.ents:
            # Map spaCy label to internal entity type
            entity_type = self.schema_mapper.map_spacy_label(ent.label_, ent.text)

            # Skip entities that shouldn't be tracked
            if entity_type is None:
                continue

            # Calculate confidence (spaCy doesn't provide confidence, use fixed value)
            # In production, you might want to use a model that provides confidence scores
            confidence = 0.85

            # Skip low-confidence entities
            if confidence < confidence_threshold:
                continue

            # Get Schema.org type and generate JSON-LD
            schema_org_type = self.schema_mapper.get_schema_org_type(entity_type)
            schema_org_data = self.schema_mapper.generate_json_ld(
                entity_type=entity_type,
                name=ent.text
            )

            entities.append(ExtractedEntity(
                text=ent.text,
                entity_type=entity_type,
                start=ent.start_char,
                end=ent.end_char,
                confidence=confidence,
                label=ent.label_,
                schema_org_type=schema_org_type,
                schema_org_data=schema_org_data
            ))

        return entities

    def extract_with_context(
        self,
        text: str,
        confidence_threshold: float = 0.7,
        validate_government_orgs: bool = True
    ) -> Dict[str, Any]:
        """
        Extract entities with additional context

        Args:
            text: Text to extract entities from
            confidence_threshold: Minimum confidence score
            validate_government_orgs: Whether to validate government orgs against database

        Returns:
            Dictionary with entities and metadata
        """
        entities = self.extract(text, confidence_threshold)

        # Convert to dicts
        entity_dicts = [e.to_dict() for e in entities]

        # Validate and enrich government organizations if requested
        if validate_government_orgs:
            entity_dicts = self._validate_government_orgs(entity_dicts)

        return {
            "entities": entity_dicts,
            "total_count": len(entity_dicts),
            "text_length": len(text),
            "entity_types": self._count_by_type(entities),
            "validated_gov_orgs": sum(1 for e in entity_dicts
                                     if e.get("verified") and e["entity_type"] == "government_org")
        }

    @staticmethod
    def _count_by_type(entities: List[ExtractedEntity]) -> Dict[str, int]:
        """Count entities by type"""
        counts: Dict[str, int] = {}
        for entity in entities:
            counts[entity.entity_type] = counts.get(entity.entity_type, 0) + 1
        return counts

    @staticmethod
    def _validate_government_orgs(entities: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        Validate and enrich government organization entities

        Args:
            entities: List of entity dictionaries

        Returns:
            List of enriched entity dictionaries
        """
        try:
            from app.services.gov_org_validator import get_gov_org_validator

            validator = get_gov_org_validator()
            enriched_entities = []

            for entity in entities:
                # Only validate government organizations
                if entity["entity_type"] == "government_org":
                    # Validate against database
                    validation_result = validator.validate_entity(
                        entity_text=entity["text"],
                        entity_type=entity["entity_type"]
                    )

                    # Enrich entity with official data
                    if validation_result is not None:
                        enriched_entity = validator.enrich_entity(entity, validation_result)
                        enriched_entities.append(enriched_entity)
                    else:
                        enriched_entities.append(entity)
                else:
                    # Non-government entities pass through unchanged
                    enriched_entities.append(entity)

            return enriched_entities

        except Exception as e:
            # If validation fails, return original entities
            print(f"Warning: Government org validation failed: {e}")
            return entities


# Singleton instance
_extractor_instance: Optional[EntityExtractor] = None


def get_entity_extractor() -> EntityExtractor:
    """
    Get or create singleton EntityExtractor instance

    Returns:
        EntityExtractor instance
    """
    global _extractor_instance
    if _extractor_instance is None:
        _extractor_instance = EntityExtractor()
    return _extractor_instance
