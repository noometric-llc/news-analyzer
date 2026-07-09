package org.newsanalyzer.service.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.TestcontainersConfiguration;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.BiasAnnotationData;
import org.newsanalyzer.dto.BiasDetectionResponse;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.dto.EntityExtractionResponse;
import org.newsanalyzer.dto.ExtractedEntityData;
import org.newsanalyzer.dto.eval.RealArticleEvaluationDTO;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.repository.ArticleBiasAnnotationRepository;
import org.newsanalyzer.repository.ArticleRepository;
import org.newsanalyzer.repository.EntityRepository;
import org.newsanalyzer.service.ArticleService;
import org.newsanalyzer.service.ReasoningServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration test for Story ES-1.6, AC6: proves the full flow — ingest a
 * real article through the production ArticleService pipeline, then read
 * its results back via EvalRealArticleService — works end-to-end against a
 * real PostgreSQL container. Only ReasoningServiceClient is mocked (the
 * actual HTTP boundary to the reasoning service), same division of labor as
 * ArticleExtractionIntegrationTest (ES-1.3/ES-1.4).
 */
@SpringBootTest
@ActiveProfiles("tc")
@org.springframework.context.annotation.Import(TestcontainersConfiguration.class)
class EvalRealArticleIntegrationTest {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private EvalRealArticleService evalRealArticleService;

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

    @Test
    void testFullPipeline_ingestExtractBiasDetectThenReadViaEvalEndpoint() {
        ExtractedEntityData extracted = new ExtractedEntityData();
        extracted.setText("Elizabeth Warren");
        extracted.setEntityType("person");
        extracted.setConfidence(0.85f);
        extracted.setSchemaOrgType("Person");
        EntityExtractionResponse extractionResponse = new EntityExtractionResponse();
        extractionResponse.setEntities(List.of(extracted));
        extractionResponse.setTotalCount(1);
        when(reasoningServiceClient.extractEntities(anyString(), anyFloat())).thenReturn(extractionResponse);

        BiasAnnotationData annotation = new BiasAnnotationData();
        annotation.setDistortionType("hasty_generalization");
        annotation.setCategory("cognitive_bias");
        annotation.setExcerpt("The administration has always been corrupt");
        annotation.setExplanation("Uses absolute language to generalize from limited evidence.");
        annotation.setConfidence(0.87f);
        annotation.setOntologyMetadata(Map.of("definition", "Drawing a broad conclusion from a small sample."));
        BiasDetectionResponse biasResponse = new BiasDetectionResponse();
        biasResponse.setAnnotations(List.of(annotation));
        biasResponse.setTotalCount(1);
        when(reasoningServiceClient.detectBias(anyString(), anyBoolean())).thenReturn(biasResponse);

        // Step 1: ingest through the real production pipeline.
        ArticleDTO created = articleService.createArticle(createRequest);
        assertEquals(ArticleStatus.SUCCESS, created.getExtractionStatus());
        assertEquals(ArticleStatus.SUCCESS, created.getBiasDetectionStatus());

        // Step 2: read it back via the new eval-only endpoint's service.
        RealArticleEvaluationDTO evaluation = evalRealArticleService.getRealArticleEvaluation(created.getId());

        assertEquals(created.getId(), evaluation.getArticle().getId());
        assertEquals(ArticleStatus.SUCCESS, evaluation.getArticle().getExtractionStatus());
        assertEquals(ArticleStatus.SUCCESS, evaluation.getArticle().getBiasDetectionStatus());

        assertEquals(1, evaluation.getEntities().size());
        assertEquals("Elizabeth Warren", evaluation.getEntities().get(0).getName());

        assertEquals(1, evaluation.getAnnotations().size());
        assertEquals("hasty_generalization", evaluation.getAnnotations().get(0).getDistortionType());
        assertNotNull(evaluation.getAnnotations().get(0).getOntologyMetadata());
    }
}
