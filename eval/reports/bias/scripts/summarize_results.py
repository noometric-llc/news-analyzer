#!/usr/bin/env python3
"""
Summarize Promptfoo Bias Evaluation Results (EVAL-3.5)

Reads raw Promptfoo JSON output and produces summary.json with:
- Aggregate P/R/F1
- Per-distortion-type breakdown
- Per-difficulty breakdown
- Per-source breakdown (synthetic vs curated)

Usage:
    python summarize_results.py --input reports/bias/output.json --output reports/bias/summary.json
    python summarize_results.py --input reports/bias/output.json --compare reports/bias-ungrounded/output.json
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path


def load_promptfoo_output(path: str) -> list[dict]:
    """Load and extract test results from Promptfoo JSON output."""
    with open(path) as f:
        data = json.load(f)

    # Promptfoo output format: { results: { results: [...] } } or { results: [...] }
    if isinstance(data, dict):
        results = data.get("results", data)
        if isinstance(results, dict):
            results = results.get("results", [])
        if isinstance(results, list):
            return results

    return data if isinstance(data, list) else []


def extract_test_metrics(result: dict) -> dict | None:
    """Extract metrics from a single Promptfoo test result."""
    # Get namedScores from the assertion result
    assert_results = result.get("gradingResult", result.get("assert", {}))
    if isinstance(assert_results, list):
        # Multiple assertions — find the python one
        for ar in assert_results:
            if ar.get("namedScores"):
                assert_results = ar
                break

    named_scores = {}
    if isinstance(assert_results, dict):
        named_scores = assert_results.get("namedScores", {})

    if not named_scores:
        return None

    # Get vars for metadata
    vars_data = result.get("vars", {})
    metadata = vars_data.get("metadata", {})

    return {
        "named_scores": named_scores,
        "metadata": metadata,
        "biases": vars_data.get("biases", []),
    }


def compute_aggregate(test_results: list[dict]) -> dict:
    """Compute aggregate P/R/F1 across all test cases."""
    total_tp = 0.0
    total_fp = 0.0
    total_fn = 0.0
    total_biases = 0
    total_detected = 0

    for tr in test_results:
        ns = tr["named_scores"]
        total_tp += ns.get("true_positives", 0)
        total_fp += ns.get("false_positives", 0)
        total_fn += ns.get("false_negatives", 0)
        total_biases += len(tr.get("biases", []))
        total_detected += int(ns.get("true_positives", 0) + ns.get("false_positives", 0))

    precision = total_tp / (total_tp + total_fp) if (total_tp + total_fp) > 0 else 0
    recall = total_tp / (total_tp + total_fn) if (total_tp + total_fn) > 0 else 0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0

    return {
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "total_articles": len(test_results),
        "total_biases_in_gold": total_biases,
        "total_detected": total_detected,
        "true_positives": round(total_tp, 1),
        "false_positives": round(total_fp, 1),
        "false_negatives": round(total_fn, 1),
    }


def compute_by_distortion_type(test_results: list[dict]) -> dict:
    """Compute per-distortion-type P/R/F1."""
    type_tp: dict[str, float] = defaultdict(float)
    type_fp: dict[str, float] = defaultdict(float)
    type_fn: dict[str, float] = defaultdict(float)

    for tr in test_results:
        ns = tr["named_scores"]
        for key, val in ns.items():
            if key.endswith("_tp"):
                t = key[:-3]
                type_tp[t] += val
            elif key.endswith("_fp"):
                t = key[:-3]
                type_fp[t] += val
            elif key.endswith("_fn"):
                t = key[:-3]
                type_fn[t] += val

    all_types = set(list(type_tp.keys()) + list(type_fp.keys()) + list(type_fn.keys()))
    result = {}

    for t in sorted(all_types):
        tp = type_tp[t]
        fp = type_fp[t]
        fn = type_fn[t]
        p = tp / (tp + fp) if (tp + fp) > 0 else 0
        r = tp / (tp + fn) if (tp + fn) > 0 else 0
        f1 = 2 * p * r / (p + r) if (p + r) > 0 else 0
        result[t] = {
            "tp": round(tp, 1), "fp": round(fp, 1), "fn": round(fn, 1),
            "precision": round(p, 4), "recall": round(r, 4), "f1": round(f1, 4),
        }

    return result


def compute_by_dimension(test_results: list[dict], dimension: str) -> dict:
    """Compute P/R/F1 grouped by a metadata dimension (difficulty, source)."""
    groups: dict[str, list[dict]] = defaultdict(list)

    for tr in test_results:
        key = tr["metadata"].get(dimension, "unknown")
        groups[key].append(tr)

    result = {}
    for key in sorted(groups.keys()):
        agg = compute_aggregate(groups[key])
        result[key] = {
            "precision": agg["precision"],
            "recall": agg["recall"],
            "f1": agg["f1"],
            "article_count": agg["total_articles"],
        }

    return result


def build_summary(promptfoo_path: str) -> dict:
    """Build full summary from Promptfoo output file."""
    raw_results = load_promptfoo_output(promptfoo_path)
    test_results = []
    for r in raw_results:
        metrics = extract_test_metrics(r)
        if metrics:
            test_results.append(metrics)

    if not test_results:
        print(f"WARNING: No test results extracted from {promptfoo_path}", file=sys.stderr)

    return {
        "generated": str(date.today()),
        "source_file": str(promptfoo_path),
        "aggregate": compute_aggregate(test_results),
        "by_distortion_type": compute_by_distortion_type(test_results),
        "by_difficulty": compute_by_dimension(test_results, "difficulty"),
        "by_source": compute_by_dimension(test_results, "source"),
    }


def build_comparison(grounded_path: str, ungrounded_path: str) -> dict:
    """Build comparison summary between grounded and ungrounded results."""
    grounded = build_summary(grounded_path)
    ungrounded = build_summary(ungrounded_path)

    return {
        "generated": str(date.today()),
        "grounded": grounded,
        "ungrounded": ungrounded,
        "delta": {
            "precision": round(
                grounded["aggregate"]["precision"] - ungrounded["aggregate"]["precision"], 4
            ),
            "recall": round(
                grounded["aggregate"]["recall"] - ungrounded["aggregate"]["recall"], 4
            ),
            "f1": round(
                grounded["aggregate"]["f1"] - ungrounded["aggregate"]["f1"], 4
            ),
        },
    }


def main():
    parser = argparse.ArgumentParser(description="Summarize Promptfoo bias evaluation results")
    parser.add_argument("--input", required=True, help="Path to Promptfoo JSON output")
    parser.add_argument("--output", default=None, help="Output summary.json path")
    parser.add_argument("--compare", default=None, help="Path to second (ungrounded) output for A/B comparison")

    args = parser.parse_args()

    if args.compare:
        result = build_comparison(args.input, args.compare)
        output_path = args.output or "reports/bias/comparison.json"
    else:
        result = build_summary(args.input)
        output_path = args.output or "reports/bias/summary.json"

    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w") as f:
        json.dump(result, f, indent=2)

    print(f"Summary written to {output_path}")

    # Print key metrics to console
    agg = result.get("aggregate", result.get("grounded", {}).get("aggregate", {}))
    if agg:
        print(f"\nAggregate: P={agg['precision']:.3f} R={agg['recall']:.3f} F1={agg['f1']:.3f}")
        print(f"Articles: {agg.get('total_articles', '?')}, TP={agg.get('true_positives', '?')}, FP={agg.get('false_positives', '?')}, FN={agg.get('false_negatives', '?')}")

    if args.compare:
        delta = result["delta"]
        print(f"\nGrounded vs Ungrounded delta: P={delta['precision']:+.3f} R={delta['recall']:+.3f} F1={delta['f1']:+.3f}")


if __name__ == "__main__":
    main()
