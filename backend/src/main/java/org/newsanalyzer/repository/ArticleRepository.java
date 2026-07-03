package org.newsanalyzer.repository;

import org.newsanalyzer.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Article persistence operations.
 *
 * Provides standard CRUD (via JpaRepository) for Story ES-1.1. Query methods
 * beyond CRUD are intentionally not added here — they belong to whichever
 * later story (ES-1.2 ingestion API, ES-1.5 grounded-query interface) first
 * needs them.
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {
}
