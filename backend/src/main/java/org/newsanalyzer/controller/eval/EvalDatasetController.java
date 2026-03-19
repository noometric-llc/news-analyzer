package org.newsanalyzer.controller.eval;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.newsanalyzer.dto.eval.*;
import org.newsanalyzer.service.eval.EvalDatasetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for EVAL dataset operations.
 *
 * Provides endpoints for storing, querying, and managing synthetic test articles
 * and their generation batches. The reasoning-service POSTs completed batches here;
 * evaluation tools query articles by various filters.
 *
 * @author James (Dev Agent)
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/eval/datasets")
@Tag(name = "Eval Datasets", description = "Synthetic test article storage and query endpoints")
@RequiredArgsConstructor
public class EvalDatasetController {

    private static final Logger log = LoggerFactory.getLogger(EvalDatasetController.class);

    private final EvalDatasetService evalDatasetService;

    // --- Batch endpoints ---

    @PostMapping("/batches")
    @Operation(summary = "Store a completed batch",
            description = "Receives a completed generation batch from the reasoning-service and persists it with all articles.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Batch created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    public ResponseEntity<GenerationBatchDTO> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        log.info("Creating batch {} with {} articles", request.getBatchId(), request.getArticlesCount());
        GenerationBatchDTO created = evalDatasetService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/batches")
    @Operation(summary = "List all batches", description = "Returns paginated list of generation batches.")
    public ResponseEntity<Page<GenerationBatchDTO>> listBatches(Pageable pageable) {
        return ResponseEntity.ok(evalDatasetService.listBatches(pageable));
    }

    @GetMapping("/batches/{id}")
    @Operation(summary = "Get batch by ID", description = "Returns a single generation batch with summary statistics.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Batch found"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<GenerationBatchDTO> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(evalDatasetService.getBatch(id));
    }

    @GetMapping("/batches/{id}/articles")
    @Operation(summary = "Get articles in batch", description = "Returns paginated articles belonging to a specific batch.")
    public ResponseEntity<Page<SyntheticArticleDTO>> getArticlesByBatch(
            @PathVariable UUID id, Pageable pageable) {
        return ResponseEntity.ok(evalDatasetService.getArticlesByBatch(id, pageable));
    }

    @DeleteMapping("/batches/{id}")
    @Operation(summary = "Delete batch", description = "Deletes a batch and all its articles (CASCADE).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Batch deleted"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<Void> deleteBatch(@PathVariable UUID id) {
        log.info("Deleting batch {}", id);
        evalDatasetService.deleteBatch(id);
        return ResponseEntity.noContent().build();
    }

    // --- Article endpoints ---

    @GetMapping("/articles")
    @Operation(summary = "Query articles with filters",
            description = "Returns paginated articles filtered by perturbation type, difficulty, branch, batch ID, and/or faithful flag.")
    public ResponseEntity<Page<SyntheticArticleDTO>> queryArticles(
            DatasetQueryRequest filter, Pageable pageable) {
        return ResponseEntity.ok(evalDatasetService.queryArticles(filter, pageable));
    }

    @GetMapping("/articles/{id}")
    @Operation(summary = "Get article by ID", description = "Returns a single article with full ground truth details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article found"),
            @ApiResponse(responseCode = "404", description = "Article not found")
    })
    public ResponseEntity<SyntheticArticleDTO> getArticle(@PathVariable UUID id) {
        return ResponseEntity.ok(evalDatasetService.getArticle(id));
    }

    // --- Stats endpoint ---

    @GetMapping("/stats")
    @Operation(summary = "Get dataset statistics",
            description = "Returns aggregate statistics across all stored articles: counts by perturbation type, difficulty, and branch.")
    public ResponseEntity<DatasetStatsDTO> getStats() {
        return ResponseEntity.ok(evalDatasetService.getStats());
    }
}
