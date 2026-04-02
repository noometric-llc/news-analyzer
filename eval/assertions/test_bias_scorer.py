"""
Tests for bias_scorer.py (EVAL-3.4).

Covers: perfect detection, no detection, false positives, partial credit,
mixed results, per-type breakdown, faithful articles, pass threshold.
"""

import pytest
from bias_scorer import get_assert, PASS_THRESHOLD, DISTORTION_CATEGORIES


# -------------------------------------------------------------------
#  Helpers
# -------------------------------------------------------------------

def _make_context(biases):
    """Build Promptfoo context with gold biases."""
    return {"vars": {"biases": biases, "article_text": "...", "metadata": {}}}


def _make_output(annotations):
    """Build detector output dict."""
    return {"annotations": annotations, "total_count": len(annotations)}


def _ann(distortion_type, confidence=0.8):
    """Shorthand for a detected annotation dict."""
    return {
        "distortion_type": distortion_type,
        "category": "",
        "excerpt": "some text",
        "explanation": "reason",
        "confidence": confidence,
    }


def _gold(bias_type):
    """Shorthand for a gold bias dict."""
    return {"type": bias_type, "excerpt": "some text", "explanation": "reason"}


# -------------------------------------------------------------------
#  Tests
# -------------------------------------------------------------------


class TestPerfectDetection:
    def test_single_exact_match(self):
        """One gold, one detected, exact match → P=1, R=1, F1=1."""
        result = get_assert(
            _make_output([_ann("framing_effect")]),
            _make_context([_gold("framing_effect")]),
        )
        assert result["namedScores"]["Precision"] == 1.0
        assert result["namedScores"]["Recall"] == 1.0
        assert result["namedScores"]["F1"] == 1.0
        assert result["pass"] is True

    def test_two_exact_matches(self):
        """Two gold, two detected, both exact → P=1, R=1, F1=1."""
        result = get_assert(
            _make_output([_ann("framing_effect"), _ann("ad_hominem")]),
            _make_context([_gold("framing_effect"), _gold("ad_hominem")]),
        )
        assert result["namedScores"]["F1"] == 1.0


class TestNoDetection:
    def test_no_detections_with_gold(self):
        """Gold exists but nothing detected → P=0, R=0, F1=0."""
        result = get_assert(
            _make_output([]),
            _make_context([_gold("framing_effect")]),
        )
        assert result["namedScores"]["Precision"] == 0.0
        assert result["namedScores"]["Recall"] == 0.0
        assert result["namedScores"]["F1"] == 0.0
        assert result["pass"] is False


class TestFalsePositivesOnly:
    def test_detections_with_no_gold(self):
        """Detected biases but gold is empty → all FPs."""
        result = get_assert(
            _make_output([_ann("framing_effect"), _ann("ad_hominem")]),
            _make_context([]),
        )
        assert result["namedScores"]["false_positives"] == 2.0
        assert result["namedScores"]["Precision"] == 0.0


class TestCategoryPartialCredit:
    def test_same_category_different_type(self):
        """Gold=ad_hominem, detected=straw_man (both informal_fallacy) → 0.5 credit."""
        result = get_assert(
            _make_output([_ann("straw_man")]),
            _make_context([_gold("ad_hominem")]),
        )
        assert result["namedScores"]["true_positives"] == 0.5
        assert result["namedScores"]["false_positives"] == 0.0
        assert result["namedScores"]["false_negatives"] == 0.0
        # P = 0.5/0.5 = 1.0, R = 0.5/0.5 = 1.0, F1 = 1.0
        assert result["namedScores"]["Precision"] == 1.0

    def test_different_category_no_credit(self):
        """Gold=framing_effect (cognitive_bias), detected=ad_hominem (informal_fallacy) → no match."""
        result = get_assert(
            _make_output([_ann("ad_hominem")]),
            _make_context([_gold("framing_effect")]),
        )
        assert result["namedScores"]["true_positives"] == 0.0
        assert result["namedScores"]["false_positives"] == 1.0
        assert result["namedScores"]["false_negatives"] == 1.0


class TestMixedResults:
    def test_one_tp_one_fp_one_fn(self):
        """1 exact match + 1 cross-category FP + 1 FN → verify P/R/F1."""
        # framing_effect (cognitive_bias) vs ad_hominem (informal_fallacy) → different categories
        result = get_assert(
            _make_output([_ann("framing_effect"), _ann("affirming_the_consequent")]),
            _make_context([_gold("framing_effect"), _gold("ad_hominem")]),
        )
        ns = result["namedScores"]
        # framing_effect matches exactly (1.0), affirming_the_consequent is formal_fallacy
        # ad_hominem is informal_fallacy → no category match → FP + FN
        assert ns["true_positives"] == 1.0
        assert ns["false_positives"] == 1.0
        assert ns["false_negatives"] == 1.0
        assert ns["Precision"] == 0.5
        assert ns["Recall"] == 0.5
        assert ns["F1"] == 0.5


class TestPerTypeBreakdown:
    def test_per_type_keys(self):
        """namedScores has per-type _tp/_fp/_fn keys."""
        result = get_assert(
            _make_output([_ann("framing_effect"), _ann("ad_hominem")]),
            _make_context([_gold("framing_effect")]),
        )
        ns = result["namedScores"]
        assert "framing_effect_tp" in ns
        assert ns["framing_effect_tp"] == 1.0
        assert "ad_hominem_fp" in ns
        assert ns["ad_hominem_fp"] == 1.0


class TestFaithfulArticles:
    def test_no_gold_no_detections(self):
        """Faithful article: no gold biases, no detections → P=1, R=1."""
        result = get_assert(
            _make_output([]),
            _make_context([]),
        )
        assert result["namedScores"]["Precision"] == 1.0
        assert result["namedScores"]["Recall"] == 1.0
        assert result["namedScores"]["F1"] == 1.0
        assert result["pass"] is True

    def test_no_gold_but_false_detections(self):
        """Faithful article with false detections → FPs counted."""
        result = get_assert(
            _make_output([_ann("framing_effect")]),
            _make_context([]),
        )
        assert result["namedScores"]["false_positives"] == 1.0
        assert result["namedScores"]["Precision"] == 0.0


class TestMultipleBiases:
    def test_three_biases_scored_independently(self):
        """Multiple biases with partial credit."""
        result = get_assert(
            _make_output([
                _ann("framing_effect"),
                _ann("ad_hominem"),
                _ann("straw_man"),
            ]),
            _make_context([
                _gold("framing_effect"),
                _gold("ad_hominem"),
                _gold("slippery_slope"),
            ]),
        )
        ns = result["namedScores"]
        # framing_effect + ad_hominem exact (2.0), straw_man partial for slippery_slope (0.5)
        assert ns["true_positives"] == 2.5
        assert ns["false_positives"] == 0.0
        assert ns["false_negatives"] == 0.0


class TestPassThreshold:
    def test_below_threshold_fails(self):
        """F1 below 0.3 → pass=False."""
        # affirming_the_consequent (formal_fallacy) can't match
        # framing_effect (cognitive_bias) or straw_man (informal_fallacy)
        result = get_assert(
            _make_output([_ann("affirming_the_consequent")]),
            _make_context([_gold("framing_effect"), _gold("straw_man")]),
        )
        assert result["pass"] is False

    def test_at_threshold_passes(self):
        """F1 >= 0.3 → pass=True."""
        # 1 TP, 1 FP → P=0.5, R=1.0, F1=0.667
        result = get_assert(
            _make_output([_ann("framing_effect"), _ann("bandwagon_effect")]),
            _make_context([_gold("framing_effect")]),
        )
        assert result["pass"] is True
        assert result["namedScores"]["F1"] >= PASS_THRESHOLD


class TestCaseInsensitive:
    def test_case_insensitive_match(self):
        """Distortion type matching is case-insensitive."""
        result = get_assert(
            _make_output([_ann("Framing_Effect")]),
            _make_context([_gold("framing_effect")]),
        )
        assert result["namedScores"]["true_positives"] == 1.0
