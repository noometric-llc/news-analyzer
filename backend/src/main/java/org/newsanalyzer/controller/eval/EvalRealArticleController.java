package org.newsanalyzer.controller.eval;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.newsanalyzer.dto.eval.RealArticleEvaluationDTO;
import org.newsanalyzer.service.eval.EvalRealArticleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for the eval harness's real-article read path (Story ES-1.6).
 *
 * Distinct from EvalDatasetController (which serves SyntheticArticle data,
 * EVAL-1's synthetic test-article pipeline): this controller reads real,
 * production-ingested Article records instead. Read-only — no endpoint here
 * triggers extraction, bias detection, or any write.
 */
@RestController
@RequestMapping("/api/eval/real-articles")
@Tag(name = "Eval Real Articles", description = "Read-only access to real, ingested articles' extraction and bias-detection results, for eval-harness smoke-testing")
@RequiredArgsConstructor
public class EvalRealArticleController {

    private final EvalRealArticleService evalRealArticleService;

    @GetMapping("/{articleId}")
    @Operation(summary = "Get a real article's evaluation bundle",
            description = "Returns a persisted Article's metadata bundled with its linked entities and bias annotations, as already computed by the production ingestion pipeline. No re-extraction is triggered.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Article found"),
            @ApiResponse(responseCode = "404", description = "Article not found")
    })
    public ResponseEntity<RealArticleEvaluationDTO> getRealArticleEvaluation(@PathVariable UUID articleId) {
        return ResponseEntity.ok(evalRealArticleService.getRealArticleEvaluation(articleId));
    }
}
