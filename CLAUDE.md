# NewsAnalyzer — Project Context for Claude Code

## Project Identity

**Type:** Public — MIT licensed, live on GitHub (`noometric-llc/news-analyzer`), deployed at newsanalyzer.org
**Owner:** Noometric LLC
**Purpose:** A public AI evaluation showcase demonstrating production-grade evaluation techniques (entity extraction, cognitive bias detection, full-stack observability). Serves as a demonstration vehicle for Noometric's consulting methodology.
**Depends on:** `noometric-intelligence` (private) via `REASONING_SERVICE_URL` API. The proprietary reasoning layer (ontology, detection methodology) is NOT in this repo and must not be added here.

## ⚠ IP Gate — Mandatory Before Every Push to GitHub

This is a **public repository**. Every push to the GitHub remote is a public disclosure.

Before pushing any new files, new algorithms, new evaluation methodology, or any implementation detail that could constitute IP:

1. Review what you are about to push against the Noometric IP Classification Matrix
2. Ask: does this contain novel methodology, scoring logic, or architecture that belongs to Noometric?
3. If uncertain, **do not push** — consult the `ip-strategist` agent in `D:\noometric` first
4. Run the `open-source-strategy` workflow if publishing a significant new component

**The ip-strategist's rule: publishing destroys patent rights. There is no recovery from premature disclosure.**

Routine pushes (bug fixes, dependency updates, documentation corrections, infrastructure changes with no novel methodology) do not require a full IP review but still require a quick sanity check: "Does anything in this diff reveal proprietary Noometric methodology?"

## Relationship to Noometric Business Project

This project is owned by Noometric LLC and referenced in the Noometric business project (`D:\noometric`) as both a demonstration vehicle and the live proof-of-concept system for the Behavioral Contract Services offering.

Noometric advisor agents relevant to decisions in this repo:

| Agent | When to Invoke |
|-------|----------------|
| `ip-strategist` | Before ANY push to GitHub that includes new methodology, algorithms, or evaluation techniques |
| `growth-marketer` | When changing public-facing framing, README positioning, or the evaluation showcase narrative |
| `business-attorney` | Before changing the MIT license, adding contributors, or entering into any partnership that involves this codebase |

## Development Process

This project uses **BMad** for story-driven development with sharded documentation.

- **Story location:** `docs/stories/`
- **Epic format:** Each epic has its own subdirectory containing the epic spec, story files, and architecture docs
- **Architecture docs:** `docs/architecture/` (sharded — coding standards, tech stack, source tree each have their own file)
- **Always-load files:** `docs/architecture/coding-standards.md`, `docs/architecture/tech-stack.md`, `docs/architecture/source-tree.md` — read these before writing any code
- **PRD:** `docs/prd.md`

Before starting any significant new work:
1. Read the always-load files above
2. Verify a story exists in `docs/stories/` for the work
3. If no story exists, create one following the BMad brownfield format before writing code
4. Update story status as work progresses

## Cross-Project Change Protocol

When engineering work in this repo is **requested by a Noometric business agent or product decision**:

1. The requesting context (`D:\noometric`) creates a story in this repo's `docs/stories/` following BMad format
2. The story includes a **Business Origin** section explaining what requested it and why (see `noometric-intelligence/docs/stories/EVAL-5/EVAL-5.epic-psychometric-validation.md` for the reference pattern)
3. The story must be self-contained — no file in `D:\noometric` should be required to understand or execute the work
4. The `D:\noometric` epic cross-references the story

**Agents in `D:\noometric` do not write production code directly in this repo.** They write story specifications.

## Boundary: What Lives Here vs. noometric-intelligence

| Belongs Here | Belongs in noometric-intelligence |
|---|---|
| Frontend (React), backend (Node/Postgres) | Reasoning service (FastAPI/Python) |
| CI/CD pipelines, Docker, infrastructure | Cognitive bias ontology, OWL reasoning |
| Promptfoo evaluation harness | Entity extraction methodology |
| Observability stack (OpenTelemetry, Grafana) | Psychometric evaluation scripts (DIF, IRR) |
| Public eval results and methodology summaries | Proprietary scoring logic and datasets |

If you find yourself writing code that belongs in the right column, stop and add it to `noometric-intelligence` instead.
