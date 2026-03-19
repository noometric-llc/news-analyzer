package org.newsanalyzer.service.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.newsanalyzer.dto.eval.*;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.eval.GenerationBatch;
import org.newsanalyzer.model.eval.SyntheticArticle;
import org.newsanalyzer.repository.eval.GenerationBatchRepository;
import org.newsanalyzer.repository.eval.SyntheticArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalDatasetServiceTest {

    @Mock
    private GenerationBatchRepository batchRepository;

    @Mock
    private SyntheticArticleRepository articleRepository;

    private EvalDatasetService service;

    @BeforeEach
    void setUp() {
        service = new EvalDatasetService(batchRepository, articleRepository);
    }

    // --- Test data helpers ---

    private GenerationBatch sampleBatch() {
        return GenerationBatch.builder()
                .id(UUID.randomUUID())
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

    private SyntheticArticle sampleArticle(UUID batchId) {
        return SyntheticArticle.builder()
                .id(UUID.randomUUID())
                .batchId(batchId)
                .articleText("A test article about a senator.")
                .articleType("news_report")
                .isFaithful(false)
                .perturbationType("wrong_party")
                .difficulty("EASY")
                .sourceFacts(Map.of("branch", "legislative", "topic", "Senator Test"))
                .groundTruth(Map.of("changed_facts", List.of(), "expected_findings", List.of()))
                .modelUsed("claude-sonnet-4-20250514")
                .tokensUsed(300)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private CreateBatchRequest sampleCreateRequest() {
        UUID batchId = UUID.randomUUID();
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
                .batchId(batchId)
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

    // --- createBatch tests ---

    @Nested
    class CreateBatchTests {

        @Test
        void createBatch_savesBatchAndArticles() {
            // Arrange
            CreateBatchRequest request = sampleCreateRequest();
            GenerationBatch savedBatch = GenerationBatch.builder()
                    .id(request.getBatchId())
                    .branch(request.getBranch())
                    .modelUsed(request.getModelUsed())
                    .configJson(request.getConfigJson())
                    .articlesCount(request.getArticlesCount())
                    .faithfulCount(request.getFaithfulCount())
                    .perturbedCount(request.getPerturbedCount())
                    .totalTokens(request.getTotalTokens())
                    .durationSeconds(request.getDurationSeconds())
                    .errors(request.getErrors())
                    .createdAt(LocalDateTime.now())
                    .build();
            when(batchRepository.save(any(GenerationBatch.class))).thenReturn(savedBatch);
            when(articleRepository.saveAll(anyList())).thenReturn(List.of());

            // Act
            GenerationBatchDTO result = service.createBatch(request);

            // Assert
            assertNotNull(result);
            assertEquals(request.getBatchId(), result.getId());
            assertEquals("legislative", result.getBranch());
            assertEquals(1, result.getArticlesCount());
            verify(batchRepository).save(any(GenerationBatch.class));
            verify(articleRepository).saveAll(anyList());
        }

        @Test
        void createBatch_mapsAllArticleFields() {
            // Arrange
            CreateBatchRequest request = sampleCreateRequest();
            GenerationBatch savedBatch = GenerationBatch.builder()
                    .id(request.getBatchId())
                    .modelUsed(request.getModelUsed())
                    .configJson(request.getConfigJson())
                    .createdAt(LocalDateTime.now())
                    .build();
            when(batchRepository.save(any())).thenReturn(savedBatch);
            when(articleRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            service.createBatch(request);

            // Assert — capture saved articles and verify field mapping
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<SyntheticArticle>> captor = ArgumentCaptor.forClass(List.class);
            verify(articleRepository).saveAll(captor.capture());

            List<SyntheticArticle> saved = captor.getValue();
            assertEquals(1, saved.size());
            SyntheticArticle article = saved.get(0);
            assertEquals(request.getBatchId(), article.getBatchId());
            assertEquals("news_report", article.getArticleType());
            assertTrue(article.getIsFaithful());
            assertEquals("MEDIUM", article.getDifficulty());
        }
    }

    // --- getBatch tests ---

    @Nested
    class GetBatchTests {

        @Test
        void getBatch_returnsDTOWhenFound() {
            // Arrange
            GenerationBatch batch = sampleBatch();
            when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));

            // Act
            GenerationBatchDTO result = service.getBatch(batch.getId());

            // Assert
            assertEquals(batch.getId(), result.getId());
            assertEquals(batch.getModelUsed(), result.getModelUsed());
            assertEquals(batch.getArticlesCount(), result.getArticlesCount());
        }

        @Test
        void getBatch_throwsWhenNotFound() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(batchRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> service.getBatch(id));
        }
    }

    // --- listBatches tests ---

    @Test
    void listBatches_returnsPaginatedDTOs() {
        // Arrange
        GenerationBatch batch = sampleBatch();
        Page<GenerationBatch> page = new PageImpl<>(List.of(batch));
        when(batchRepository.findAll(any(Pageable.class))).thenReturn(page);

        // Act
        Page<GenerationBatchDTO> result = service.listBatches(PageRequest.of(0, 10));

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals(batch.getId(), result.getContent().get(0).getId());
    }

    // --- getArticle tests ---

    @Nested
    class GetArticleTests {

        @Test
        void getArticle_returnsDTOWhenFound() {
            // Arrange
            UUID batchId = UUID.randomUUID();
            SyntheticArticle article = sampleArticle(batchId);
            when(articleRepository.findById(article.getId())).thenReturn(Optional.of(article));

            // Act
            SyntheticArticleDTO result = service.getArticle(article.getId());

            // Assert
            assertEquals(article.getId(), result.getId());
            assertEquals(batchId, result.getBatchId());
            assertEquals("wrong_party", result.getPerturbationType());
            assertFalse(result.getIsFaithful());
        }

        @Test
        void getArticle_throwsWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(articleRepository.findById(id)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> service.getArticle(id));
        }
    }

    // --- getArticlesByBatch tests ---

    @Test
    void getArticlesByBatch_returnsPaginatedDTOs() {
        // Arrange
        UUID batchId = UUID.randomUUID();
        SyntheticArticle article = sampleArticle(batchId);
        Page<SyntheticArticle> page = new PageImpl<>(List.of(article));
        when(articleRepository.findByBatchId(eq(batchId), any(Pageable.class))).thenReturn(page);

        // Act
        Page<SyntheticArticleDTO> result = service.getArticlesByBatch(batchId, PageRequest.of(0, 10));

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals(batchId, result.getContent().get(0).getBatchId());
    }

    // --- queryArticles tests ---

    @Nested
    class QueryArticlesTests {

        @Test
        void queryArticles_passesFiltersToRepository() {
            // Arrange
            DatasetQueryRequest filter = DatasetQueryRequest.builder()
                    .perturbationType("wrong_party")
                    .difficulty("EASY")
                    .branch("legislative")
                    .build();
            Page<SyntheticArticle> page = new PageImpl<>(List.of());
            when(articleRepository.findByFilters(
                    anyString(), anyString(), anyString(), anyString(),
                    isNull(), isNull(), any(Pageable.class)
            )).thenReturn(page);

            // Act
            service.queryArticles(filter, PageRequest.of(0, 10));

            // Assert — verify branch is converted to JSONB containment format
            verify(articleRepository).findByFilters(
                    eq("wrong_party"),
                    eq("EASY"),
                    eq("legislative"),
                    eq("{\"branch\": \"legislative\"}"),
                    isNull(),
                    isNull(),
                    any(Pageable.class)
            );
        }

        @Test
        void queryArticles_nullBranchSkipsJsonb() {
            // Arrange
            DatasetQueryRequest filter = DatasetQueryRequest.builder()
                    .perturbationType("wrong_party")
                    .build();
            Page<SyntheticArticle> page = new PageImpl<>(List.of());
            when(articleRepository.findByFilters(
                    anyString(), isNull(), isNull(), isNull(),
                    isNull(), isNull(), any(Pageable.class)
            )).thenReturn(page);

            // Act
            service.queryArticles(filter, PageRequest.of(0, 10));

            // Assert
            verify(articleRepository).findByFilters(
                    eq("wrong_party"),
                    isNull(),
                    isNull(),
                    isNull(),
                    isNull(),
                    isNull(),
                    any(Pageable.class)
            );
        }
    }

    // --- getStats tests ---

    @Test
    void getStats_aggregatesCorrectly() {
        // Arrange
        when(articleRepository.count()).thenReturn(10L);
        when(articleRepository.findByIsFaithful(eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 3));
        when(articleRepository.countByPerturbationType()).thenReturn(List.of(
                new Object[]{"wrong_party", 4L},
                new Object[]{"wrong_state", 3L}
        ));
        when(articleRepository.countByDifficulty()).thenReturn(List.of(
                new Object[]{"EASY", 7L},
                new Object[]{"MEDIUM", 3L}
        ));
        when(articleRepository.countByBranch()).thenReturn(List.of(
                new Object[]{"legislative", 8L},
                new Object[]{"executive", 2L}
        ));

        // Act
        DatasetStatsDTO stats = service.getStats();

        // Assert
        assertEquals(10L, stats.getTotalArticles());
        assertEquals(3L, stats.getFaithfulCount());
        assertEquals(7L, stats.getPerturbedCount());
        assertEquals(4L, stats.getByPerturbationType().get("wrong_party"));
        assertEquals(7L, stats.getByDifficulty().get("EASY"));
        assertEquals(8L, stats.getByBranch().get("legislative"));
    }

    // --- deleteBatch tests ---

    @Nested
    class DeleteBatchTests {

        @Test
        void deleteBatch_deletesWhenExists() {
            UUID id = UUID.randomUUID();
            when(batchRepository.existsById(id)).thenReturn(true);

            service.deleteBatch(id);

            verify(batchRepository).deleteById(id);
        }

        @Test
        void deleteBatch_throwsWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(batchRepository.existsById(id)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> service.deleteBatch(id));
            verify(batchRepository, never()).deleteById(any());
        }
    }
}
