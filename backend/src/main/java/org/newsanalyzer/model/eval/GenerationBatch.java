package org.newsanalyzer.model.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for the generation_batches table.
 * Stores metadata about a single batch generation run from the reasoning-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "generation_batches")
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(length = 20)
    private String branch;

    @Column(name = "model_used", nullable = false, length = 50)
    private String modelUsed;

    @Type(JsonBinaryType.class)
    @Column(name = "config_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> configJson;

    @Column(name = "articles_count", nullable = false)
    @Builder.Default
    private Integer articlesCount = 0;

    @Column(name = "faithful_count", nullable = false)
    @Builder.Default
    private Integer faithfulCount = 0;

    @Column(name = "perturbed_count", nullable = false)
    @Builder.Default
    private Integer perturbedCount = 0;

    @Column(name = "total_tokens", nullable = false)
    @Builder.Default
    private Integer totalTokens = 0;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> errors;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
