package org.newsanalyzer.dto.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Filter parameters for querying synthetic articles.
 * All fields are optional — null means "don't filter by this".
 * Bound from query parameters on GET /api/eval/datasets/articles.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetQueryRequest {

    private String perturbationType;
    private String difficulty;
    private String branch;
    private UUID batchId;
    private Boolean isFaithful;
}
