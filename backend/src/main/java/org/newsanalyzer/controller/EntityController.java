package org.newsanalyzer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.CreateEntityRequest;
import org.newsanalyzer.dto.EntityDTO;
import org.newsanalyzer.model.EntityType;
import org.newsanalyzer.service.EntityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API Controller for Entity operations.
 *
 * Endpoints:
 * - POST   /api/entities                - Create entity
 * - POST   /api/entities/validate       - Create and validate entity (with gov org linking)
 * - GET    /api/entities                - List all entities
 * - GET    /api/entities/{id}           - Get entity by ID
 * - PUT    /api/entities/{id}           - Update entity
 * - DELETE /api/entities/{id}           - Delete entity
 * - POST   /api/entities/{id}/validate  - Validate existing entity
 * - POST   /api/entities/{id}/verify    - Verify entity
 * - GET    /api/entities/type/{type}    - Get entities by type
 * - GET    /api/entities/search         - Search entities
 */
@Slf4j
@RestController
@RequestMapping("/api/entities")
@RequiredArgsConstructor
@Tag(name = "Entities", description = """
    AI-extracted entities — named people, organizations, locations, and concepts \
    identified by the reasoning service while analyzing news articles.

    These are NOT authoritative government records. For curated government organization \
    data imported from official sources, see the Government Organizations endpoints.

    Entities gain a confidence score (0.0–1.0) from extraction and can be linked to a \
    Government Organization record via POST /{id}/validate once confirmed.
    """)
public class EntityController {

    private final EntityService entityService;

    /**
     * Create a new entity
     */
    @PostMapping
    @Operation(summary = "Create new entity", description = "Create a new entity with automatic Schema.org mapping")
    public ResponseEntity<EntityDTO> createEntity(@Valid @RequestBody CreateEntityRequest request) {
        log.info("POST /api/entities - Creating entity: {}", request.getName());
        EntityDTO created = entityService.createEntity(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get all entities
     */
    @GetMapping
    @Operation(summary = "List all entities", description = "Get all entities in the database")
    public ResponseEntity<List<EntityDTO>> getAllEntities() {
        log.info("GET /api/entities - Fetching all entities");
        List<EntityDTO> entities = entityService.getAllEntities();
        return ResponseEntity.ok(entities);
    }

    /**
     * Get entity by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get entity by ID", description = "Retrieve a specific entity by UUID")
    public ResponseEntity<EntityDTO> getEntityById(
        @Parameter(description = "Entity UUID") @PathVariable UUID id
    ) {
        log.info("GET /api/entities/{} - Fetching entity", id);
        EntityDTO entity = entityService.getEntityById(id);
        return ResponseEntity.ok(entity);
    }

    /**
     * Update entity
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update entity", description = "Update an existing entity")
    public ResponseEntity<EntityDTO> updateEntity(
        @Parameter(description = "Entity UUID") @PathVariable UUID id,
        @Valid @RequestBody CreateEntityRequest request
    ) {
        log.info("PUT /api/entities/{} - Updating entity", id);
        EntityDTO updated = entityService.updateEntity(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete entity
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete entity", description = "Delete an entity by UUID")
    public ResponseEntity<Void> deleteEntity(
        @Parameter(description = "Entity UUID") @PathVariable UUID id
    ) {
        log.info("DELETE /api/entities/{} - Deleting entity", id);
        entityService.deleteEntity(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get entities by internal type
     */
    @GetMapping("/type/{type}")
    @Operation(summary = "Get entities by type", description = "Filter entities by internal EntityType")
    public ResponseEntity<List<EntityDTO>> getEntitiesByType(
        @Parameter(description = "Entity type") @PathVariable EntityType type
    ) {
        log.info("GET /api/entities/type/{} - Fetching entities by type", type);
        List<EntityDTO> entities = entityService.getEntitiesByType(type);
        return ResponseEntity.ok(entities);
    }

    /**
     * Get entities by Schema.org type
     */
    @GetMapping("/schema-org-type/{schemaOrgType}")
    @Operation(summary = "Get entities by Schema.org type", description = "Filter entities by Schema.org type (e.g., Person, GovernmentOrganization)")
    public ResponseEntity<List<EntityDTO>> getEntitiesBySchemaOrgType(
        @Parameter(description = "Schema.org type") @PathVariable String schemaOrgType
    ) {
        log.info("GET /api/entities/schema-org-type/{} - Fetching entities", schemaOrgType);
        List<EntityDTO> entities = entityService.getEntitiesBySchemaOrgType(schemaOrgType);
        return ResponseEntity.ok(entities);
    }

    /**
     * Search entities by name
     */
    @GetMapping("/search")
    @Operation(summary = "Search entities", description = "Search entities by name (case-insensitive substring match)")
    public ResponseEntity<List<EntityDTO>> searchEntities(
        @Parameter(description = "Search query") @RequestParam String q
    ) {
        log.info("GET /api/entities/search?q={}", q);
        List<EntityDTO> entities = entityService.searchEntitiesByName(q);
        return ResponseEntity.ok(entities);
    }

    /**
     * Full-text search
     */
    @GetMapping("/search/fulltext")
    @Operation(summary = "Full-text search", description = "Full-text search using PostgreSQL tsvector")
    public ResponseEntity<List<EntityDTO>> fullTextSearch(
        @Parameter(description = "Search text") @RequestParam String q,
        @Parameter(description = "Result limit") @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("GET /api/entities/search/fulltext?q={}&limit={}", q, limit);
        List<EntityDTO> entities = entityService.fullTextSearch(q, limit);
        return ResponseEntity.ok(entities);
    }

    /**
     * Get recent entities
     */
    @GetMapping("/recent")
    @Operation(summary = "Get recent entities", description = "Get entities created in the last N days")
    public ResponseEntity<List<EntityDTO>> getRecentEntities(
        @Parameter(description = "Number of days") @RequestParam(defaultValue = "7") int days
    ) {
        log.info("GET /api/entities/recent?days={}", days);
        List<EntityDTO> entities = entityService.getRecentEntities(days);
        return ResponseEntity.ok(entities);
    }

    /**
     * Verify entity
     */
    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify entity", description = "Mark an entity as manually verified")
    public ResponseEntity<EntityDTO> verifyEntity(
        @Parameter(description = "Entity UUID") @PathVariable UUID id
    ) {
        log.info("POST /api/entities/{}/verify - Verifying entity", id);
        EntityDTO verified = entityService.verifyEntity(id);
        return ResponseEntity.ok(verified);
    }

    /**
     * Create and validate entity (with automatic government org linking)
     */
    @PostMapping("/validate")
    @Operation(
        summary = "Create and validate entity",
        description = "Create a new entity with automatic validation and linking to government organizations (Master Data Management pattern)"
    )
    public ResponseEntity<EntityDTO> createAndValidateEntity(@Valid @RequestBody CreateEntityRequest request) {
        log.info("POST /api/entities/validate - Creating and validating entity: {}", request.getName());
        EntityDTO created = entityService.createAndValidateEntity(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Validate existing entity
     */
    @PostMapping("/{id}/validate")
    @Operation(
        summary = "Validate existing entity",
        description = "Validate an existing entity against government organizations and link if match found"
    )
    public ResponseEntity<EntityDTO> validateExistingEntity(
        @Parameter(description = "Entity UUID") @PathVariable UUID id
    ) {
        log.info("POST /api/entities/{}/validate - Validating entity", id);
        EntityDTO validated = entityService.validateEntity(id);
        return ResponseEntity.ok(validated);
    }
}
