"""
Batch Orchestrator — coordinates end-to-end batch generation of synthetic
test articles.

Flow:
  1. Build entity pool via FactSetBuilder
  2. For each entity × article type: generate faithful article
  3. For each entity × article type × perturbation: generate perturbed article
  4. Collect all ArticleTestCases with ground-truth labels
  5. Store results via backend API
"""

from __future__ import annotations

import logging
import time
from uuid import uuid4

from app.clients.backend_client import BackendClient
from app.models.eval import (
    ArticleTestCase,
    BatchConfig,
    BatchResult,
)
from app.services.eval.article_generator import ArticleGenerator
from app.services.eval.fact_set_builder import FactSetBuilder
from app.services.eval.perturbation_engine import PerturbationEngine

logger = logging.getLogger(__name__)


class BatchOrchestrator:
    """Coordinates batch generation of synthetic test articles."""

    def __init__(
        self,
        fact_set_builder: FactSetBuilder,
        perturbation_engine: PerturbationEngine,
        article_generator: ArticleGenerator,
        backend_client: BackendClient,
    ) -> None:
        self._builder = fact_set_builder
        self._perturber = perturbation_engine
        self._generator = article_generator
        self._backend = backend_client

    async def run_batch(self, config: BatchConfig) -> BatchResult:
        """Execute a full batch generation run."""
        start_time = time.time()
        batch_id = uuid4()
        test_cases: list[ArticleTestCase] = []
        total_tokens = 0
        errors: list[str] = []

        # 1. Build entity pool (extras for CONFLATE_INDIVIDUALS)
        pool_size = config.entity_count + 5
        entity_pool = await self._builder.build_entity_pool(
            branch=config.branch, count=pool_size
        )

        # 2. For each entity, generate faithful + perturbed articles
        for fact_set in entity_pool[: config.entity_count]:
            for article_type in config.article_types:
                try:
                    # --- Faithful article ---
                    text, tokens = await self._generator.generate(
                        fact_set, article_type
                    )
                    total_tokens += tokens
                    test_cases.append(
                        ArticleTestCase(
                            article_text=text,
                            article_type=article_type,
                            source_facts=fact_set,
                            is_faithful=True,
                            model_used=config.model,
                            tokens_used=tokens,
                        )
                    )

                    # --- Perturbed articles ---
                    for perturbation in config.perturbation_types:
                        try:
                            perturbed = self._perturber.perturb(
                                fact_set, perturbation, entity_pool
                            )
                            if perturbed is None:
                                # Incompatible perturbation — skip
                                continue

                            p_text, p_tokens = await self._generator.generate(
                                perturbed.perturbed, article_type
                            )
                            total_tokens += p_tokens

                            expected_findings = [
                                f"Incorrect {c['predicate']}: "
                                f"stated '{c['perturbed_value']}', "
                                f"should be '{c['original_value']}'"
                                for c in perturbed.changed_facts
                            ]

                            test_cases.append(
                                ArticleTestCase(
                                    article_text=p_text,
                                    article_type=article_type,
                                    source_facts=perturbed.original,
                                    is_faithful=False,
                                    perturbation_type=perturbation,
                                    changed_facts=perturbed.changed_facts,
                                    expected_findings=expected_findings,
                                    difficulty=perturbed.difficulty,
                                    model_used=config.model,
                                    tokens_used=p_tokens,
                                )
                            )
                        except Exception as e:
                            errors.append(
                                f"Perturbation {perturbation.value} failed "
                                f"for {fact_set.topic}: {e}"
                            )

                except Exception as e:
                    errors.append(
                        f"Error generating for {fact_set.topic} "
                        f"({article_type.value}): {e}"
                    )

        # 3. Store results via backend API
        duration = time.time() - start_time
        if not config.dry_run:
            await self._store_batch(batch_id, config, test_cases, duration, errors)

        return BatchResult(
            batch_id=batch_id,
            articles_generated=len(test_cases),
            faithful_count=sum(1 for tc in test_cases if tc.is_faithful),
            perturbed_count=sum(1 for tc in test_cases if not tc.is_faithful),
            total_tokens=total_tokens,
            model_used=config.model,
            duration_seconds=duration,
            errors=errors,
        )

    async def _store_batch(
        self,
        batch_id,
        config: BatchConfig,
        test_cases: list[ArticleTestCase],
        duration_seconds: float = 0.0,
        errors: list[str] | None = None,
    ) -> None:
        """POST batch results to backend for persistent storage.

        Errors are logged but do not fail the batch — generation results
        are still returned to the caller even if storage fails.
        """
        try:
            logger.info(
                "Storing batch %s with %d articles to backend",
                batch_id,
                len(test_cases),
            )
            await self._backend.post_batch(batch_id, config, test_cases)
            logger.info("Batch %s stored successfully", batch_id)
        except Exception as e:
            msg = f"Failed to store batch {batch_id} to backend: {e}"
            logger.warning(msg)
            if errors is not None:
                errors.append(msg)
