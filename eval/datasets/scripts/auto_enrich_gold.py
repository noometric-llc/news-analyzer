"""
Gold Dataset Auto-Enricher

Automatically adds commonly missed entities to gold dataset annotations:
1. Dateline locations (WASHINGTON, JACKSON, Miss., etc.)
2. First occurrence of "Senate", "Congress", "House" as government_org
3. First occurrence of quoted person names with titles

This is a curation helper — run it once, then manually review the results.

Usage:
    python eval/datasets/scripts/auto_enrich_gold.py [--dry-run] [--file FILE]

Options:
    --dry-run    Show what would be added without modifying files
    --file FILE  Process a single file (default: all gold/*.yaml)
"""

import argparse
import re
import sys
from pathlib import Path

import yaml


GOLD_DIR = Path(__file__).resolve().parent.parent / "gold"


# --- Dateline Extraction ---

# Matches: **CITY, State, Date** — or CITY, State — etc.
# Captures the location portion of the dateline
# US states (abbreviations) used to distinguish "CITY, State" from "CITY, Month"
_US_STATE_ABBREVS = {
    "Ala.", "Alaska", "Ariz.", "Ark.", "Calif.", "Colo.", "Conn.", "D.C.",
    "Del.", "Fla.", "Ga.", "Hawaii", "Idaho", "Ill.", "Ind.", "Iowa",
    "Kan.", "Ky.", "La.", "Maine", "Md.", "Mass.", "Mich.", "Minn.",
    "Miss.", "Mo.", "Mont.", "Neb.", "Nev.", "N.H.", "N.J.", "N.M.",
    "N.Y.", "N.C.", "N.D.", "Ohio", "Okla.", "Ore.", "Pa.", "R.I.",
    "S.C.", "S.D.", "Tenn.", "Texas", "Utah", "Vt.", "Va.", "Wash.",
    "W.Va.", "Wis.", "Wyo.",
}

# Multi-word city names that the simple ALL-CAPS regex would truncate
_MULTI_WORD_CITIES = {
    "SALT LAKE CITY", "WEST PALM BEACH", "EL PASO", "BATON ROUGE",
    "PAGO PAGO", "LITTLE ROCK", "NEW YORK", "NEW ORLEANS", "LAS VEGAS",
    "LOS ANGELES", "SAN FRANCISCO", "SAN ANTONIO", "SAN DIEGO",
    "FORT WORTH", "CAPE CANAVERAL", "VIRGINIA BEACH", "GRAND RAPIDS",
    "CEDAR RAPIDS", "DES MOINES", "CORPUS CHRISTI", "COLORADO SPRINGS",
    "PALM BEACH", "WEST VIRGINIA", "NORTH CAROLINA", "SOUTH CAROLINA",
}


def find_dateline_location(article_text: str) -> list[dict]:
    """Extract dateline location entity from article start."""
    # Strip leading bold markers to get to the text
    text = article_text
    bold_offset = 0
    if text.startswith("**"):
        bold_offset = 2
        text = text[2:]

    # Try multi-word cities first
    for city in sorted(_MULTI_WORD_CITIES, key=len, reverse=True):
        if text.startswith(city):
            # Check what follows: comma+state, comma+date, or space/punctuation
            after = text[len(city):]
            loc_text = city

            # Check for ", State" suffix
            state_match = re.match(r",\s*([A-Z][A-Za-z.]+)", after)
            if state_match and state_match.group(1) in _US_STATE_ABBREVS:
                loc_text = city + after[:state_match.end()]

            start = bold_offset
            return [{
                "text": loc_text,
                "type": "location",
                "start": start,
                "end": start + len(loc_text),
            }]

    # Single-word ALL-CAPS city: match consecutive uppercase letters
    m = re.match(r"([A-Z]{2,})", text)
    if not m:
        return []

    city = m.group(1)
    after = text[len(city):]

    # Check for ", State" or ", D.C." suffix
    loc_text = city
    state_match = re.match(r",\s*([A-Z][A-Za-z.]+(?:\.\s*[A-Z][A-Za-z.]+)?)", after)
    if state_match:
        candidate = state_match.group(1)
        # Only append if it looks like a state abbreviation, not a month/date
        if candidate in _US_STATE_ABBREVS or candidate.startswith("D.C"):
            loc_text = city + after[:state_match.end()]

    start = bold_offset
    return [{
        "text": loc_text,
        "type": "location",
        "start": start,
        "end": start + len(loc_text),
    }]


# --- Government Organization Extraction ---

GOV_ORG_PATTERNS = [
    # Only match first occurrence of each
    ("Senate", "government_org", re.compile(r"\bthe\s+(U\.S\.\s+)?Senate\b|\bSenate\b")),
    ("House", "government_org", re.compile(r"\bthe\s+(?:U\.S\.\s+)?House(?:\s+of\s+Representatives)?\b|\bHouse\s+of\s+Representatives\b")),
    ("Congress", "government_org", re.compile(r"\b(?:the\s+)?(?:U\.S\.\s+)?Congress\b")),
    ("Supreme Court", "government_org", re.compile(r"\b(?:the\s+)?(?:U\.S\.\s+)?Supreme\s+Court\b")),
]


def find_gov_orgs(article_text: str) -> list[dict]:
    """Find first occurrence of common government organizations."""
    entities = []
    for name, ent_type, pattern in GOV_ORG_PATTERNS:
        m = pattern.search(article_text)
        if m:
            matched_text = m.group(0)
            # Strip leading "the " for cleaner entity text
            display_text = matched_text
            display_start = m.start()

            # If it starts with "the ", keep the more specific part
            if matched_text.lower().startswith("the "):
                display_text = matched_text[4:]
                display_start = m.start() + 4

            entities.append({
                "text": display_text,
                "type": ent_type,
                "start": display_start,
                "end": display_start + len(display_text),
            })
    return entities


# --- Quoted Person Names ---

QUOTED_PERSON_RE = re.compile(
    r"(?:said|according to|noted|added|explained|stated|argued|remarked)\s+"
    r"(?:Dr\.|Sen\.|Rep\.|Professor|Mr\.|Mrs\.|Ms\.)?\s*"
    r"(?P<name>[A-Z][a-z]+(?:\s+[A-Z]\.?\s*)?[A-Z][a-z]+)",
    re.MULTILINE,
)

TITLE_PERSON_RE = re.compile(
    r"(?:Dr\.|Professor|Sen\.|Rep\.)\s+"
    r"(?P<name>[A-Z][a-z]+(?:\s+[A-Z]\.?\s*)*[A-Z][a-z]+)",
)


def find_quoted_persons(article_text: str) -> list[dict]:
    """Find person names from attribution phrases and titled references."""
    entities = []
    seen_names = set()

    for pattern in [QUOTED_PERSON_RE, TITLE_PERSON_RE]:
        for m in pattern.finditer(article_text):
            name = m.group("name").strip()
            if name.lower() in seen_names:
                continue
            if len(name.split()) < 2:
                continue  # Skip single-word matches
            seen_names.add(name.lower())

            # Find exact position of the name
            name_start = article_text.find(name, m.start())
            if name_start >= 0:
                entities.append({
                    "text": name,
                    "type": "person",
                    "start": name_start,
                    "end": name_start + len(name),
                })

    return entities


# --- Merging Logic ---

def entities_overlap(a: dict, b: dict) -> bool:
    """Check if two entity spans overlap."""
    return a["start"] < b["end"] and b["start"] < a["end"]


def entity_already_covered(new_ent: dict, existing: list[dict]) -> bool:
    """Check if an entity is already annotated (by overlap or same text)."""
    for existing_ent in existing:
        # Exact text match (case-insensitive)
        if new_ent["text"].lower() == existing_ent.get("text", "").lower():
            return True
        # Span overlap
        if entities_overlap(new_ent, existing_ent):
            return True
    return False


def enrich_entry(entry: dict) -> list[dict]:
    """Find entities to add to a single gold dataset entry. Returns list of new entities."""
    vars_block = entry.get("vars", {})
    article_text = vars_block.get("article_text", "")
    existing_entities = vars_block.get("entities", [])
    metadata = vars_block.get("metadata", {})

    if not article_text:
        return []

    # Skip CoNLL entries — those are externally sourced and already curated
    if isinstance(metadata, dict) and metadata.get("source") == "conll":
        return []

    new_entities = []

    # 1. Dateline locations
    for ent in find_dateline_location(article_text):
        if not entity_already_covered(ent, existing_entities):
            new_entities.append(ent)

    # 2. Government organizations (first occurrence only)
    for ent in find_gov_orgs(article_text):
        if not entity_already_covered(ent, existing_entities + new_entities):
            new_entities.append(ent)

    # 3. Quoted/titled person names
    for ent in find_quoted_persons(article_text):
        if not entity_already_covered(ent, existing_entities + new_entities):
            new_entities.append(ent)

    return new_entities


def apply_enrichments(entry: dict, new_entities: list[dict]):
    """Add new entities to an entry and re-sort by start offset."""
    vars_block = entry.get("vars", {})
    existing = vars_block.get("entities", [])
    existing.extend(new_entities)
    existing.sort(key=lambda e: e.get("start", 0))
    vars_block["entities"] = existing


def process_file(
    path: Path, dry_run: bool = False
) -> tuple[int, int, list[tuple[str, list[dict]]]]:
    """Process a single gold YAML file. Returns (entries_enriched, entities_added, details)."""
    with open(path, "r", encoding="utf-8") as f:
        raw = f.read()

    data = yaml.safe_load(raw)
    if not data or not isinstance(data, list):
        return (0, 0, [])

    entries_enriched = 0
    total_added = 0
    details = []

    for entry in data:
        entry_id = entry.get("vars", {}).get("metadata", {}).get("id", "unknown")
        new_entities = enrich_entry(entry)

        if new_entities:
            entries_enriched += 1
            total_added += len(new_entities)
            details.append((entry_id, new_entities))

            if not dry_run:
                apply_enrichments(entry, new_entities)

    if not dry_run and total_added > 0:
        # Preserve header comments
        header_lines = []
        for line in raw.splitlines(True):
            if line.startswith("#"):
                header_lines.append(line)
            else:
                break
        header = "".join(header_lines)

        yaml_content = yaml.dump(
            data,
            default_flow_style=False,
            allow_unicode=True,
            sort_keys=False,
            width=120,
        )

        with open(path, "w", encoding="utf-8") as f:
            f.write(header)
            f.write(yaml_content)

    return (entries_enriched, total_added, details)


def main():
    parser = argparse.ArgumentParser(description="Auto-enrich gold dataset annotations")
    parser.add_argument("--dry-run", action="store_true", help="Show changes without writing")
    parser.add_argument("--file", type=str, help="Process a single file")
    args = parser.parse_args()

    if args.file:
        files = [Path(args.file)]
    else:
        files = sorted(GOLD_DIR.glob("*.yaml"))

    if not files:
        print("No gold dataset files found.")
        sys.exit(1)

    grand_entries = 0
    grand_added = 0

    for path in files:
        print(f"\n{'='*60}")
        print(f"Processing {path.name}{'  (DRY RUN)' if args.dry_run else ''}...")
        print(f"{'='*60}")

        entries_enriched, total_added, details = process_file(path, dry_run=args.dry_run)

        for entry_id, new_ents in details:
            print(f"\n  [{entry_id}] +{len(new_ents)} entities:")
            for ent in new_ents:
                print(f"    + {ent['type']:15s} '{ent['text']}' [{ent['start']}:{ent['end']}]")

        if not details:
            print("  (no new entities to add)")

        grand_entries += entries_enriched
        grand_added += total_added

    print(f"\n{'='*60}")
    print(f"TOTAL: {grand_added} entities added across {grand_entries} entries")
    if args.dry_run:
        print("(DRY RUN — no files modified. Remove --dry-run to apply.)")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
