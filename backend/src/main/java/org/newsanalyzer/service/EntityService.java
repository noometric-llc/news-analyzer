package org.newsanalyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.CreateEntityRequest;
import org.newsanalyzer.dto.EntityDTO;
import org.newsanalyzer.dto.ExtractedEntityData;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.Entity;
import org.newsanalyzer.model.EntityType;
import org.newsanalyzer.repository.EntityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for Entity operations.
 *
 * Handles:
 * - CRUD operations
 * - Schema.org mapping (via SchemaOrgMapper)
 * - Entity search and filtering
 * - Data validation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityService {

    private final EntityRepository entityRepository;
    private final SchemaOrgMapper schemaOrgMapper;
    private final GovernmentOrganizationService governmentOrganizationService;

    /**
     * Create a new entity with automatic Schema.org mapping
     */
    @Transactional
    public EntityDTO createEntity(CreateEntityRequest request) {
        log.info("Creating entity: type={}, name={}", request.getEntityType(), request.getName());

        Entity entity = new Entity();
        entity.setEntityType(request.getEntityType());
        entity.setName(request.getName());
        entity.setProperties(request.getProperties() != null ? request.getProperties() : Map.of());
        entity.setSource(request.getSource());
        entity.setConfidenceScore(request.getConfidenceScore() != null ? request.getConfidenceScore() : 1.0f);
        entity.setVerified(request.getVerified() != null ? request.getVerified() : false);

        // Auto-generate Schema.org type if not provided
        if (request.getSchemaOrgType() == null) {
            String schemaOrgType = schemaOrgMapper.getSchemaOrgType(request.getEntityType());
            entity.setSchemaOrgType(schemaOrgType);
            log.debug("Auto-mapped entity type {} to Schema.org type {}", request.getEntityType(), schemaOrgType);
        } else {
            entity.setSchemaOrgType(request.getSchemaOrgType());
        }

        // Auto-generate Schema.org JSON-LD if not provided
        if (request.getSchemaOrgData() == null || request.getSchemaOrgData().isEmpty()) {
            Map<String, Object> jsonLd = schemaOrgMapper.generateJsonLd(entity);
            entity.setSchemaOrgData(jsonLd);
            log.debug("Auto-generated Schema.org JSON-LD for entity: {}", entity.getName());
        } else {
            // Enrich provided data
            Map<String, Object> enriched = schemaOrgMapper.enrichSchemaOrgData(
                request.getSchemaOrgData(),
                entity
            );
            entity.setSchemaOrgData(enriched);
        }

        Entity saved = entityRepository.save(entity);
        log.info("Created entity: id={}, name={}", saved.getId(), saved.getName());

        return toDTO(saved);
    }

    /**
     * Create an Entity from a reasoning-service extraction result, linked to
     * its source Article via articleId.
     *
     * Unlike createEntity(), no Schema.org auto-generation is needed here —
     * the reasoning service's /entities/extract response already includes
     * schemaOrgType/schemaOrgData directly.
     *
     * Deliberately does not set the freeform `source` field (e.g. the old
     * "article:123" convention) — articleId is the real, queryable FK now;
     * duplicating the same fact into a string field would be redundant.
     *
     * Called by ArticleService (Story ES-1.3) — see the must-fix
     * transaction-boundary guidance in the architecture doc: this method
     * itself is a short, self-contained transaction, called only AFTER the
     * external reasoning-service HTTP call has already completed.
     */
    @Transactional
    public Entity createEntityFromExtraction(ExtractedEntityData extracted, UUID articleId) {
        log.info("Creating entity from extraction: text={}, articleId={}", extracted.getText(), articleId);

        Entity entity = new Entity();
        entity.setEntityType(EntityType.valueOf(extracted.getEntityType().toUpperCase()));
        entity.setName(extracted.getText());
        entity.setProperties(extracted.getProperties() != null ? extracted.getProperties() : Map.of());
        entity.setSchemaOrgType(extracted.getSchemaOrgType());
        entity.setSchemaOrgData(extracted.getSchemaOrgData() != null ? extracted.getSchemaOrgData() : Map.of());
        entity.setConfidenceScore(extracted.getConfidence());
        entity.setVerified(false);
        entity.setArticleId(articleId);

        Entity saved = entityRepository.save(entity);
        log.info("Created entity from extraction: id={}, name={}, articleId={}",
            saved.getId(), saved.getName(), saved.getArticleId());

        return saved;
    }

    /**
     * Create every entity in an extraction batch atomically: either all of
     * them persist, or (if any single one fails to map — e.g. an
     * entity_type the EntityType enum doesn't recognize) none of them do.
     *
     * Added during QA review of Story ES-1.3: calling createEntityFromExtraction()
     * per-entity in a loop from ArticleService meant each call committed
     * independently, so a failure partway through a batch left earlier
     * entities persisted even though the article's extractionStatus was set
     * to FAILED — contradicting the "no entities created" invariant that
     * status is supposed to guarantee. Routing the whole batch through one
     * @Transactional method fixes that: an exception on any entity rolls
     * back the entire batch, so FAILED always means zero entities from this
     * extraction attempt actually landed in the database.
     */
    @Transactional
    public List<Entity> createEntitiesFromExtraction(List<ExtractedEntityData> extractedEntities, UUID articleId) {
        return extractedEntities.stream()
            .map(extracted -> createEntityFromExtraction(extracted, articleId))
            .collect(Collectors.toList());
    }

    /**
     * Create and validate entity - with automatic government organization linking.
     *
     * This method implements the Master Data Management pattern:
     * 1. Create entity (fast write to entities table)
     * 2. If GOVERNMENT_ORG type, validate against government_organizations table
     * 3. If valid match found, link and enrich entity with authoritative data
     *
     * Reference: docs/architecture/entity-vs-government-org-design.md (lines 187-275)
     */
    @Transactional
    public EntityDTO createAndValidateEntity(CreateEntityRequest request) {
        log.info("Creating and validating entity: type={}, name={}", request.getEntityType(), request.getName());

        // Step 1: Create entity (standard flow)
        EntityDTO createdEntity = createEntity(request);

        // Step 2: If government org, attempt validation and enrichment
        if (request.getEntityType() == EntityType.GOVERNMENT_ORG) {
            log.debug("Entity is GOVERNMENT_ORG type, attempting validation against master data");

            GovernmentOrganizationService.EntityValidationResult validation =
                governmentOrganizationService.validateEntity(request.getName(), "government_org");

            if (validation.isValid()) {
                log.info("Validation successful: matched={}, confidence={}, matchType={}",
                    validation.getMatchedOrganization().getOfficialName(),
                    validation.getConfidence(),
                    validation.getMatchType());

                // Step 3: Enrich entity with official government org data
                Entity entity = entityRepository.findById(createdEntity.getId())
                    .orElseThrow(() -> new RuntimeException("Entity not found after creation: " + createdEntity.getId()));

                enrichEntityWithGovernmentOrg(entity, validation);

                Entity enriched = entityRepository.save(entity);
                log.info("Entity enriched and linked to government org: entity_id={}, gov_org_id={}",
                    enriched.getId(),
                    enriched.getGovernmentOrganization().getId());

                return toDTO(enriched);
            } else {
                log.warn("Validation failed for entity '{}'. Suggestions: {}",
                    request.getName(),
                    validation.getSuggestions());
            }
        }

        return createdEntity;
    }

    /**
     * Validate existing entity against government organizations.
     *
     * This can be used to:
     * - Validate entities created before linking was implemented
     * - Re-validate entities after government org data updates
     * - Manually trigger validation for specific entities
     */
    @Transactional
    public EntityDTO validateEntity(UUID entityId) {
        log.info("Validating entity: id={}", entityId);

        Entity entity = entityRepository.findById(entityId)
            .orElseThrow(() -> new ResourceNotFoundException("Entity", entityId));

        // Only validate government org entities
        if (entity.getEntityType() != EntityType.GOVERNMENT_ORG) {
            log.debug("Entity is not GOVERNMENT_ORG type, skipping validation");
            return toDTO(entity);
        }

        // Already linked and verified?
        if (entity.getGovernmentOrganization() != null && entity.getVerified()) {
            log.debug("Entity already linked and verified, skipping re-validation");
            return toDTO(entity);
        }

        // Attempt validation
        GovernmentOrganizationService.EntityValidationResult validation =
            governmentOrganizationService.validateEntity(entity.getName(), "government_org");

        if (validation.isValid()) {
            enrichEntityWithGovernmentOrg(entity, validation);
            Entity saved = entityRepository.save(entity);
            log.info("Entity validated and enriched: entity_id={}, gov_org_id={}",
                saved.getId(),
                saved.getGovernmentOrganization().getId());
            return toDTO(saved);
        } else {
            log.warn("Validation failed for entity '{}'. Suggestions: {}",
                entity.getName(),
                validation.getSuggestions());
            return toDTO(entity);
        }
    }

    /**
     * Enrich entity with government organization data.
     *
     * This helper method populates:
     * - government_org_id foreign key (linking)
     * - name standardization (official name)
     * - verified flag
     * - confidence score (from validation)
     * - properties enrichment (acronym, website, jurisdiction, etc.)
     * - schema_org_data enrichment
     */
    private void enrichEntityWithGovernmentOrg(
        Entity entity,
        GovernmentOrganizationService.EntityValidationResult validation
    ) {
        var govOrg = validation.getMatchedOrganization();

        // Link to government organization (Master Data Management)
        entity.setGovernmentOrganization(govOrg);

        // Standardize name to official name
        entity.setName(govOrg.getOfficialName());

        // Mark as verified
        entity.setVerified(true);

        // Update confidence score (validation confidence)
        if (validation.getConfidence() > entity.getConfidenceScore()) {
            entity.setConfidenceScore((float) validation.getConfidence());
        }

        // Enrich properties with government org data
        if (govOrg.getAcronym() != null) {
            entity.addProperty("acronym", govOrg.getAcronym());
        }
        if (govOrg.getWebsiteUrl() != null) {
            entity.addProperty("website", govOrg.getWebsiteUrl());
        }
        if (govOrg.getOrgType() != null) {
            entity.addProperty("orgType", govOrg.getOrgType().getValue());
        }
        if (govOrg.getBranch() != null) {
            entity.addProperty("branch", govOrg.getBranch().getValue());
        }
        if (govOrg.getJurisdictionAreas() != null && !govOrg.getJurisdictionAreas().isEmpty()) {
            entity.addProperty("jurisdictionAreas", govOrg.getJurisdictionAreas());
        }
        if (govOrg.getMissionStatement() != null) {
            entity.addProperty("missionStatement", govOrg.getMissionStatement());
        }

        // Enrich Schema.org data
        if (govOrg.getSchemaOrgData() != null) {
            // Merge government org Schema.org data with entity's existing data
            Map<String, Object> enrichedSchemaOrg = new java.util.HashMap<>(entity.getSchemaOrgData());
            enrichedSchemaOrg.putAll(
                com.fasterxml.jackson.databind.ObjectMapper.class.cast(
                    govOrg.getSchemaOrgData()
                ).convertValue(govOrg.getSchemaOrgData(), Map.class)
            );
            entity.setSchemaOrgData(enrichedSchemaOrg);
        }

        log.debug("Entity enriched with government org data: name={}, acronym={}, website={}",
            govOrg.getOfficialName(),
            govOrg.getAcronym(),
            govOrg.getWebsiteUrl());
    }

    /**
     * Get entity by ID
     */
    @Transactional(readOnly = true)
    public EntityDTO getEntityById(UUID id) {
        log.debug("Fetching entity by id: {}", id);
        Entity entity = entityRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Entity", id));
        return toDTO(entity);
    }

    /**
     * Get all entities
     */
    @Transactional(readOnly = true)
    public List<EntityDTO> getAllEntities() {
        log.debug("Fetching all entities");
        return entityRepository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get entities by type
     */
    @Transactional(readOnly = true)
    public List<EntityDTO> getEntitiesByType(EntityType entityType) {
        log.debug("Fetching entities by type: {}", entityType);
        return entityRepository.findByEntityType(entityType).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get entities by Schema.org type
     */
    @Transactional(readOnly = true)
    public List<EntityDTO> getEntitiesBySchemaOrgType(String schemaOrgType) {
        log.debug("Fetching entities by Schema.org type: {}", schemaOrgType);
        return entityRepository.findBySchemaOrgType(schemaOrgType).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Search entities by name
     */
    @Transactional(readOnly = true)
    public List<EntityDTO> searchEntitiesByName(String nameFragment) {
        log.debug("Searching entities by name: {}", nameFragment);
        return entityRepository.findByNameContainingIgnoreCase(nameFragment).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Full-text search
     */
    @Transactional(readOnly = true)
    public List<EntityDTO> fullTextSearch(String searchText, int limit) {
        log.debug("Full-text search: text={}, limit={}", searchText, limit);
        return entityRepository.fullTextSearch(searchText, limit).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get recent entities
     */
    @Transactional(readOnly = true)
    public List<EntityDTO> getRecentEntities(int days) {
        LocalDateTime fromDate = LocalDateTime.now().minusDays(days);
        log.debug("Fetching entities created after: {}", fromDate);
        return entityRepository.findRecentEntities(fromDate).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Update entity
     */
    @Transactional
    public EntityDTO updateEntity(UUID id, CreateEntityRequest request) {
        log.info("Updating entity: id={}", id);

        Entity entity = entityRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Entity", id));

        // Update fields
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getEntityType() != null) {
            entity.setEntityType(request.getEntityType());
            // Regenerate Schema.org type
            entity.setSchemaOrgType(schemaOrgMapper.getSchemaOrgType(request.getEntityType()));
        }
        if (request.getProperties() != null) {
            entity.setProperties(request.getProperties());
        }
        if (request.getVerified() != null) {
            entity.setVerified(request.getVerified());
        }
        if (request.getConfidenceScore() != null) {
            entity.setConfidenceScore(request.getConfidenceScore());
        }
        if (request.getSource() != null) {
            entity.setSource(request.getSource());
        }

        // Regenerate Schema.org JSON-LD
        Map<String, Object> jsonLd = schemaOrgMapper.generateJsonLd(entity);
        entity.setSchemaOrgData(jsonLd);

        Entity updated = entityRepository.save(entity);
        log.info("Updated entity: id={}, name={}", updated.getId(), updated.getName());

        return toDTO(updated);
    }

    /**
     * Delete entity
     */
    @Transactional
    public void deleteEntity(UUID id) {
        log.info("Deleting entity: id={}", id);
        if (!entityRepository.existsById(id)) {
            throw new ResourceNotFoundException("Entity", id);
        }
        entityRepository.deleteById(id);
    }

    /**
     * Verify entity
     */
    @Transactional
    public EntityDTO verifyEntity(UUID id) {
        log.info("Verifying entity: id={}", id);
        Entity entity = entityRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Entity", id));

        entity.setVerified(true);
        Entity saved = entityRepository.save(entity);

        return toDTO(saved);
    }

    /**
     * Convert Entity to EntityDTO
     */
    private EntityDTO toDTO(Entity entity) {
        EntityDTO dto = new EntityDTO();
        dto.setId(entity.getId());
        dto.setEntityType(entity.getEntityType());
        dto.setName(entity.getName());
        dto.setProperties(entity.getProperties());
        dto.setSchemaOrgType(entity.getSchemaOrgType());
        dto.setSchemaOrgData(entity.getSchemaOrgData());
        dto.setSource(entity.getSource());
        dto.setConfidenceScore(entity.getConfidenceScore());
        dto.setVerified(entity.getVerified());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Include government organization link if present
        if (entity.getGovernmentOrganization() != null) {
            dto.setGovernmentOrganizationId(entity.getGovernmentOrganization().getId());
            dto.setGovernmentOrganizationName(entity.getGovernmentOrganization().getOfficialName());
        }

        return dto;
    }
}
