package org.newsanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import lombok.Data;

import java.time.Duration;

/**
 * Configuration properties for the reasoning service (noometric-intelligence,
 * private repo). See docs/api/reasoning-service-contract.md for the API
 * contract this client consumes.
 *
 * Also defines the reasoningServiceRestTemplate bean directly, rather than
 * having ReasoningServiceClient build it internally from an injected
 * RestTemplateBuilder (the pattern CongressApiClient uses). This is a
 * deliberate, narrow deviation: CongressApiClient's own test
 * (CongressApiClientTest) documents in a comment that it couldn't test real
 * HTTP behavior because of that internal-construction style and fell back to
 * asserting config values only. Exposing the RestTemplate as an injectable
 * bean lets ReasoningServiceClientTest use Spring's MockRestServiceServer for
 * genuine request/response verification instead of repeating that gap.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "reasoning-service")
@Data
public class ReasoningServiceConfig {

    /**
     * Base URL for the reasoning service
     */
    private String baseUrl = "http://localhost:8000";

    /**
     * API key sent as the X-Noometric-API-Key header
     */
    private String apiKey;

    /**
     * Request timeout in milliseconds for the fast, spaCy-backed
     * /entities/extract endpoint (30s per NFR1 / reasoning-service-contract.md)
     */
    private int timeout = 30000;

    /**
     * Request timeout in milliseconds for the slower, LLM-backed
     * /eval/bias/detect endpoint (60s per NFR2 / reasoning-service-contract.md,
     * Story ES-1.4). Deliberately a separate property/bean from {@link #timeout} —
     * sharing one timeout would force /entities/extract to wait as long as the
     * LLM-backed call before failing fast, silently widening its documented 30s
     * budget.
     */
    private int biasTimeout = 60000;

    /**
     * Check if API key is configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Bean
    public RestTemplate reasoningServiceRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofMillis(timeout))
            .setReadTimeout(Duration.ofMillis(timeout))
            .build();
    }

    @Bean
    public RestTemplate reasoningServiceBiasRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofMillis(biasTimeout))
            .setReadTimeout(Duration.ofMillis(biasTimeout))
            .build();
    }
}
