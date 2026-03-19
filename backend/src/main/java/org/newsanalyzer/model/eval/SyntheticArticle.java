package org.newsanalyzer.model.eval;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for the synthetic_articles table.
 * Stores a generated article with its ground-truth labels.
 *
 * Uses the dual-column FK pattern for batch_id:
 * - batchId (UUID) is exposed to the API
 * - batch (entity ref) is for JPA navigation only, marked @JsonIgnore
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "synthetic_articles")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    @JsonIgnore
    private GenerationBatch batch;

    @Column(name = "article_text", nullable = false, columnDefinition = "TEXT")
    private String articleText;

    @Column(name = "article_type", nullable = false, length = 30)
    private String articleType;

    @Column(name = "is_faithful", nullable = false)
    private Boolean isFaithful;

    @Column(name = "perturbation_type", length = 30)
    private String perturbationType;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String difficulty = "MEDIUM";

    @Type(JsonBinaryType.class)
    @Column(name = "source_facts", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> sourceFacts;

    @Type(JsonBinaryType.class)
    @Column(name = "ground_truth", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> groundTruth;

    @Column(name = "model_used", nullable = false, length = 50)
    private String modelUsed;

    @Column(name = "tokens_used", nullable = false)
    @Builder.Default
    private Integer tokensUsed = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
