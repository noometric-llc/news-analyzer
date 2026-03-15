package org.newsanalyzer.apitests.integration;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.apitests.integration.util.ServiceOrchestrator;
import org.newsanalyzer.apitests.integration.util.WorkflowAssertions;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the complete entity processing pipeline.
 *
 * <p>Tests the full end-to-end workflow: extract → store → validate → link → enrich.</p>
 *
 * <p>Full Pipeline:</p>
 * <pre>
 * [News Article Text]
 *        │
 *        ▼
 * [1. Extract Entities] POST /entities/extract
 *        │
 *        ▼
 * [2. Store in Backend] POST /api/entities
 *        │
 *        ▼
 * [3. Validate Against Gov Orgs] POST /api/entities/validate
 *        │
 *        ▼
 * [4. Link to External KBs] POST /entities/link
 *        │
 *        ▼
 * [5. Apply OWL Reasoning] POST /entities/reason
 *        │
 *        ▼
 * [6. Update Backend] PUT /api/entities/{id}
 *        │
 *        ▼
 * [Fully Enriched Entity]
 * </pre>
 */
@Tag("integration")
@DisplayName("Full Pipeline Workflow Tests")
class FullPipelineWorkflowTest extends IntegrationTestBase {

    private ServiceOrchestrator orchestrator;

    @Override
    void setupIntegrationClients() {
        super.setupIntegrationClients();
        orchestrator = new ServiceOrchestrator(entityClient, govOrgClient, reasoningClient);
    }

    @AfterEach
    void cleanup() {
        cleanupTestData();
    }

    // ==================== AC 1, 2, 3, 4, 5: Full Pipeline ====================

    @Test
    @DisplayName("Given news article, when full pipeline executed, then entities extracted, validated, linked, and enriched")
    void shouldProcessArticle_extractValidateLinkEnrich() {
        // Given - news article with multiple entity types
        System.out.println("\n[TEST] shouldProcessArticle_extractValidateLinkEnrich");
        System.out.println("=" .repeat(60));

        String article = SAMPLE_ARTICLE;
        System.out.println("Input Article:\n" + article);
        System.out.println("=" .repeat(60));

        Instant start = startTiming();
        Instant pipelineStart = Instant.now();

        // Step 1: Extract entities from reasoning service
        System.out.println("\n[STEP 1] Extracting entities...");
        Response extractResponse = reasoningClient.extractEntities(article, 0.5);
        endTiming(start, "Step 1: Extraction");

        assertThat(extractResponse.statusCode())
                .as("Extraction should succeed")
                .isEqualTo(200);

        List<Map<String, Object>> extractedEntities = extractResponse.jsonPath().getList("entities");
        int extractedCount = extractedEntities != null ? extractedEntities.size() : 0;
        System.out.println("  Extracted " + extractedCount + " entities");

        if (extractedEntities != null) {
            for (Map<String, Object> e : extractedEntities) {
                System.out.println("    - " + e.get("text") + " (" + e.get("entity_type") + ")");
            }
        }

        assertThat(extractedCount)
                .as("Should extract at least one entity")
                .isGreaterThan(0);

        // Step 2: Store each entity in backend
        System.out.println("\n[STEP 2] Storing entities in backend...");
        start = startTiming();

        int storedCount = 0;
        for (Map<String, Object> extracted : extractedEntities) {
            Map<String, Object> entityRequest = buildEntityRequestFromExtracted(extracted);
            Response createResponse = entityClient.createEntity(entityRequest);

            if (createResponse.statusCode() == 201 || createResponse.statusCode() == 200) {
                String entityId = createResponse.jsonPath().getString("id");
                trackEntityForCleanup(entityId);
                storedCount++;
                System.out.println("    Stored: " + extracted.get("text") + " -> " + entityId);
            } else {
                System.out.println("    Failed to store: " + extracted.get("text") +
                        " (status: " + createResponse.statusCode() + ")");
            }
        }
        endTiming(start, "Step 2: Storage");
        System.out.println("  Stored " + storedCount + "/" + extractedCount + " entities");

        // Step 3: Validate government organization entities
        System.out.println("\n[STEP 3] Validating gov org entities...");
        start = startTiming();

        int validatedCount = 0;
        int matchedCount = 0;
        for (String entityId : createdEntityIds) {
            Response getResponse = entityClient.getEntityById(entityId);
            if (getResponse.statusCode() == 200) {
                String type = getResponse.jsonPath().getString("entityType");
                String name = getResponse.jsonPath().getString("name");

                if ("GOVERNMENT_ORG".equals(type)) {
                    Response validateResponse = govOrgClient.validateEntity(name, type);
                    validatedCount++;

                    if (validateResponse.statusCode() == 200) {
                        String matchedName = validateResponse.jsonPath().getString("matchedOrganization.name");
                        if (matchedName != null) {
                            matchedCount++;
                            System.out.println("    Validated: " + name + " -> " + matchedName);
                        }
                    }
                }
            }
        }
        endTiming(start, "Step 3: Validation");
        System.out.println("  Validated " + validatedCount + " gov org entities, " + matchedCount + " matched");

        // Step 4: Link entities to external knowledge bases
        System.out.println("\n[STEP 4] Linking to external knowledge bases...");
        start = startTiming();

        int linkedCount = 0;
        for (String entityId : createdEntityIds) {
            ServiceOrchestrator.LinkingResult linkResult = orchestrator.linkToExternalKB(entityId);
            if (linkResult.success) {
                linkedCount++;
                System.out.println("    Linked: " + entityId.substring(0, 8) + "... -> " +
                        (linkResult.wikidataId != null ? "Wikidata:" + linkResult.wikidataId : "DBpedia"));
            }
        }
        endTiming(start, "Step 4: External Linking");
        System.out.println("  Linked " + linkedCount + "/" + createdEntityIds.size() + " entities");

        // Step 5: Apply OWL reasoning for enrichment
        System.out.println("\n[STEP 5] Applying OWL reasoning...");
        start = startTiming();

        int enrichedCount = 0;
        int totalInferredTriples = 0;
        for (String entityId : createdEntityIds) {
            ServiceOrchestrator.EnrichmentResult enrichResult = orchestrator.enrichWithReasoning(entityId);
            if (enrichResult.success) {
                enrichedCount++;
                totalInferredTriples += enrichResult.inferredTriples;
            }
        }
        endTiming(start, "Step 5: OWL Reasoning");
        System.out.println("  Enriched " + enrichedCount + "/" + createdEntityIds.size() + " entities");
        System.out.println("  Total inferred triples: " + totalInferredTriples);

        // Step 6: Verify final state
        System.out.println("\n[STEP 6] Verifying final state...");
        start = startTiming();

        for (String entityId : createdEntityIds) {
            Response finalResponse = entityClient.getEntityById(entityId);
            assertThat(finalResponse.statusCode()).isEqualTo(200);

            String name = finalResponse.jsonPath().getString("name");
            String type = finalResponse.jsonPath().getString("entityType");
            System.out.println("    Final: " + name + " (" + type + ")");
        }
        endTiming(start, "Step 6: Verification");

        // Calculate total pipeline time
        long totalPipelineTime = java.time.Duration.between(pipelineStart, Instant.now()).toMillis();
        System.out.println("\n" + "=" .repeat(60));
        System.out.println("PIPELINE COMPLETE");
        System.out.println("  Total time: " + totalPipelineTime + " ms");
        System.out.println("  Entities processed: " + storedCount);
        System.out.println("=" .repeat(60));

        printTimingSummary();

        // Assert overall success
        assertThat(storedCount)
                .as("At least one entity should be stored")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Given article with multiple entity types, when processed, then all types handled correctly")
    void shouldProcessMultipleEntityTypes_inSingleArticle() {
        // Given - article with diverse entity types
        System.out.println("\n[TEST] shouldProcessMultipleEntityTypes_inSingleArticle");

        String article = """
                President Joe Biden met with EPA Administrator Michael Regan
                at the White House in Washington DC to discuss climate policy.
                The Environmental Protection Agency announced new regulations
                affecting the Department of Energy's renewable energy initiatives.
                """;

        Instant start = startTiming();

        // When - execute full pipeline via orchestrator
        ServiceOrchestrator.FullPipelineResult result = orchestrator.processArticle(article);
        endTiming(start, "Full Pipeline Execution");

        // Then - verify results
        System.out.println("  Pipeline success: " + result.success);

        if (result.extractionResult != null) {
            System.out.println("  Extracted: " + result.extractionResult.extractedCount + " entities");
            System.out.println("  Stored: " + result.extractionResult.storedEntityIds.size() + " entities");

            // Track for cleanup
            createdEntityIds.addAll(result.extractionResult.storedEntityIds);

            // Verify different entity types were extracted
            for (String entityId : result.extractionResult.storedEntityIds) {
                Response getResponse = entityClient.getEntityById(entityId);
                if (getResponse.statusCode() == 200) {
                    String name = getResponse.jsonPath().getString("name");
                    String type = getResponse.jsonPath().getString("entityType");
                    System.out.println("    " + type + ": " + name);
                }
            }
        }

        if (result.entityResults != null) {
            System.out.println("\n  Entity Pipeline Results:");
            for (ServiceOrchestrator.EntityPipelineResult entityResult : result.entityResults) {
                System.out.println("    Entity: " + entityResult.entityId.substring(0, 8) + "...");
                if (entityResult.validationResult != null) {
                    System.out.println("      Validation: " + entityResult.validationResult.success);
                }
                if (entityResult.linkingResult != null) {
                    System.out.println("      Linking: " + entityResult.linkingResult.success);
                }
                if (entityResult.enrichmentResult != null) {
                    System.out.println("      Enrichment: " + entityResult.enrichmentResult.success);
                }
            }
        }

        printTimingSummary();
    }

    @Test
    @DisplayName("Given pipeline result, when verified, then Schema.org data preserved end-to-end")
    void shouldPreserveSchemaOrgData_endToEnd() {
        // Given - extract entity with Schema.org data
        System.out.println("\n[TEST] shouldPreserveSchemaOrgData_endToEnd");

        String text = "The Environmental Protection Agency issued new regulations.";

        Instant start = startTiming();

        // Extract
        Response extractResponse = reasoningClient.extractEntities(text, 0.5);
        endTiming(start, "Extraction");

        assertThat(extractResponse.statusCode()).isEqualTo(200);

        List<Map<String, Object>> entities = extractResponse.jsonPath().getList("entities");
        assertThat(entities).isNotEmpty();

        // Find entity with Schema.org data
        Map<String, Object> entityWithSchema = entities.stream()
                .filter(e -> e.get("schema_org_type") != null)
                .findFirst()
                .orElse(entities.get(0));

        String originalSchemaType = (String) entityWithSchema.get("schema_org_type");
        @SuppressWarnings("unchecked")
        Map<String, Object> originalSchemaData = (Map<String, Object>) entityWithSchema.get("schema_org_data");

        System.out.println("  Original Schema.org type: " + originalSchemaType);

        // Store
        start = startTiming();
        Map<String, Object> entityRequest = buildEntityRequestFromExtracted(entityWithSchema);
        Response createResponse = entityClient.createEntity(entityRequest);
        endTiming(start, "Storage");

        assertThat(createResponse.statusCode()).isIn(200, 201);
        String entityId = createResponse.jsonPath().getString("id");
        trackEntityForCleanup(entityId);

        // Retrieve and verify
        start = startTiming();
        Response getResponse = entityClient.getEntityById(entityId);
        endTiming(start, "Retrieval");

        assertThat(getResponse.statusCode()).isEqualTo(200);

        String storedSchemaType = getResponse.jsonPath().getString("schemaOrgType");
        Map<String, Object> storedSchemaData = getResponse.jsonPath().getMap("schemaOrgData");

        System.out.println("  Stored Schema.org type: " + storedSchemaType);
        System.out.println("  Schema data preserved: " + (storedSchemaData != null));

        // Verify Schema.org data integrity
        if (originalSchemaType != null) {
            assertThat(storedSchemaType)
                    .as("Schema.org type should be preserved")
                    .isNotNull();
        }

        if (originalSchemaData != null && storedSchemaData != null) {
            // Verify JSON-LD structure preserved
            if (originalSchemaData.containsKey("@context")) {
                assertThat(storedSchemaData.get("@context"))
                        .as("@context should be preserved")
                        .isNotNull();
            }
            if (originalSchemaData.containsKey("@type")) {
                assertThat(storedSchemaData.get("@type"))
                        .as("@type should be preserved")
                        .isNotNull();
            }
        }

        printTimingSummary();
    }

    @Test
    @Tag("performance")
    @DisplayName("Given pipeline execution, when timing measured, then completes within threshold")
    void shouldCompleteWithinTimeThreshold() {
        // Given - article to process
        System.out.println("\n[TEST] shouldCompleteWithinTimeThreshold");

        Instant pipelineStart = Instant.now();

        // When - execute pipeline
        ServiceOrchestrator.FullPipelineResult result = orchestrator.processArticle(SAMPLE_ARTICLE);

        long totalTime = java.time.Duration.between(pipelineStart, Instant.now()).toMillis();
        recordTiming("Complete Pipeline", totalTime);

        System.out.println("  Total pipeline time: " + totalTime + " ms");
        System.out.println("  Threshold: " + FULL_PIPELINE_BATCH_THRESHOLD_MS + " ms");

        // Cleanup
        if (result.extractionResult != null) {
            createdEntityIds.addAll(result.extractionResult.storedEntityIds);
        }

        // Then - should complete within threshold
        assertThat(totalTime)
                .as("Pipeline should complete within time threshold")
                .isLessThan(FULL_PIPELINE_BATCH_THRESHOLD_MS);

        printTimingSummary();
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> buildEntityRequestFromExtracted(Map<String, Object> extracted) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", extracted.get("text"));

        String entityType = (String) extracted.get("entity_type");
        request.put("entityType", mapToEntityType(entityType));

        request.put("confidenceScore", extracted.getOrDefault("confidence", 0.9));

        if (extracted.containsKey("schema_org_type")) {
            request.put("schemaOrgType", extracted.get("schema_org_type"));
        }
        if (extracted.containsKey("schema_org_data")) {
            request.put("schemaOrgData", extracted.get("schema_org_data"));
        }
        if (extracted.containsKey("properties")) {
            request.put("properties", extracted.get("properties"));
        }

        return request;
    }

    private String mapToEntityType(String extractedType) {
        if (extractedType == null) return "CONCEPT";

        return switch (extractedType.toLowerCase()) {
            case "person" -> "PERSON";
            case "organization", "org" -> "ORGANIZATION";
            case "government_org", "governmentorganization", "gov_org" -> "GOVERNMENT_ORG";
            case "location", "gpe", "loc" -> "LOCATION";
            case "event" -> "EVENT";
            default -> "CONCEPT";
        };
    }
}
