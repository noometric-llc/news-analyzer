package org.newsanalyzer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.newsanalyzer.dto.ArticleDTO;
import org.newsanalyzer.dto.CreateArticleRequest;
import org.newsanalyzer.service.ArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API Controller for Article operations.
 *
 * Endpoints:
 * - POST /api/articles      - Submit and persist a new article
 * - GET  /api/articles/{id} - Get article by ID
 *
 * Persistence only at this stage (Story ES-1.2) — no extraction or
 * bias-detection calls occur here yet.
 */
@Slf4j
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Tag(name = "Articles", description = """
    The Evidence Store's core record — persisted, source-attributed news articles. \
    Distinct from Government Organizations (authoritative Factbase data): this is where \
    real ingested article content lives, which Entity records can be traced back to.

    Duplicate submissions (same URL/text) are not deduplicated at MVP — each submission \
    creates a new record.
    """)
public class ArticleController {

    private final ArticleService articleService;

    /**
     * Submit a new article
     */
    @PostMapping
    @Operation(summary = "Submit a new article", description = "Persist a new article for the Evidence Store")
    public ResponseEntity<ArticleDTO> createArticle(@Valid @RequestBody CreateArticleRequest request) {
        log.info("POST /api/articles - Creating article: {}", request.getSourceName());
        ArticleDTO created = articleService.createArticle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get article by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get article by ID", description = "Retrieve a specific article by UUID")
    public ResponseEntity<ArticleDTO> getArticleById(
        @Parameter(description = "Article UUID") @PathVariable UUID id
    ) {
        log.info("GET /api/articles/{} - Fetching article", id);
        ArticleDTO article = articleService.getArticleById(id);
        return ResponseEntity.ok(article);
    }
}
