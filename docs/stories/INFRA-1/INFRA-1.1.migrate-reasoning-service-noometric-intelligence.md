# Story INFRA-1.1: Migrate Reasoning Service to noometric-intelligence

## Status

Complete

## Story

**As** Noometric LLC's founder,
**I want** the reasoning service, ontology files, evaluation methodology, and bias datasets moved into the private `noometric-intelligence` repository,
**so that** Noometric's proprietary IP is no longer in the public MIT-licensed repo.

## Context

This story copies content into `noometric-intelligence`. It does **not** remove it from `news-analyzer` yet — that happens in INFRA-1.3 once the API boundary is established. The goal here is to get the IP safely into its new home and set up `noometric-intelligence` for ongoing development.

**What moves:**
- `reasoning-service/` — the entire Python FastAPI service and ontology files
- `docs/evaluation-methodology-bias.md` — proprietary methodology documentation
- `eval/datasets/bias/` — curated bias evaluation datasets

**What does NOT move in this story:**
- The evaluation harness code (`eval/` minus the bias datasets) — this stays in NewsAnalyzer as a public showcase of eval technique
- All backend, frontend, and application code — stays in NewsAnalyzer

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `noometric-intelligence` repo contains `reasoning-service/` with all app code, ontology files, and tests |
| AC2 | `noometric-intelligence` repo contains `docs/evaluation-methodology-bias.md` |
| AC3 | `noometric-intelligence` repo contains `eval/datasets/bias/` with all curated datasets |
| AC4 | Reasoning service runs correctly from `noometric-intelligence` (`docker build` succeeds, service starts, `/docs` endpoint responds) |
| AC5 | `.bmad-core/` is bootstrapped in `noometric-intelligence` for future story tracking |
| AC6 | `noometric-intelligence` has a `LICENSE` file set to All Rights Reserved (proprietary) |
| AC7 | `noometric-intelligence` has an updated `README.md` describing the service |
| AC8 | All content is committed and pushed to the remote `noometric-intelligence` repo |

## Tasks / Subtasks

- [ ] Task 1: Clone `noometric-intelligence` locally (AC1)
  - [X] Clone to a sibling directory: `D:/VSCProjects/noometric-intelligence/`
    ```bash
    git clone https://github.com/noometric-llc/noometric-intelligence.git D:/VSCProjects/noometric-intelligence
    ```

- [ ] Task 2: Copy reasoning service content (AC1)
  - [X] Copy `D:/VSCProjects/NewsAnalyzer_V2/reasoning-service/` → `D:/VSCProjects/noometric-intelligence/reasoning-service/`
  - [X] Exclude: `__pycache__/`, `.pytest_cache/`, any `.pyc` files (handle via .gitignore)
  - [X] Verify the following are present in the copy:
    - `reasoning-service/app/` (full FastAPI application)
    - `reasoning-service/ontology/cognitive-bias.ttl`
    - `reasoning-service/ontology/cognitive-bias-shapes.ttl`
    - `reasoning-service/ontology/newsanalyzer.ttl`
    - `reasoning-service/tests/`
    - `reasoning-service/Dockerfile`
    - `reasoning-service/requirements.txt`

- [ ] Task 3: Copy methodology and dataset content (AC2, AC3)
  - [X] Copy `D:/VSCProjects/NewsAnalyzer_V2/docs/evaluation-methodology-bias.md` → `D:/VSCProjects/noometric-intelligence/docs/evaluation-methodology-bias.md`
  - [X] Create `docs/` directory if it doesn't exist
  - [X] Copy `D:/VSCProjects/NewsAnalyzer_V2/eval/datasets/bias/` → `D:/VSCProjects/noometric-intelligence/eval/datasets/bias/`
  - [X] Create `eval/datasets/` directory structure as needed

- [ ] Task 4: Set up `.gitignore` (AC8)
  - [X] Create `D:/VSCProjects/noometric-intelligence/.gitignore` appropriate for Python/FastAPI:
    ```
    __pycache__/
    *.pyc
    *.pyo
    .pytest_cache/
    .env
    .env.local
    venv/
    .venv/
    *.egg-info/
    dist/
    build/
    ```

- [ ] Task 5: Add LICENSE file (AC6)
  - [X] Create `D:/VSCProjects/noometric-intelligence/LICENSE` with content:
    ```
    Copyright (c) 2026 Noometric LLC. All Rights Reserved.

    This software and associated documentation files are proprietary and confidential.
    Unauthorized copying, distribution, modification, or use of this software,
    via any medium, is strictly prohibited without express written permission
    from Noometric LLC.
    ```

- [X] Task 6: Update README.md (AC7)
  - [X] Replace the placeholder README with a proper description:
    - What this service is (Noometric Intelligence — reasoning and evaluation layer)
    - What it contains (reasoning service, cognitive bias ontology, evaluation datasets)
    - How to run it locally (Docker or direct Python)
    - Note that this is a private Noometric LLC repository
  - [X] Keep README accurate but do not include detailed methodology — that's in `docs/`

- [X] Task 7: Bootstrap BMAD (AC5)
  - [X] Copy `.bmad-core/` from `D:/VSCProjects/NewsAnalyzer_V2/` to `D:/VSCProjects/noometric-intelligence/`
  - [X] Update `.bmad-core/core-config.yaml` for the new repo context:
    - `devStoryLocation: docs/stories`
    - Remove references to NewsAnalyzer-specific paths
  - [X] Create `docs/stories/` directory (placeholder for future story tracking)
  - [X] Note: `.claude/` commands directory can be copied too if desired for IDE integration

- [X] Task 8: Verify reasoning service runs from new location (AC4)
  - [X] From `D:/VSCProjects/noometric-intelligence/reasoning-service/`:
    ```bash
    docker build -t noometric-intelligence-reasoning .
    docker run -p 8000:8000 -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY noometric-intelligence-reasoning
    ```
  - [X] Verify `http://localhost:8000/docs` loads the FastAPI Swagger UI
  - [X] Verify `GET /eval/bias/ontology/stats` returns ontology statistics

- [X] Task 9: Commit and push to noometric-intelligence (AC8)
  - [X] Stage all files: `git add .`
  - [x] Commit:
    ```
    feat(INFRA-1.1): migrate reasoning-service, ontology, and bias datasets from news-analyzer

    Establishes noometric-intelligence as the private home for Noometric LLC's
    proprietary AI evaluation intelligence layer. Includes:
    - Full FastAPI reasoning service with entity extraction, bias detection, and ontology querying
    - Cognitive bias OWL ontology and SHACL shapes (EVAL-3 output)
    - Evaluation methodology documentation
    - Curated bias evaluation datasets

    Source: noometric-llc/news-analyzer (migrated, not removed — removal in INFRA-1.3)
    ```
  - [X] Push: `git push origin main`

## Dev Notes

### What Stays in news-analyzer (For Now)

Do NOT remove anything from `news-analyzer` in this story. The application will break if reasoning-service is removed before the external API boundary is set up in INFRA-1.3. The ontology files and methodology docs can exist in both repos temporarily — this is acceptable.

### BMAD Core Config Changes

The `core-config.yaml` in the new repo should be simplified:

```yaml
devStoryLocation: docs/stories
prd:
  prdFile: docs/prd.md
  prdSharded: false
architecture:
  architectureFile: docs/architecture.md
  architectureSharded: false
devLoadAlwaysFiles:
  - reasoning-service/requirements.txt
devDebugLog: .ai/debug-log.md
slashPrefix: BMad
```

### Ontology Namespace Note

The cognitive-bias ontology uses `http://newsanalyzer.org/ontology/` as its base namespace. This namespace should be updated to `http://noometric.com/ontology/` in a future story (when the ontology is officially published or versioned). Do not change it in this migration story — changing namespaces in OWL ontologies is a breaking change that requires updating all references.

### Python Environment Note

The `requirements.txt` includes all dependencies. If running without Docker:
```bash
cd reasoning-service
python -m venv venv
source venv/bin/activate  # or venv\Scripts\activate on Windows
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## Dev Agent Record

### Agent Model Used

_To be filled in after completion_

### File List

| File | Action | Description |
|------|--------|-------------|
| `reasoning-service/` | COPIED | Full FastAPI reasoning service from news-analyzer |
| `docs/evaluation-methodology-bias.md` | COPIED | Bias evaluation methodology documentation |
| `eval/datasets/bias/` | COPIED | Curated bias evaluation datasets |
| `.gitignore` | NEW | Python/FastAPI gitignore |
| `LICENSE` | MODIFIED | Updated from placeholder to All Rights Reserved |
| `README.md` | MODIFIED | Updated with service description |
| `.bmad-core/` | NEW | BMAD framework bootstrapped for story tracking |
| `.bmad-core/core-config.yaml` | MODIFIED | Updated for noometric-intelligence context |

### Completion Notes

_To be filled in after completion_

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-04-14 | 1.0 | Initial story draft | Sarah (PO) |
| 2026-04-14 | 1.1 | Completed all steps | Steve Kosuth-Wood
