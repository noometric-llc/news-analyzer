package org.newsanalyzer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for submitting a new article.
 *
 * Used for POST /api/articles
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateArticleRequest {

    @NotBlank(message = "Source name is required")
    @Size(max = 255, message = "Source name must not exceed 255 characters")
    private String sourceName;

    /**
     * Optional source URL
     */
    @Size(max = 1000, message = "URL must not exceed 1000 characters")
    private String url;

    /**
     * Optional original publication date, as reported by the source
     */
    private LocalDateTime publicationDate;

    /**
     * Full article text.
     *
     * Capped at 100,000 characters — basic hygiene against abusive payloads,
     * separate from rate limiting (deferred to ES-1.3, once the cost-bearing
     * reasoning-service call exists to actually protect against).
     */
    @NotBlank(message = "Article text is required")
    @Size(max = 100_000, message = "Article text must not exceed 100,000 characters")
    private String rawText;
}
