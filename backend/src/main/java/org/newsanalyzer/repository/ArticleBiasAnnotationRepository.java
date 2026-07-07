package org.newsanalyzer.repository;

import org.newsanalyzer.model.ArticleBiasAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for ArticleBiasAnnotation persistence operations.
 *
 * Provides standard CRUD (via JpaRepository) for Story ES-1.4. Query methods
 * beyond CRUD are intentionally not added here, mirroring ArticleRepository's
 * bare-repository precedent — they belong to whichever later story
 * (e.g. ES-1.5 grounded-query interface) first needs them.
 */
@Repository
public interface ArticleBiasAnnotationRepository extends JpaRepository<ArticleBiasAnnotation, UUID> {
}
