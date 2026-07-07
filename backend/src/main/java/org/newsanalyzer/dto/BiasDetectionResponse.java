package org.newsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response from the reasoning service's POST /eval/bias/detect endpoint.
 * See docs/api/reasoning-service-contract.md for the full contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiasDetectionResponse {

    /**
     * Detected bias/fallacy annotations
     */
    private List<BiasAnnotationData> annotations;

    /**
     * Count of annotations in the response
     */
    @JsonProperty("total_count")
    private Integer totalCount;

    /**
     * All bias types checked during this request
     */
    @JsonProperty("distortions_checked")
    private List<String> distortionsChecked;

    /**
     * Whether the bias ontology passed SHACL validation at startup
     */
    @JsonProperty("shacl_valid")
    private Boolean shaclValid;
}
