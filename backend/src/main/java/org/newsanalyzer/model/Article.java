package org.newsanalyzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.newsanalyzer.model.converter.ArticleStatusConverter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted, source-attributed news article — the Evidence Store's core record.
 *
 * Distinct from the government-data Factbase (authoritative structured records)
 * and from {@code SyntheticArticle} (synthetic eval-only data): this table holds
 * real ingested article content that {@link Entity} records can be traced back to.
 *
 * Maps to the {@code evidence_articles} table, not {@code articles} — a table
 * of that name already exists from V1__initial_schema.sql, a dead design never
 * wired to any application code (verified: zero references anywhere in this
 * codebase). Left untouched rather than reused or dropped.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.Entity
@Table(name = "evidence_articles", indexes = {
    @Index(name = "idx_evidence_articles_source_name", columnList = "source_name"),
    @Index(name = "idx_evidence_articles_publication_date", columnList = "publication_date")
})
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * Outlet/publication name (e.g. "CNN", "Fox News").
     */
    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    /**
     * Source URL. Nullable — not every ingestion path is guaranteed to have one.
     */
    @Column(name = "url", length = 1000)
    private String url;

    /**
     * Original publication date, as reported by the source.
     */
    @Column(name = "publication_date")
    private LocalDateTime publicationDate;

    /**
     * Full article text.
     */
    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    /**
     * When NewsAnalyzer ingested this article. Set automatically on persist.
     */
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;

    /**
     * Outcome of the entity-extraction call ({@code /entities/extract}).
     * Tracked separately from {@link #biasDetectionStatus} so partial failures
     * remain diagnosable — extraction can fail independently of bias detection.
     */
    @Convert(converter = ArticleStatusConverter.class)
    @Column(name = "extraction_status", nullable = false, length = 20)
    private ArticleStatus extractionStatus = ArticleStatus.PENDING;

    /**
     * Outcome of the bias/fallacy-detection call ({@code /eval/bias/detect}).
     * Best-effort: a FAILED status here must never block article or entity
     * persistence.
     */
    @Convert(converter = ArticleStatusConverter.class)
    @Column(name = "bias_detection_status", nullable = false, length = 20)
    private ArticleStatus biasDetectionStatus = ArticleStatus.PENDING;

    /**
     * Source-level reliability score. Always {@code null} at MVP — cross-article
     * aggregation methodology is deferred (sampling-representativeness and
     * correlated-bias risk). The column exists now so no future migration is
     * needed once that methodology lands.
     */
    @Column(name = "reliability_score")
    private Float reliabilityScore;

    @PrePersist
    protected void onCreate() {
        ingestedAt = LocalDateTime.now();
    }
}
