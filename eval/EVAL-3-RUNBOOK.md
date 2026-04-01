# EVAL-3 Evaluation Runbook

Steps to run the bias detection evaluation. All infrastructure is built — just execute these commands.

## Prerequisites

- Reasoning service running: `docker compose -f deploy/dev/docker-compose.yml up reasoning`
- `ANTHROPIC_API_KEY` set in environment
- Node.js + npx available (for Promptfoo)

## Step 0: Verify Service

```bash
curl http://localhost:8000/eval/bias/ontology/stats
# Should return: {"total_distortions": 14, "shacl_valid": true, ...}
```

## Step 1: Generate Full Synthetic Dataset (optional — seed dataset works for testing)

```bash
cd eval
python datasets/bias/scripts/generate_biased_articles.py --count 3
# Generates ~42 articles (3 per type × 14 types). Cost: ~$0.50
# Output: datasets/bias/synthetic_biased.yaml (overwrites seed)
```

## Step 2: Run Grounded Evaluation

```bash
cd eval
npx promptfoo eval -c promptfoo-bias.yaml
```

## Step 3: Run Ungrounded Evaluation (A/B comparison)

```bash
cd eval
npx promptfoo eval -c promptfoo-bias-ungrounded.yaml
```

## Step 4: Generate Summary Reports

```bash
# Find the Promptfoo output files (check reports/bias/ and reports/bias-ungrounded/)
python reports/bias/scripts/summarize_results.py \
  --input reports/bias/output.json \
  --output reports/bias/summary.json

# A/B comparison
python reports/bias/scripts/summarize_results.py \
  --input reports/bias/output.json \
  --compare reports/bias-ungrounded/output.json \
  --output reports/bias/comparison.json
```

## Step 5: Update Methodology Document

Replace `[PLACEHOLDER]` markers in `docs/evaluation-methodology-bias.md` with actual numbers from `summary.json`.

## Step 6: Update ROADMAP

In `docs/ROADMAP.md`, update EVAL-3 status to Complete with key metrics.

## Step 7: Commit Results

```bash
git add eval/reports/bias/summary.json eval/reports/bias/baseline_results.json
git add docs/evaluation-methodology-bias.md docs/ROADMAP.md
git commit -m "feat(eval): EVAL-3 baseline results — bias detection P/R/F1"
```
