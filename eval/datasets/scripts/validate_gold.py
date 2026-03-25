"""
Gold Dataset Validator

Validates entity annotations in gold dataset YAML files:
1. Offset check: article_text[start:end] must match entity text
2. Overlap detection: no two entities should have overlapping spans
3. Sort order: entities should be sorted by start offset
4. Missing entity hints: flags names/orgs in text not covered by annotations

Usage:
    python eval/datasets/scripts/validate_gold.py [--file FILE] [--fix-offsets] [--suggest]

Options:
    --file FILE       Validate a single file (default: all gold/*.yaml files)
    --fix-offsets     Auto-fix offsets where possible (writes corrected YAML)
    --suggest         Scan for potentially missing entities using heuristics
    --curated-only    Only validate entries with curated: true
"""

import argparse
import re
import sys
from pathlib import Path
from typing import Optional

import yaml


GOLD_DIR = Path(__file__).resolve().parent.parent / "gold"

# Entity types from the schema
VALID_TYPES = {
    "person",
    "government_org",
    "organization",
    "location",
    "event",
    "concept",
    "legislation",
}

# Heuristic patterns for --suggest mode
# These help find entities the derivation script may have missed
TITLE_PATTERNS = re.compile(
    r"\b(?:Senator|Rep\.|Representative|President|Justice|Judge|"
    r"Secretary|Director|Chairman|Chairwoman|Governor|Mayor|"
    r"Dr\.|Gen\.|Admiral|Chief Justice|Vice President)\s+"
    r"([A-Z][a-z]+(?:\s+[A-Z]\.?\s*)?[A-Z][a-z]+)",
    re.MULTILINE,
)

ORG_PATTERNS = re.compile(
    r"\b(?:the\s+)?((?:[A-Z][a-z]+\s+){0,3}"
    r"(?:Committee|Commission|Department|Agency|Bureau|Court|"
    r"Senate|House|Congress|University|Institute|Association|Council))",
    re.MULTILINE,
)

LOCATION_PATTERNS = re.compile(
    r"\b(?:WASHINGTON|Washington|D\.C\.|Capitol Hill)\b"
)


class ValidationResult:
    def __init__(self, file_path: str, entry_id: str):
        self.file_path = file_path
        self.entry_id = entry_id
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.suggestions: list[str] = []

    @property
    def has_issues(self) -> bool:
        return bool(self.errors or self.warnings)

    def add_error(self, msg: str):
        self.errors.append(msg)

    def add_warning(self, msg: str):
        self.warnings.append(msg)

    def add_suggestion(self, msg: str):
        self.suggestions.append(msg)


def load_gold_file(path: Path) -> list[dict]:
    """Load a gold dataset YAML file, return list of test case entries."""
    with open(path, "r", encoding="utf-8") as f:
        data = yaml.safe_load(f)
    if data is None:
        return []
    if not isinstance(data, list):
        raise ValueError(f"{path.name}: Expected a YAML list at top level")
    return data


def get_entry_id(entry: dict) -> str:
    """Extract a human-readable ID from an entry."""
    metadata = entry.get("vars", {}).get("metadata", {})
    if isinstance(metadata, dict):
        return metadata.get("id", "unknown")
    return "unknown"


def get_article_text(entry: dict) -> Optional[str]:
    """Extract article_text from a Promptfoo vars entry."""
    vars_block = entry.get("vars", {})
    return vars_block.get("article_text")


def validate_offsets(article_text: str, entities: list[dict], result: ValidationResult):
    """Check that each entity's start:end slice matches its text."""
    for i, ent in enumerate(entities):
        text = ent.get("text", "")
        start = ent.get("start")
        end = ent.get("end")

        if start is None or end is None:
            result.add_error(f"  Entity #{i+1} '{text}': missing start or end offset")
            continue

        if not isinstance(start, int) or not isinstance(end, int):
            result.add_error(f"  Entity #{i+1} '{text}': start/end must be integers (got start={start}, end={end})")
            continue

        if start < 0 or end < 0:
            result.add_error(f"  Entity #{i+1} '{text}': negative offset (start={start}, end={end})")
            continue

        if start >= end:
            result.add_error(f"  Entity #{i+1} '{text}': start ({start}) >= end ({end})")
            continue

        if end > len(article_text):
            result.add_error(
                f"  Entity #{i+1} '{text}': end ({end}) exceeds article length ({len(article_text)})"
            )
            continue

        actual = article_text[start:end]
        if actual != text:
            # Check case-insensitive match
            if actual.lower() == text.lower():
                result.add_warning(
                    f"  Entity #{i+1}: case mismatch — "
                    f"text='{text}' but article[{start}:{end}]='{actual}'"
                )
            else:
                # Try to find the correct offset
                found_at = article_text.find(text)
                if found_at >= 0:
                    result.add_error(
                        f"  Entity #{i+1}: OFFSET MISMATCH — "
                        f"text='{text}' not at [{start}:{end}] (found '{actual}'), "
                        f"correct offset would be [{found_at}:{found_at + len(text)}]"
                    )
                else:
                    # Case-insensitive search
                    found_ci = article_text.lower().find(text.lower())
                    if found_ci >= 0:
                        actual_ci = article_text[found_ci:found_ci + len(text)]
                        result.add_error(
                            f"  Entity #{i+1}: OFFSET MISMATCH — "
                            f"text='{text}' not at [{start}:{end}] (found '{actual}'), "
                            f"case-insensitive match at [{found_ci}:{found_ci + len(text)}] = '{actual_ci}'"
                        )
                    else:
                        result.add_error(
                            f"  Entity #{i+1}: TEXT NOT FOUND — "
                            f"'{text}' not found anywhere in article text"
                        )


def validate_types(entities: list[dict], result: ValidationResult):
    """Check that all entity types are valid."""
    for i, ent in enumerate(entities):
        ent_type = ent.get("type", "")
        if ent_type not in VALID_TYPES:
            result.add_error(
                f"  Entity #{i+1} '{ent.get('text', '')}': "
                f"invalid type '{ent_type}' (valid: {', '.join(sorted(VALID_TYPES))})"
            )


def validate_overlaps(entities: list[dict], result: ValidationResult):
    """Check for overlapping entity spans."""
    sorted_ents = sorted(
        [(e.get("start", 0), e.get("end", 0), e.get("text", "")) for e in entities]
    )
    for i in range(len(sorted_ents) - 1):
        _, end_a, text_a = sorted_ents[i]
        start_b, _, text_b = sorted_ents[i + 1]
        if end_a > start_b:
            result.add_warning(
                f"  Overlapping spans: '{text_a}' ends at {end_a} but '{text_b}' starts at {start_b}"
            )


def validate_sort_order(entities: list[dict], result: ValidationResult):
    """Check that entities are sorted by start offset."""
    starts = [e.get("start", 0) for e in entities]
    if starts != sorted(starts):
        result.add_warning("  Entities are not sorted by start offset")


def suggest_missing_entities(
    article_text: str, entities: list[dict], result: ValidationResult
):
    """Heuristic scan for entities that might be missing from annotations."""
    annotated_texts = {e.get("text", "").lower() for e in entities}

    # Check for person names with titles
    for match in TITLE_PATTERNS.finditer(article_text):
        name = match.group(1)
        if name.lower() not in annotated_texts:
            pos = match.start(1)
            result.add_suggestion(
                f"  Possible person: '{name}' at offset {pos}"
            )

    # Check for organizations
    for match in ORG_PATTERNS.finditer(article_text):
        org = match.group(1).strip()
        if org.lower() not in annotated_texts and len(org) > 5:
            pos = match.start(1)
            result.add_suggestion(
                f"  Possible org: '{org}' at offset {pos}"
            )

    # Check for common locations in datelines
    for match in LOCATION_PATTERNS.finditer(article_text):
        loc = match.group(0)
        if loc.lower() not in annotated_texts:
            pos = match.start()
            result.add_suggestion(
                f"  Possible location: '{loc}' at offset {pos}"
            )


def find_correct_offset(article_text: str, entity_text: str) -> Optional[tuple[int, int]]:
    """Find the correct start/end offset for an entity text in the article."""
    idx = article_text.find(entity_text)
    if idx >= 0:
        return (idx, idx + len(entity_text))
    # Case-insensitive fallback
    idx = article_text.lower().find(entity_text.lower())
    if idx >= 0:
        return (idx, idx + len(entity_text))
    return None


def fix_offsets_in_file(path: Path) -> tuple[int, int]:
    """Auto-fix offsets in a gold YAML file. Returns (fixed_count, unfixable_count)."""
    with open(path, "r", encoding="utf-8") as f:
        raw_content = f.read()

    data = yaml.safe_load(raw_content)
    if not data or not isinstance(data, list):
        return (0, 0)

    fixed = 0
    unfixable = 0

    for entry in data:
        article_text = get_article_text(entry)
        if not article_text:
            continue

        entities = entry.get("vars", {}).get("entities", [])
        for ent in entities:
            text = ent.get("text", "")
            start = ent.get("start")
            end = ent.get("end")

            if start is None or end is None:
                continue

            actual = article_text[start:end] if 0 <= start < end <= len(article_text) else ""

            if actual != text:
                correct = find_correct_offset(article_text, text)
                if correct:
                    ent["start"] = correct[0]
                    ent["end"] = correct[1]
                    fixed += 1
                else:
                    unfixable += 1

    if fixed > 0:
        # Re-read to preserve comments, write back with fixes
        # Use yaml.dump for the data portion
        with open(path, "r", encoding="utf-8") as f:
            lines = f.readlines()

        # Extract header comments
        header_lines = []
        for line in lines:
            if line.startswith("#"):
                header_lines.append(line)
            else:
                break

        header = "".join(header_lines)
        yaml_content = yaml.dump(data, default_flow_style=False, allow_unicode=True, sort_keys=False)

        with open(path, "w", encoding="utf-8") as f:
            f.write(header)
            f.write(yaml_content)

    return (fixed, unfixable)


def validate_file(
    path: Path, suggest: bool = False, curated_only: bool = False
) -> list[ValidationResult]:
    """Validate all entries in a gold dataset YAML file."""
    results = []
    data = load_gold_file(path)

    for i, entry in enumerate(data):
        entry_id = get_entry_id(entry)
        article_text = get_article_text(entry)

        if article_text is None:
            result = ValidationResult(path.name, entry_id)
            result.add_error("  Missing article_text in vars")
            results.append(result)
            continue

        metadata = entry.get("vars", {}).get("metadata", {})
        is_curated = metadata.get("curated", False) if isinstance(metadata, dict) else False

        if curated_only and not is_curated:
            continue

        entities = entry.get("vars", {}).get("entities", [])

        result = ValidationResult(path.name, entry_id)

        if not entities:
            result.add_warning("  No entities annotated")
        else:
            validate_offsets(article_text, entities, result)
            validate_types(entities, result)
            validate_overlaps(entities, result)
            validate_sort_order(entities, result)

        if suggest:
            suggest_missing_entities(article_text, entities, result)

        if result.has_issues or result.suggestions:
            results.append(result)

    return results


def print_results(results: list[ValidationResult], show_suggestions: bool = False):
    """Print validation results in a human-readable format."""
    error_count = sum(len(r.errors) for r in results)
    warning_count = sum(len(r.warnings) for r in results)
    suggestion_count = sum(len(r.suggestions) for r in results)

    for result in results:
        if result.errors or result.warnings or (show_suggestions and result.suggestions):
            print(f"\n[{result.file_path}] {result.entry_id}")

            for err in result.errors:
                print(f"  ERROR: {err}")
            for warn in result.warnings:
                print(f"  WARN:  {warn}")
            if show_suggestions:
                for sug in result.suggestions:
                    print(f"  HINT:  {sug}")

    print(f"\n{'='*60}")
    print(f"Errors: {error_count}  |  Warnings: {warning_count}  |  Suggestions: {suggestion_count}")

    if error_count > 0:
        print("Run with --fix-offsets to auto-correct offset mismatches.")


def print_summary(all_files: list[Path]):
    """Print a summary table of gold dataset status."""
    print(f"\n{'='*60}")
    print("GOLD DATASET SUMMARY")
    print(f"{'='*60}")
    print(f"{'File':<25} {'Total':>6} {'Curated':>8} {'Entities':>9}")
    print(f"{'-'*25} {'-'*6} {'-'*8} {'-'*9}")

    grand_total = 0
    grand_curated = 0
    grand_entities = 0

    for path in all_files:
        data = load_gold_file(path)
        total = len(data)
        curated = 0
        entities = 0

        for entry in data:
            metadata = entry.get("vars", {}).get("metadata", {})
            if isinstance(metadata, dict) and metadata.get("curated"):
                curated += 1
            ent_list = entry.get("vars", {}).get("entities", [])
            entities += len(ent_list)

        grand_total += total
        grand_curated += curated
        grand_entities += entities

        print(f"{path.name:<25} {total:>6} {curated:>8} {entities:>9}")

    print(f"{'-'*25} {'-'*6} {'-'*8} {'-'*9}")
    print(f"{'TOTAL':<25} {grand_total:>6} {grand_curated:>8} {grand_entities:>9}")


def main():
    parser = argparse.ArgumentParser(description="Validate gold dataset YAML files")
    parser.add_argument("--file", type=str, help="Validate a single file")
    parser.add_argument(
        "--fix-offsets", action="store_true", help="Auto-fix offset mismatches"
    )
    parser.add_argument(
        "--suggest", action="store_true", help="Suggest potentially missing entities"
    )
    parser.add_argument(
        "--curated-only",
        action="store_true",
        help="Only validate curated entries",
    )
    parser.add_argument(
        "--summary", action="store_true", help="Print dataset summary table"
    )
    args = parser.parse_args()

    if args.file:
        files = [Path(args.file)]
    else:
        files = sorted(GOLD_DIR.glob("*.yaml"))

    if not files:
        print("No gold dataset files found.")
        sys.exit(1)

    if args.summary:
        print_summary(files)
        print()

    if args.fix_offsets:
        total_fixed = 0
        total_unfixable = 0
        for path in files:
            fixed, unfixable = fix_offsets_in_file(path)
            if fixed or unfixable:
                print(f"{path.name}: fixed {fixed} offsets, {unfixable} unfixable")
            total_fixed += fixed
            total_unfixable += unfixable
        print(f"\nTotal: {total_fixed} fixed, {total_unfixable} unfixable")
        return

    all_results = []
    for path in files:
        print(f"Validating {path.name}...")
        results = validate_file(path, suggest=args.suggest, curated_only=args.curated_only)
        all_results.extend(results)

    print_results(all_results, show_suggestions=args.suggest)

    if args.summary:
        print_summary(files)

    # Exit with error code if there are errors
    error_count = sum(len(r.errors) for r in all_results)
    sys.exit(1 if error_count > 0 else 0)


if __name__ == "__main__":
    main()
