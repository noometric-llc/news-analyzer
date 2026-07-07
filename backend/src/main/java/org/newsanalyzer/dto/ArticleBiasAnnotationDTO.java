package org.newsanalyzer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for a single ArticleBiasAnnotation.
 *
 * Used wherever an Article's bias/fallacy annotations are exposed
 * (e.g. a future GET /api/articles/{id}/annotations, or embedded in a
 * grounded-query response per Story ES-1.5).
 */
@Schema(description = """
    A single cognitive-bias/logical-fallacy annotation for an Article, from \
    POST /eval/bias/detect. Raw signal only — not yet aggregated into a \
    reliability score.
    """)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleBiasAnnotationDTO {

    private UUID id;

    private UUID articleId;

    /**
     * Snake-case ontology identifier (e.g. "hasty_generalization")
     */
    private String distortionType;

    /**
     * "cognitive_bias" or "logical_fallacy"
     */
    private String category;

    private String excerpt;

    private String explanation;

    private Float confidence;

    /**
     * definition/academic_source/detection_pattern, when present
     */
    private Map<String, Object> ontologyMetadata;
}
