package org.newsanalyzer.service.eval;

import org.newsanalyzer.dto.eval.*;
import org.newsanalyzer.exception.ResourceNotFoundException;
import org.newsanalyzer.model.eval.GenerationBatch;
import org.newsanalyzer.model.eval.SyntheticArticle;
import org.newsanalyzer.repository.eval.GenerationBatchRepository;
import org.newsanalyzer.repository.eval.SyntheticArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service layer for EVAL dataset CRUD and query operations.
 *
 * Default transaction mode is read-only. Write methods override with @Transactional.
 */
@Service
@Transactional(readOnly = true)
public class EvalDatasetService {

    private final GenerationBatchRepository batchRepository;
    private final SyntheticArticleRepository articleRepository;

    public EvalDatasetService(GenerationBatchRepository batchRepository,
                              SyntheticArticleRepository articleRepository) {
        this.batchRepository = batchRepository;
        this.articleRepository = articleRepository;
    }

    // --- Write operations ---

    /**
     * Store a completed batch with all its articles in a single transaction.
     * If any article fails to save, the entire batch is rolled back.
     */
    @Transactional
    public GenerationBatchDTO createBatch(CreateBatchRequest request) {
        GenerationBatch batch = GenerationBatch.builder()
                .id(request.getBatchId())
                .branch(request.getBranch())
                .modelUsed(request.getModelUsed())
                .configJson(request.getConfigJson())
                .articlesCount(request.getArticlesCount())
                .faithfulCount(request.getFaithfulCount())
                .perturbedCount(request.getPerturbedCount())
                .totalTokens(request.getTotalTokens())
                .durationSeconds(request.getDurationSeconds())
                .errors(request.getErrors())
                .build();

        batch = batchRepository.save(batch);

        List<SyntheticArticle> articles = new ArrayList<>();
        for (CreateBatchRequest.ArticleEntry entry : request.getArticles()) {
            articles.add(SyntheticArticle.builder()
                    .batchId(batch.getId())
                    .articleText(entry.getArticleText())
                    .articleType(entry.getArticleType())
                    .isFaithful(entry.getIsFaithful())
                    .perturbationType(entry.getPerturbationType())
                    .difficulty(entry.getDifficulty())
                    .sourceFacts(entry.getSourceFacts())
                    .groundTruth(entry.getGroundTruth())
                    .modelUsed(entry.getModelUsed())
                    .tokensUsed(entry.getTokensUsed())
                    .build());
        }
        articleRepository.saveAll(articles);

        return toBatchDTO(batch);
    }

    /**
     * Delete a batch and all its articles (CASCADE).
     */
    @Transactional
    public void deleteBatch(UUID id) {
        if (!batchRepository.existsById(id)) {
            throw new ResourceNotFoundException("Batch", id);
        }
        batchRepository.deleteById(id);
    }

    // --- Read operations ---

    public GenerationBatchDTO getBatch(UUID id) {
        return batchRepository.findById(id)
                .map(this::toBatchDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", id));
    }

    public Page<GenerationBatchDTO> listBatches(Pageable pageable) {
        return batchRepository.findAll(pageable).map(this::toBatchDTO);
    }

    public SyntheticArticleDTO getArticle(UUID id) {
        return articleRepository.findById(id)
                .map(this::toArticleDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Article", id));
    }

    public Page<SyntheticArticleDTO> getArticlesByBatch(UUID batchId, Pageable pageable) {
        return articleRepository.findByBatchId(batchId, pageable).map(this::toArticleDTO);
    }

    /**
     * Query articles with optional combined filters.
     * Delegates to the native JSONB-aware query in the repository.
     */
    public Page<SyntheticArticleDTO> queryArticles(DatasetQueryRequest filter, Pageable pageable) {
        // Build the JSONB containment fragment for branch filtering
        String branchJson = filter.getBranch() != null
                ? "{\"branch\": \"" + filter.getBranch() + "\"}"
                : null;

        return articleRepository.findByFilters(
                filter.getPerturbationType(),
                filter.getDifficulty(),
                filter.getBranch(),
                branchJson,
                filter.getBatchId(),
                filter.getIsFaithful(),
                pageable
        ).map(this::toArticleDTO);
    }

    /**
     * Aggregate statistics across all stored articles.
     */
    public DatasetStatsDTO getStats() {
        long total = articleRepository.count();
        long faithful = articleRepository.findByIsFaithful(true,
                Pageable.unpaged()).getTotalElements();

        return DatasetStatsDTO.builder()
                .totalArticles(total)
                .faithfulCount(faithful)
                .perturbedCount(total - faithful)
                .byPerturbationType(toCountMap(articleRepository.countByPerturbationType()))
                .byDifficulty(toCountMap(articleRepository.countByDifficulty()))
                .byBranch(toCountMap(articleRepository.countByBranch()))
                .build();
    }

    // --- Mapping helpers ---

    private GenerationBatchDTO toBatchDTO(GenerationBatch batch) {
        return GenerationBatchDTO.builder()
                .id(batch.getId())
                .branch(batch.getBranch())
                .modelUsed(batch.getModelUsed())
                .configJson(batch.getConfigJson())
                .articlesCount(batch.getArticlesCount())
                .faithfulCount(batch.getFaithfulCount())
                .perturbedCount(batch.getPerturbedCount())
                .totalTokens(batch.getTotalTokens())
                .durationSeconds(batch.getDurationSeconds())
                .errors(batch.getErrors())
                .createdAt(batch.getCreatedAt())
                .build();
    }

    private SyntheticArticleDTO toArticleDTO(SyntheticArticle article) {
        return SyntheticArticleDTO.builder()
                .id(article.getId())
                .batchId(article.getBatchId())
                .articleText(article.getArticleText())
                .articleType(article.getArticleType())
                .isFaithful(article.getIsFaithful())
                .perturbationType(article.getPerturbationType())
                .difficulty(article.getDifficulty())
                .sourceFacts(article.getSourceFacts())
                .groundTruth(article.getGroundTruth())
                .modelUsed(article.getModelUsed())
                .tokensUsed(article.getTokensUsed())
                .createdAt(article.getCreatedAt())
                .build();
    }

    /**
     * Convert native query result rows (Object[]{key, count}) to a Map.
     */
    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? row[0].toString() : "none";
            Long count = ((Number) row[1]).longValue();
            map.put(key, count);
        }
        return map;
    }
}
