package org.newsanalyzer.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.newsanalyzer.TestcontainersConfiguration;
import org.newsanalyzer.model.Article;
import org.newsanalyzer.model.ArticleBiasAnnotation;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.model.Entity;
import org.newsanalyzer.model.EntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ArticleRepository.
 * Uses PostgreSQL Testcontainer for full PostgreSQL feature support (real
 * schema, CHECK constraints, FK enforcement) — mirrors EntityRepositoryTest's
 * setup exactly.
 *
 * Added during QA review of Story ES-1.1: closes the gap where ArticleStatus's
 * enum<->varchar conversion (ArticleStatusConverter) and the V45 migration's
 * CHECK constraints were only verified by inference (successful Flyway apply),
 * never by an actual data round-trip.
 */
@DataJpaTest
@ActiveProfiles("tc")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ArticleRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private EntityRepository entityRepository;

    @Autowired
    private ArticleBiasAnnotationRepository articleBiasAnnotationRepository;

    private Article testArticle;

    @BeforeEach
    void setUp() {
        articleBiasAnnotationRepository.deleteAll();
        articleRepository.deleteAll();
        entityRepository.deleteAll();

        testArticle = new Article();
        testArticle.setSourceName("CNN");
        testArticle.setUrl("https://example.com/article");
        testArticle.setPublicationDate(LocalDateTime.of(2026, 6, 30, 14, 0));
        testArticle.setRawText("Full article text...");
    }

    @Test
    void testSaveArticle() {
        Article saved = articleRepository.save(testArticle);

        assertNotNull(saved.getId());
        assertEquals("CNN", saved.getSourceName());
        assertNotNull(saved.getIngestedAt());
    }

    @Test
    void testFindById() {
        Article saved = articleRepository.save(testArticle);
        entityManager.flush();

        Optional<Article> found = articleRepository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("CNN", found.get().getSourceName());
    }

    @Test
    void testDefaultStatusValuesRoundTripThroughDatabase() {
        // Proves ArticleStatusConverter correctly writes and reads back the
        // Java-level PENDING default against the V45 migration's
        // CHECK (extraction_status IN ('pending', 'success', 'failed')) column.
        Article saved = articleRepository.save(testArticle);
        entityManager.flush();
        entityManager.clear();

        Optional<Article> found = articleRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(ArticleStatus.PENDING, found.get().getExtractionStatus());
        assertEquals(ArticleStatus.PENDING, found.get().getBiasDetectionStatus());
    }

    @Test
    void testAllArticleStatusValuesRoundTrip() {
        // Exercises every enum value against the real CHECK constraint —
        // this is exactly what would silently break if the converter's
        // getValue() strings ever drifted from the migration's CHECK list.
        for (ArticleStatus status : ArticleStatus.values()) {
            Article article = new Article();
            article.setSourceName("Test Source");
            article.setRawText("Text for " + status);
            article.setExtractionStatus(status);

            Article saved = articleRepository.save(article);
            entityManager.flush();
            entityManager.clear();

            Optional<Article> found = articleRepository.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals(status, found.get().getExtractionStatus());
        }
    }

    @Test
    void testNullableUrlPersistedCorrectly() {
        Article article = new Article();
        article.setSourceName("Manual Transcription");
        article.setRawText("Text with no known URL.");

        Article saved = articleRepository.save(article);
        entityManager.flush();
        entityManager.clear();

        Optional<Article> found = articleRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertNull(found.get().getUrl());
    }

    @Test
    void testReliabilityScoreRemainsNullAfterPersist() {
        Article saved = articleRepository.save(testArticle);
        entityManager.flush();
        entityManager.clear();

        Optional<Article> found = articleRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertNull(found.get().getReliabilityScore());
    }

    @Test
    void testUpdateArticle() {
        Article saved = articleRepository.save(testArticle);
        entityManager.flush();

        saved.setExtractionStatus(ArticleStatus.SUCCESS);
        Article updated = articleRepository.save(saved);
        entityManager.flush();
        entityManager.clear();

        Optional<Article> found = articleRepository.findById(updated.getId());
        assertTrue(found.isPresent());
        assertEquals(ArticleStatus.SUCCESS, found.get().getExtractionStatus());
    }

    @Test
    void testDeleteArticle() {
        Article saved = articleRepository.save(testArticle);
        entityManager.flush();

        articleRepository.deleteById(saved.getId());
        entityManager.flush();

        Optional<Article> found = articleRepository.findById(saved.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testEntityArticleIdForeignKeyRoundTrip() {
        // Proves the fk_entities_article constraint and Entity.articleId
        // mapping actually work end-to-end against real Postgres — not just
        // that the migration applies, but that the link it creates is usable.
        Article savedArticle = articleRepository.save(testArticle);
        entityManager.flush();

        Entity entity = new Entity();
        entity.setEntityType(EntityType.PERSON);
        entity.setName("Elizabeth Warren");
        entity.setArticleId(savedArticle.getId());

        Entity savedEntity = entityRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        Optional<Entity> found = entityRepository.findById(savedEntity.getId());
        assertTrue(found.isPresent());
        assertEquals(savedArticle.getId(), found.get().getArticleId());
    }

    @Test
    void testEntityArticleIdSetNullOnArticleDelete() {
        // Proves the ON DELETE SET NULL behavior from V46 — deleting an
        // Article must not cascade-delete the Entity, only null its link.
        Article savedArticle = articleRepository.save(testArticle);
        entityManager.flush();

        Entity entity = new Entity();
        entity.setEntityType(EntityType.PERSON);
        entity.setName("Elizabeth Warren");
        entity.setArticleId(savedArticle.getId());
        Entity savedEntity = entityRepository.save(entity);
        entityManager.flush();

        articleRepository.deleteById(savedArticle.getId());
        entityManager.flush();
        entityManager.clear();

        Optional<Entity> found = entityRepository.findById(savedEntity.getId());
        assertTrue(found.isPresent(), "Entity must survive its linked Article's deletion");
        assertNull(found.get().getArticleId());
    }

    @Test
    void testArticleBiasAnnotationCascadeDeleteOnArticleDelete() {
        // Proves the ON DELETE CASCADE behavior from V47 — deliberately the
        // opposite of entities.article_id's SET NULL, since an annotation has
        // no independent meaning without its source article (see V47's
        // migration comment and ArticleBiasAnnotation's Javadoc).
        Article savedArticle = articleRepository.save(testArticle);
        entityManager.flush();

        ArticleBiasAnnotation annotation = new ArticleBiasAnnotation();
        annotation.setArticleId(savedArticle.getId());
        annotation.setDistortionType("hasty_generalization");
        annotation.setCategory("cognitive_bias");
        annotation.setExcerpt("The administration has always been corrupt");
        annotation.setExplanation("Uses absolute language to generalize from limited evidence.");
        annotation.setConfidence(0.87f);
        ArticleBiasAnnotation savedAnnotation = articleBiasAnnotationRepository.save(annotation);
        entityManager.flush();

        articleRepository.deleteById(savedArticle.getId());
        entityManager.flush();
        entityManager.clear();

        Optional<ArticleBiasAnnotation> found = articleBiasAnnotationRepository.findById(savedAnnotation.getId());
        assertFalse(found.isPresent(), "Annotation must be deleted along with its Article, not orphaned");
    }
}
