package org.newsanalyzer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.config.ReasoningServiceConfig;
import org.newsanalyzer.dto.BiasDetectionResponse;
import org.newsanalyzer.dto.EntityExtractionResponse;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for ReasoningServiceClient, using MockRestServiceServer for
 * genuine request/response verification (see ReasoningServiceConfig's
 * Javadoc for why this client injects RestTemplate directly rather than
 * building it internally like CongressApiClient does).
 */
class ReasoningServiceClientTest {

    private ReasoningServiceConfig config;
    private RestTemplate restTemplate;
    private RestTemplate biasRestTemplate;
    private MockRestServiceServer mockServer;
    private MockRestServiceServer biasMockServer;
    private ReasoningServiceClient client;

    @BeforeEach
    void setUp() {
        config = new ReasoningServiceConfig();
        config.setBaseUrl("http://localhost:8000");
        config.setApiKey("test-api-key");
        config.setTimeout(30000);
        config.setBiasTimeout(60000);

        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);

        biasRestTemplate = new RestTemplate();
        biasMockServer = MockRestServiceServer.createServer(biasRestTemplate);

        client = new ReasoningServiceClient(config, restTemplate, biasRestTemplate);
    }

    @Test
    void testExtractEntities_success() {
        String mockResponse = """
            {
              "entities": [
                {
                  "text": "Elizabeth Warren",
                  "entity_type": "person",
                  "start": 8,
                  "end": 24,
                  "confidence": 0.85,
                  "schema_org_type": "Person",
                  "schema_org_data": {"@type": "Person", "name": "Elizabeth Warren"},
                  "properties": {}
                }
              ],
              "total_count": 1
            }
            """;

        mockServer.expect(requestTo("http://localhost:8000/entities/extract"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("X-Noometric-API-Key", "test-api-key"))
            .andExpect(jsonPath("$.text").value("Senator Warren met with EPA officials."))
            .andExpect(jsonPath("$.confidence_threshold").value(0.7))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        EntityExtractionResponse response =
            client.extractEntities("Senator Warren met with EPA officials.", 0.7f);

        assertNotNull(response);
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getEntities().size());
        assertEquals("Elizabeth Warren", response.getEntities().get(0).getText());
        assertEquals("person", response.getEntities().get(0).getEntityType());
        assertEquals("Person", response.getEntities().get(0).getSchemaOrgType());
        assertEquals(0.85f, response.getEntities().get(0).getConfidence());

        mockServer.verify();
    }

    @Test
    void testExtractEntities_emptyResults() {
        String mockResponse = """
            {"entities": [], "total_count": 0}
            """;

        mockServer.expect(requestTo("http://localhost:8000/entities/extract"))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        EntityExtractionResponse response = client.extractEntities("No entities here.", 0.7f);

        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertTrue(response.getEntities().isEmpty());
    }

    @Test
    void testExtractEntities_serverError_throwsRestClientException() {
        mockServer.expect(requestTo("http://localhost:8000/entities/extract"))
            .andRespond(withServerError());

        assertThrows(HttpServerErrorException.class, () ->
            client.extractEntities("Some text.", 0.7f));
    }

    @Test
    void testExtractEntities_networkFailure_throwsRestClientException() {
        mockServer.expect(requestTo("http://localhost:8000/entities/extract"))
            .andRespond(request -> {
                throw new ResourceAccessException("Connection refused");
            });

        assertThrows(RestClientException.class, () ->
            client.extractEntities("Some text.", 0.7f));
    }

    @Test
    void testDetectBias_success() {
        String mockResponse = """
            {
              "annotations": [
                {
                  "distortion_type": "hasty_generalization",
                  "category": "cognitive_bias",
                  "excerpt": "The administration has always been corrupt",
                  "explanation": "Uses absolute language to generalize from limited evidence.",
                  "confidence": 0.87,
                  "ontology_metadata": {
                    "definition": "Drawing a broad conclusion from a small or unrepresentative sample.",
                    "academic_source": "Kahneman, 2011",
                    "detection_pattern": "Look for absolute quantifiers combined with evaluative claims."
                  }
                }
              ],
              "total_count": 1,
              "distortions_checked": ["hasty_generalization", "confirmation_bias"],
              "shacl_valid": true
            }
            """;

        biasMockServer.expect(requestTo("http://localhost:8000/eval/bias/detect"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("X-Noometric-API-Key", "test-api-key"))
            .andExpect(jsonPath("$.text").value("The administration has always been corrupt."))
            .andExpect(jsonPath("$.grounded").value(true))
            .andExpect(jsonPath("$.confidence_threshold").value(0.0))
            .andExpect(jsonPath("$.include_ontology_metadata").value(true))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        BiasDetectionResponse response =
            client.detectBias("The administration has always been corrupt.", true);

        assertNotNull(response);
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getAnnotations().size());
        assertEquals("hasty_generalization", response.getAnnotations().get(0).getDistortionType());
        assertEquals("cognitive_bias", response.getAnnotations().get(0).getCategory());
        assertEquals(0.87f, response.getAnnotations().get(0).getConfidence());
        assertNotNull(response.getAnnotations().get(0).getOntologyMetadata());
        assertTrue(response.getShaclValid());

        biasMockServer.verify();
    }

    @Test
    void testDetectBias_emptyResults() {
        String mockResponse = """
            {"annotations": [], "total_count": 0, "distortions_checked": [], "shacl_valid": true}
            """;

        biasMockServer.expect(requestTo("http://localhost:8000/eval/bias/detect"))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        BiasDetectionResponse response = client.detectBias("No bias here.", true);

        assertNotNull(response);
        assertEquals(0, response.getTotalCount());
        assertTrue(response.getAnnotations().isEmpty());
    }

    @Test
    void testDetectBias_serverError_throwsRestClientException() {
        biasMockServer.expect(requestTo("http://localhost:8000/eval/bias/detect"))
            .andRespond(withServerError());

        assertThrows(HttpServerErrorException.class, () ->
            client.detectBias("Some text.", true));
    }

    @Test
    void testDetectBias_networkFailure_throwsRestClientException() {
        biasMockServer.expect(requestTo("http://localhost:8000/eval/bias/detect"))
            .andRespond(request -> {
                throw new ResourceAccessException("Connection refused");
            });

        assertThrows(RestClientException.class, () ->
            client.detectBias("Some text.", true));
    }
}
