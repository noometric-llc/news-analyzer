# NewsAnalyzer Entity Extraction Evaluation Harness

Promptfoo-based evaluation harness that measures entity extraction quality (precision/recall/F1) by comparing two extractors — spaCy NER and Claude LLM — against a curated gold dataset.

## Prerequisites

- **Node.js** 18+ (for Promptfoo CLI)
- **Python** 3.11+ (for providers and scorer)
- **reasoning-service** running on `localhost:8000`
- **ANTHROPIC_API_KEY** set in reasoning-service `.env` (for Claude extractor)

## Quick Start

```bash
# Install Promptfoo
cd eval/
npm install

# Set Python path (point to your newsanalyzer conda env)
export PROMPTFOO_PYTHON=/path/to/miniconda3/envs/newsanalyzer/python.exe

# Run evaluation (default: legislative dataset)
npm run eval

# View results dashboard
npm run eval:view
```

## Available Commands

| Command | Description |
|---------|-------------|
| `npm run eval` | Run default evaluation (legislative dataset) |
| `npm run eval:legislative` | Run legislative branch only |
| `npm run eval:executive` | Run executive branch only |
| `npm run eval:judicial` | Run judicial branch only |
| `npm run eval:all` | Run all gold datasets |
| `npm run eval:view` | Open Promptfoo results dashboard |

## Project Structure

```
eval/
├── promptfooconfig.yaml        # Promptfoo orchestration config
├── package.json                # npm scripts and Promptfoo dependency
├── datasets/
│   ├── gold/                   # Gold-standard entity annotations
│   │   ├── legislative.yaml    # 53 articles (14 curated)
│   │   ├── executive.yaml      # 20 articles (15 curated)
│   │   ├── judicial.yaml       # 15 articles (10 curated)
│   │   └── conll_sample.yaml   # 25 CoNLL-2003 sentences (external validation)
│   └── scripts/                # Dataset derivation and validation tools
│       ├── derive_gold.py      # Automated gold derivation from EVAL-1 articles
│       ├── validate_gold.py    # Offset/type/overlap validation
│       ├── auto_enrich_gold.py # Auto-add dateline locations and gov orgs
│       └── find_offset.py      # Helper for manual curation
├── providers/
│   ├── spacy_provider.py       # Calls POST /entities/extract
│   └── llm_provider.py         # Calls POST /eval/extract/llm
├── assertions/
│   ├── entity_scorer.py        # Fuzzy-matching P/R/F1 scorer
│   └── test_entity_scorer.py   # 35 pytest tests for scorer
└── reports/
    └── baseline/               # Committed baseline results for CI comparison
```

## How It Works

1. **Promptfoo** reads test cases from gold dataset YAML files
2. Each article's text is sent to both **providers** (spaCy + Claude)
3. Provider responses are passed to the **entity scorer** assertion
4. The scorer compares extracted entities against gold annotations using **fuzzy matching**
5. **derivedMetrics** compute aggregate Precision, Recall, and F1

## Fuzzy Matching Strategy

The scorer uses a priority-based matching system:

| Priority | Rule | Credit |
|----------|------|--------|
| 1 | Exact text + type match | 1.0 (full TP) |
| 2 | Exact text + type mismatch | 0.5 (partial TP) |
| 3 | Substring containment + type match | 1.0 (full TP) |
| 4 | Substring containment + type mismatch | 0.5 (partial TP) |
| 5 | Levenshtein similarity >= 0.8 + type match | 1.0 (full TP) |
| 6 | Levenshtein similarity >= 0.8 + type mismatch | 0.5 (partial TP) |
| — | No match | FP (extracted) or FN (gold) |

## Adding Test Cases

Add new articles to the appropriate `datasets/gold/*.yaml` file:

```yaml
- vars:
    article_text: "Your article text here..."
    entities:
      - text: "Entity Name"
        type: "person"     # person|government_org|organization|location|event|concept|legislation
        start: 0           # character offset (0-based)
        end: 11            # character offset (exclusive)
    metadata:
      id: "eval-2-leg-999"
      curated: true
      curated_date: "2026-03-25"
```

Run `python eval/datasets/scripts/validate_gold.py` to check offsets and types.

## Adding Providers

Create a new Python file in `eval/providers/`:

```python
def call_api(prompt, options, context):
    # prompt = article text
    # Return: {"output": {"entities": [...], "total_count": N}}
    pass
```

Add it to `promptfooconfig.yaml` under `providers`.

## Understanding Results

- **Precision**: Of entities extracted, what fraction are correct?
- **Recall**: Of gold entities, what fraction were found?
- **F1**: Harmonic mean of precision and recall
- **Pass threshold**: F1 >= 0.5

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `PROMPTFOO_PYTHON` | Path to Python interpreter with eval dependencies | Yes |
| `ANTHROPIC_API_KEY` | Anthropic API key (set in reasoning-service `.env`) | For Claude provider |
| `EVAL_DRY_RUN` | Set `true` to skip Claude API calls | For CI |
