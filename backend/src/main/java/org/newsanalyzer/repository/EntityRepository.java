package org.newsanalyzer.repository;

import org.newsanalyzer.model.Entity;
import org.newsanalyzer.model.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Entity persistence operations.
 *
 * Provides:
 * - Standard CRUD operations (via JpaRepository)
 * - Custom queries for entity search and filtering
 * - Schema.org type queries
 * - Full-text search (using PostgreSQL search_vector)
 */
@Repository
public interface EntityRepository extends JpaRepository<Entity, UUID> {

    /**
     * Find entities by internal entity type
     */
    List<Entity> findByEntityType(EntityType entityType);

    /**
     * Find entities linked to a specific article (Story ES-1.6)
     */
    List<Entity> findByArticleId(UUID articleId);

    /**
     * Find entities by Schema.org type
     */
    @Query("SELECT e FROM Entity e WHERE e.schemaOrgType = :schemaOrgType")
    List<Entity> findBySchemaOrgType(@Param("schemaOrgType") String schemaOrgType);

    /**
     * Find entity by exact name (case-sensitive)
     */
    Optional<Entity> findByName(String name);

    /**
     * Find entities by name containing (case-insensitive)
     */
    List<Entity> findByNameContainingIgnoreCase(String nameFragment);

    /**
     * Find verified entities only
     */
    List<Entity> findByVerifiedTrue();

    /**
     * Find entities by type and verification status
     */
    List<Entity> findByEntityTypeAndVerified(EntityType entityType, Boolean verified);

    /**
     * Find entities with confidence score above threshold
     */
    @Query("SELECT e FROM Entity e WHERE e.confidenceScore >= :threshold")
    List<Entity> findByConfidenceScoreGreaterThanEqual(@Param("threshold") Float threshold);

    /**
     * Full-text search using PostgreSQL's tsvector
     * Searches in name and properties->>'description'
     */
    @Query(value = """
        SELECT * FROM entities
        WHERE search_vector @@ plainto_tsquery('english', :searchText)
        ORDER BY ts_rank(search_vector, plainto_tsquery('english', :searchText)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Entity> fullTextSearch(
        @Param("searchText") String searchText,
        @Param("limit") int limit
    );

    /**
     * Find entities by source
     */
    List<Entity> findBySource(String source);

    /**
     * Count entities by type
     */
    long countByEntityType(EntityType entityType);

    /**
     * Find entities created after a specific date
     */
    @Query("SELECT e FROM Entity e WHERE e.createdAt >= :fromDate ORDER BY e.createdAt DESC")
    List<Entity> findRecentEntities(@Param("fromDate") java.time.LocalDateTime fromDate);

    /**
     * Query JSONB properties using native PostgreSQL syntax
     * Example: Find all entities with a specific property value
     */
    @Query(value = """
        SELECT * FROM entities
        WHERE properties->>'jobTitle' = :jobTitle
        """, nativeQuery = true)
    List<Entity> findByPropertyJobTitle(@Param("jobTitle") String jobTitle);

    /**
     * Advanced JSONB query: Find entities where Schema.org data contains a field
     * TODO: Fix parameter binding issue with JSONB ? operator
     */
    // @Query(value = """
    //     SELECT * FROM entities
    //     WHERE schema_org_data ? :fieldName
    //     """, nativeQuery = true)
    // List<Entity> findBySchemaOrgDataContainingField(@Param("fieldName") String fieldName);
}
