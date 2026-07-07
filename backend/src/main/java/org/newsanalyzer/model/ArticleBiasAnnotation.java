package org.newsanalyzer.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.util.Map;
import java.util.UUID;

/**
 * A single cognitive-bias/logical-fallacy annotation for an {@link Article},
 * as returned by the reasoning service's {@code POST /eval/bias/detect}
 * (Story ES-1.4). Raw signal only — not yet aggregated into
 * {@link Article#getReliabilityScore()}.
 *
 * Unlike {@link Entity}, this record has no meaning independent of its
 * source article, so {@code article_id} is a required (not nullable) FK
 * with {@code ON DELETE CASCADE} — see V47's migration comment for why that
 * deliberately diverges from {@code entities.article_id}'s {@code SET NULL}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@jakarta.persistence.Entity
@Table(name = "article_bias_annotations", indexes = {
    @Index(name = "idx_article_bias_annotations_article_id", columnList = "article_id")
})
public class ArticleBiasAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * FK to the source article. Required — an annotation with no article to
     * explain what it's about is meaningless.
     */
    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    /**
     * Snake-case ontology identifier (e.g. "hasty_generalization"), stored
     * as-is from the contract response.
     */
    @Column(name = "distortion_type", nullable = false, length = 100)
    private String distortionType;

    /**
     * "cognitive_bias" or "logical_fallacy", per the bias ontology.
     */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "excerpt", nullable = false, columnDefinition = "TEXT")
    private String excerpt;

    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "confidence", nullable = false)
    private Float confidence;

    /**
     * definition/academic_source/detection_pattern as one flexible blob,
     * mirroring {@link Entity#getProperties()}/{@link Entity#getSchemaOrgData()}'s
     * JSONB pattern. Nullable per the contract: only present when the
     * reasoning service was called with include_ontology_metadata=true and
     * grounded=true.
     */
    @Type(JsonBinaryType.class)
    @Column(name = "ontology_metadata", columnDefinition = "jsonb")
    private Map<String, Object> ontologyMetadata;
}
