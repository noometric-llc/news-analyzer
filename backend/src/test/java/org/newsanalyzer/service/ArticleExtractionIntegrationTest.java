package org.newsanalyzer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.TestcontainersConfiguration;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.BiasAnnotationData;
import org.newsanalyzer.dto.BiasDetectionResponse;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.dto.EntityExtractionResponse;
import org.newsanalyzer.dto.ExtractedEntityData;
import org.newsanalyzer.model.ArticleBiasAnnotation;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.model.Entity;
import org.newsanalyzer.model.EntityType;
import org.newsanalyzer.repository.ArticleBiasAnnotationRepository;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for the full article-submission -> entity-extraction ->
 * bias-detection -> persistence flow (Stories ES-1.3 AC5, ES-1.4 AC6). Runs
 * against a real PostgreSQL container (mirroring ArticleRepositoryTest's
 * setup) so the FK links between Article, Entity, and ArticleBiasAnnotation
 * are proven end-to-end. Only ReasoningServiceClient is mocked, since it is
 * the actual HTTP boundary to the (separately owned) reasoning service —
 * everything else runs as it would in production.
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

    @Autowired
    private ArticleBiasAnnotationRepository articleBiasAnnotationRepository;

    @MockBean
    private ReasoningServiceClient reasoningServiceClient;

    private CreateArticleRequest createRequest;

    @BeforeEach
    void setUp() {
        articleBiasAnnotationRepository.deleteAll();
        articleRepository.deleteAll();
        entityRepository.deleteAll();

        createRequest = new CreateArticleRequest();
        createRequest.setSourceName("CNN");
        createRequest.setUrl("https://example.com/article");
        createRequest.setRawText("Senator Warren met with EPA officials.");
    }

    private BiasDetectionResponse emptyBiasDetectionResponse() {
        BiasDetectionResponse response = new BiasDetectionResponse();
        response.setAnnotations(List.of());
        response.setTotalCount(0);
        return response;
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

    @Test
    void testCreateArticle_biasDetectionSucceeds_annotationsPersistedAndLinked() {
        EntityExtractionResponse emptyExtractionResponse = new EntityExtractionResponse();
        emptyExtractionResponse.setEntities(List.of());
        emptyExtractionResponse.setTotalCount(0);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat()))
            .thenReturn(emptyExtractionResponse);

        BiasAnnotationData annotation = new BiasAnnotationData();
        annotation.setDistortionType("hasty_generalization");
        annotation.setCategory("cognitive_bias");
        annotation.setExcerpt("The administration has always been corrupt");
        annotation.setExplanation("Uses absolute language to generalize from limited evidence.");
        annotation.setConfidence(0.87f);
        annotation.setOntologyMetadata(Map.of(
            "definition", "Drawing a broad conclusion from a small or unrepresentative sample.",
            "academic_source", "Kahneman, 2011"
        ));

        BiasDetectionResponse response = new BiasDetectionResponse();
        response.setAnnotations(List.of(annotation));
        response.setTotalCount(1);
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean())).thenReturn(response);

        ArticleDTO created = articleService.createArticle(createRequest);

        assertEquals(ArticleStatus.SUCCESS, created.getBiasDetectionStatus());
        // AC4: reliabilityScore stays null — this story doesn't populate it,
        // no cross-article aggregation logic exists yet.
        assertNull(created.getReliabilityScore());

        List<ArticleBiasAnnotation> linkedAnnotations = articleBiasAnnotationRepository.findAll().stream()
            .filter(a -> created.getId().equals(a.getArticleId()))
            .toList();

        assertEquals(1, linkedAnnotations.size());
        ArticleBiasAnnotation persisted = linkedAnnotations.get(0);
        assertEquals("hasty_generalization", persisted.getDistortionType());
        assertEquals("cognitive_bias", persisted.getCategory());
        assertEquals(0.87f, persisted.getConfidence());
        assertNotNull(persisted.getOntologyMetadata());
    }

    @Test
    void testCreateArticle_biasDetectionFails_articleAndEntitiesPersistWithNoAnnotations() {
        ExtractedEntityData extracted = new ExtractedEntityData();
        extracted.setText("Elizabeth Warren");
        extracted.setEntityType("person");
        extracted.setConfidence(0.85f);
        extracted.setSchemaOrgType("Person");

        EntityExtractionResponse extractionResponse = new EntityExtractionResponse();
        extractionResponse.setEntities(List.of(extracted));
        extractionResponse.setTotalCount(1);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat())).thenReturn(extractionResponse);

        when(reasoningServiceClient.detectBias(anyString(), anyBoolean()))
            .thenThrow(new RestClientException("Reasoning service timeout"));

        ArticleDTO created = articleService.createArticle(createRequest);

        // Bias-detection failure must never block Article or Entity persistence.
        assertNotNull(articleRepository.findById(created.getId()).orElse(null));
        assertEquals(ArticleStatus.FAILED, created.getBiasDetectionStatus());

        // Extraction succeeded independently of bias-detection's failure (NFR3).
        assertEquals(ArticleStatus.SUCCESS, created.getExtractionStatus());
        List<Entity> linkedEntities = entityRepository.findAll().stream()
            .filter(e -> created.getId().equals(e.getArticleId()))
            .toList();
        assertEquals(1, linkedEntities.size());

        List<ArticleBiasAnnotation> linkedAnnotations = articleBiasAnnotationRepository.findAll().stream()
            .filter(a -> created.getId().equals(a.getArticleId()))
            .toList();
        assertTrue(linkedAnnotations.isEmpty());
    }
}
