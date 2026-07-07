package org.newsanalyzer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.BiasAnnotationData;
import org.newsanalyzer.dto.BiasDetectionResponse;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.dto.EntityExtractionResponse;
import org.newsanalyzer.dto.ExtractedEntityData;
import org.newsanalyzer.model.Article;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.repository.ArticleBiasAnnotationRepository;
import org.newsanalyzer.repository.ArticleRepository;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ArticleService with mocked dependencies.
 *
 * Story ES-1.3 note: createArticle() now always calls articleRepository.save()
 * and invokes ReasoningServiceClient.extractEntities() — every test below
 * stubs both, even the ones focused on persistence.
 *
 * Story ES-1.4 note: createArticle() also always calls
 * ReasoningServiceClient.detectBias(), making three total articleRepository.save()
 * calls per invocation (persist, extraction-status update, bias-detection-status
 * update) — every test below stubs detectBias() too, for the same reason.
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleBiasAnnotationRepository articleBiasAnnotationRepository;

    @Mock
    private ReasoningServiceClient reasoningServiceClient;

    @Mock
    private EntityService entityService;

    @InjectMocks
    private ArticleService articleService;

    private CreateArticleRequest createRequest;
    private Article testArticle;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();

        createRequest = new CreateArticleRequest();
        createRequest.setSourceName("CNN");
        createRequest.setUrl("https://example.com/article");
        createRequest.setPublicationDate(LocalDateTime.of(2026, 6, 30, 14, 0));
        createRequest.setRawText("Full article text...");

        testArticle = new Article();
        testArticle.setId(testId);
        testArticle.setSourceName("CNN");
        testArticle.setUrl("https://example.com/article");
        testArticle.setPublicationDate(LocalDateTime.of(2026, 6, 30, 14, 0));
        testArticle.setRawText("Full article text...");
        testArticle.setIngestedAt(LocalDateTime.now());
    }

    private EntityExtractionResponse emptyExtractionResponse() {
        EntityExtractionResponse response = new EntityExtractionResponse();
        response.setEntities(List.of());
        response.setTotalCount(0);
        return response;
    }

    private BiasDetectionResponse emptyBiasDetectionResponse() {
        BiasDetectionResponse response = new BiasDetectionResponse();
        response.setAnnotations(List.of());
        response.setTotalCount(0);
        return response;
    }

    @Test
    void testCreateArticle_extractionSucceeds_returnsPersistedArticle() {
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat()))
            .thenReturn(emptyExtractionResponse());
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean()))
            .thenReturn(emptyBiasDetectionResponse());

        ArticleDTO result = articleService.createArticle(createRequest);

        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals("CNN", result.getSourceName());
        assertEquals(ArticleStatus.SUCCESS, result.getExtractionStatus());
        assertEquals(ArticleStatus.SUCCESS, result.getBiasDetectionStatus());
        assertNull(result.getReliabilityScore());

        verify(articleRepository, times(3)).save(any(Article.class));
    }

    @Test
    void testCreateArticlePersistsRequestFields() {
        // Proves the CreateArticleRequest -> Article mapping is correct on
        // the FIRST save() call (before extraction/bias-detection run) —
        // captures every save() invocation and checks the first one specifically.
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat()))
            .thenReturn(emptyExtractionResponse());
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean()))
            .thenReturn(emptyBiasDetectionResponse());

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        articleService.createArticle(createRequest);

        verify(articleRepository, times(3)).save(captor.capture());
        Article firstSave = captor.getAllValues().get(0);
        assertEquals("CNN", firstSave.getSourceName());
        assertEquals("https://example.com/article", firstSave.getUrl());
        assertEquals(createRequest.getPublicationDate(), firstSave.getPublicationDate());
        assertEquals("Full article text...", firstSave.getRawText());
    }

    @Test
    void testCreateArticle_extractionSucceeds_entitiesCreatedAndLinked() {
        ExtractedEntityData extracted = new ExtractedEntityData();
        extracted.setText("Elizabeth Warren");
        extracted.setEntityType("person");
        extracted.setConfidence(0.85f);
        extracted.setSchemaOrgType("Person");

        EntityExtractionResponse response = new EntityExtractionResponse();
        response.setEntities(List.of(extracted));
        response.setTotalCount(1);

        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat())).thenReturn(response);
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean()))
            .thenReturn(emptyBiasDetectionResponse());

        ArticleDTO result = articleService.createArticle(createRequest);

        assertEquals(ArticleStatus.SUCCESS, result.getExtractionStatus());
        verify(entityService).createEntitiesFromExtraction(List.of(extracted), testId);
    }

    @Test
    void testCreateArticle_extractionFails_articleStillPersistsWithFailedStatus() {
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat()))
            .thenThrow(new RestClientException("Connection refused"));
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean()))
            .thenReturn(emptyBiasDetectionResponse());

        ArticleDTO result = articleService.createArticle(createRequest);

        // The article still exists and is returned — extraction failure
        // must never block persistence (NFR3).
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals(ArticleStatus.FAILED, result.getExtractionStatus());

        // Bias detection is independent of extraction (NFR2/NFR3) — it still
        // ran and succeeded even though extraction failed.
        assertEquals(ArticleStatus.SUCCESS, result.getBiasDetectionStatus());

        verify(entityService, never()).createEntitiesFromExtraction(any(), any());
        verify(articleRepository, times(3)).save(any(Article.class));
    }

    @Test
    void testCreateArticle_unexpectedExceptionDuringPersistence_stillMarksFailed() {
        // Proves the broader catch(Exception) safety net around entity
        // persistence, not just around the reasoning-service call itself.
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat()))
            .thenReturn(emptyExtractionResponse());
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean()))
            .thenReturn(emptyBiasDetectionResponse());
        // Simulate an unexpected bug in downstream processing (e.g. a
        // malformed entity_type the batch can't map) by making the atomic
        // batch call itself throw — createEntitiesFromExtraction() is
        // @Transactional, so in production this would roll back the whole
        // batch, not just this one call.
        ExtractedEntityData extracted = new ExtractedEntityData();
        extracted.setText("Bad Entity");
        extracted.setEntityType("person");
        EntityExtractionResponse response = new EntityExtractionResponse();
        response.setEntities(List.of(extracted));
        response.setTotalCount(1);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat())).thenReturn(response);
        when(entityService.createEntitiesFromExtraction(any(), any()))
            .thenThrow(new RuntimeException("Unexpected mapping bug"));

        ArticleDTO result = articleService.createArticle(createRequest);

        assertNotNull(result);
        assertEquals(ArticleStatus.FAILED, result.getExtractionStatus());
    }

    @Test
    void testCreateArticle_biasDetectionSucceeds_annotationsCreatedAndLinked() {
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat()))
            .thenReturn(emptyExtractionResponse());

        BiasAnnotationData annotation = new BiasAnnotationData();
        annotation.setDistortionType("hasty_generalization");
        annotation.setCategory("cognitive_bias");
        annotation.setExcerpt("The administration has always been corrupt");
        annotation.setExplanation("Uses absolute language to generalize from limited evidence.");
        annotation.setConfidence(0.87f);

        BiasDetectionResponse response = new BiasDetectionResponse();
        response.setAnnotations(List.of(annotation));
        response.setTotalCount(1);
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean())).thenReturn(response);

        ArticleDTO result = articleService.createArticle(createRequest);

        assertEquals(ArticleStatus.SUCCESS, result.getBiasDetectionStatus());
        verify(articleBiasAnnotationRepository).saveAll(argThat(annotations -> {
            List<?> list = (List<?>) annotations;
            return list.size() == 1;
        }));
    }

    @Test
    void testCreateArticle_biasDetectionFails_articleAndEntitiesStillPersistExtractionUnaffected() {
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);

        ExtractedEntityData extracted = new ExtractedEntityData();
        extracted.setText("Elizabeth Warren");
        extracted.setEntityType("person");
        EntityExtractionResponse extractionResponse = new EntityExtractionResponse();
        extractionResponse.setEntities(List.of(extracted));
        extractionResponse.setTotalCount(1);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat())).thenReturn(extractionResponse);

        when(reasoningServiceClient.detectBias(anyString(), anyBoolean()))
            .thenThrow(new RestClientException("Reasoning service timeout"));

        ArticleDTO result = articleService.createArticle(createRequest);

        // Bias-detection failure must never block Article or Entity persistence.
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals(ArticleStatus.FAILED, result.getBiasDetectionStatus());

        // Extraction's own status is unaffected by bias-detection's failure —
        // the two failure modes are tracked independently (NFR3).
        assertEquals(ArticleStatus.SUCCESS, result.getExtractionStatus());
        verify(entityService).createEntitiesFromExtraction(List.of(extracted), testId);
        verify(articleBiasAnnotationRepository, never()).saveAll(any());
    }

    @Test
    void testCreateArticle_unexpectedExceptionDuringBiasAnnotationPersistence_stillMarksFailed() {
        // Mirrors testCreateArticle_unexpectedExceptionDuringPersistence_stillMarksFailed's
        // coverage for the extraction side: proves the broader catch(Exception)
        // safety net also covers bias-annotation persistence, not just the
        // detectBias() HTTP call itself. A malformed reasoning-service response
        // (e.g. a "required" field coming back null, violating a NOT NULL DB
        // column) would surface here as a DataIntegrityViolationException from
        // saveAll() — a RuntimeException, not a RestClientException — so this
        // proves that failure mode is caught gracefully too, not just network-level
        // failures.
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat()))
            .thenReturn(emptyExtractionResponse());

        BiasAnnotationData annotation = new BiasAnnotationData();
        annotation.setDistortionType("hasty_generalization");
        BiasDetectionResponse response = new BiasDetectionResponse();
        response.setAnnotations(List.of(annotation));
        response.setTotalCount(1);
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean())).thenReturn(response);
        when(articleBiasAnnotationRepository.saveAll(any()))
            .thenThrow(new RuntimeException("Simulated DataIntegrityViolationException"));

        ArticleDTO result = articleService.createArticle(createRequest);

        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals(ArticleStatus.FAILED, result.getBiasDetectionStatus());
        // Extraction succeeded independently — the two failure modes remain
        // decoupled even when the failure originates in the persistence step
        // rather than the HTTP call.
        assertEquals(ArticleStatus.SUCCESS, result.getExtractionStatus());
    }

    @Test
    void testGetArticleById() {
        when(articleRepository.findById(testId)).thenReturn(Optional.of(testArticle));

        ArticleDTO result = articleService.getArticleById(testId);

        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals("CNN", result.getSourceName());

        verify(articleRepository).findById(testId);
    }

    @Test
    void testGetArticleByIdNotFound() {
        when(articleRepository.findById(testId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            articleService.getArticleById(testId);
        });

        verify(articleRepository).findById(testId);
    }
}
