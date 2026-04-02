"""
Gold Dataset Integrity Tests (EVAL-3.4 Task 8).

Validates that gold YAML files are well-formed and consistent with the ontology.
"""

import pytest
import yaml
from pathlib import Path

BIAS_DIR = Path(__file__).parent.parent
DATASETS = ["synthetic_biased.yaml", "curated_biased.yaml"]

# Valid distortion types from the ontology (14 total)
VALID_TYPES = {
    "confirmation_bias", "anchoring_bias", "framing_effect",
    "availability_heuristic", "bandwagon_effect",
    "ad_hominem", "straw_man", "false_dilemma", "slippery_slope",
    "appeal_to_authority", "red_herring", "circular_reasoning",
    "affirming_the_consequent", "denying_the_antecedent",
}

VALID_DIFFICULTIES = {"easy", "medium", "hard"}
VALID_SOURCES = {"synthetic", "curated"}


def _load_dataset(filename: str) -> list[dict]:
    path = BIAS_DIR / filename
    if not path.exists():
        return []
    with open(path) as f:
        data = yaml.safe_load(f)
    return data if isinstance(data, list) else []


def _all_entries():
    entries = []
    for ds in DATASETS:
        entries.extend(_load_dataset(ds))
    return entries


class TestYAMLParsing:
    @pytest.mark.parametrize("filename", DATASETS)
    def test_yaml_parses(self, filename):
        """Gold YAML files parse without errors."""
        path = BIAS_DIR / filename
        if not path.exists():
            pytest.skip(f"{filename} not yet created")
        with open(path) as f:
            data = yaml.safe_load(f)
        assert isinstance(data, list)


class TestBiasTypes:
    def test_all_types_valid(self):
        """Every bias type is a valid distortion from the ontology."""
        for entry in _all_entries():
            biases = entry.get("vars", {}).get("biases", []) or []
            for b in biases:
                assert b["type"] in VALID_TYPES, f"Invalid type: {b['type']}"


class TestMetadata:
    def test_unique_ids(self):
        """Every article has a unique metadata.id."""
        ids = []
        for entry in _all_entries():
            mid = entry.get("vars", {}).get("metadata", {}).get("id")
            assert mid is not None, "Missing metadata.id"
            ids.append(mid)
        assert len(ids) == len(set(ids)), f"Duplicate IDs: {[x for x in ids if ids.count(x) > 1]}"

    def test_valid_difficulty(self):
        """All entries have valid difficulty levels."""
        for entry in _all_entries():
            diff = entry.get("vars", {}).get("metadata", {}).get("difficulty")
            assert diff in VALID_DIFFICULTIES, f"Invalid difficulty: {diff}"

    def test_valid_source(self):
        """All entries have valid source values."""
        for entry in _all_entries():
            source = entry.get("vars", {}).get("metadata", {}).get("source")
            assert source in VALID_SOURCES, f"Invalid source: {source}"

    def test_injected_types_match_biases(self):
        """Synthetic articles have injected_types matching their biases."""
        for entry in _all_entries():
            meta = entry.get("vars", {}).get("metadata", {})
            if meta.get("source") != "synthetic":
                continue
            injected = set(meta.get("injected_types", []))
            bias_types = set(b["type"] for b in entry.get("vars", {}).get("biases", []))
            assert injected == bias_types, f"Mismatch in {meta.get('id')}: injected={injected}, biases={bias_types}"

    def test_bias_count_matches(self):
        """bias_count matches actual number of biases."""
        for entry in _all_entries():
            meta = entry.get("vars", {}).get("metadata", {})
            biases = entry.get("vars", {}).get("biases", []) or []
            assert meta.get("bias_count") == len(biases), f"Mismatch in {meta.get('id')}"


class TestFaithfulArticles:
    def test_faithful_have_empty_biases(self):
        """Articles with bias_count=0 have empty biases arrays."""
        for entry in _all_entries():
            meta = entry.get("vars", {}).get("metadata", {})
            if meta.get("bias_count", -1) == 0:
                biases = entry.get("vars", {}).get("biases", []) or []
                assert len(biases) == 0, f"Faithful article {meta.get('id')} has biases"


class TestCoverage:
    def test_multiple_difficulty_levels(self):
        """At least 2 difficulty levels represented."""
        difficulties = set()
        for entry in _all_entries():
            diff = entry.get("vars", {}).get("metadata", {}).get("difficulty")
            if diff:
                difficulties.add(diff)
        assert len(difficulties) >= 2, f"Only {difficulties} difficulties found"

    def test_multiple_distortion_types(self):
        """At least 5 different distortion types represented."""
        types = set()
        for entry in _all_entries():
            for b in entry.get("vars", {}).get("biases", []) or []:
                types.add(b["type"])
        assert len(types) >= 5, f"Only {len(types)} types: {types}"
