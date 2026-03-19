package org.newsanalyzer.repository.eval;

import org.newsanalyzer.model.eval.SyntheticArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for SyntheticArticle entities.
 *
 * Simple single-column filters use Spring Data query derivation.
 * Combined filters and JSONB branch queries use native SQL.
 */
@Repository
public interface SyntheticArticleRepository extends JpaRepository<SyntheticArticle, UUID> {

    // --- Single-column filters (Spring Data derived queries) ---

    Page<SyntheticArticle> findByBatchId(UUID batchId, Pageable pageable);

    Page<SyntheticArticle> findByPerturbationType(String perturbationType, Pageable pageable);

    Page<SyntheticArticle> findByDifficulty(String difficulty, Pageable pageable);

    Page<SyntheticArticle> findByIsFaithful(Boolean isFaithful, Pageable pageable);

    // --- Combined filter (native query for JSONB branch filtering) ---

    /**
     * Query articles with optional combined filters.
     * All parameters are optional — pass null to skip a filter.
     * Branch filtering uses PostgreSQL JSONB containment operator (@>)
     * which leverages the GIN index on source_facts.
     */
    @Query(value = """
            SELECT * FROM synthetic_articles sa
            WHERE (:perturbationType IS NULL OR sa.perturbation_type = :perturbationType)
              AND (:difficulty IS NULL OR sa.difficulty = :difficulty)
              AND (:branch IS NULL OR sa.source_facts @> CAST(:branchJson AS jsonb))
              AND (:batchId IS NULL OR sa.batch_id = :batchId)
              AND (:isFaithful IS NULL OR sa.is_faithful = :isFaithful)
            """,
            countQuery = """
            SELECT COUNT(*) FROM synthetic_articles sa
            WHERE (:perturbationType IS NULL OR sa.perturbation_type = :perturbationType)
              AND (:difficulty IS NULL OR sa.difficulty = :difficulty)
              AND (:branch IS NULL OR sa.source_facts @> CAST(:branchJson AS jsonb))
              AND (:batchId IS NULL OR sa.batch_id = :batchId)
              AND (:isFaithful IS NULL OR sa.is_faithful = :isFaithful)
            """,
            nativeQuery = true)
    Page<SyntheticArticle> findByFilters(
            @Param("perturbationType") String perturbationType,
            @Param("difficulty") String difficulty,
            @Param("branch") String branch,
            @Param("branchJson") String branchJson,
            @Param("batchId") UUID batchId,
            @Param("isFaithful") Boolean isFaithful,
            Pageable pageable
    );

    // --- Aggregate stats queries (native for GROUP BY projections) ---

    @Query(value = """
            SELECT perturbation_type AS key, COUNT(*) AS count
            FROM synthetic_articles
            GROUP BY perturbation_type
            ORDER BY count DESC
            """, nativeQuery = true)
    List<Object[]> countByPerturbationType();

    @Query(value = """
            SELECT difficulty AS key, COUNT(*) AS count
            FROM synthetic_articles
            GROUP BY difficulty
            ORDER BY count DESC
            """, nativeQuery = true)
    List<Object[]> countByDifficulty();

    @Query(value = """
            SELECT source_facts->>'branch' AS key, COUNT(*) AS count
            FROM synthetic_articles
            GROUP BY source_facts->>'branch'
            ORDER BY count DESC
            """, nativeQuery = true)
    List<Object[]> countByBranch();
}
