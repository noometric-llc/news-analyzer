"""
Tests for the entity scorer — EVAL-2.3.

Covers all scenarios from architecture doc Section 9:
  - Perfect extraction
  - No entities extracted
  - False positives only
  - Partial recall
  - Substring matching
  - Type mismatch partial credit
  - Duplicate extraction
  - Levenshtein matching
  - Per-type breakdown
  - Edge cases
"""

from __future__ import annotations

import pytest

from entity_scorer import (
    PASS_THRESHOLD,
    TYPE_MISMATCH_CREDIT,
    compute_prf,
    compute_scores,
    find_best_match,
    get_assert,
    levenshtein_ratio,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _gold(text: str, etype: str) -> dict:
    return {"text": text, "type": etype}


def _ext(text: str, etype: str) -> dict:
    return {"text": text, "entity_type": etype}


# ---------------------------------------------------------------------------
# levenshtein_ratio tests
# ---------------------------------------------------------------------------


class TestLevenshteinRatio:
    def test_identical_strings(self):
        assert levenshtein_ratio("hello", "hello") == 1.0

    def test_completely_different(self):
        ratio = levenshtein_ratio("abc", "xyz")
        assert ratio < 0.3

    def test_similar_strings(self):
        ratio = levenshtein_ratio("Fetterman", "Fettermann")
        assert ratio >= 0.8

    def test_empty_both(self):
        assert levenshtein_ratio("", "") == 1.0

    def test_empty_one(self):
        assert levenshtein_ratio("hello", "") == 0.0
        assert levenshtein_ratio("", "hello") == 0.0

    def test_case_insensitive(self):
        assert levenshtein_ratio("EPA", "epa") == 1.0


# ---------------------------------------------------------------------------
# find_best_match tests
# ---------------------------------------------------------------------------


class TestFindBestMatch:
    def test_exact_match(self):
        gold = [_gold("EPA", "government_org")]
        idx, credit = find_best_match(_ext("EPA", "government_org"), gold, set())
        assert idx == 0
        assert credit == 1.0

    def test_substring_extracted_in_gold(self):
        gold = [_gold("Senate Banking Committee", "government_org")]
        idx, credit = find_best_match(
            _ext("Banking Committee", "government_org"), gold, set()
        )
        assert idx == 0
        assert credit == 1.0

    def test_substring_gold_in_extracted(self):
        gold = [_gold("Warren", "person")]
        idx, credit = find_best_match(
            _ext("Elizabeth Warren", "person"), gold, set()
        )
        assert idx == 0
        assert credit == 1.0

    def test_levenshtein_match(self):
        gold = [_gold("John Fetterman", "person")]
        idx, credit = find_best_match(
            _ext("John Fettermann", "person"), gold, set()
        )
        assert idx == 0
        assert credit == 1.0

    def test_type_mismatch_partial_credit(self):
        gold = [_gold("EPA", "government_org")]
        idx, credit = find_best_match(
            _ext("EPA", "organization"), gold, set()
        )
        assert idx == 0
        assert credit == TYPE_MISMATCH_CREDIT

    def test_no_match(self):
        gold = [_gold("EPA", "government_org")]
        idx, credit = find_best_match(
            _ext("Totally Different", "person"), gold, set()
        )
        assert idx is None
        assert credit == 0.0

    def test_already_matched_skipped(self):
        gold = [_gold("EPA", "government_org"), _gold("Senate", "government_org")]
        idx, credit = find_best_match(
            _ext("EPA", "government_org"), gold, {0}
        )
        # EPA at index 0 is already matched, so no match
        assert idx is None
        assert credit == 0.0

    def test_prefers_exact_over_substring(self):
        gold = [
            _gold("Senate Banking Committee", "government_org"),
            _gold("Senate", "government_org"),
        ]
        idx, credit = find_best_match(
            _ext("Senate", "government_org"), gold, set()
        )
        assert idx == 1  # Exact match preferred
        assert credit == 1.0


# ---------------------------------------------------------------------------
# compute_scores tests
# ---------------------------------------------------------------------------


class TestComputeScores:
    def test_perfect_extraction(self):
        gold = [
            _gold("EPA", "government_org"),
            _gold("John Smith", "person"),
            _gold("Washington", "location"),
        ]
        extracted = [
            _ext("EPA", "government_org"),
            _ext("John Smith", "person"),
            _ext("Washington", "location"),
        ]
        scores = compute_scores(extracted, gold)
        assert scores["true_positives"] == 3.0
        assert scores["false_positives"] == 0.0
        assert scores["false_negatives"] == 0.0

    def test_no_entities_extracted(self):
        gold = [
            _gold("EPA", "government_org"),
            _gold("John Smith", "person"),
        ]
        scores = compute_scores([], gold)
        assert scores["true_positives"] == 0.0
        assert scores["false_positives"] == 0.0
        assert scores["false_negatives"] == 2.0

    def test_all_extracted_plus_false_positives(self):
        gold = [
            _gold("EPA", "government_org"),
            _gold("John Smith", "person"),
        ]
        extracted = [
            _ext("EPA", "government_org"),
            _ext("John Smith", "person"),
            _ext("Fake Corp", "organization"),
            _ext("Nowhere", "location"),
            _ext("Nothing", "concept"),
        ]
        scores = compute_scores(extracted, gold)
        assert scores["true_positives"] == 2.0
        assert scores["false_positives"] == 3.0
        assert scores["false_negatives"] == 0.0

    def test_partial_recall(self):
        gold = [
            _gold("EPA", "government_org"),
            _gold("John Smith", "person"),
            _gold("Washington", "location"),
            _gold("Senate", "government_org"),
            _gold("Democratic", "concept"),
        ]
        extracted = [
            _ext("EPA", "government_org"),
            _ext("John Smith", "person"),
        ]
        scores = compute_scores(extracted, gold)
        assert scores["true_positives"] == 2.0
        assert scores["false_positives"] == 0.0
        assert scores["false_negatives"] == 3.0

    def test_substring_match_counts_as_tp(self):
        gold = [_gold("Senate Banking Committee", "government_org")]
        extracted = [_ext("Banking Committee", "government_org")]
        scores = compute_scores(extracted, gold)
        assert scores["true_positives"] == 1.0
        assert scores["false_positives"] == 0.0
        assert scores["false_negatives"] == 0.0

    def test_type_mismatch_partial_credit(self):
        gold = [_gold("EPA", "government_org")]
        extracted = [_ext("EPA", "organization")]
        scores = compute_scores(extracted, gold)
        assert scores["true_positives"] == TYPE_MISMATCH_CREDIT
        assert scores["false_positives"] == 0.0
        assert scores["false_negatives"] == 0.0

    def test_duplicate_extraction(self):
        """Only the first match counts as TP; duplicate is FP."""
        gold = [_gold("EPA", "government_org")]
        extracted = [
            _ext("EPA", "government_org"),
            _ext("EPA", "government_org"),
        ]
        scores = compute_scores(extracted, gold)
        assert scores["true_positives"] == 1.0
        assert scores["false_positives"] == 1.0
        assert scores["false_negatives"] == 0.0

    def test_levenshtein_match_counts_as_tp(self):
        gold = [_gold("John Fetterman", "person")]
        extracted = [_ext("John Fettermann", "person")]
        scores = compute_scores(extracted, gold)
        assert scores["true_positives"] == 1.0

    def test_per_type_breakdown(self):
        gold = [
            _gold("EPA", "government_org"),
            _gold("John Smith", "person"),
            _gold("Washington", "location"),
        ]
        extracted = [
            _ext("EPA", "government_org"),
            _ext("Unknown Corp", "organization"),
        ]
        scores = compute_scores(extracted, gold)

        assert scores["government_org_tp"] == 1.0
        assert scores["government_org_fp"] == 0.0
        assert scores["government_org_fn"] == 0.0

        assert scores["organization_fp"] == 1.0

        assert scores["person_fn"] == 1.0
        assert scores["location_fn"] == 1.0

    def test_empty_gold_and_extracted(self):
        scores = compute_scores([], [])
        assert scores["true_positives"] == 0.0
        assert scores["false_positives"] == 0.0
        assert scores["false_negatives"] == 0.0


# ---------------------------------------------------------------------------
# compute_prf tests
# ---------------------------------------------------------------------------


class TestComputePRF:
    def test_perfect(self):
        p, r, f1 = compute_prf(3.0, 0.0, 0.0)
        assert p == 1.0
        assert r == 1.0
        assert f1 == 1.0

    def test_no_extraction(self):
        p, r, f1 = compute_prf(0.0, 0.0, 5.0)
        assert p == 0.0
        assert r == 0.0
        assert f1 == 0.0

    def test_all_false_positives(self):
        p, r, f1 = compute_prf(0.0, 3.0, 0.0)
        assert p == 0.0
        assert r == 0.0
        assert f1 == 0.0

    def test_partial(self):
        p, r, f1 = compute_prf(2.0, 0.0, 3.0)
        assert p == 1.0
        assert r == pytest.approx(0.4)
        assert f1 == pytest.approx(2 * 1.0 * 0.4 / (1.0 + 0.4))

    def test_all_zeros(self):
        p, r, f1 = compute_prf(0.0, 0.0, 0.0)
        assert p == 0.0
        assert r == 0.0
        assert f1 == 0.0


# ---------------------------------------------------------------------------
# get_assert integration tests
# ---------------------------------------------------------------------------


class TestGetAssert:
    def test_perfect_extraction(self):
        output = {
            "entities": [
                _ext("EPA", "government_org"),
                _ext("John Smith", "person"),
            ]
        }
        context = {
            "vars": {
                "entities": [
                    _gold("EPA", "government_org"),
                    _gold("John Smith", "person"),
                ]
            }
        }
        result = get_assert(output, context)
        assert result["pass"] is True
        assert result["score"] == 1.0
        assert result["namedScores"]["precision"] == 1.0
        assert result["namedScores"]["recall"] == 1.0
        assert result["namedScores"]["f1"] == 1.0

    def test_no_extraction(self):
        output = {"entities": []}
        context = {
            "vars": {
                "entities": [
                    _gold("EPA", "government_org"),
                ]
            }
        }
        result = get_assert(output, context)
        assert result["pass"] is False
        assert result["score"] == 0.0

    def test_string_output_treated_as_empty(self):
        result = get_assert(
            "Error: service unavailable",
            {"vars": {"entities": [_gold("EPA", "government_org")]}},
        )
        assert result["pass"] is False
        assert result["namedScores"]["extracted_count"] == 0.0

    def test_named_scores_present(self):
        output = {"entities": [_ext("EPA", "government_org")]}
        context = {"vars": {"entities": [_gold("EPA", "government_org")]}}
        result = get_assert(output, context)

        ns = result["namedScores"]
        assert "true_positives" in ns
        assert "false_positives" in ns
        assert "false_negatives" in ns
        assert "precision" in ns
        assert "recall" in ns
        assert "f1" in ns
        assert "extracted_count" in ns
        assert "gold_count" in ns

    def test_per_type_scores_in_named_scores(self):
        output = {"entities": [_ext("EPA", "government_org")]}
        context = {"vars": {"entities": [_gold("EPA", "government_org")]}}
        result = get_assert(output, context)

        ns = result["namedScores"]
        assert "person_tp" in ns
        assert "government_org_tp" in ns
        assert "location_fn" in ns

    def test_empty_gold_empty_extracted(self):
        result = get_assert({"entities": []}, {"vars": {"entities": []}})
        assert result["pass"] is False  # F1=0 < 0.5
        assert result["score"] == 0.0
