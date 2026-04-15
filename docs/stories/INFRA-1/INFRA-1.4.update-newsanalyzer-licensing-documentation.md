# Story INFRA-1.4: Update NewsAnalyzer Licensing and Documentation

## Status

Ready for Development

## Story

**As** Noometric LLC's founder,
**I want** the `news-analyzer` repo's license, copyright, and documentation updated to reflect its new status as a public Noometric showcase project,
**so that** the repo accurately represents who owns it, who built it, and what its relationship to Noometric is.

## Context

After INFRA-1.3, the proprietary IP is gone from `news-analyzer`. What remains is a clean, MIT-licensed evaluation showcase. This final story makes the documentation consistent with that reality:

- Copyright holder changes from "NewsAnalyzer Contributors" (vestige of the non-profit vision) to Noometric LLC
- README is updated to explain what NewsAnalyzer is and its relationship to Noometric
- Any remaining documentation that describes proprietary methodology or references the old `newsanalyzer-admin` GitHub org is cleaned up

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `LICENSE` copyright line updated from "NewsAnalyzer Contributors" to "Noometric LLC" |
| AC2 | `README.md` describes NewsAnalyzer as a Noometric LLC public project and links to noometric.com |
| AC3 | `README.md` clearly states what NewsAnalyzer demonstrates (evaluation techniques) and what it does NOT contain (Noometric's proprietary intelligence layer) |
| AC4 | No remaining references to `newsanalyzer-admin` GitHub org anywhere in the repo |
| AC5 | Any documentation files containing detailed proprietary methodology are removed or replaced with a high-level public-facing summary |
| AC6 | Changes committed and pushed to `noometric-llc/news-analyzer` |

## Tasks / Subtasks

- [ ] Task 1: Update LICENSE copyright (AC1)
  - [ ] Open `LICENSE`
  - [ ] Change: `Copyright (c) 2025 NewsAnalyzer Contributors`
  - [ ] To: `Copyright (c) 2026 Noometric LLC`

- [ ] Task 2: Audit for `newsanalyzer-admin` references (AC4)
  - [ ] Search the entire repo:
    ```bash
    grep -r "newsanalyzer-admin" . --include="*.md" --include="*.yaml" --include="*.yml" --include="*.json"
    ```
  - [ ] Update any found references to `noometric-llc`

- [ ] Task 3: Audit for proprietary methodology content (AC5)
  - [ ] Check if `docs/evaluation-methodology-bias.md` was removed as part of INFRA-1.3 (it should have been via `git rm`)
  - [ ] Search for any other docs that describe the specific Noometric evaluation methodology in detail:
    ```bash
    grep -rl "meta-bias\|ontology-grounded\|SHACL\|cognitive-bias.ttl" docs/ --include="*.md"
    ```
  - [ ] For each found file, decide: **remove entirely** or **replace with public summary**
    - If the document is purely internal methodology: remove it
    - If the document explains a technique publicly (acceptable): keep it with any Noometric-proprietary specifics removed
  - [ ] EVAL stories in `docs/stories/` can stay — they document how the eval was built, which is part of the showcase value

- [ ] Task 4: Update README.md (AC2, AC3)
  - [ ] Update the top of `README.md` to include:
    - What NewsAnalyzer is: a public showcase of AI evaluation techniques, built by Noometric LLC
    - Link to [noometric.com](https://noometric.com)
    - What it demonstrates: entity extraction evaluation, cognitive bias detection evaluation, evaluation harness design
    - What it does not include: Noometric's proprietary reasoning service and cognitive intelligence layer (available separately through Noometric LLC)
    - Local development note: full functionality requires the Noometric Intelligence reasoning service
  - [ ] Keep existing technical documentation (architecture, setup) — just add the Noometric context at the top

- [ ] Task 5: Commit and push (AC6)
  - [ ] Commit message:
    ```
    chore(INFRA-1.4): update licensing and documentation for Noometric LLC

    - Updated LICENSE copyright to Noometric LLC (2026)
    - Updated README to reflect NewsAnalyzer as a Noometric public showcase
    - Removed/replaced proprietary methodology documentation
    - Cleaned up newsanalyzer-admin references
    ```
  - [ ] Push to `noometric-llc/news-analyzer`

## Dev Notes

### README Top Section Suggested Content

```markdown
# NewsAnalyzer

A public AI evaluation showcase by [Noometric LLC](https://noometric.com).

NewsAnalyzer demonstrates evaluation techniques for AI-powered news analysis:
- **Entity extraction evaluation** — Promptfoo-based harness with precision/recall/F1 metrics
- **Cognitive bias detection evaluation** — Ontology-grounded neuro-symbolic evaluation
- **Evaluation harness design** — Patterns for evaluating LLM outputs in journalistic contexts

This repository contains the evaluation framework and application shell.
The Noometric Intelligence reasoning layer (cognitive bias ontology, proprietary
detection methodology) is maintained separately by Noometric LLC.

> **Full local setup** requires access to the Noometric Intelligence service.
> Contact [noometric.com](https://noometric.com) for access.
```

### What to Keep vs. Remove

| Document | Action | Reason |
|---|---|---|
| `docs/evaluation-methodology-bias.md` | Should already be removed (INFRA-1.3) | Proprietary |
| `docs/stories/EVAL-*/` | Keep | Documents technique, not proprietary IP |
| `docs/prd/` or `docs/prd.md` | Keep | Product requirements for the showcase |
| `docs/architecture/` | Keep | Application architecture (not proprietary) |
| Any doc referencing `cognitive-bias.ttl` internals | Remove or summarize | Ontology detail is proprietary |

### Year Note

The LICENSE currently says 2025. Update to 2026 since that's when Noometric LLC was established and this restructure is happening.

## Dev Agent Record

### Agent Model Used

_To be filled in after completion_

### File List

| File | Action | Description |
|------|--------|-------------|
| `LICENSE` | MODIFIED | Updated copyright to Noometric LLC 2026 |
| `README.md` | MODIFIED | Added Noometric context, updated description |
| `docs/evaluation-methodology-bias.md` | DELETED (if not already removed) | Proprietary methodology |

### Completion Notes

_To be filled in after completion_

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-04-14 | 1.0 | Initial story draft | Sarah (PO) |
