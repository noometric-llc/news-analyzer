package org.newsanalyzer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.newsanalyzer.model.EntityType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for Entity API requests and responses.
 *
 * This DTO is used for:
 * - API responses (GET /api/entities)
 * - API requests (POST /api/entities, PUT /api/entities/{id})
 */
@Schema(description = """
    An AI-extracted entity — a named person, organization, location, or concept \
    identified by the reasoning service while analyzing a news article. \
    Not an authoritative government record; confidence score reflects extraction certainty. \
    Government organization entities may be linked to a verified GovernmentOrganization \
    record via the /validate endpoint.
    """)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityDTO {

    private UUID id;

    /**
     * Internal entity type (PERSON, GOVERNMENT_ORG, etc.)
     */
    private EntityType entityType;

    /**
     * Entity name
     */
    private String name;

    /**
     * Flexible properties (JSONB)
     */
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Schema.org type (e.g., "Person", "GovernmentOrganization")
     */
    private String schemaOrgType;

    /**
     * Full Schema.org JSON-LD representation
     */
    private Map<String, Object> schemaOrgData = new HashMap<>();

    /**
     * Source identifier
     */
    private String source;

    /**
     * Confidence score (0.0 to 1.0)
     */
    private Float confidenceScore;

    /**
     * Verification status
     */
    private Boolean verified;

    /**
     * Linked government organization ID (if entity_type = GOVERNMENT_ORG and validated)
     */
    private UUID governmentOrganizationId;

    /**
     * Linked government organization name (for convenience, avoids join in frontend)
     */
    private String governmentOrganizationName;

    /**
     * Creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    private LocalDateTime updatedAt;
}
