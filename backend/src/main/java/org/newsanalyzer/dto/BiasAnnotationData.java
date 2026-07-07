package org.newsanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A single bias/fallacy annotation, as returned by the reasoning service's
 * POST /eval/bias/detect endpoint. Field names map to the documented
 * snake_case contract (docs/api/reasoning-service-contract.md).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiasAnnotationData {

    /**
     * Snake-case ontology identifier (e.g. "hasty_generalization")
     */
    @JsonProperty("distortion_type")
    private String distortionType;

    /**
     * "cognitive_bias" or "logical_fallacy"
     */
    private String category;

    private String excerpt;

    private String explanation;

    private Float confidence;

    /**
     * definition/academic_source/detection_pattern, present only when the
     * request set include_ontology_metadata=true and grounded=true.
     */
    @JsonProperty("ontology_metadata")
    private Map<String, Object> ontologyMetadata;
}
