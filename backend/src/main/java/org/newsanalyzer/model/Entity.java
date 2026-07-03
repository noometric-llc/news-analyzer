package org.newsanalyzer.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entity model with dual-layer design:
 * - Internal layer: entity_type (database optimization, business logic)
 * - Semantic layer: schema_org_type and schema_org_data (Schema.org standards)
 *
 * This fixes V1's "government-entity-first" mistake by using a unified
 * entity table with flexible JSONB properties.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.Entity
@Table(name = "entities", indexes = {
    @Index(name = "idx_entities_type", columnList = "entity_type"),
    @Index(name = "idx_entities_name", columnList = "name"),
    @Index(name = "idx_entities_verified", columnList = "verified")
})
public class Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Internal entity classification for database queries and business logic.
     * Values: PERSON, GOVERNMENT_ORG, ORGANIZATION, LOCATION, EVENT, CONCEPT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private EntityType entityType;

    /**
     * Entity name (e.g., "United States Senate", "Elizabeth Warren")
     */
    @Column(nullable = false, length = 500)
    private String name;

    /**
     * Flexible, type-specific properties stored as JSONB.
     * Examples:
     * - Person: {"jobTitle": "Senator", "affiliation": "Democratic Party"}
     * - Government Org: {"url": "https://senate.gov", "jurisdiction": "federal"}
     * - Location: {"latitude": 38.8977, "longitude": -77.0365}
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Schema.org type (e.g., "Person", "GovernmentOrganization", "Place").
     * This is the standardized semantic web vocabulary.
     *
     * Maps from internal EntityType:
     * - PERSON → "Person"
     * - GOVERNMENT_ORG → "GovernmentOrganization"
     * - ORGANIZATION → "Organization"
     * - LOCATION → "Place"
     * - EVENT → "Event"
     * - CONCEPT → "Thing" or "CreativeWork"
     */
    @Column(name = "schema_org_type", length = 255)
    private String schemaOrgType;

    /**
     * Full Schema.org JSON-LD representation.
     * Example:
     * {
     *   "@context": "https://schema.org",
     *   "@type": "Person",
     *   "name": "Elizabeth Warren",
     *   "jobTitle": "United States Senator",
     *   "worksFor": {
     *     "@type": "GovernmentOrganization",
     *     "name": "United States Senate"
     *   }
     * }
     */
    @Type(JsonBinaryType.class)
    @Column(name = "schema_org_data", columnDefinition = "jsonb")
    private Map<String, Object> schemaOrgData = new HashMap<>();

    /**
     * Link to authoritative government organization record (Master Data Management).
     * Only populated for entities where entity_type = GOVERNMENT_ORG.
     *
     * This enables:
     * - Validation of extracted entities against official records
     * - Enrichment with authoritative data (mission, website, hierarchy)
     * - Deduplication ("EPA" vs "Environmental Protection Agency" → same org)
     * - Analytics queries joining transient entities with master data
     *
     * Reference: docs/architecture/entity-vs-government-org-design.md
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "government_org_id", foreignKey = @ForeignKey(name = "fk_entities_government_org"))
    private GovernmentOrganization governmentOrganization;

    /**
     * Plain FK id for the originating Article, for cheap access without
     * triggering a lazy load. Null for entities not sourced from an ingested
     * article (e.g., manually created entities).
     */
    @Column(name = "article_id")
    private UUID articleId;

    /**
     * Lazy relation to the Evidence Store Article this entity was extracted
     * from. Read-only navigation (insertable/updatable = false) — writes go
     * through {@link #articleId}. Mirrors GovernmentOrganization's
     * parent/parentId pattern.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", insertable = false, updatable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Article article;

    /**
     * Source of the entity (e.g., "article:123", "manual_entry", "wikidata")
     */
    @Column(length = 100)
    private String source;

    /**
     * Confidence score from entity extraction (0.0 to 1.0)
     */
    @Column(name = "confidence_score")
    private Float confidenceScore = 1.0f;

    /**
     * Whether the entity has been manually verified
     */
    @Column(nullable = false)
    private Boolean verified = false;

    /**
     * Entity creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Automatically set timestamps before persist
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * Automatically update timestamp before update
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Convenience method to add a property
     */
    public void addProperty(String key, Object value) {
        if (this.properties == null) {
            this.properties = new HashMap<>();
        }
        this.properties.put(key, value);
    }

    /**
     * Convenience method to get a property
     */
    public Object getProperty(String key) {
        return this.properties != null ? this.properties.get(key) : null;
    }

    /**
     * Convenience method to add Schema.org data field
     */
    public void addSchemaOrgField(String key, Object value) {
        if (this.schemaOrgData == null) {
            this.schemaOrgData = new HashMap<>();
        }
        this.schemaOrgData.put(key, value);
    }
}
