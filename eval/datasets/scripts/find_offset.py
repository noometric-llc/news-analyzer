"""
Find character offsets for entity text in gold dataset articles.

Usage:
    python eval/datasets/scripts/find_offset.py --file executive.yaml --id eval-2-exe-002 --text "John F. Kennedy"
    python eval/datasets/scripts/find_offset.py --file executive.yaml --id eval-2-exe-002 --text "John F. Kennedy" --all

Options:
    --file FILE   Gold YAML filename (looks in eval/datasets/gold/)
    --id ID       Article metadata ID
    --text TEXT   Entity text to find
    --all         Show all occurrences, not just the first
"""

import argparse
import sys
from pathlib import Path

import yaml


GOLD_DIR = Path(__file__).resolve().parent.parent / "gold"


def main():
    parser = argparse.ArgumentParser(description="Find entity offsets in gold articles")
    parser.add_argument("--file", required=True, help="Gold YAML filename (e.g. executive.yaml)")
    parser.add_argument("--id", required=True, help="Article metadata ID (e.g. eval-2-exe-002)")
    parser.add_argument("--text", required=True, help="Entity text to find")
    parser.add_argument("--all", action="store_true", help="Show all occurrences")
    args = parser.parse_args()

    path = GOLD_DIR / args.file
    if not path.exists():
        print(f"File not found: {path}")
        sys.exit(1)

    data = yaml.safe_load(open(path, encoding="utf-8"))
    entry = None
    for e in data:
        if e.get("vars", {}).get("metadata", {}).get("id") == args.id:
            entry = e
            break

    if not entry:
        print(f"Article '{args.id}' not found in {args.file}")
        sys.exit(1)

    article_text = entry["vars"]["article_text"]
    search_text = args.text

    # Exact search
    positions = []
    start = 0
    while True:
        idx = article_text.find(search_text, start)
        if idx == -1:
            break
        positions.append(idx)
        if not args.all:
            break
        start = idx + 1

    # Case-insensitive fallback
    if not positions:
        start = 0
        lower_article = article_text.lower()
        lower_search = search_text.lower()
        while True:
            idx = lower_article.find(lower_search, start)
            if idx == -1:
                break
            positions.append(idx)
            if not args.all:
                break
            start = idx + 1
        if positions:
            print("(case-insensitive match)")

    if not positions:
        print(f"'{search_text}' not found in article {args.id}")
        sys.exit(1)

    for idx in positions:
        end = idx + len(search_text)
        actual = article_text[idx:end]
        # Show surrounding context
        ctx_start = max(0, idx - 30)
        ctx_end = min(len(article_text), end + 30)
        context = article_text[ctx_start:ctx_end].replace("\n", " ")
        marker_start = idx - ctx_start
        marker_end = marker_start + len(search_text)

        print(f"  text: {actual}")
        print(f"  type: <FILL IN>")
        print(f"  start: {idx}")
        print(f"  end: {end}")
        print(f"  context: ...{context}...")
        print(f"           {'':>{marker_start + 3}}{'^' * len(search_text)}")
        print()

    # Print ready-to-paste YAML
    print("YAML to paste:")
    print(f"    - text: {search_text}")
    print(f"      type: <FILL_IN>")
    print(f"      start: {positions[0]}")
    print(f"      end: {positions[0] + len(search_text)}")


if __name__ == "__main__":
    main()
