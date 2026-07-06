package org.newsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A single extracted entity, as returned by the reasoning service's
 * POST /entities/extract endpoint. Field names map to the documented
 * snake_case contract (docs/api/reasoning-service-contract.md).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedEntityData {

    /**
     * Extracted entity string (e.g. "Elizabeth Warren")
     */
    private String text;

    /**
     * One of: person, government_org, organization, location, event, concept
     */
    @JsonProperty("entity_type")
    private String entityType;

    /**
     * Character offset start in source text
     */
    private Integer start;

    /**
     * Character offset end in source text
     */
    private Integer end;

    /**
     * Extraction confidence score
     */
    private Float confidence;

    /**
     * Schema.org type (e.g. "Person", "GovernmentOrganization")
     */
    @JsonProperty("schema_org_type")
    private String schemaOrgType;

    /**
     * JSON-LD Schema.org representation
     */
    @JsonProperty("schema_org_data")
    private Map<String, Object> schemaOrgData;

    /**
     * Additional entity properties (may be empty)
     */
    private Map<String, Object> properties;
}
