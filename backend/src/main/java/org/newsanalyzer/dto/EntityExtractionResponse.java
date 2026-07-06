package org.newsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from the reasoning service's POST /entities/extract endpoint.
 * See docs/api/reasoning-service-contract.md for the full contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityExtractionResponse {

    /**
     * Extracted entities meeting the confidence threshold
     */
    private List<ExtractedEntityData> entities;

    /**
     * Count of entities in the response
     */
    @JsonProperty("total_count")
    private Integer totalCount;
}
