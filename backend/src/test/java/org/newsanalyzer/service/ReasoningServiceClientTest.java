package org.newsanalyzer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.config.ReasoningServiceConfig;
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
    private MockRestServiceServer mockServer;
    private ReasoningServiceClient client;

    @BeforeEach
    void setUp() {
        config = new ReasoningServiceConfig();
        config.setBaseUrl("http://localhost:8000");
        config.setApiKey("test-api-key");
        config.setTimeout(30000);

        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new ReasoningServiceClient(config, restTemplate);
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
}
