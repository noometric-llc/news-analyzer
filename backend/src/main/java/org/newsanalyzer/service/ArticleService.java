package org.newsanalyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.Article;
import org.newsanalyzer.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service layer for Article operations.
 *
 * Persistence only at this stage (Story ES-1.2) — no entity-extraction or
 * bias-detection calls happen here. That's ES-1.3/ES-1.4's ArticleService
 * extension, once ReasoningServiceClient exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;

    /**
     * Persist a new article
     */
    @Transactional
    public ArticleDTO createArticle(CreateArticleRequest request) {
        log.info("Creating article: source={}", request.getSourceName());

        Article article = new Article();
        article.setSourceName(request.getSourceName());
        article.setUrl(request.getUrl());
        article.setPublicationDate(request.getPublicationDate());
        article.setRawText(request.getRawText());

        Article saved = articleRepository.save(article);
        log.info("Created article: id={}, source={}", saved.getId(), saved.getSourceName());

        return toDTO(saved);
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
