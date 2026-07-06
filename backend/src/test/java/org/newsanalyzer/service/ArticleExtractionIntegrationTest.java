package org.newsanalyzer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.TestcontainersConfiguration;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.dto.EntityExtractionResponse;
import org.newsanalyzer.dto.ExtractedEntityData;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.model.Entity;
import org.newsanalyzer.model.EntityType;
import org.newsanalyzer.repository.ArticleRepository;
import org.newsanalyzer.repository.EntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for the full article-submission -> entity-extraction ->
 * persistence flow (Story ES-1.3, AC5). Runs against a real PostgreSQL
 * container (mirroring ArticleRepositoryTest's setup) so the FK link between
 * Article and Entity is proven end-to-end. Only ReasoningServiceClient is
 * mocked, since it is the actual HTTP boundary to the (separately owned)
 * reasoning service — everything else runs as it would in production.
 */
@SpringBootTest
@ActiveProfiles("tc")
@org.springframework.context.annotation.Import(TestcontainersConfiguration.class)
class ArticleExtractionIntegrationTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private EntityRepository entityRepository;

    @MockBean
    private ReasoningServiceClient reasoningServiceClient;

    private CreateArticleRequest createRequest;

    @BeforeEach
    void setUp() {
        articleRepository.deleteAll();
        entityRepository.deleteAll();

        createRequest = new CreateArticleRequest();
        createRequest.setSourceName("CNN");
        createRequest.setUrl("https://example.com/article");
        createRequest.setRawText("Senator Warren met with EPA officials.");
    }

    @Test
    void testCreateArticle_extractionSucceeds_entitiesPersistedAndLinked() {
        ExtractedEntityData extracted = new ExtractedEntityData();
        extracted.setText("Elizabeth Warren");
        extracted.setEntityType("person");
        extracted.setConfidence(0.85f);
        extracted.setSchemaOrgType("Person");
        extracted.setSchemaOrgData(Map.of("@type", "Person", "name", "Elizabeth Warren"));

        EntityExtractionResponse response = new EntityExtractionResponse();
        response.setEntities(List.of(extracted));
        response.setTotalCount(1);

        when(reasoningServiceClient.extractEntities(anyString(), anyFloat())).thenReturn(response);

        ArticleDTO created = articleService.createArticle(createRequest);

        assertEquals(ArticleStatus.SUCCESS, created.getExtractionStatus());

        List<Entity> linkedEntities = entityRepository.findAll().stream()
            .filter(e -> created.getId().equals(e.getArticleId()))
            .toList();

        assertEquals(1, linkedEntities.size());
        Entity persisted = linkedEntities.get(0);
        assertEquals("Elizabeth Warren", persisted.getName());
        assertEquals(EntityType.PERSON, persisted.getEntityType());
        assertEquals("Person", persisted.getSchemaOrgType());
        assertEquals(0.85f, persisted.getConfidenceScore());
        assertFalse(persisted.getVerified());
    }

    @Test
    void testCreateArticle_extractionFails_articlePersistsWithNoEntities() {
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat()))
            .thenThrow(new RestClientException("Connection refused"));

        ArticleDTO created = articleService.createArticle(createRequest);

        assertNotNull(articleRepository.findById(created.getId()).orElse(null));
        assertEquals(ArticleStatus.FAILED, created.getExtractionStatus());

        List<Entity> linkedEntities = entityRepository.findAll().stream()
            .filter(e -> created.getId().equals(e.getArticleId()))
            .toList();
        assertTrue(linkedEntities.isEmpty());
    }

    @Test
    void testCreateArticle_oneEntityInBatchFailsToMap_wholeBatchRolledBack() {
        // QA finding (ES-1.3 review): a mid-batch mapping failure must not
        // leave earlier, successfully-processed entities in the same batch
        // orphaned in the database while extractionStatus reads FAILED. This
        // proves the fix against a real transaction manager and real
        // Postgres — a Mockito-based unit test cannot demonstrate an actual
        // rollback, only that processing stopped at the failing entity.
        ExtractedEntityData valid = new ExtractedEntityData();
        valid.setText("Elizabeth Warren");
        valid.setEntityType("person");
        valid.setConfidence(0.85f);
        valid.setSchemaOrgType("Person");

        ExtractedEntityData invalid = new ExtractedEntityData();
        invalid.setText("Something Unrecognized");
        invalid.setEntityType("not_a_real_type");
        invalid.setConfidence(0.5f);

        EntityExtractionResponse response = new EntityExtractionResponse();
        response.setEntities(List.of(valid, invalid));
        response.setTotalCount(2);

        when(reasoningServiceClient.extractEntities(anyString(), anyFloat())).thenReturn(response);

        ArticleDTO created = articleService.createArticle(createRequest);

        assertEquals(ArticleStatus.FAILED, created.getExtractionStatus());
        assertNotNull(articleRepository.findById(created.getId()).orElse(null),
            "Article itself must still persist per NFR3, regardless of the entity batch outcome");

        List<Entity> linkedEntities = entityRepository.findAll().stream()
            .filter(e -> created.getId().equals(e.getArticleId()))
            .toList();
        assertTrue(linkedEntities.isEmpty(),
            "The valid entity processed before the failing one must be rolled back, not left orphaned");
    }
}
