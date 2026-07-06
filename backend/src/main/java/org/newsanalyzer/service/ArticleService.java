package org.newsanalyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.dto.EntityExtractionResponse;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.Article;
import org.newsanalyzer.model.ArticleStatus;
import org.newsanalyzer.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Service layer for Article operations.
 *
 * Story ES-1.3 adds entity-extraction orchestration on top of ES-1.2's
 * persistence-only createArticle(). IMPORTANT: createArticle() itself is
 * deliberately NOT @Transactional — see the class-level note on
 * extractAndPersistEntities() for why. Each individual persistence step
 * (articleRepository.save(), entityService.createEntitiesFromExtraction())
 * carries its own short transaction — the entity batch is one atomic
 * transaction covering the whole batch, not one per entity, see that
 * method's Javadoc — and the ~30s reasoning-service call in between runs
 * outside of any transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private static final float DEFAULT_CONFIDENCE_THRESHOLD = 0.7f;

    private final ArticleRepository articleRepository;
    private final ReasoningServiceClient reasoningServiceClient;
    private final EntityService entityService;

    /**
     * Persist a new article, then extract and link entities.
     *
     * Deliberately not @Transactional (must-fix from architecture review):
     * this method's body includes an external HTTP call that can take up to
     * 30 seconds. Wrapping the whole method in one transaction would hold a
     * DB connection idle for that entire time — with a small connection
     * pool, a handful of concurrent requests would exhaust it and stall
     * every other DB operation in the app. Instead, each DB-touching step
     * below is its own short, independent transaction (articleRepository's
     * methods and entityService.createEntitiesFromExtraction() each carry
     * their own @Transactional), with the slow call sitting outside all of
     * them.
     */
    public ArticleDTO createArticle(CreateArticleRequest request) {
        log.info("Creating article: source={}", request.getSourceName());

        Article article = new Article();
        article.setSourceName(request.getSourceName());
        article.setUrl(request.getUrl());
        article.setPublicationDate(request.getPublicationDate());
        article.setRawText(request.getRawText());

        // Step 1: persist the article. articleRepository.save() carries its
        // own transaction (Spring Data JPA's default per-method behavior) —
        // no explicit @Transactional needed here.
        Article saved = articleRepository.save(article);
        log.info("Created article: id={}, source={}", saved.getId(), saved.getSourceName());

        // Step 2 (no transaction) + Step 3 (its own short transaction, on
        // success): call the reasoning service, then persist any extracted
        // entities. Extraction failure never blocks article persistence —
        // saved already exists regardless of what happens next.
        Article withExtractionResult = extractAndPersistEntities(saved);

        return toDTO(withExtractionResult);
    }

    /**
     * Calls the reasoning service (no transaction) and, on success, persists
     * the extracted entities as one atomic batch (entityService.createEntitiesFromExtraction()) —
     * either all entities in the response are persisted, or none are. On any
     * failure — network, timeout, unrecognized entity data, or unexpected —
     * extractionStatus is set to FAILED and the article is left otherwise
     * untouched. Per NFR3, this must never prevent the article itself from
     * having been persisted.
     */
    private Article extractAndPersistEntities(Article article) {
        try {
            EntityExtractionResponse response =
                reasoningServiceClient.extractEntities(article.getRawText(), DEFAULT_CONFIDENCE_THRESHOLD);

            if (response != null && response.getEntities() != null && !response.getEntities().isEmpty()) {
                // Atomic batch: entityService.createEntitiesFromExtraction() persists
                // all-or-nothing, so a single malformed entity can't leave the rest
                // of the batch orphaned under a FAILED status (see its Javadoc).
                entityService.createEntitiesFromExtraction(response.getEntities(), article.getId());
                log.info("Extraction succeeded for article {}: {} entities linked",
                    article.getId(), response.getEntities().size());
            }

            article.setExtractionStatus(ArticleStatus.SUCCESS);
        } catch (RestClientException e) {
            log.warn("Entity extraction failed for article {}: {}", article.getId(), e.getMessage());
            article.setExtractionStatus(ArticleStatus.FAILED);
        } catch (Exception e) {
            log.error("Unexpected error during entity extraction for article {}: {}",
                article.getId(), e.getMessage(), e);
            article.setExtractionStatus(ArticleStatus.FAILED);
        }

        // Step 4: persist the status update — its own short transaction.
        return articleRepository.save(article);
    }

    /**
     * Get article by id
     */
    @Transactional(readOnly = true)
    public ArticleDTO getArticleById(UUID id) {
        log.debug("Fetching article by id: {}", id);
        Article article = articleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Article", id));
        return toDTO(article);
    }

    private ArticleDTO toDTO(Article article) {
        ArticleDTO dto = new ArticleDTO();
        dto.setId(article.getId());
        dto.setSourceName(article.getSourceName());
        dto.setUrl(article.getUrl());
        dto.setPublicationDate(article.getPublicationDate());
        dto.setRawText(article.getRawText());
        dto.setIngestedAt(article.getIngestedAt());
        dto.setExtractionStatus(article.getExtractionStatus());
        dto.setBiasDetectionStatus(article.getBiasDetectionStatus());
        dto.setReliabilityScore(article.getReliabilityScore());
        return dto;
    }
}
