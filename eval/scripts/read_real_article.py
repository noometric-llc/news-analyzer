"""
Real-Article Evaluation Read Script

Story ES-1.6: fetches a real, production-ingested Article's already-computed
extraction and bias-detection results from the news-analyzer backend, for
manual/qualitative review.

This is deliberately NOT a promptfoo provider — unlike spacy_provider.py and
bias_provider.py, it triggers no new extraction and computes no score. Real
articles have no curated ground truth to score against (see ES-1.6's Scope
Decision), so this script's only job is to prove the full ingest -> extract
-> bias-detect pipeline produced readable results for a given article, and
display them.

Usage:
    python read_real_article.py --article-id <uuid>
    python read_real_article.py --article-id <uuid> --backend-url http://localhost:8080
"""

from __future__ import annotations

import argparse
import json
import logging
import sys

import requests

logger = logging.getLogger(__name__)


def fetch_real_article_evaluation(backend_url: str, article_id: str) -> dict:
    """Fetch the bundled Article + entities + bias annotations for one article.

    Raises requests.HTTPError for any non-2xx response other than 404, which
    is handled by the caller as an expected "article not found" outcome.
    """
    base = backend_url.rstrip("/")
    url = f"{base}/api/eval/real-articles/{article_id}"

    resp = requests.get(url, timeout=30)
    if resp.status_code == 404:
        return None
    resp.raise_for_status()
    return resp.json()


def summarize(evaluation: dict) -> None:
    """Log a human-readable summary of the bundled evaluation result."""
    article = evaluation["article"]
    entities = evaluation["entities"]
    annotations = evaluation["annotations"]

    logger.info("Article: %s (%s)", article["id"], article["sourceName"])
    logger.info("  extractionStatus=%s  biasDetectionStatus=%s",
                 article["extractionStatus"], article["biasDetectionStatus"])
    logger.info("  %d linked entities, %d linked bias annotations", len(entities), len(annotations))

    for entity in entities:
        logger.info("  entity: %s (%s)", entity["name"], entity["entityType"])

    for annotation in annotations:
        logger.info("  annotation: %s (%s)", annotation["distortionType"], annotation["category"])


def main() -> None:
    """CLI entry point."""
    parser = argparse.ArgumentParser(
        description="Fetch and display a real article's stored extraction/bias-detection results"
    )
    parser.add_argument(
        "--article-id",
        required=True,
        help="UUID of the real Article to read (from POST /api/articles)",
    )
    parser.add_argument(
        "--backend-url",
        default="http://localhost:8080",
        help="Backend API base URL (default: http://localhost:8080)",
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose logging",
    )

    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    evaluation = fetch_real_article_evaluation(args.backend_url, args.article_id)
    if evaluation is None:
        logger.error("Article not found: %s", args.article_id)
        sys.exit(1)

    summarize(evaluation)
    print(json.dumps(evaluation, indent=2))


if __name__ == "__main__":
    main()
