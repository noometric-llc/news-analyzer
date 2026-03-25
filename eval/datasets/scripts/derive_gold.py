"""
Gold Dataset Derivation Script

Transforms EVAL-1 synthetic articles + FactSets into Promptfoo-compatible
gold entity annotations for the EVAL-2 evaluation harness.

Architecture: Functional Core, Imperative Shell
- Pure functions: predicate mapping, span location, entity derivation
- I/O at edges: API fetching, YAML writing

Usage:
    python derive_gold.py --backend-url http://localhost:8080 --output eval/datasets/gold/
    python derive_gold.py --help
"""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path
from typing import Any

import requests
import yaml

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Predicate → Entity Type Mapping (Pure)
# ---------------------------------------------------------------------------

# Maps FactPredicate values to the gold entity type for the fact's *object*.
# fact.subject is always mapped to "person".
PREDICATE_TO_ENTITY_TYPE: dict[str, str] = {
    "state": "location",
    "district": "location",
    "committee_membership": "government_org",
    "court": "government_org",
    "party_affiliation": "concept",
    "vice_president": "person",
    "appointing_president": "person",
    "court_level": "government_org",
}

# Predicates whose objects should NOT become entity annotations.
# These are contextual/numeric values, not named entities.
SKIP_PREDICATES: set[str] = {
    "chamber",
    "term_start",
    "term_end",
    "presidency_number",
    "cabinet_position",
    "executive_order",
    "chief_of_staff",
    "agency_head",
    "leadership_role",
    "confirmation_date",
    "statute_reference",
    "statute_subject",
    "confidence",
}


def map_predicate_to_entity_type(predicate: str) -> str | None:
    """Map a FactPredicate value to the expected gold entity type.

    Returns the entity type string, or None if this predicate's object
    should not be an entity annotation.
    """
    if predicate in SKIP_PREDICATES:
        return None
    return PREDICATE_TO_ENTITY_TYPE.get(predicate)


# ---------------------------------------------------------------------------
# Span Location (Pure)
# ---------------------------------------------------------------------------


def locate_span(entity_text: str, article_text: str) -> tuple[int, int] | None:
    """Find the character offsets of entity_text within article_text.

    Tries exact match first, then case-insensitive fallback.
    Returns (start, end) tuple or None if not found.
    """
    # Exact match
    start = article_text.find(entity_text)
    if start != -1:
        return (start, start + len(entity_text))

    # Case-insensitive fallback
    start = article_text.lower().find(entity_text.lower())
    if start != -1:
        # Use the actual text from the article (preserves casing)
        return (start, start + len(entity_text))

    return None


# ---------------------------------------------------------------------------
# Entity Derivation (Pure)
# ---------------------------------------------------------------------------


def derive_entities_from_facts(
    facts: list[dict[str, Any]], article_text: str
) -> list[dict[str, Any]]:
    """Derive gold entity annotations from a list of Fact dicts.

    Extracts entity mentions from fact subjects and objects,
    locates their spans in the article text, and returns
    annotation dicts ready for the gold dataset.
    """
    entities: list[dict[str, Any]] = []
    seen_texts: set[str] = set()  # Deduplicate by (text_lower, type)

    for fact in facts:
        subject = fact.get("subject", "")
        predicate = fact.get("predicate", "")
        obj = fact.get("object", "")

        # 1. Subject is always a person
        _add_entity(entities, seen_texts, subject, "person", article_text)

        # 2. Object depends on predicate
        entity_type = map_predicate_to_entity_type(predicate)
        if entity_type and obj:
            _add_entity(entities, seen_texts, obj, entity_type, article_text)

    # Sort by start offset for readability
    entities.sort(key=lambda e: e["start"])
    return entities


def _add_entity(
    entities: list[dict[str, Any]],
    seen_texts: set[str],
    text: str,
    entity_type: str,
    article_text: str,
) -> None:
    """Add an entity annotation if the text is found in the article.

    Deduplicates by (lowercased text, type) to avoid duplicate entries
    when the same entity appears in multiple facts.
    """
    if not text or len(text) < 2:
        return

    dedup_key = (text.lower(), entity_type)
    if dedup_key in seen_texts:
        return

    span = locate_span(text, article_text)
    if span is None:
        logger.debug("Entity '%s' (%s) not found in article text, skipping", text, entity_type)
        return

    seen_texts.add(dedup_key)
    entities.append(
        {
            "text": article_text[span[0] : span[1]],  # Use actual article casing
            "type": entity_type,
            "start": span[0],
            "end": span[1],
        }
    )


# ---------------------------------------------------------------------------
# Article → Gold Test Case (Pure)
# ---------------------------------------------------------------------------


def article_to_test_case(
    article: dict[str, Any], branch_abbrev: str, index: int
) -> dict[str, Any] | None:
    """Convert a backend SyntheticArticleDTO to a Promptfoo test case.

    Returns None if derivation produces zero entities.
    """
    article_text = article.get("articleText", "")
    source_facts = article.get("sourceFacts", {})
    ground_truth = article.get("groundTruth", {})

    facts = source_facts.get("facts", [])
    if not facts:
        logger.warning(
            "Article %s has no facts in sourceFacts, skipping", article.get("id")
        )
        return None

    entities = derive_entities_from_facts(facts, article_text)
    if not entities:
        logger.warning(
            "Article %s: no entities could be located in text, skipping",
            article.get("id"),
        )
        return None

    branch = source_facts.get("branch", "unknown")

    return {
        "vars": {
            "article_text": article_text,
            "entities": entities,
            "metadata": {
                "id": f"eval-2-{branch_abbrev}-{index:03d}",
                "article_id": str(article.get("id", "")),
                "article_type": article.get("articleType", "unknown"),
                "branch": branch,
                "source": "derived",
                "perturbation_type": article.get("perturbationType") or "none",
                "difficulty": article.get("difficulty", "medium"),
                "fact_count": len(facts),
                "curated": False,
                "curated_date": None,
            },
        }
    }


# ---------------------------------------------------------------------------
# API Client (Imperative Shell)
# ---------------------------------------------------------------------------

BRANCH_ABBREV = {
    "legislative": "leg",
    "executive": "exe",
    "judicial": "jud",
}


def fetch_articles(
    backend_url: str,
    faithful_only: bool = True,
    page_size: int = 100,
) -> list[dict[str, Any]]:
    """Fetch synthetic articles from the backend API via batch endpoints.

    Uses GET /batches then GET /batches/{id}/articles to avoid the
    native JSONB query bug in the articles filter endpoint.
    """
    base = backend_url.rstrip("/")
    articles: list[dict[str, Any]] = []

    # 1. Fetch all batches
    batches: list[dict[str, Any]] = []
    page = 0
    while True:
        resp = requests.get(
            f"{base}/api/eval/datasets/batches",
            params={"page": page, "size": page_size},
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        content = data.get("content", [])
        if not content:
            break
        batches.extend(content)
        page += 1
        if page >= data.get("totalPages", 1):
            break

    logger.info("Found %d batches", len(batches))

    # 2. Fetch articles per batch
    for batch in batches:
        batch_id = batch["id"]
        page = 0
        while True:
            resp = requests.get(
                f"{base}/api/eval/datasets/batches/{batch_id}/articles",
                params={"page": page, "size": page_size},
                timeout=30,
            )
            resp.raise_for_status()
            data = resp.json()
            content = data.get("content", [])
            if not content:
                break

            for article in content:
                if faithful_only and not article.get("isFaithful", False):
                    continue
                articles.append(article)

            page += 1
            if page >= data.get("totalPages", 1):
                break

    logger.info("Fetched %d articles total (faithful_only=%s)", len(articles), faithful_only)
    return articles


# ---------------------------------------------------------------------------
# YAML Output (Imperative Shell)
# ---------------------------------------------------------------------------


def group_by_branch(
    articles: list[dict[str, Any]],
) -> dict[str, list[dict[str, Any]]]:
    """Group articles by government branch from sourceFacts."""
    groups: dict[str, list[dict[str, Any]]] = {
        "legislative": [],
        "executive": [],
        "judicial": [],
    }

    for article in articles:
        source_facts = article.get("sourceFacts", {})
        branch = source_facts.get("branch", "unknown")
        if branch in groups:
            groups[branch].append(article)
        else:
            logger.warning("Unknown branch '%s' for article %s", branch, article.get("id"))

    return groups


def write_gold_files(
    test_cases: dict[str, list[dict[str, Any]]],
    output_dir: Path,
) -> dict[str, int]:
    """Write gold dataset YAML files grouped by branch.

    Returns dict of {branch: count} for reporting.
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    counts: dict[str, int] = {}

    for branch, cases in test_cases.items():
        if not cases:
            logger.info("No test cases for branch '%s', skipping file", branch)
            counts[branch] = 0
            continue

        filepath = output_dir / f"{branch}.yaml"

        # Preserve existing schema comment header if file exists
        header = _get_header_comment(filepath)

        with open(filepath, "w", encoding="utf-8") as f:
            if header:
                f.write(header)
                f.write("\n")
            yaml.dump(
                cases,
                f,
                default_flow_style=False,
                allow_unicode=True,
                sort_keys=False,
                width=120,
            )

        counts[branch] = len(cases)
        logger.info("Wrote %d test cases to %s", len(cases), filepath)

    return counts


def _get_header_comment(filepath: Path) -> str:
    """Extract leading comment lines from an existing YAML file."""
    if not filepath.exists():
        return ""

    lines = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                lines.append(line.rstrip())
            else:
                break

    return "\n".join(lines) if lines else ""


# ---------------------------------------------------------------------------
# Main (CLI)
# ---------------------------------------------------------------------------


def derive(backend_url: str, output_dir: Path, faithful_only: bool = True) -> dict[str, int]:
    """Run the full derivation pipeline.

    Returns dict of {branch: article_count} for reporting.
    """
    # 1. Fetch articles
    articles = fetch_articles(backend_url, faithful_only=faithful_only)

    # 2. Group by branch
    grouped = group_by_branch(articles)

    # 3. Derive test cases per branch
    all_test_cases: dict[str, list[dict[str, Any]]] = {}
    for branch, branch_articles in grouped.items():
        abbrev = BRANCH_ABBREV.get(branch, branch[:3])
        cases = []
        for i, article in enumerate(branch_articles, start=1):
            test_case = article_to_test_case(article, abbrev, i)
            if test_case:
                cases.append(test_case)
        all_test_cases[branch] = cases

    # 4. Write YAML files
    counts = write_gold_files(all_test_cases, output_dir)

    # 5. Report
    total = sum(counts.values())
    logger.info("Derivation complete: %d articles across %d branches", total, len(counts))
    for branch, count in counts.items():
        logger.info("  %s: %d articles", branch, count)

    return counts


def main() -> None:
    """CLI entry point."""
    parser = argparse.ArgumentParser(
        description="Derive gold entity annotations from EVAL-1 synthetic articles"
    )
    parser.add_argument(
        "--backend-url",
        default="http://localhost:8080",
        help="Backend API base URL (default: http://localhost:8080)",
    )
    parser.add_argument(
        "--output",
        default="eval/datasets/gold/",
        help="Output directory for gold YAML files (default: eval/datasets/gold/)",
    )
    parser.add_argument(
        "--include-perturbed",
        action="store_true",
        help="Include perturbed articles (default: faithful only)",
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

    output_dir = Path(args.output)
    derive(
        backend_url=args.backend_url,
        output_dir=output_dir,
        faithful_only=not args.include_perturbed,
    )


if __name__ == "__main__":
    main()
