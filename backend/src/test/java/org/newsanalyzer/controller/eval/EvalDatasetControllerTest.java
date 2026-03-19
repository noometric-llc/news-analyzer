package org.newsanalyzer.controller.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.dto.eval.*;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.service.eval.EvalDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for EvalDatasetController.
 * Uses @WebMvcTest to load only the controller + Spring MVC infrastructure.
 * The service layer is mocked via @MockBean.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@WebMvcTest(EvalDatasetController.class)
@AutoConfigureMockMvc(addFilters = false)
class EvalDatasetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EvalDatasetService evalDatasetService;

    // --- Test data helpers ---

    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID ARTICLE_ID = UUID.randomUUID();

    private GenerationBatchDTO sampleBatchDTO() {
        return GenerationBatchDTO.builder()
                .id(BATCH_ID)
                .branch("legislative")
                .modelUsed("claude-sonnet-4-20250514")
                .configJson(Map.of("entity_count", 5))
                .articlesCount(7)
                .faithfulCount(1)
                .perturbedCount(6)
                .totalTokens(2100)
                .durationSeconds(5.0)
                .errors(List.of())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private SyntheticArticleDTO sampleArticleDTO() {
        return SyntheticArticleDTO.builder()
                .id(ARTICLE_ID)
                .batchId(BATCH_ID)
                .articleText("A test article about a senator.")
                .articleType("news_report")
                .isFaithful(false)
                .perturbationType("wrong_party")
                .difficulty("EASY")
                .sourceFacts(Map.of("branch", "legislative"))
                .groundTruth(Map.of("changed_facts", List.of()))
                .modelUsed("claude-sonnet-4-20250514")
                .tokensUsed(300)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private CreateBatchRequest sampleCreateRequest() {
        CreateBatchRequest.ArticleEntry entry = CreateBatchRequest.ArticleEntry.builder()
                .articleText("Generated article text.")
                .articleType("news_report")
                .isFaithful(true)
                .difficulty("MEDIUM")
                .sourceFacts(Map.of("branch", "legislative"))
                .groundTruth(Map.of("changed_facts", List.of()))
                .modelUsed("claude-sonnet-4-20250514")
                .tokensUsed(250)
                .build();

        return CreateBatchRequest.builder()
                .batchId(BATCH_ID)
                .branch("legislative")
                .modelUsed("claude-sonnet-4-20250514")
                .configJson(Map.of("entity_count", 1))
                .articlesCount(1)
                .faithfulCount(1)
                .perturbedCount(0)
                .totalTokens(250)
                .durationSeconds(2.5)
                .errors(List.of())
                .articles(List.of(entry))
                .build();
    }

    // =====================================================================
    // POST /api/eval/datasets/batches
    // =====================================================================

    @Nested
    class CreateBatchEndpoint {

        @Test
        void createBatch_returns201WithDTO() throws Exception {
            GenerationBatchDTO dto = sampleBatchDTO();
            when(evalDatasetService.createBatch(any(CreateBatchRequest.class))).thenReturn(dto);

            mockMvc.perform(post("/api/eval/datasets/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(BATCH_ID.toString())))
                    .andExpect(jsonPath("$.branch", is("legislative")))
                    .andExpect(jsonPath("$.articlesCount", is(7)));

            verify(evalDatasetService).createBatch(any(CreateBatchRequest.class));
        }

        @Test
        void createBatch_invalidRequest_returns400() throws Exception {
            // Missing required fields: batchId, modelUsed, configJson, articles
            String invalidJson = "{}";

            mockMvc.perform(post("/api/eval/datasets/batches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    // GET /api/eval/datasets/batches
    // =====================================================================

    @Test
    void listBatches_returnsPaginatedResults() throws Exception {
        Page<GenerationBatchDTO> page = new PageImpl<>(
                List.of(sampleBatchDTO()), PageRequest.of(0, 20), 1);
        when(evalDatasetService.listBatches(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/eval/datasets/batches")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(BATCH_ID.toString())))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    // =====================================================================
    // GET /api/eval/datasets/batches/{id}
    // =====================================================================

    @Nested
    class GetBatchEndpoint {

        @Test
        void getBatch_found_returnsDTO() throws Exception {
            when(evalDatasetService.getBatch(BATCH_ID)).thenReturn(sampleBatchDTO());

            mockMvc.perform(get("/api/eval/datasets/batches/" + BATCH_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(BATCH_ID.toString())))
                    .andExpect(jsonPath("$.modelUsed", is("claude-sonnet-4-20250514")));
        }

        @Test
        void getBatch_notFound_returns404() throws Exception {
            UUID unknownId = UUID.randomUUID();
            when(evalDatasetService.getBatch(unknownId))
                    .thenThrow(new ResourceNotFoundException("Batch", unknownId));

            mockMvc.perform(get("/api/eval/datasets/batches/" + unknownId))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    // GET /api/eval/datasets/batches/{id}/articles
    // =====================================================================

    @Test
    void getArticlesByBatch_returnsPaginatedArticles() throws Exception {
        Page<SyntheticArticleDTO> page = new PageImpl<>(
                List.of(sampleArticleDTO()), PageRequest.of(0, 20), 1);
        when(evalDatasetService.getArticlesByBatch(eq(BATCH_ID), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/eval/datasets/batches/" + BATCH_ID + "/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].batchId", is(BATCH_ID.toString())))
                .andExpect(jsonPath("$.content[0].perturbationType", is("wrong_party")));
    }

    // =====================================================================
    // DELETE /api/eval/datasets/batches/{id}
    // =====================================================================

    @Nested
    class DeleteBatchEndpoint {

        @Test
        void deleteBatch_exists_returns204() throws Exception {
            doNothing().when(evalDatasetService).deleteBatch(BATCH_ID);

            mockMvc.perform(delete("/api/eval/datasets/batches/" + BATCH_ID))
                    .andExpect(status().isNoContent());

            verify(evalDatasetService).deleteBatch(BATCH_ID);
        }

        @Test
        void deleteBatch_notFound_returns404() throws Exception {
            UUID unknownId = UUID.randomUUID();
            doThrow(new ResourceNotFoundException("Batch", unknownId))
                    .when(evalDatasetService).deleteBatch(unknownId);

            mockMvc.perform(delete("/api/eval/datasets/batches/" + unknownId))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    // GET /api/eval/datasets/articles
    // =====================================================================

    @Nested
    class QueryArticlesEndpoint {

        @Test
        void queryArticles_withFilters_returnsFilteredResults() throws Exception {
            Page<SyntheticArticleDTO> page = new PageImpl<>(
                    List.of(sampleArticleDTO()), PageRequest.of(0, 20), 1);
            when(evalDatasetService.queryArticles(any(DatasetQueryRequest.class), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/eval/datasets/articles")
                            .param("perturbationType", "wrong_party")
                            .param("difficulty", "EASY")
                            .param("branch", "legislative"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].difficulty", is("EASY")));
        }

        @Test
        void queryArticles_noFilters_returnsAll() throws Exception {
            Page<SyntheticArticleDTO> page = new PageImpl<>(
                    List.of(sampleArticleDTO()), PageRequest.of(0, 20), 1);
            when(evalDatasetService.queryArticles(any(DatasetQueryRequest.class), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/eval/datasets/articles"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }
    }

    // =====================================================================
    // GET /api/eval/datasets/articles/{id}
    // =====================================================================

    @Nested
    class GetArticleEndpoint {

        @Test
        void getArticle_found_returnsFullDTO() throws Exception {
            when(evalDatasetService.getArticle(ARTICLE_ID)).thenReturn(sampleArticleDTO());

            mockMvc.perform(get("/api/eval/datasets/articles/" + ARTICLE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(ARTICLE_ID.toString())))
                    .andExpect(jsonPath("$.articleText", is("A test article about a senator.")))
                    .andExpect(jsonPath("$.isFaithful", is(false)))
                    .andExpect(jsonPath("$.groundTruth", notNullValue()));
        }

        @Test
        void getArticle_notFound_returns404() throws Exception {
            UUID unknownId = UUID.randomUUID();
            when(evalDatasetService.getArticle(unknownId))
                    .thenThrow(new ResourceNotFoundException("Article", unknownId));

            mockMvc.perform(get("/api/eval/datasets/articles/" + unknownId))
                    .andExpect(status().isNotFound());
        }
    }

    // =====================================================================
    // GET /api/eval/datasets/stats
    // =====================================================================

    @Test
    void getStats_returnsAggregates() throws Exception {
        DatasetStatsDTO stats = DatasetStatsDTO.builder()
                .totalArticles(100)
                .faithfulCount(20)
                .perturbedCount(80)
                .byPerturbationType(Map.of("wrong_party", 40L, "wrong_state", 40L))
                .byDifficulty(Map.of("EASY", 50L, "MEDIUM", 30L, "HARD", 20L))
                .byBranch(Map.of("legislative", 60L, "executive", 40L))
                .build();
        when(evalDatasetService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/eval/datasets/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArticles", is(100)))
                .andExpect(jsonPath("$.faithfulCount", is(20)))
                .andExpect(jsonPath("$.perturbedCount", is(80)))
                .andExpect(jsonPath("$.byPerturbationType.wrong_party", is(40)))
                .andExpect(jsonPath("$.byDifficulty.EASY", is(50)))
                .andExpect(jsonPath("$.byBranch.legislative", is(60)));
    }
}
