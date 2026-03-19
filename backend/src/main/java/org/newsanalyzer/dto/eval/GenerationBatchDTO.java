package org.newsanalyzer.dto.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for a generation batch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerationBatchDTO {

    private UUID id;
    private String branch;
    private String modelUsed;
    private Map<String, Object> configJson;
    private Integer articlesCount;
    private Integer faithfulCount;
    private Integer perturbedCount;
    private Integer totalTokens;
    private Double durationSeconds;
    private List<String> errors;
    private LocalDateTime createdAt;
}
