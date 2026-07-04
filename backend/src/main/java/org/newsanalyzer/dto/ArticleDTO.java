package org.newsanalyzer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.newsanalyzer.model.ArticleStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Article API requests and responses.
 *
 * This DTO is used for:
 * - API responses (GET /api/articles/{id})
 * - API responses (POST /api/articles)
 */
@Schema(description = """
    A persisted, source-attributed news article — the Evidence Store's core record. \
    Distinct from GovernmentOrganization (authoritative Factbase data): this represents \
    real ingested article content that Entity records can be traced back to via articleId.
    """)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDTO {

    private UUID id;

    /**
     * Outlet/publication name (e.g. "CNN", "Fox News")
     */
    private String sourceName;

    /**
     * Source URL, if known
     */
    private String url;

    /**
     * Original publication date, as reported by the source
     */
    private LocalDateTime publicationDate;

    /**
     * Full article text
     */
    private String rawText;

    /**
     * When NewsAnalyzer ingested this article
     */
    private LocalDateTime ingestedAt;

    /**
     * Outcome of the /entities/extract call for this article
     */
    private ArticleStatus extractionStatus;

    /**
     * Outcome of the /eval/bias/detect call for this article
     */
    private ArticleStatus biasDetectionStatus;

    /**
     * Deferred cross-article reliability score; always null at MVP
     */
    private Float reliabilityScore;
}
