package org.newsanalyzer.repository;

import org.newsanalyzer.model.ArticleBiasAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for ArticleBiasAnnotation persistence operations.
 *
 * Provides standard CRUD (via JpaRepository) for Story ES-1.4, plus one query
 * method added by Story ES-1.6.
 */
@Repository
public interface ArticleBiasAnnotationRepository extends JpaRepository<ArticleBiasAnnotation, UUID> {

    /**
     * Find annotations linked to a specific article (Story ES-1.6)
     */
    List<ArticleBiasAnnotation> findByArticleId(UUID articleId);
}
