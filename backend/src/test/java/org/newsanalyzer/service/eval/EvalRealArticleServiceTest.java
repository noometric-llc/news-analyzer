package org.newsanalyzer.service.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.eval.RealArticleEvaluationDTO;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.ArticleBiasAnnotation;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.model.Entity;
import org.newsanalyzer.model.EntityType;
import org.newsanalyzer.repository.ArticleBiasAnnotationRepository;
import org.newsanalyzer.repository.EntityRepository;
import org.newsanalyzer.service.ArticleService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EvalRealArticleService with mocked dependencies.
 * Mirrors EvalDatasetServiceTest's manual-construction convention.
 */
@ExtendWith(MockitoExtension.class)
class EvalRealArticleServiceTest {

    @Mock
    private ArticleService articleService;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private ArticleBiasAnnotationRepository articleBiasAnnotationRepository;

    private EvalRealArticleService service;

    private UUID articleId;
    private ArticleDTO articleDTO;

    @BeforeEach
    void setUp() {
        service = new EvalRealArticleService(articleService, entityRepository, articleBiasAnnotationRepository);

        articleId = UUID.randomUUID();
        articleDTO = new ArticleDTO();
        articleDTO.setId(articleId);
        articleDTO.setSourceName("CNN");
        articleDTO.setExtractionStatus(ArticleStatus.SUCCESS);
        articleDTO.setBiasDetectionStatus(ArticleStatus.SUCCESS);
    }

    @Test
    void testGetRealArticleEvaluation_dataPresent_returnsBundledResult() {
        when(articleService.getArticleById(articleId)).thenReturn(articleDTO);

        Entity entity = new Entity();
        entity.setId(UUID.randomUUID());
        entity.setEntityType(EntityType.PERSON);
        entity.setName("Elizabeth Warren");
        when(entityRepository.findByArticleId(articleId)).thenReturn(List.of(entity));

        ArticleBiasAnnotation annotation = new ArticleBiasAnnotation();
        annotation.setId(UUID.randomUUID());
        annotation.setArticleId(articleId);
        annotation.setDistortionType("hasty_generalization");
        annotation.setCategory("cognitive_bias");
        when(articleBiasAnnotationRepository.findByArticleId(articleId)).thenReturn(List.of(annotation));

        RealArticleEvaluationDTO result = service.getRealArticleEvaluation(articleId);

        assertNotNull(result);
        assertEquals(articleId, result.getArticle().getId());
        assertEquals(1, result.getEntities().size());
        assertEquals("Elizabeth Warren", result.getEntities().get(0).getName());
        assertEquals(1, result.getAnnotations().size());
        assertEquals("hasty_generalization", result.getAnnotations().get(0).getDistortionType());
    }

    @Test
    void testGetRealArticleEvaluation_noEntitiesOrAnnotations_returnsEmptyLists() {
        when(articleService.getArticleById(articleId)).thenReturn(articleDTO);
        when(entityRepository.findByArticleId(articleId)).thenReturn(List.of());
        when(articleBiasAnnotationRepository.findByArticleId(articleId)).thenReturn(List.of());

        RealArticleEvaluationDTO result = service.getRealArticleEvaluation(articleId);

        assertNotNull(result);
        assertTrue(result.getEntities().isEmpty());
        assertTrue(result.getAnnotations().isEmpty());
    }

    @Test
    void testGetRealArticleEvaluation_articleNotFound_throwsResourceNotFoundException() {
        when(articleService.getArticleById(articleId))
            .thenThrow(new ResourceNotFoundException("Article", articleId));

        assertThrows(ResourceNotFoundException.class, () ->
            service.getRealArticleEvaluation(articleId));
    }

    @Test
    void testGetRealArticleEvaluation_partialPipelineFailure_stillReturnsAvailableData() {
        // extractionStatus succeeded, biasDetectionStatus failed — the
        // endpoint must not gate on both being SUCCESS (AC2); it returns
        // whatever exists with both statuses visible on the article.
        articleDTO.setBiasDetectionStatus(ArticleStatus.FAILED);
        when(articleService.getArticleById(articleId)).thenReturn(articleDTO);

        Entity entity = new Entity();
        entity.setId(UUID.randomUUID());
        entity.setEntityType(EntityType.PERSON);
        entity.setName("Elizabeth Warren");
        when(entityRepository.findByArticleId(articleId)).thenReturn(List.of(entity));
        when(articleBiasAnnotationRepository.findByArticleId(articleId)).thenReturn(List.of());

        RealArticleEvaluationDTO result = service.getRealArticleEvaluation(articleId);

        assertEquals(ArticleStatus.SUCCESS, result.getArticle().getExtractionStatus());
        assertEquals(ArticleStatus.FAILED, result.getArticle().getBiasDetectionStatus());
        assertEquals(1, result.getEntities().size());
        assertTrue(result.getAnnotations().isEmpty());
    }
}
