package org.newsanalyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.config.ReasoningServiceConfig;
import org.newsanalyzer.dto.BiasDetectionResponse;
import org.newsanalyzer.dto.EntityExtractionResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Client for the noometric-intelligence reasoning service.
 *
 * First Java-side caller of this service — see
 * docs/api/reasoning-service-contract.md for the full contract. Timeout
 * configuration mirrors CongressApiClient's philosophy (configured via
 * RestTemplateBuilder), but the RestTemplate itself is injected as a bean
 * (defined in ReasoningServiceConfig) rather than built internally — see
 * that class's Javadoc for why. Does NOT retry: the contract explicitly
 * recommends no retry for /entities/extract ("fast; failures are hard
 * errors"), unlike the LLM-backed endpoints.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 * @see <a href="docs/api/reasoning-service-contract.md">Reasoning Service Contract</a>
 */
@Slf4j
@Service
public class ReasoningServiceClient {

    private static final String API_KEY_HEADER = "X-Noometric-API-Key";

    private final ReasoningServiceConfig config;
    private final RestTemplate restTemplate;
    private final RestTemplate biasRestTemplate;

    public ReasoningServiceClient(
            ReasoningServiceConfig config,
            @Qualifier("reasoningServiceRestTemplate") RestTemplate reasoningServiceRestTemplate,
            @Qualifier("reasoningServiceBiasRestTemplate") RestTemplate reasoningServiceBiasRestTemplate) {
        this.config = config;
        this.restTemplate = reasoningServiceRestTemplate;
        this.biasRestTemplate = reasoningServiceBiasRestTemplate;
    }

    /**
     * Extract named entities from article text via POST /entities/extract.
     *
     * No retry — per the documented contract, this endpoint is fast (typical
     * 50-200ms) and failures should be treated as hard errors, not transient
     * ones worth retrying.
     *
     * @param text article text to analyze
     * @param confidenceThreshold minimum confidence to include an entity (0.0-1.0)
     * @return the extraction response
     * @throws org.springframework.web.client.RestClientException on any HTTP/network failure
     */
    public EntityExtractionResponse extractEntities(String text, float confidenceThreshold) {
        String url = config.getBaseUrl() + "/entities/extract";

        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("confidence_threshold", confidenceThreshold);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(API_KEY_HEADER, config.getApiKey());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.debug("POST {}/entities/extract - text length={}", config.getBaseUrl(), text.length());
        EntityExtractionResponse response = restTemplate.postForObject(url, request, EntityExtractionResponse.class);
        log.debug("Extraction returned {} entities", response != null ? response.getTotalCount() : 0);

        return response;
    }

    /**
     * Detect cognitive biases and logical fallacies in article text via
     * POST /eval/bias/detect.
     *
     * Uses the separate, longer-timeout bias RestTemplate (60s per NFR2) —
     * this endpoint is LLM-backed and materially slower than
     * /entities/extract, unlike that endpoint's spaCy-backed fast path.
     *
     * @param text article text to analyze
     * @param grounded true for SPARQL-grounded LLM prompting; false for a generic prompt
     * @return the bias-detection response
     * @throws org.springframework.web.client.RestClientException on any HTTP/network failure
     */
    public BiasDetectionResponse detectBias(String text, boolean grounded) {
        String url = config.getBaseUrl() + "/eval/bias/detect";

        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("confidence_threshold", 0.0f);
        body.put("include_ontology_metadata", true);
        body.put("grounded", grounded);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(API_KEY_HEADER, config.getApiKey());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.debug("POST {}/eval/bias/detect - text length={}, grounded={}", config.getBaseUrl(), text.length(), grounded);
        BiasDetectionResponse response = biasRestTemplate.postForObject(url, request, BiasDetectionResponse.class);
        log.debug("Bias detection returned {} annotations", response != null ? response.getTotalCount() : 0);

        return response;
    }
}
