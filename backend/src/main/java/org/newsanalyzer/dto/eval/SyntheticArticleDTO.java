package org.newsanalyzer.dto.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for a synthetic article with ground-truth labels.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyntheticArticleDTO {

    private UUID id;
    private UUID batchId;
    private String articleText;
    private String articleType;
    private Boolean isFaithful;
    private String perturbationType;
    private String difficulty;
    private Map<String, Object> sourceFacts;
    private Map<String, Object> groundTruth;
    private String modelUsed;
    private Integer tokensUsed;
    private LocalDateTime createdAt;
}
