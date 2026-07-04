package org.newsanalyzer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.model.Article;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.repository.ArticleRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ArticleService with mocked dependencies.
 * Mirrors EntityServiceTest's structure.
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

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

    @Test
    void testCreateArticle() {
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);

        ArticleDTO result = articleService.createArticle(createRequest);

        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals("CNN", result.getSourceName());
        assertEquals(ArticleStatus.PENDING, result.getExtractionStatus());
        assertEquals(ArticleStatus.PENDING, result.getBiasDetectionStatus());
        assertNull(result.getReliabilityScore());

        verify(articleRepository).save(any(Article.class));
    }

    @Test
    void testCreateArticlePersistsRequestFields() {
        // Proves the CreateArticleRequest -> Article mapping in ArticleService
        // actually carries every field through, not just the ones asserted
        // on the returned DTO above.
        when(articleRepository.save(any(Article.class))).thenReturn(testArticle);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        articleService.createArticle(createRequest);

        verify(articleRepository).save(captor.capture());
        Article persisted = captor.getValue();
        assertEquals("CNN", persisted.getSourceName());
        assertEquals("https://example.com/article", persisted.getUrl());
        assertEquals(createRequest.getPublicationDate(), persisted.getPublicationDate());
        assertEquals("Full article text...", persisted.getRawText());
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
