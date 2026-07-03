package org.newsanalyzer.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Article model class.
 * Tests field mapping, default status/reliability values, and the
 * @PrePersist timestamp behavior.
 */
class ArticleTest {

    private Article article;

    @BeforeEach
    void setUp() {
        article = new Article();
    }

    @Test
    void testArticleCreation() {
        article.setSourceName("CNN");
        article.setUrl("https://example.com/article");
        article.setPublicationDate(LocalDateTime.of(2026, 6, 30, 14, 0));
        article.setRawText("Full article text...");

        assertEquals("CNN", article.getSourceName());
        assertEquals("https://example.com/article", article.getUrl());
        assertEquals(LocalDateTime.of(2026, 6, 30, 14, 0), article.getPublicationDate());
        assertEquals("Full article text...", article.getRawText());
    }

    @Test
    void testDefaultValues() {
        assertEquals(ArticleStatus.PENDING, article.getExtractionStatus());
        assertEquals(ArticleStatus.PENDING, article.getBiasDetectionStatus());
        assertNull(article.getReliabilityScore());
    }

    @Test
    void testUrlIsNullable() {
        article.setSourceName("Manual Transcription");
        article.setRawText("Text with no known URL.");

        assertNull(article.getUrl());
    }

    @Test
    void testExtractionStatusIndependentOfBiasDetectionStatus() {
        article.setExtractionStatus(ArticleStatus.SUCCESS);
        article.setBiasDetectionStatus(ArticleStatus.FAILED);

        assertEquals(ArticleStatus.SUCCESS, article.getExtractionStatus());
        assertEquals(ArticleStatus.FAILED, article.getBiasDetectionStatus());
    }

    @Test
    void testReliabilityScoreRemainsNullAtMvp() {
        // FR7: reliability_score is schema-only at MVP; no code path should
        // populate it yet. This test documents that expectation.
        article.setSourceName("CNN");
        article.setRawText("Some text");

        assertNull(article.getReliabilityScore());
    }

    @Test
    void testIngestedAtSetOnPrePersist() {
        assertNull(article.getIngestedAt());

        article.onCreate();

        assertNotNull(article.getIngestedAt());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime pubDate = LocalDateTime.of(2026, 6, 30, 14, 0);
        LocalDateTime ingestedAt = LocalDateTime.of(2026, 7, 3, 9, 0);

        Article testArticle = new Article(
            null,               // id
            "Fox News",         // sourceName
            "https://example.com/fox-article", // url
            pubDate,
            "Some article text.",
            ingestedAt,
            ArticleStatus.SUCCESS,
            ArticleStatus.PENDING,
            null                // reliabilityScore
        );

        assertEquals("Fox News", testArticle.getSourceName());
        assertEquals(pubDate, testArticle.getPublicationDate());
        assertEquals(ArticleStatus.SUCCESS, testArticle.getExtractionStatus());
        assertEquals(ArticleStatus.PENDING, testArticle.getBiasDetectionStatus());
        assertNull(testArticle.getReliabilityScore());
    }

    @Test
    void testArticleStatusValues() {
        for (ArticleStatus status : ArticleStatus.values()) {
            article.setExtractionStatus(status);
            assertEquals(status, article.getExtractionStatus());
        }
    }
}
