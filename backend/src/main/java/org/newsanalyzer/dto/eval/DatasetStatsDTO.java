package org.newsanalyzer.dto.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Aggregate statistics for the eval dataset.
 * Each map is keyed by category value (e.g., "wrong_party") with count as value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetStatsDTO {

    private long totalArticles;
    private long faithfulCount;
    private long perturbedCount;
    private Map<String, Long> byPerturbationType;
    private Map<String, Long> byDifficulty;
    private Map<String, Long> byBranch;
}
