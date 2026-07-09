package org.newsanalyzer.controller.eval;

import org.junit.jupiter.api.Test;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.eval.RealArticleEvaluationDTO;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.service.eval.EvalRealArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for EvalRealArticleController.
 * Mirrors EvalDatasetControllerTest's @WebMvcTest + @MockBean conventions.
 */
@WebMvcTest(EvalRealArticleController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvalRealArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EvalRealArticleService evalRealArticleService;

    private static final UUID ARTICLE_ID = UUID.randomUUID();

    @Test
    void testGetRealArticleEvaluation_found_returnsBundledResult() throws Exception {
        ArticleDTO article = new ArticleDTO();
        article.setId(ARTICLE_ID);
        article.setSourceName("CNN");
        article.setExtractionStatus(ArticleStatus.SUCCESS);
        article.setBiasDetectionStatus(ArticleStatus.SUCCESS);

        RealArticleEvaluationDTO evaluation = new RealArticleEvaluationDTO(article, List.of(), List.of());
        when(evalRealArticleService.getRealArticleEvaluation(ARTICLE_ID)).thenReturn(evaluation);

        mockMvc.perform(get("/api/eval/real-articles/{articleId}", ARTICLE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.article.id").value(ARTICLE_ID.toString()))
            .andExpect(jsonPath("$.article.sourceName").value("CNN"))
            .andExpect(jsonPath("$.entities").isArray())
            .andExpect(jsonPath("$.annotations").isArray());
    }

    @Test
    void testGetRealArticleEvaluation_notFound_returns404() throws Exception {
        when(evalRealArticleService.getRealArticleEvaluation(ARTICLE_ID))
            .thenThrow(new ResourceNotFoundException("Article", ARTICLE_ID));

        mockMvc.perform(get("/api/eval/real-articles/{articleId}", ARTICLE_ID))
            .andExpect(status().isNotFound());
    }
}
