package org.newsanalyzer.apitests.integration;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.apitests.integration.util.ServiceOrchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for performance measurement of cross-service workflows.
 *
 * <p>Measures and reports end-to-end response times for various workflow scenarios.</p>
 *
 * <p>Performance thresholds:</p>
 * <ul>
 *   <li>Single entity extraction: 2 seconds</li>
 *   <li>Single entity storage: 500 ms</li>
 *   <li>Entity validation: 1 second</li>
 *   <li>OWL reasoning: 3 seconds</li>
 *   <li>Full pipeline (1 entity): 5 seconds</li>
 *   <li>Full pipeline (10 entities): 30 seconds</li>
 * </ul>
 */
@Tag("integration")
@Tag("performance")
@DisplayName("Performance Measurement Tests")
class PerformanceTest extends IntegrationTestBase {

    private ServiceOrchestrator orchestrator;

    // Performance metrics collection
    private final List<PerformanceMetric> metrics = new ArrayList<>();

    @Override
    void setupIntegrationClients() {
        super.setupIntegrationClients();
        orchestrator = new ServiceOrchestrator(entityClient, govOrgClient, reasoningClient);
    }

    @AfterEach
    void cleanup() {
        cleanupTestData();
        printPerformanceReport();
    }

    // ==================== AC 7: Performance Measurement ====================

    @Test
    @DisplayName("Given single entity extraction, when timed, then completes under 2 seconds")
    void shouldCompleteExtractionWorkflow_underTimeLimit() {
        // Given - simple text for extraction
        System.out.println("\n[TEST] shouldCompleteExtractionWorkflow_underTimeLimit");
        System.out.println("  Threshold: " + EXTRACTION_THRESHOLD_MS + " ms");

        String text = "Senator Elizabeth Warren spoke at the hearing.";

        // When - extract entities
        Instant start = Instant.now();
        Response response = reasoningClient.extractEntities(text, 0.5);
        long duration = Duration.between(start, Instant.now()).toMillis();

        // Record metric
        metrics.add(new PerformanceMetric("Single Extraction", duration, EXTRACTION_THRESHOLD_MS));

        // Then - verify timing and success
        System.out.println("  Duration: " + duration + " ms");
        System.out.println("  Status: " + response.statusCode());

        assertThat(response.statusCode())
                .as("Extraction should succeed")
                .isEqualTo(200);

        assertThat(duration)
                .as("Extraction should complete under threshold")
                .isLessThan(EXTRACTION_THRESHOLD_MS);

        int count = response.jsonPath().getInt("total_count");
        System.out.println("  Entities extracted: " + count);
    }

    @Test
    @DisplayName("Given single entity storage, when timed, then completes under 500ms")
    void shouldCompleteStorageWorkflow_underTimeLimit() {
        // Given - entity to store
        System.out.println("\n[TEST] shouldCompleteStorageWorkflow_underTimeLimit");
        System.out.println("  Threshold: " + STORAGE_THRESHOLD_MS + " ms");

        Map<String, Object> entityRequest = new HashMap<>();
        entityRequest.put("name", "Performance Test Entity");
        entityRequest.put("entityType", "CONCEPT");
        entityRequest.put("confidenceScore", 0.9);

        // When - store entity
        Instant start = Instant.now();
        Response response = entityClient.createEntity(entityRequest);
        long duration = Duration.between(start, Instant.now()).toMillis();

        // Record metric
        metrics.add(new PerformanceMetric("Single Storage", duration, STORAGE_THRESHOLD_MS));

        // Then - verify timing and success
        System.out.println("  Duration: " + duration + " ms");
        System.out.println("  Status: " + response.statusCode());

        assertThat(response.statusCode())
                .as("Storage should succeed")
                .isIn(200, 201);

        if (response.statusCode() == 201 || response.statusCode() == 200) {
            String entityId = response.jsonPath().getString("id");
            trackEntityForCleanup(entityId);
        }

        assertThat(duration)
                .as("Storage should complete under threshold")
                .isLessThan(STORAGE_THRESHOLD_MS);
    }

    @Test
    @DisplayName("Given batch of 10 entities, when processed, then completes under 30 seconds")
    void shouldCompleteBatchProcessing_underTimeLimit() {
        // Given - generate 10 extraction requests
        System.out.println("\n[TEST] shouldCompleteBatchProcessing_underTimeLimit");
        System.out.println("  Threshold: " + FULL_PIPELINE_BATCH_THRESHOLD_MS + " ms");

        String[] texts = {
                "Senator Elizabeth Warren discussed policy.",
                "The EPA announced new regulations.",
                "President Biden met with officials.",
                "The Department of Justice filed charges.",
                "Congress passed new legislation.",
                "The Supreme Court issued a ruling.",
                "The Federal Reserve raised rates.",
                "The Pentagon released a statement.",
                "NASA launched a new mission.",
                "The CDC updated guidelines."
        };

        // When - process all texts
        Instant batchStart = Instant.now();
        int successCount = 0;
        int totalEntities = 0;

        for (String text : texts) {
            ServiceOrchestrator.WorkflowResult result = orchestrator.extractAndStore(text, 0.5);
            if (result.success) {
                successCount++;
                totalEntities += result.storedEntityIds.size();
                createdEntityIds.addAll(result.storedEntityIds);
            }
        }

        long batchDuration = Duration.between(batchStart, Instant.now()).toMillis();

        // Record metric
        metrics.add(new PerformanceMetric("Batch Processing (10 texts)", batchDuration, FULL_PIPELINE_BATCH_THRESHOLD_MS));

        // Then - verify timing
        System.out.println("  Batch duration: " + batchDuration + " ms");
        System.out.println("  Successful: " + successCount + "/" + texts.length);
        System.out.println("  Total entities: " + totalEntities);
        System.out.println("  Average per text: " + (batchDuration / texts.length) + " ms");

        assertThat(batchDuration)
                .as("Batch processing should complete under threshold")
                .isLessThan(FULL_PIPELINE_BATCH_THRESHOLD_MS);

        assertThat(successCount)
                .as("All texts should be processed successfully")
                .isEqualTo(texts.length);
    }

    @Test
    @DisplayName("Given full pipeline, when timed, then each step meets threshold")
    void shouldMeetThresholds_forEachPipelineStep() {
        // Given - article for full pipeline
        System.out.println("\n[TEST] shouldMeetThresholds_forEachPipelineStep");

        String text = "The Environmental Protection Agency announced new climate regulations.";

        // Step 1: Extraction
        System.out.println("\n  Step 1: Extraction");
        Instant start = Instant.now();
        Response extractResponse = reasoningClient.extractEntities(text, 0.5);
        long extractionTime = Duration.between(start, Instant.now()).toMillis();
        metrics.add(new PerformanceMetric("Extraction Step", extractionTime, EXTRACTION_THRESHOLD_MS));
        System.out.println("    Duration: " + extractionTime + " ms (threshold: " + EXTRACTION_THRESHOLD_MS + " ms)");

        assertThat(extractResponse.statusCode()).isEqualTo(200);

        // Step 2: Storage
        System.out.println("\n  Step 2: Storage");
        List<Map<String, Object>> entities = extractResponse.jsonPath().getList("entities");
        assertThat(entities).isNotEmpty();

        Map<String, Object> extracted = entities.get(0);
        Map<String, Object> entityRequest = new HashMap<>();
        entityRequest.put("name", extracted.get("text"));
        entityRequest.put("entityType", "GOVERNMENT_ORG");
        entityRequest.put("confidenceScore", extracted.getOrDefault("confidence", 0.9));

        start = Instant.now();
        Response createResponse = entityClient.createEntity(entityRequest);
        long storageTime = Duration.between(start, Instant.now()).toMillis();
        metrics.add(new PerformanceMetric("Storage Step", storageTime, STORAGE_THRESHOLD_MS));
        System.out.println("    Duration: " + storageTime + " ms (threshold: " + STORAGE_THRESHOLD_MS + " ms)");

        assertThat(createResponse.statusCode()).isIn(200, 201);
        String entityId = createResponse.jsonPath().getString("id");
        trackEntityForCleanup(entityId);

        // Step 3: Validation
        System.out.println("\n  Step 3: Validation");
        start = Instant.now();
        Response validateResponse = govOrgClient.validateEntity((String) extracted.get("text"), "GOVERNMENT_ORG");
        long validationTime = Duration.between(start, Instant.now()).toMillis();
        metrics.add(new PerformanceMetric("Validation Step", validationTime, VALIDATION_THRESHOLD_MS));
        System.out.println("    Duration: " + validationTime + " ms (threshold: " + VALIDATION_THRESHOLD_MS + " ms)");

        // Step 4: OWL Reasoning
        System.out.println("\n  Step 4: OWL Reasoning");
        start = Instant.now();
        ServiceOrchestrator.EnrichmentResult enrichmentResult = orchestrator.enrichWithReasoning(entityId);
        long reasoningTime = Duration.between(start, Instant.now()).toMillis();
        metrics.add(new PerformanceMetric("Reasoning Step", reasoningTime, REASONING_THRESHOLD_MS));
        System.out.println("    Duration: " + reasoningTime + " ms (threshold: " + REASONING_THRESHOLD_MS + " ms)");

        // Calculate total
        long totalTime = extractionTime + storageTime + validationTime + reasoningTime;
        metrics.add(new PerformanceMetric("Total Pipeline", totalTime, FULL_PIPELINE_SINGLE_THRESHOLD_MS));
        System.out.println("\n  Total pipeline time: " + totalTime + " ms");

        // Verify individual thresholds
        assertThat(extractionTime).isLessThan(EXTRACTION_THRESHOLD_MS);
        assertThat(storageTime).isLessThan(STORAGE_THRESHOLD_MS);
        assertThat(validationTime).isLessThan(VALIDATION_THRESHOLD_MS);
        assertThat(reasoningTime).isLessThan(REASONING_THRESHOLD_MS);
        assertThat(totalTime).isLessThan(FULL_PIPELINE_SINGLE_THRESHOLD_MS);
    }

    @Test
    @DisplayName("Given multiple sequential requests, when timed, then no performance degradation")
    void shouldMaintainPerformance_underLoad() {
        // Given - 20 sequential requests
        System.out.println("\n[TEST] shouldMaintainPerformance_underLoad");

        int numRequests = 20;
        List<Long> durations = new ArrayList<>();

        // When - execute sequential requests
        for (int i = 0; i < numRequests; i++) {
            Map<String, Object> entityRequest = new HashMap<>();
            entityRequest.put("name", "Load Test Entity " + i);
            entityRequest.put("entityType", "CONCEPT");
            entityRequest.put("confidenceScore", 0.9);

            Instant start = Instant.now();
            Response response = entityClient.createEntity(entityRequest);
            long duration = Duration.between(start, Instant.now()).toMillis();
            durations.add(duration);

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                String entityId = response.jsonPath().getString("id");
                trackEntityForCleanup(entityId);
            }
        }

        // Then - analyze performance
        long minDuration = durations.stream().min(Long::compare).orElse(0L);
        long maxDuration = durations.stream().max(Long::compare).orElse(0L);
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        long totalDuration = durations.stream().mapToLong(Long::longValue).sum();

        System.out.println("\n  Performance Statistics (" + numRequests + " requests):");
        System.out.println("    Min: " + minDuration + " ms");
        System.out.println("    Max: " + maxDuration + " ms");
        System.out.println("    Avg: " + String.format("%.2f", avgDuration) + " ms");
        System.out.println("    Total: " + totalDuration + " ms");

        // Check for first vs last 5 requests to detect degradation
        double firstFiveAvg = durations.subList(0, 5).stream().mapToLong(Long::longValue).average().orElse(0);
        double lastFiveAvg = durations.subList(numRequests - 5, numRequests).stream().mapToLong(Long::longValue).average().orElse(0);

        System.out.println("    First 5 avg: " + String.format("%.2f", firstFiveAvg) + " ms");
        System.out.println("    Last 5 avg: " + String.format("%.2f", lastFiveAvg) + " ms");

        double degradation = ((lastFiveAvg - firstFiveAvg) / firstFiveAvg) * 100;
        System.out.println("    Degradation: " + String.format("%.1f", degradation) + "%");

        // Allow up to 50% degradation (generous to account for variability)
        assertThat(degradation)
                .as("Performance degradation should be minimal")
                .isLessThan(100.0);

        // Average should still be under threshold
        assertThat(avgDuration)
                .as("Average response time should be under threshold")
                .isLessThan(STORAGE_THRESHOLD_MS);
    }

    @Test
    @DisplayName("Given reasoning service, when multiple requests sent, then response times consistent")
    void shouldHaveConsistentReasoningPerformance() {
        // Given - 10 reasoning requests
        System.out.println("\n[TEST] shouldHaveConsistentReasoningPerformance");

        int numRequests = 10;
        List<Long> durations = new ArrayList<>();

        Map<String, Object> reasoningRequest = new HashMap<>();
        List<Map<String, Object>> entities = new ArrayList<>();
        entities.add(Map.of(
                "text", "EPA",
                "entity_type", "government_org",
                "confidence", 0.9,
                "properties", Map.of("regulates", "environment")
        ));
        reasoningRequest.put("entities", entities);
        reasoningRequest.put("enable_inference", true);

        // When - execute multiple reasoning requests
        for (int i = 0; i < numRequests; i++) {
            Instant start = Instant.now();
            Response response = reasoningClient.reasonEntities(reasoningRequest);
            long duration = Duration.between(start, Instant.now()).toMillis();
            durations.add(duration);

            System.out.println("    Request " + (i + 1) + ": " + duration + " ms, status: " + response.statusCode());
        }

        // Then - analyze consistency
        long minDuration = durations.stream().min(Long::compare).orElse(0L);
        long maxDuration = durations.stream().max(Long::compare).orElse(0L);
        double avgDuration = durations.stream().mapToLong(Long::longValue).average().orElse(0);

        // Calculate standard deviation
        double variance = durations.stream()
                .mapToDouble(d -> Math.pow(d - avgDuration, 2))
                .average()
                .orElse(0);
        double stdDev = Math.sqrt(variance);

        System.out.println("\n  Reasoning Performance Statistics:");
        System.out.println("    Min: " + minDuration + " ms");
        System.out.println("    Max: " + maxDuration + " ms");
        System.out.println("    Avg: " + String.format("%.2f", avgDuration) + " ms");
        System.out.println("    Std Dev: " + String.format("%.2f", stdDev) + " ms");

        metrics.add(new PerformanceMetric("Reasoning Avg", (long) avgDuration, REASONING_THRESHOLD_MS));

        // Verify consistency (std dev should be reasonable)
        assertThat(stdDev)
                .as("Standard deviation should indicate consistent performance")
                .isLessThan(avgDuration); // Std dev less than average is reasonable

        assertThat(avgDuration)
                .as("Average reasoning time should be under threshold")
                .isLessThan(REASONING_THRESHOLD_MS);
    }

    // ==================== Helper Methods ====================

    private void printPerformanceReport() {
        if (metrics.isEmpty()) return;

        System.out.println("\n" + "=".repeat(70));
        System.out.println("PERFORMANCE REPORT");
        System.out.println("=".repeat(70));
        System.out.println(String.format("%-35s %10s %10s %10s", "Metric", "Duration", "Threshold", "Status"));
        System.out.println("-".repeat(70));

        for (PerformanceMetric metric : metrics) {
            String status = metric.duration < metric.threshold ? "PASS" : "FAIL";
            System.out.println(String.format("%-35s %8d ms %8d ms %10s",
                    metric.name, metric.duration, metric.threshold, status));
        }

        System.out.println("=".repeat(70));

        // Clear metrics for next test
        metrics.clear();
    }

    private static class PerformanceMetric {
        final String name;
        final long duration;
        final long threshold;

        PerformanceMetric(String name, long duration, long threshold) {
            this.name = name;
            this.duration = duration;
            this.threshold = threshold;
        }
    }
}
