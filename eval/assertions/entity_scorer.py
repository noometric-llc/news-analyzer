"""
Entity Scorer — Promptfoo custom assertion for entity extraction evaluation.

Computes precision, recall, and F1 by comparing extracted entities against
gold-standard annotations using fuzzy matching:

  1. Exact text + type match         → full TP (1.0)
  2. Substring containment + type    → full TP (1.0)
  3. Levenshtein ≥ 0.8 + type       → full TP (1.0)
  4. Text match + type mismatch      → partial TP (0.5)
  5. No match                        → FP (extracted) or FN (gold)

Called by Promptfoo as: file://assertions/entity_scorer.py
"""

from __future__ import annotations

from difflib import SequenceMatcher
from typing import Any


# Entity types in the taxonomy
ENTITY_TYPES = [
    "person",
    "government_org",
    "organization",
    "location",
    "event",
    "concept",
    "legislation",
]

# Minimum Levenshtein ratio to count as a match
LEVENSHTEIN_THRESHOLD = 0.8

# Minimum F1 to pass the assertion
PASS_THRESHOLD = 0.5

# Partial credit for text match with type mismatch
TYPE_MISMATCH_CREDIT = 0.5


def levenshtein_ratio(s1: str, s2: str) -> float:
    """Compute normalized similarity between two strings (0.0–1.0).

    Uses difflib.SequenceMatcher which is a good approximation of
    normalized Levenshtein similarity and is in the standard library.
    """
    if not s1 and not s2:
        return 1.0
    if not s1 or not s2:
        return 0.0
    return SequenceMatcher(None, s1.lower(), s2.lower()).ratio()


def _normalize_text(text: str) -> str:
    """Normalize entity text for comparison."""
    return text.strip().lower()


def find_best_match(
    extracted: dict,
    gold_entities: list[dict],
    already_matched: set[int],
) -> tuple[int | None, float]:
    """Find the best matching gold entity for an extracted entity.

    Returns (gold_index, credit) where credit is:
      1.0 — full match (exact/substring/Levenshtein + type match)
      0.5 — partial match (text matches but type differs)
      None, 0.0 — no match

    Args:
        extracted: Extracted entity dict with "text" and "type"/"entity_type".
        gold_entities: List of gold entity dicts with "text" and "type".
        already_matched: Set of gold indices already matched (prevents double-counting).
    """
    ext_text = _normalize_text(extracted.get("text", ""))
    ext_type = extracted.get("entity_type", extracted.get("type", ""))

    if not ext_text:
        return None, 0.0

    best_idx = None
    best_credit = 0.0
    # Track match quality: 1=exact, 2=substring, 3=levenshtein, 4=type-mismatch
    best_quality = 99

    for i, gold in enumerate(gold_entities):
        if i in already_matched:
            continue

        gold_text = _normalize_text(gold.get("text", ""))
        gold_type = gold.get("type", gold.get("entity_type", ""))

        if not gold_text:
            continue

        type_matches = ext_type == gold_type

        # Priority 1: Exact text match + type match
        if ext_text == gold_text and type_matches:
            return i, 1.0  # Can't do better

        # Priority 2: Exact text match + type mismatch
        if ext_text == gold_text and not type_matches:
            if best_quality > 2:
                best_quality = 2
                best_credit = TYPE_MISMATCH_CREDIT
                best_idx = i
            continue

        # Priority 3: Substring containment + type match
        if (ext_text in gold_text or gold_text in ext_text) and type_matches:
            if best_quality > 3:
                best_quality = 3
                best_credit = 1.0
                best_idx = i
            continue

        # Priority 4: Substring containment + type mismatch
        if ext_text in gold_text or gold_text in ext_text:
            if best_quality > 4:
                best_quality = 4
                best_credit = TYPE_MISMATCH_CREDIT
                best_idx = i
            continue

        # Priority 5: Levenshtein similarity + type match
        ratio = levenshtein_ratio(ext_text, gold_text)
        if ratio >= LEVENSHTEIN_THRESHOLD and type_matches:
            if best_quality > 5:
                best_quality = 5
                best_credit = 1.0
                best_idx = i
            continue

        # Priority 6: Levenshtein similarity + type mismatch
        if ratio >= LEVENSHTEIN_THRESHOLD:
            if best_quality > 6:
                best_quality = 6
                best_credit = TYPE_MISMATCH_CREDIT
                best_idx = i

    return best_idx, best_credit


def compute_scores(
    extracted: list[dict], gold: list[dict]
) -> dict[str, float]:
    """Compute TP/FP/FN counts from extracted vs gold entities.

    Returns dict with:
      true_positives, false_positives, false_negatives — aggregate counts
      {type}_tp, {type}_fp, {type}_fn — per-type counts
    """
    tp = 0.0
    fp = 0.0
    matched_gold: set[int] = set()

    # Per-type tracking
    type_tp: dict[str, float] = {t: 0.0 for t in ENTITY_TYPES}
    type_fp: dict[str, float] = {t: 0.0 for t in ENTITY_TYPES}
    type_fn: dict[str, float] = {t: 0.0 for t in ENTITY_TYPES}

    # Match each extracted entity against gold
    for ext in extracted:
        ext_type = ext.get("entity_type", ext.get("type", ""))
        idx, credit = find_best_match(ext, gold, matched_gold)

        if idx is not None and credit > 0:
            matched_gold.add(idx)
            tp += credit
            if ext_type in type_tp:
                type_tp[ext_type] += credit
        else:
            fp += 1.0
            if ext_type in type_fp:
                type_fp[ext_type] += 1.0

    # Unmatched gold entities are false negatives
    fn = 0.0
    for i, g in enumerate(gold):
        if i not in matched_gold:
            fn += 1.0
            g_type = g.get("type", g.get("entity_type", ""))
            if g_type in type_fn:
                type_fn[g_type] += 1.0

    scores: dict[str, float] = {
        "true_positives": tp,
        "false_positives": fp,
        "false_negatives": fn,
    }

    # Add per-type scores
    for t in ENTITY_TYPES:
        scores[f"{t}_tp"] = type_tp[t]
        scores[f"{t}_fp"] = type_fp[t]
        scores[f"{t}_fn"] = type_fn[t]

    return scores


def compute_prf(tp: float, fp: float, fn: float) -> tuple[float, float, float]:
    """Compute precision, recall, F1 from counts."""
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = (
        2 * precision * recall / (precision + recall)
        if (precision + recall) > 0
        else 0.0
    )
    return precision, recall, f1


def get_assert(output: Any, context: dict) -> dict:
    """Promptfoo assertion entry point.

    Args:
        output: Provider response — expected to be a dict with "entities" key.
        context: Promptfoo context with "vars" containing gold entities.

    Returns:
        GradingResult dict with pass, score, reason, namedScores.
    """
    # Extract entities from provider output
    if isinstance(output, dict):
        extracted = output.get("entities", [])
    elif isinstance(output, str):
        # Provider returned a string error
        extracted = []
    else:
        extracted = []

    # Extract gold entities from test case vars
    vars_block = context.get("vars", {})
    gold = vars_block.get("entities", [])

    # Compute scores
    scores = compute_scores(extracted, gold)
    tp = scores["true_positives"]
    fp = scores["false_positives"]
    fn = scores["false_negatives"]

    precision, recall, f1 = compute_prf(tp, fp, fn)

    # Add P/R/F1 to named scores
    scores["precision"] = precision
    scores["recall"] = recall
    scores["f1"] = f1
    scores["extracted_count"] = float(len(extracted))
    scores["gold_count"] = float(len(gold))

    reason = (
        f"P={precision:.2f} R={recall:.2f} F1={f1:.2f} "
        f"(TP={tp:.1f} FP={fp:.1f} FN={fn:.1f}) "
        f"[{len(extracted)} extracted vs {len(gold)} gold]"
    )

    return {
        "pass": f1 >= PASS_THRESHOLD,
        "score": f1,
        "reason": reason,
        "namedScores": scores,
    }
