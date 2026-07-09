package org.newsanalyzer.dto.eval;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.newsanalyzer.dto.ArticleBiasAnnotationDTO;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.EntityDTO;

import java.util.List;

/**
 * Bundles a real, ingested Article with its linked Entity and
 * ArticleBiasAnnotation records, for the eval harness's real-article
 * read path (Story ES-1.6).
 *
 * Read-only: every field here reflects data already computed by the
 * production ingestion pipeline (ES-1.1-ES-1.4) — this DTO triggers no
 * re-extraction or scoring. See ES-1.6's Scope Decision for why: real
 * articles have no curated ground truth, so there is nothing to score
 * against yet.
 */
@Schema(description = """
    A real Article bundled with its linked entities and bias annotations, \
    for eval-harness read access. Distinct from SyntheticArticleDTO — this \
    represents real ingested content, not EVAL-1-generated test data.
    """)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RealArticleEvaluationDTO {

    private ArticleDTO article;

    private List<EntityDTO> entities;

    private List<ArticleBiasAnnotationDTO> annotations;
}
