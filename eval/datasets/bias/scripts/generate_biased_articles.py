#!/usr/bin/env python3
"""
Synthetic Biased Article Generator (EVAL-3.4)

Takes neutral EVAL-1 articles and injects specific cognitive biases via Claude.
The injected bias IS the gold annotation — we know exactly what was put in.

Uses ontology definitions to ground the injection prompt — the same neuro-symbolic
pattern as detection, but in reverse.

Usage:
    python generate_biased_articles.py --output eval/datasets/bias/synthetic_biased.yaml --count 3
    python generate_biased_articles.py --dry-run --types framing_effect,ad_hominem
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import yaml
from pathlib import Path

# Add project roots to path for imports
SCRIPT_DIR = Path(__file__).parent
EVAL_DIR = SCRIPT_DIR.parent.parent.parent
PROJECT_ROOT = EVAL_DIR.parent
REASONING_DIR = PROJECT_ROOT / "reasoning-service"

sys.path.insert(0, str(REASONING_DIR))

from rdflib import Graph, Namespace

CB = Namespace("http://newsanalyzer.org/ontology/cognitive-bias#")
ONTOLOGY_PATH = REASONING_DIR / "ontology" / "cognitive-bias.ttl"
GOLD_DIR = EVAL_DIR / "datasets" / "gold"

DIFFICULTY_PROMPTS = {
    "easy": "Make the bias OBVIOUS and heavy-handed. A casual reader should immediately notice it.",
    "medium": "Make the bias present but SUBTLE. It should be detectable with careful reading.",
    "hard": "Make the bias very SUBTLE. A reader would need to understand the formal definition to identify it.",
}


def _uri_to_snake(name: str) -> str:
    """Convert PascalCase to snake_case."""
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def load_ontology_definitions() -> list[dict]:
    """Load all distortion definitions from the ontology via SPARQL."""
    g = Graph()
    g.parse(str(ONTOLOGY_PATH), format="turtle")

    query = """
    SELECT ?d ?label ?def ?patternDesc ?author ?year WHERE {
        ?d rdf:type owl:NamedIndividual .
        ?d rdf:type/rdfs:subClassOf* <http://newsanalyzer.org/ontology/cognitive-bias#CognitiveDistortion> .
        ?d rdfs:label ?label .
        ?d <http://newsanalyzer.org/ontology/cognitive-bias#hasDefinition> ?def .
        ?d <http://newsanalyzer.org/ontology/cognitive-bias#hasDetectionPattern> ?pattern .
        ?pattern <http://newsanalyzer.org/ontology/cognitive-bias#patternDescription> ?patternDesc .
        ?d <http://newsanalyzer.org/ontology/cognitive-bias#hasAcademicSource> ?src .
        ?src <http://newsanalyzer.org/ontology/cognitive-bias#sourceAuthor> ?author .
        ?src <http://newsanalyzer.org/ontology/cognitive-bias#sourceYear> ?year .
    }
    """
    definitions = []
    for row in g.query(query):
        local_name = str(row[0]).split("#")[-1]
        definitions.append({
            "type_id": _uri_to_snake(local_name),
            "label": str(row[1]),
            "definition": str(row[2]),
            "detection_pattern": str(row[3]),
            "author": str(row[4]),
            "year": int(row[5]),
        })
    return definitions


def load_source_articles(max_count: int = 15) -> list[str]:
    """Load neutral articles from EVAL-1 gold datasets."""
    articles = []
    for yaml_file in sorted(GOLD_DIR.glob("*.yaml")):
        with open(yaml_file) as f:
            data = yaml.safe_load(f)
        if not isinstance(data, list):
            continue
        for entry in data:
            vars_data = entry.get("vars", {})
            metadata = vars_data.get("metadata", {})
            # Only use faithful (unperturbed) articles
            if metadata.get("perturbation_type", "") in ("none", ""):
                text = vars_data.get("article_text", "")
                if len(text) > 100:
                    articles.append(text)
        if len(articles) >= max_count:
            break
    return articles[:max_count]


def build_injection_prompt(
    article_text: str,
    bias_def: dict,
    difficulty: str,
) -> str:
    """Build the Claude prompt for bias injection."""
    diff_instruction = DIFFICULTY_PROMPTS.get(difficulty, DIFFICULTY_PROMPTS["medium"])

    return f"""Rewrite the following news article to contain a clear example of {bias_def['label']}.

{bias_def['label']}: {bias_def['definition']}
Detection pattern: {bias_def['detection_pattern']}

Difficulty level: {difficulty.upper()}
{diff_instruction}

Requirements:
- Keep the article topic and key facts the same
- Inject the bias naturally — it should be detectable but not cartoonish
- Mark the specific excerpt where the bias appears with [BIAS_START] and [BIAS_END] tags
- Maintain article length (~200-400 words)
- Return ONLY the rewritten article text with the tags. No explanation.

Original article:
{article_text}"""


def parse_injection_response(response_text: str) -> tuple[str, str]:
    """Parse Claude's response to extract article and tagged excerpt.

    Returns (clean_article, excerpt) where excerpt is the text between tags.
    """
    # Extract tagged excerpt
    match = re.search(r"\[BIAS_START\](.*?)\[BIAS_END\]", response_text, re.DOTALL)
    if match:
        excerpt = match.group(1).strip()
        # Remove tags from article
        clean = response_text.replace("[BIAS_START]", "").replace("[BIAS_END]", "")
        return clean.strip(), excerpt
    else:
        # No tags found — use the whole response as article, excerpt unknown
        return response_text.strip(), ""


def generate_articles(
    definitions: list[dict],
    source_articles: list[str],
    count_per_type: int = 3,
    types_filter: list[str] | None = None,
    dry_run: bool = False,
    rate_limit_sleep: float = 1.5,
) -> list[dict]:
    """Generate synthetic biased articles.

    Args:
        definitions: Ontology distortion definitions.
        source_articles: Neutral articles to inject biases into.
        count_per_type: Number of articles per distortion type.
        types_filter: Only generate for these types (None = all).
        dry_run: Print prompts without calling Claude.
        rate_limit_sleep: Seconds between API calls.

    Returns:
        List of Promptfoo test case dicts.
    """
    import anthropic

    if not dry_run:
        api_key = os.environ.get("ANTHROPIC_API_KEY", "")
        if not api_key:
            print("ERROR: ANTHROPIC_API_KEY not set", file=sys.stderr)
            sys.exit(1)
        client = anthropic.Anthropic(api_key=api_key)

    if types_filter:
        definitions = [d for d in definitions if d["type_id"] in types_filter]

    difficulties = ["easy", "medium", "hard"]
    test_cases = []
    article_idx = 0
    case_num = 1

    for defn in definitions:
        for i in range(count_per_type):
            source = source_articles[article_idx % len(source_articles)]
            article_idx += 1
            difficulty = difficulties[i % len(difficulties)]

            prompt = build_injection_prompt(source, defn, difficulty)

            if dry_run:
                print(f"\n--- DRY RUN: {defn['type_id']} ({difficulty}) ---")
                print(prompt[:300] + "...\n")
                clean_article = f"[DRY RUN] {defn['type_id']} {difficulty} article"
                excerpt = f"[DRY RUN excerpt for {defn['type_id']}]"
            else:
                print(f"Generating: {defn['type_id']} ({difficulty})...", end=" ", flush=True)
                response = client.messages.create(
                    model="claude-sonnet-4-20250514",
                    max_tokens=2048,
                    messages=[{"role": "user", "content": prompt}],
                )
                response_text = response.content[0].text
                clean_article, excerpt = parse_injection_response(response_text)
                print(f"OK ({len(clean_article)} chars)")

                time.sleep(rate_limit_sleep)

            source_ref = f"{defn['author'].split(',')[0]}, {defn['year']}"
            test_cases.append({
                "vars": {
                    "article_text": clean_article,
                    "biases": [
                        {
                            "type": defn["type_id"],
                            "excerpt": excerpt,
                            "explanation": f"Injected {defn['label']} — {defn['definition'][:100]}...",
                            "academic_source": source_ref,
                        }
                    ],
                    "metadata": {
                        "id": f"eval-3-bias-{case_num:03d}",
                        "source": "synthetic",
                        "difficulty": difficulty,
                        "bias_count": 1,
                        "injected_types": [defn["type_id"]],
                    },
                }
            })
            case_num += 1

    return test_cases


def main():
    parser = argparse.ArgumentParser(
        description="Generate synthetic biased articles for EVAL-3 evaluation"
    )
    parser.add_argument(
        "--output",
        default=str(SCRIPT_DIR.parent / "synthetic_biased.yaml"),
        help="Output YAML file path",
    )
    parser.add_argument("--count", type=int, default=3, help="Articles per distortion type")
    parser.add_argument("--types", type=str, default=None, help="Comma-separated type filter")
    parser.add_argument("--dry-run", action="store_true", help="Print prompts without API calls")
    parser.add_argument("--sleep", type=float, default=1.5, help="Seconds between API calls")

    args = parser.parse_args()

    types_filter = args.types.split(",") if args.types else None

    print("Loading ontology definitions...")
    definitions = load_ontology_definitions()
    print(f"Found {len(definitions)} distortion definitions")

    print("Loading source articles...")
    source_articles = load_source_articles()
    print(f"Found {len(source_articles)} source articles")

    if not source_articles:
        print("ERROR: No source articles found in eval/datasets/gold/", file=sys.stderr)
        sys.exit(1)

    test_cases = generate_articles(
        definitions=definitions,
        source_articles=source_articles,
        count_per_type=args.count,
        types_filter=types_filter,
        dry_run=args.dry_run,
        rate_limit_sleep=args.sleep,
    )

    if not args.dry_run:
        output_path = Path(args.output)
        with open(output_path, "w") as f:
            yaml.dump(test_cases, f, default_flow_style=False, allow_unicode=True, width=120)
        print(f"\nGenerated {len(test_cases)} test cases → {output_path}")
    else:
        print(f"\n[DRY RUN] Would generate {len(test_cases)} test cases")


if __name__ == "__main__":
    main()
