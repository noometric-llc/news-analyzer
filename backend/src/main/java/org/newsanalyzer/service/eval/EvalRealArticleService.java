package org.newsanalyzer.service.eval;

import org.newsanalyzer.dto.ArticleBiasAnnotationDTO;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.EntityDTO;
import org.newsanalyzer.dto.eval.RealArticleEvaluationDTO;
import org.newsanalyzer.model.ArticleBiasAnnotation;
import org.newsanalyzer.model.Entity;
import org.newsanalyzer.repository.ArticleBiasAnnotationRepository;
import org.newsanalyzer.repository.EntityRepository;
import org.newsanalyzer.service.ArticleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only bundling of a real Article with its linked Entity and
 * ArticleBiasAnnotation records, for the eval harness's real-article read
 * path (Story ES-1.6).
 *
 * Delegates the Article lookup (including 404 handling) to the existing
 * ArticleService.getArticleById() rather than re-querying ArticleRepository
 * and re-deriving ResourceNotFoundException handling — that logic is
 * already stable and QA-verified, and this class has no reason to duplicate
 * it.
 *
 * Deliberately does not trigger extraction or bias-detection, and does not
 * gate on extractionStatus/biasDetectionStatus being SUCCESS — the bundled
 * data (however partial) is returned as-is, with both statuses visible on
 * the embedded ArticleDTO, so the caller can judge what's trustworthy.
 */
@Service
@Transactional(readOnly = true)
public class EvalRealArticleService {

    private final ArticleService articleService;
    private final EntityRepository entityRepository;
    private final ArticleBiasAnnotationRepository articleBiasAnnotationRepository;

    public EvalRealArticleService(
            ArticleService articleService,
            EntityRepository entityRepository,
            ArticleBiasAnnotationRepository articleBiasAnnotationRepository) {
        this.articleService = articleService;
        this.entityRepository = entityRepository;
        this.articleBiasAnnotationRepository = articleBiasAnnotationRepository;
    }

    public RealArticleEvaluationDTO getRealArticleEvaluation(UUID articleId) {
        ArticleDTO article = articleService.getArticleById(articleId);

        List<EntityDTO> entities = entityRepository.findByArticleId(articleId).stream()
            .map(this::toEntityDTO)
            .collect(Collectors.toList());

        List<ArticleBiasAnnotationDTO> annotations = articleBiasAnnotationRepository.findByArticleId(articleId).stream()
            .map(this::toAnnotationDTO)
            .collect(Collectors.toList());

        return new RealArticleEvaluationDTO(article, entities, annotations);
    }

    private EntityDTO toEntityDTO(Entity entity) {
        EntityDTO dto = new EntityDTO();
        dto.setId(entity.getId());
        dto.setEntityType(entity.getEntityType());
        dto.setName(entity.getName());
        dto.setProperties(entity.getProperties());
        dto.setSchemaOrgType(entity.getSchemaOrgType());
        dto.setSchemaOrgData(entity.getSchemaOrgData());
        dto.setSource(entity.getSource());
        dto.setConfidenceScore(entity.getConfidenceScore());
        dto.setVerified(entity.getVerified());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (entity.getGovernmentOrganization() != null) {
            dto.setGovernmentOrganizationId(entity.getGovernmentOrganization().getId());
            dto.setGovernmentOrganizationName(entity.getGovernmentOrganization().getOfficialName());
        }

        return dto;
    }

    private ArticleBiasAnnotationDTO toAnnotationDTO(ArticleBiasAnnotation annotation) {
        ArticleBiasAnnotationDTO dto = new ArticleBiasAnnotationDTO();
        dto.setId(annotation.getId());
        dto.setArticleId(annotation.getArticleId());
        dto.setDistortionType(annotation.getDistortionType());
        dto.setCategory(annotation.getCategory());
        dto.setExcerpt(annotation.getExcerpt());
        dto.setExplanation(annotation.getExplanation());
        dto.setConfidence(annotation.getConfidence());
        dto.setOntologyMetadata(annotation.getOntologyMetadata());
        return dto;
    }
}
