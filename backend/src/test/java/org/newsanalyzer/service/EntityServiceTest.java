package org.newsanalyzer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.newsanalyzer.dto.CreateEntityRequest;
import org.newsanalyzer.dto.EntityDTO;
import org.newsanalyzer.dto.ExtractedEntityData;
import org.newsanalyzer.model.Entity;
import org.newsanalyzer.model.EntityType;
import org.newsanalyzer.repository.EntityRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EntityService with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class EntityServiceTest {

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private SchemaOrgMapper schemaOrgMapper;

    @Mock
    private GovernmentOrganizationService governmentOrganizationService;

    @InjectMocks
    private EntityService entityService;

    private CreateEntityRequest createRequest;
    private Entity testEntity;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();

        // Setup test request
        createRequest = new CreateEntityRequest();
        createRequest.setEntityType(EntityType.PERSON);
        createRequest.setName("Elizabeth Warren");

        Map<String, Object> properties = new HashMap<>();
        properties.put("jobTitle", "Senator");
        createRequest.setProperties(properties);

        // Setup test entity
        testEntity = new Entity();
        testEntity.setId(testId);
        testEntity.setEntityType(EntityType.PERSON);
        testEntity.setName("Elizabeth Warren");
        testEntity.setProperties(properties);
        testEntity.setSchemaOrgType("Person");

        Map<String, Object> schemaOrgData = new HashMap<>();
        schemaOrgData.put("@type", "Person");
        schemaOrgData.put("name", "Elizabeth Warren");
        testEntity.setSchemaOrgData(schemaOrgData);

        testEntity.setConfidenceScore(1.0f);
        testEntity.setVerified(false);
        testEntity.setCreatedAt(LocalDateTime.now());
        testEntity.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testCreateEntity() {
        // Setup mocks
        when(schemaOrgMapper.getSchemaOrgType(EntityType.PERSON)).thenReturn("Person");
        when(schemaOrgMapper.generateJsonLd(any(Entity.class))).thenReturn(testEntity.getSchemaOrgData());
        when(entityRepository.save(any(Entity.class))).thenReturn(testEntity);

        // Execute
        EntityDTO result = entityService.createEntity(createRequest);

        // Verify
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals("Elizabeth Warren", result.getName());
        assertEquals(EntityType.PERSON, result.getEntityType());
        assertEquals("Person", result.getSchemaOrgType());
        assertNotNull(result.getSchemaOrgData());

        verify(schemaOrgMapper).getSchemaOrgType(EntityType.PERSON);
        verify(schemaOrgMapper).generateJsonLd(any(Entity.class));
        verify(entityRepository).save(any(Entity.class));
    }

    @Test
    void testCreateEntityWithProvidedSchemaOrgType() {
        createRequest.setSchemaOrgType("CustomType");

        Map<String, Object> customSchema = new HashMap<>();
        customSchema.put("@type", "CustomType");
        createRequest.setSchemaOrgData(customSchema);

        when(schemaOrgMapper.enrichSchemaOrgData(any(), any())).thenReturn(customSchema);
        when(entityRepository.save(any(Entity.class))).thenReturn(testEntity);

        EntityDTO result = entityService.createEntity(createRequest);

        assertNotNull(result);
        verify(schemaOrgMapper).enrichSchemaOrgData(any(), any());
        verify(schemaOrgMapper, never()).getSchemaOrgType(any());
        verify(schemaOrgMapper, never()).generateJsonLd(any());
    }

    @Test
    void testGetEntityById() {
        when(entityRepository.findById(testId)).thenReturn(Optional.of(testEntity));

        EntityDTO result = entityService.getEntityById(testId);

        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals("Elizabeth Warren", result.getName());

        verify(entityRepository).findById(testId);
    }

    @Test
    void testGetEntityByIdNotFound() {
        when(entityRepository.findById(testId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            entityService.getEntityById(testId);
        });

        verify(entityRepository).findById(testId);
    }

    @Test
    void testGetAllEntities() {
        List<Entity> entities = Arrays.asList(testEntity);
        when(entityRepository.findAll()).thenReturn(entities);

        List<EntityDTO> results = entityService.getAllEntities();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Elizabeth Warren", results.get(0).getName());

        verify(entityRepository).findAll();
    }

    @Test
    void testGetEntitiesByType() {
        List<Entity> entities = Arrays.asList(testEntity);
        when(entityRepository.findByEntityType(EntityType.PERSON)).thenReturn(entities);

        List<EntityDTO> results = entityService.getEntitiesByType(EntityType.PERSON);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(EntityType.PERSON, results.get(0).getEntityType());

        verify(entityRepository).findByEntityType(EntityType.PERSON);
    }

    @Test
    void testGetEntitiesBySchemaOrgType() {
        List<Entity> entities = Arrays.asList(testEntity);
        when(entityRepository.findBySchemaOrgType("Person")).thenReturn(entities);

        List<EntityDTO> results = entityService.getEntitiesBySchemaOrgType("Person");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Person", results.get(0).getSchemaOrgType());

        verify(entityRepository).findBySchemaOrgType("Person");
    }

    @Test
    void testSearchEntitiesByName() {
        List<Entity> entities = Arrays.asList(testEntity);
        when(entityRepository.findByNameContainingIgnoreCase("warren")).thenReturn(entities);

        List<EntityDTO> results = entityService.searchEntitiesByName("warren");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.get(0).getName().toLowerCase().contains("warren"));

        verify(entityRepository).findByNameContainingIgnoreCase("warren");
    }

    @Test
    void testFullTextSearch() {
        List<Entity> entities = Arrays.asList(testEntity);
        when(entityRepository.fullTextSearch("senator", 10)).thenReturn(entities);

        List<EntityDTO> results = entityService.fullTextSearch("senator", 10);

        assertNotNull(results);
        assertEquals(1, results.size());

        verify(entityRepository).fullTextSearch("senator", 10);
    }

    @Test
    void testUpdateEntity() {
        CreateEntityRequest updateRequest = new CreateEntityRequest();
        updateRequest.setName("Elizabeth Warren Updated");
        updateRequest.setVerified(true);

        Entity updatedEntity = new Entity();
        updatedEntity.setId(testId);
        updatedEntity.setName("Elizabeth Warren Updated");
        updatedEntity.setEntityType(EntityType.PERSON);
        updatedEntity.setVerified(true);
        updatedEntity.setSchemaOrgType("Person");
        updatedEntity.setSchemaOrgData(testEntity.getSchemaOrgData());

        when(entityRepository.findById(testId)).thenReturn(Optional.of(testEntity));
        when(schemaOrgMapper.generateJsonLd(any(Entity.class))).thenReturn(testEntity.getSchemaOrgData());
        when(entityRepository.save(any(Entity.class))).thenReturn(updatedEntity);

        EntityDTO result = entityService.updateEntity(testId, updateRequest);

        assertNotNull(result);
        assertEquals("Elizabeth Warren Updated", result.getName());
        assertTrue(result.getVerified());

        verify(entityRepository).findById(testId);
        verify(schemaOrgMapper).generateJsonLd(any(Entity.class));
        verify(entityRepository).save(any(Entity.class));
    }

    @Test
    void testUpdateEntityNotFound() {
        when(entityRepository.findById(testId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            entityService.updateEntity(testId, createRequest);
        });

        verify(entityRepository).findById(testId);
        verify(entityRepository, never()).save(any());
    }

    @Test
    void testDeleteEntity() {
        when(entityRepository.existsById(testId)).thenReturn(true);
        doNothing().when(entityRepository).deleteById(testId);

        entityService.deleteEntity(testId);

        verify(entityRepository).existsById(testId);
        verify(entityRepository).deleteById(testId);
    }

    @Test
    void testVerifyEntity() {
        Entity verifiedEntity = new Entity();
        verifiedEntity.setId(testId);
        verifiedEntity.setName("Elizabeth Warren");
        verifiedEntity.setEntityType(EntityType.PERSON);
        verifiedEntity.setVerified(true);
        verifiedEntity.setSchemaOrgType("Person");
        verifiedEntity.setSchemaOrgData(testEntity.getSchemaOrgData());

        when(entityRepository.findById(testId)).thenReturn(Optional.of(testEntity));
        when(entityRepository.save(any(Entity.class))).thenReturn(verifiedEntity);

        EntityDTO result = entityService.verifyEntity(testId);

        assertNotNull(result);
        assertTrue(result.getVerified());

        verify(entityRepository).findById(testId);
        verify(entityRepository).save(any(Entity.class));
    }

    @Test
    void testVerifyEntityNotFound() {
        when(entityRepository.findById(testId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            entityService.verifyEntity(testId);
        });

        verify(entityRepository).findById(testId);
        verify(entityRepository, never()).save(any());
    }

    @Test
    void testGetRecentEntities() {
        List<Entity> entities = Arrays.asList(testEntity);
        when(entityRepository.findRecentEntities(any(LocalDateTime.class))).thenReturn(entities);

        List<EntityDTO> results = entityService.getRecentEntities(7);

        assertNotNull(results);
        assertEquals(1, results.size());

        verify(entityRepository).findRecentEntities(any(LocalDateTime.class));
    }

    @Test
    void testCreateEntityWithDefaultValues() {
        CreateEntityRequest minimalRequest = new CreateEntityRequest();
        minimalRequest.setEntityType(EntityType.PERSON);
        minimalRequest.setName("Test Person");

        when(schemaOrgMapper.getSchemaOrgType(EntityType.PERSON)).thenReturn("Person");
        when(schemaOrgMapper.generateJsonLd(any(Entity.class))).thenReturn(new HashMap<>());
        when(entityRepository.save(any(Entity.class))).thenReturn(testEntity);

        EntityDTO result = entityService.createEntity(minimalRequest);

        assertNotNull(result);
        verify(entityRepository).save(argThat(entity ->
            entity.getConfidenceScore() == 1.0f &&
            !entity.getVerified() &&
            entity.getProperties() != null
        ));
    }

    // =====================================================================
    // Story ES-1.3: Extraction-to-Entity Tests
    // =====================================================================

    private ExtractedEntityData extractedEntity(String text, String entityType) {
        ExtractedEntityData extracted = new ExtractedEntityData();
        extracted.setText(text);
        extracted.setEntityType(entityType);
        extracted.setConfidence(0.85f);
        extracted.setSchemaOrgType("Person");
        return extracted;
    }

    @Test
    void testCreateEntityFromExtraction() {
        ExtractedEntityData extracted = extractedEntity("Elizabeth Warren", "person");
        when(entityRepository.save(any(Entity.class))).thenReturn(testEntity);

        Entity result = entityService.createEntityFromExtraction(extracted, testId);

        assertNotNull(result);
        verify(entityRepository).save(argThat(entity ->
            entity.getEntityType() == EntityType.PERSON &&
            entity.getName().equals("Elizabeth Warren") &&
            entity.getArticleId().equals(testId) &&
            !entity.getVerified()
        ));
    }

    @Test
    void testCreateEntityFromExtraction_unrecognizedEntityType_throws() {
        // The reasoning-service contract documents a fixed set of entity_type
        // values; an unrecognized one (e.g. a future contract addition this
        // side hasn't been updated for yet) must surface as a hard failure,
        // not silently map to a wrong/default EntityType.
        ExtractedEntityData extracted = extractedEntity("Something", "not_a_real_type");

        assertThrows(IllegalArgumentException.class, () ->
            entityService.createEntityFromExtraction(extracted, testId));

        verify(entityRepository, never()).save(any());
    }

    @Test
    void testCreateEntitiesFromExtraction_allValid_allPersisted() {
        ExtractedEntityData first = extractedEntity("Elizabeth Warren", "person");
        ExtractedEntityData second = extractedEntity("EPA", "government_org");
        when(entityRepository.save(any(Entity.class))).thenReturn(testEntity);

        List<Entity> results = entityService.createEntitiesFromExtraction(List.of(first, second), testId);

        assertEquals(2, results.size());
        verify(entityRepository, times(2)).save(any(Entity.class));
    }

    @Test
    void testCreateEntitiesFromExtraction_oneEntityFailsToMap_abortsBeforeLaterEntities() {
        // Story ES-1.3 QA finding: createEntityFromExtraction() looped
        // per-entity in ArticleService meant earlier entities in a batch
        // committed independently of a later failure. Routing the whole
        // batch through this one @Transactional method means Spring rolls
        // back everything on any exception — this unit test proves the
        // stream aborts at the failing entity (no calls past it); the real
        // rollback-of-already-flushed-rows guarantee is proven against a
        // real database in ArticleExtractionIntegrationTest, since Mockito
        // can't simulate an actual transaction rollback.
        ExtractedEntityData valid = extractedEntity("Elizabeth Warren", "person");
        ExtractedEntityData invalid = extractedEntity("Something", "not_a_real_type");
        when(entityRepository.save(any(Entity.class))).thenReturn(testEntity);

        assertThrows(IllegalArgumentException.class, () ->
            entityService.createEntitiesFromExtraction(List.of(valid, invalid), testId));

        // The valid entity (processed before the invalid one) was saved to
        // the mocked repository, but in production this call runs inside the
        // batch's single transaction, so a real database would roll it back
        // too — see ArticleExtractionIntegrationTest for that proof.
        verify(entityRepository, times(1)).save(any(Entity.class));
    }

    // =====================================================================
    // Phase 1.6: Entity-to-GovernmentOrganization Validation Tests
    // =====================================================================

    @Test
    void testCreateAndValidateEntity_GovernmentOrg_SuccessfulValidation() {
        // Given: A government org entity request for "EPA"
        CreateEntityRequest govOrgRequest = new CreateEntityRequest();
        govOrgRequest.setEntityType(EntityType.GOVERNMENT_ORG);
        govOrgRequest.setName("EPA");
        govOrgRequest.setSource("article:123");
        govOrgRequest.setConfidenceScore(0.92f);

        // Setup: Create entity (first save)
        Entity createdEntity = new Entity();
        createdEntity.setId(testId);
        createdEntity.setEntityType(EntityType.GOVERNMENT_ORG);
        createdEntity.setName("EPA");
        createdEntity.setSchemaOrgType("GovernmentOrganization");
        createdEntity.setSchemaOrgData(new HashMap<>());
        createdEntity.setVerified(false);
        createdEntity.setConfidenceScore(0.92f);
        createdEntity.setCreatedAt(LocalDateTime.now());
        createdEntity.setUpdatedAt(LocalDateTime.now());

        // Setup: Matched government organization
        org.newsanalyzer.model.GovernmentOrganization matchedGovOrg =
            new org.newsanalyzer.model.GovernmentOrganization();
        matchedGovOrg.setId(UUID.randomUUID());
        matchedGovOrg.setOfficialName("Environmental Protection Agency");
        matchedGovOrg.setAcronym("EPA");
        matchedGovOrg.setWebsiteUrl("https://www.epa.gov");
        matchedGovOrg.setOrgType(org.newsanalyzer.model.GovernmentOrganization.OrganizationType.INDEPENDENT_AGENCY);
        matchedGovOrg.setBranch(org.newsanalyzer.model.GovernmentOrganization.GovernmentBranch.EXECUTIVE);

        // Setup: Validation result
        GovernmentOrganizationService.EntityValidationResult validationResult =
            GovernmentOrganizationService.EntityValidationResult.valid(matchedGovOrg, 1.0, "acronym");

        // Setup: Enriched entity (second save)
        Entity enrichedEntity = new Entity();
        enrichedEntity.setId(testId);
        enrichedEntity.setEntityType(EntityType.GOVERNMENT_ORG);
        enrichedEntity.setName("Environmental Protection Agency"); // Standardized
        enrichedEntity.setGovernmentOrganization(matchedGovOrg);
        enrichedEntity.setVerified(true); // Verified after validation
        enrichedEntity.setConfidenceScore(1.0f); // Updated to validation confidence
        enrichedEntity.setSchemaOrgType("GovernmentOrganization");
        enrichedEntity.setSchemaOrgData(new HashMap<>());
        enrichedEntity.setCreatedAt(createdEntity.getCreatedAt());
        enrichedEntity.setUpdatedAt(LocalDateTime.now());

        // Mock behavior
        when(schemaOrgMapper.getSchemaOrgType(EntityType.GOVERNMENT_ORG)).thenReturn("GovernmentOrganization");
        when(schemaOrgMapper.generateJsonLd(any(Entity.class))).thenReturn(new HashMap<>());
        when(entityRepository.save(any(Entity.class)))
            .thenReturn(createdEntity) // First save
            .thenReturn(enrichedEntity); // Second save after enrichment
        when(entityRepository.findById(testId)).thenReturn(Optional.of(createdEntity));
        when(governmentOrganizationService.validateEntity("EPA", "government_org"))
            .thenReturn(validationResult);

        // When: Create and validate entity
        EntityDTO result = entityService.createAndValidateEntity(govOrgRequest);

        // Then: Entity is created, validated, and enriched
        assertNotNull(result);
        assertEquals("Environmental Protection Agency", result.getName()); // Name standardized
        assertTrue(result.getVerified()); // Marked as verified
        assertEquals(1.0f, result.getConfidenceScore()); // Confidence updated
        assertEquals(matchedGovOrg.getId(), result.getGovernmentOrganizationId()); // Linked
        assertEquals("Environmental Protection Agency", result.getGovernmentOrganizationName());

        // Verify interactions
        verify(entityRepository, times(2)).save(any(Entity.class)); // Saved twice (create + enrich)
        verify(governmentOrganizationService).validateEntity("EPA", "government_org");
        verify(entityRepository).findById(testId);
    }

    @Test
    void testCreateAndValidateEntity_GovernmentOrg_NoMatch() {
        // Given: A government org entity request for unknown org
        CreateEntityRequest govOrgRequest = new CreateEntityRequest();
        govOrgRequest.setEntityType(EntityType.GOVERNMENT_ORG);
        govOrgRequest.setName("Unknown Agency");
        govOrgRequest.setSource("article:456");
        govOrgRequest.setConfidenceScore(0.75f);

        Entity createdEntity = new Entity();
        createdEntity.setId(testId);
        createdEntity.setEntityType(EntityType.GOVERNMENT_ORG);
        createdEntity.setName("Unknown Agency");
        createdEntity.setSchemaOrgType("GovernmentOrganization");
        createdEntity.setSchemaOrgData(new HashMap<>());
        createdEntity.setVerified(false);
        createdEntity.setConfidenceScore(0.75f);
        createdEntity.setCreatedAt(LocalDateTime.now());
        createdEntity.setUpdatedAt(LocalDateTime.now());

        // Validation returns invalid (no match)
        GovernmentOrganizationService.EntityValidationResult validationResult =
            GovernmentOrganizationService.EntityValidationResult.invalid(
                Arrays.asList("Department of Defense", "Department of State")
            );

        when(schemaOrgMapper.getSchemaOrgType(EntityType.GOVERNMENT_ORG)).thenReturn("GovernmentOrganization");
        when(schemaOrgMapper.generateJsonLd(any(Entity.class))).thenReturn(new HashMap<>());
        when(entityRepository.save(any(Entity.class))).thenReturn(createdEntity);
        when(governmentOrganizationService.validateEntity("Unknown Agency", "government_org"))
            .thenReturn(validationResult);

        // When: Create and validate entity
        EntityDTO result = entityService.createAndValidateEntity(govOrgRequest);

        // Then: Entity created but NOT enriched
        assertNotNull(result);
        assertEquals("Unknown Agency", result.getName()); // Name unchanged
        assertFalse(result.getVerified()); // Not verified
        assertEquals(0.75f, result.getConfidenceScore()); // Confidence unchanged
        assertNull(result.getGovernmentOrganizationId()); // Not linked

        // Verify: Only saved once (no enrichment)
        verify(entityRepository, times(1)).save(any(Entity.class));
        verify(governmentOrganizationService).validateEntity("Unknown Agency", "government_org");
        verify(entityRepository, never()).findById(any()); // No fetch for enrichment
    }

    @Test
    void testCreateAndValidateEntity_NonGovernmentOrg_SkipsValidation() {
        // Given: A PERSON entity (not government org)
        CreateEntityRequest personRequest = new CreateEntityRequest();
        personRequest.setEntityType(EntityType.PERSON);
        personRequest.setName("John Doe");

        when(schemaOrgMapper.getSchemaOrgType(EntityType.PERSON)).thenReturn("Person");
        when(schemaOrgMapper.generateJsonLd(any(Entity.class))).thenReturn(new HashMap<>());
        when(entityRepository.save(any(Entity.class))).thenReturn(testEntity);

        // When: Create and validate entity
        EntityDTO result = entityService.createAndValidateEntity(personRequest);

        // Then: Entity created, but validation NOT attempted
        assertNotNull(result);
        verify(entityRepository, times(1)).save(any(Entity.class));
        verify(governmentOrganizationService, never()).validateEntity(any(), any()); // No validation
    }

    @Test
    void testValidateEntity_ExistingUnvalidatedGovernmentOrg_Success() {
        // Given: Existing unvalidated government org entity
        Entity existingEntity = new Entity();
        existingEntity.setId(testId);
        existingEntity.setEntityType(EntityType.GOVERNMENT_ORG);
        existingEntity.setName("FDA");
        existingEntity.setVerified(false);
        existingEntity.setGovernmentOrganization(null); // Not linked yet

        // Matched government organization
        org.newsanalyzer.model.GovernmentOrganization matchedGovOrg =
            new org.newsanalyzer.model.GovernmentOrganization();
        matchedGovOrg.setId(UUID.randomUUID());
        matchedGovOrg.setOfficialName("Food and Drug Administration");
        matchedGovOrg.setAcronym("FDA");
        matchedGovOrg.setWebsiteUrl("https://www.fda.gov");

        GovernmentOrganizationService.EntityValidationResult validationResult =
            GovernmentOrganizationService.EntityValidationResult.valid(matchedGovOrg, 1.0, "acronym");

        // Enriched entity after validation
        Entity enrichedEntity = new Entity();
        enrichedEntity.setId(testId);
        enrichedEntity.setEntityType(EntityType.GOVERNMENT_ORG);
        enrichedEntity.setName("Food and Drug Administration");
        enrichedEntity.setGovernmentOrganization(matchedGovOrg);
        enrichedEntity.setVerified(true);

        when(entityRepository.findById(testId)).thenReturn(Optional.of(existingEntity));
        when(governmentOrganizationService.validateEntity("FDA", "government_org"))
            .thenReturn(validationResult);
        when(entityRepository.save(any(Entity.class))).thenReturn(enrichedEntity);

        // When: Validate existing entity
        EntityDTO result = entityService.validateEntity(testId);

        // Then: Entity validated and enriched
        assertNotNull(result);
        assertTrue(result.getVerified());
        assertEquals(matchedGovOrg.getId(), result.getGovernmentOrganizationId());

        verify(entityRepository).findById(testId);
        verify(governmentOrganizationService).validateEntity("FDA", "government_org");
        verify(entityRepository).save(any(Entity.class));
    }

    @Test
    void testValidateEntity_AlreadyValidated_SkipsRevalidation() {
        // Given: Entity already linked and verified
        org.newsanalyzer.model.GovernmentOrganization govOrg =
            new org.newsanalyzer.model.GovernmentOrganization();
        govOrg.setId(UUID.randomUUID());
        govOrg.setOfficialName("Environmental Protection Agency");

        Entity validatedEntity = new Entity();
        validatedEntity.setId(testId);
        validatedEntity.setEntityType(EntityType.GOVERNMENT_ORG);
        validatedEntity.setName("Environmental Protection Agency");
        validatedEntity.setGovernmentOrganization(govOrg);
        validatedEntity.setVerified(true); // Already verified

        when(entityRepository.findById(testId)).thenReturn(Optional.of(validatedEntity));

        // When: Validate entity
        EntityDTO result = entityService.validateEntity(testId);

        // Then: Returns entity without re-validation
        assertNotNull(result);
        verify(entityRepository).findById(testId);
        verify(governmentOrganizationService, never()).validateEntity(any(), any()); // No re-validation
        verify(entityRepository, never()).save(any()); // No save
    }

    @Test
    void testValidateEntity_NonGovernmentOrgType_SkipsValidation() {
        // Given: PERSON entity (not government org)
        Entity personEntity = new Entity();
        personEntity.setId(testId);
        personEntity.setEntityType(EntityType.PERSON);
        personEntity.setName("Jane Doe");

        when(entityRepository.findById(testId)).thenReturn(Optional.of(personEntity));

        // When: Validate entity
        EntityDTO result = entityService.validateEntity(testId);

        // Then: Returns entity without validation
        assertNotNull(result);
        verify(entityRepository).findById(testId);
        verify(governmentOrganizationService, never()).validateEntity(any(), any());
        verify(entityRepository, never()).save(any());
    }

    @Test
    void testValidateEntity_EntityNotFound() {
        // Given: Non-existent entity ID
        when(entityRepository.findById(testId)).thenReturn(Optional.empty());

        // When/Then: Throws exception
        assertThrows(RuntimeException.class, () -> {
            entityService.validateEntity(testId);
        });

        verify(entityRepository).findById(testId);
        verify(governmentOrganizationService, never()).validateEntity(any(), any());
    }

    @Test
    void testValidateEntity_ValidationFails() {
        // Given: Entity that doesn't match any gov org
        Entity entity = new Entity();
        entity.setId(testId);
        entity.setEntityType(EntityType.GOVERNMENT_ORG);
        entity.setName("Typo Agency");
        entity.setVerified(false);

        GovernmentOrganizationService.EntityValidationResult validationResult =
            GovernmentOrganizationService.EntityValidationResult.invalid(Collections.emptyList());

        when(entityRepository.findById(testId)).thenReturn(Optional.of(entity));
        when(governmentOrganizationService.validateEntity("Typo Agency", "government_org"))
            .thenReturn(validationResult);

        // When: Validate entity
        EntityDTO result = entityService.validateEntity(testId);

        // Then: Returns entity unchanged
        assertNotNull(result);
        assertFalse(result.getVerified());
        assertNull(result.getGovernmentOrganizationId());

        verify(governmentOrganizationService).validateEntity("Typo Agency", "government_org");
        verify(entityRepository, never()).save(any()); // No save if validation fails
    }
}
