# Epic EVAL-DASH: AI Evaluation Portfolio Dashboard

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | EVAL-DASH |
| **Epic Name** | AI Evaluation Portfolio Dashboard |
| **Track** | AI Evaluation / User Experience |
| **Epic Type** | New Feature — Portfolio Showcase |
| **Priority** | HIGHEST (Job Search — surfaces invisible EVAL work) |
| **Status** | PROPOSED |
| **Created** | 2026-03-25 |
| **Owner** | Sarah (PO) |
| **Depends On** | EVAL-1 Complete, EVAL-2 Complete |
| **Blocked By** | None |
| **Estimated Effort** | 1–1.5 weeks (5 stories, 4 required + 1 stretch) |

## Executive Summary

Build a public-facing "AI Evaluation" section in the frontend that surfaces the work completed in EVAL-1 and EVAL-2. Currently, all evaluation work (gold datasets, Promptfoo harness, precision/recall/F1 metrics, model comparison) exists entirely in the backend and `eval/` directory — **invisible to anyone visiting the site.**

A recruiter or hiring manager visiting the deployed application today sees a government civics reference tool. After this epic, they see an AI evaluation engineer's portfolio piece with quantitative results front and center.

### Problem Statement

> "I get the impression from other people who have looked at the UI that they are not quite sure what they are looking at."

The current UI was designed when the project priorities were different (factbase, civics education). The AI evaluation work — which is the highest-portfolio-value component — has no frontend presence. This epic fixes that gap with surgical precision: one new section, no existing UI restructuring.

## Business Value

1. **Surfaces hidden portfolio value** — EVAL-1 + EVAL-2 represent weeks of work that is currently invisible to site visitors
2. **Tells the evaluation story visually** — "Claude F1=0.60 vs spaCy F1=0.31" is more compelling as a chart than a bullet point on a resume
3. **Demonstrates full-stack capability** — Data pipeline → evaluation framework → visualization (not just backend scripts)
4. **Provides a shareable URL** — One link to send recruiters: `https://[domain]/evaluation`
5. **Low risk, high ROI** — No changes to existing pages, just additive

## Existing Assets to Surface

| Asset | Location | Currently Visible? |
|-------|----------|--------------------|
| Baseline evaluation results (P/R/F1 by branch, by extractor) | `eval/reports/baseline/summary.json` | No |
| Per-branch detailed results (legislative, executive, judicial, CoNLL) | `eval/reports/baseline/*_results.json` | No |
| Gold dataset (64 curated + 25 CoNLL articles with annotations) | `eval/datasets/gold/*.yaml` | No |
| Synthetic articles with ground truth | Backend API: `GET /api/eval/datasets/articles` | No (API exists, no UI) |
| Dataset statistics (counts by type/difficulty/branch) | Backend API: `GET /api/eval/datasets/stats` | No (API exists, no UI) |
| LLM entity extraction endpoint | Reasoning: `POST /eval/extract/llm` | No |
| spaCy entity extraction endpoint | Reasoning: `POST /entities/extract` | No (used by Article Analyzer but not comparatively) |
| Evaluation methodology write-up | `docs/evaluation-methodology.md` | No (file in repo) |

## Architecture Decisions

### Frontend-Only Where Possible

The evaluation results (JSON) and gold datasets (YAML) are static files produced by Promptfoo runs. Rather than building new backend endpoints to serve these, we'll:
- **Option A (recommended):** Add a lightweight API route in Next.js (`/api/eval/results`) that reads the JSON files from a known path or bundled at build time
- **Option B:** Create a new reasoning-service endpoint that serves evaluation results

Option A keeps the scope small and avoids backend changes. The data changes infrequently (only on new eval runs).

### New Dependency: Recharts

Install `recharts` for bar/radar charts comparing extractors. This is the most common React charting library, works well with Tailwind/shadcn, and is lightweight. No other charting library exists in the project currently.

### Navigation Integration

Add "AI Evaluation" as a **top-level section** alongside "Knowledge Base" and "Article Analyzer" on the home page. It gets its own sidebar (following the existing `SidebarLayout` pattern) with 4 pages.

## Stories

---

### EVAL-DASH.1: Evaluation Section Shell & Navigation

**Goal:** Create the routing structure, sidebar, and layout for the new AI Evaluation section.

**Scope:**
- New route group: `/evaluation` with layout using `SidebarLayout`
- New sidebar: `EvaluationSidebar` following `PublicSidebar`/`ArticleAnalyzerSidebar` pattern
- Add to `menu-config.ts`: Evaluation section with 4 items (Overview, Dataset Explorer, Live Comparison, Methodology)
- Update home page (`/`): Add "AI Evaluation" as a third feature card alongside Knowledge Base and Article Analyzer
- Install `recharts` dependency
- Landing page at `/evaluation` with section overview and quick-stat cards (total articles evaluated, extractors compared, F1 scores)
- Zustand store for sidebar state (follow existing pattern)

**Acceptance Criteria:**
- [ ] `/evaluation` route renders with sidebar layout
- [ ] Sidebar has 4 navigation items
- [ ] Home page shows 3 sections (KB, Article Analyzer, AI Evaluation)
- [ ] `recharts` installed and importable
- [ ] Mobile responsive (hamburger sidebar)

**Effort:** ~1 day

---

### EVAL-DASH.2: Model Comparison Dashboard

**Goal:** The headline page — a visual comparison of spaCy vs Claude entity extraction with P/R/F1 metrics.

**Scope:**
- Route: `/evaluation/results` (default view after landing)
- Data source: `eval/reports/baseline/summary.json` (loaded via Next.js API route or bundled)
- **Headline metrics cards:** Overall F1 for each extractor (large numbers, colored)
- **Bar chart:** Side-by-side P/R/F1 by government branch (legislative, executive, judicial, CoNLL)
  - Grouped bars: spaCy (one color) vs Claude (another color)
  - Three metric groups: Precision, Recall, F1
- **Per-entity-type breakdown table:** TP/FP/FN counts for each entity type (person, government_org, organization, location, etc.) per extractor
- **Key findings cards:** 2-3 insight callouts (e.g., "Claude excels at government_org entities where domain context matters", "spaCy outperforms on CoNLL general-domain text")
- **Cost/quality card:** Claude API cost per article vs. quality improvement over spaCy
- Filter/tab by branch: All / Legislative / Executive / Judicial / CoNLL

**Acceptance Criteria:**
- [ ] Headline F1 cards render for both extractors
- [ ] Bar chart shows P/R/F1 grouped by branch with extractor comparison
- [ ] Per-entity-type table shows TP/FP/FN breakdown
- [ ] Branch filter/tabs work
- [ ] Responsive layout (charts stack on mobile)
- [ ] Data loads from summary.json

**Effort:** ~2 days

---

### EVAL-DASH.3: Gold Dataset Explorer

**Goal:** Let visitors browse the evaluation dataset — synthetic articles with ground-truth annotations and perturbation labels.

**Scope:**
- Route: `/evaluation/datasets`
- Data source: Backend API `GET /api/eval/datasets/articles` + `GET /api/eval/datasets/stats`
- **Stats overview:** Total articles, breakdown by branch (pie/donut chart), breakdown by perturbation type, breakdown by difficulty
- **Article browser table:** Searchable, filterable table of test articles
  - Columns: Title/excerpt, Branch, Article Type, Perturbation Type (badge), Difficulty (badge), Faithful? (boolean badge)
  - Filters: branch, perturbation type, difficulty, faithful/perturbed
- **Article detail view** (expandable row or slide-out panel):
  - Full article text
  - Ground-truth entities highlighted inline (color-coded by entity type)
  - Source facts listed
  - Perturbation details (what was changed and why)
- **Dataset quality section:** Show curated vs. auto-derived counts, entity count distribution

**Acceptance Criteria:**
- [ ] Stats cards render with dataset overview
- [ ] Article table loads with pagination
- [ ] Filters work (branch, perturbation type, difficulty)
- [ ] Article detail shows full text with entity annotations
- [ ] Perturbation details visible for perturbed articles
- [ ] Uses existing backend API (no new endpoints)

**Effort:** ~2 days

---

### EVAL-DASH.4: Evaluation Methodology Page

**Goal:** Render the evaluation methodology as a polished, portfolio-ready page — the thing you link on your resume.

**Scope:**
- Route: `/evaluation/methodology`
- Content source: Adapted from `docs/evaluation-methodology.md` (not a raw markdown render — structured as designed page sections)
- **Sections:**
  1. **Overview** — What we evaluated, why, and the key finding (Claude 2x spaCy F1 on gov domain)
  2. **Entity Taxonomy** — Visual display of 7 entity types with icons and descriptions
  3. **Gold Dataset Construction** — Pipeline diagram: KB facts → synthetic articles → automated derivation → human curation
  4. **Evaluation Metrics** — P/R/F1 explained with visual formulas
  5. **Fuzzy Matching Strategy** — The 6-priority matching approach with examples
  6. **Results Summary** — Key charts (can reuse components from EVAL-DASH.2)
  7. **Limitations & Future Work** — What would improve with more time/data
  8. **Tools Used** — Promptfoo, spaCy, Claude API, Python, Pydantic (skill tags/badges)
- Design: Clean, readable, minimal — think technical blog post, not documentation dump
- Subtle callouts for key metrics and insights

**Acceptance Criteria:**
- [ ] All 8 sections render with structured content
- [ ] Entity taxonomy displayed visually (not just a list)
- [ ] Pipeline diagram communicates the evaluation flow
- [ ] Key metrics are visually prominent
- [ ] Tools/skills section shows relevant technologies
- [ ] Page is linkable and looks professional when shared
- [ ] Mobile responsive

**Effort:** ~2 days

---

### EVAL-DASH.5: Live Extraction Comparison (STRETCH)

**Goal:** Let visitors paste text and see both extractors run side-by-side in real time. The "try it yourself" moment.

**Scope:**
- Route: `/evaluation/compare`
- **Input area:** Text input (textarea) with sample article buttons (pre-loaded from gold dataset)
- **Side-by-side results:** Two columns showing entities extracted by each extractor
  - Entity type badges, confidence scores, text highlights
  - Visual diff: entities found by one but not the other highlighted differently
- **Live metrics:** P/R/F1 computed client-side if gold annotation exists for the sample
- Calls: `POST /entities/extract` (spaCy) and `POST /eval/extract/llm` (Claude) simultaneously
- Loading states for both (Claude will be slower)
- **Cost notice:** Small disclaimer that LLM extraction uses Claude API credits
- **Rate limiting note:** Consider disabling for public deployment or adding a daily limit

**Why stretch:** This is the most impressive demo feature but also the most complex. It requires both services running, costs Claude API credits per use, and needs careful UX for the async comparison. Worth doing if time permits — skip if not.

**Acceptance Criteria:**
- [ ] Text input with pre-loaded sample options
- [ ] Both extractors called and results displayed side-by-side
- [ ] Entity type badges and confidence scores shown
- [ ] Visual indication of entities found by one extractor but not the other
- [ ] Loading states for async extraction
- [ ] Works on mobile (stacked columns)

**Effort:** ~1.5 days

---

## Sizing Summary

| Story | Description | Effort | Required? |
|-------|-------------|--------|-----------|
| EVAL-DASH.1 | Section shell, routing, navigation | ~1 day | Yes |
| EVAL-DASH.2 | Model comparison dashboard | ~2 days | Yes |
| EVAL-DASH.3 | Gold dataset explorer | ~2 days | Yes |
| EVAL-DASH.4 | Methodology page | ~2 days | Yes |
| EVAL-DASH.5 | Live extraction comparison | ~1.5 days | Stretch |
| **Total (required)** | | **~7 days** | |
| **Total (with stretch)** | | **~8.5 days** | |

## Compatibility Requirements

- [ ] Existing pages (Knowledge Base, Article Analyzer, Admin) remain unchanged
- [ ] No database schema changes
- [ ] No backend code changes (uses existing APIs + static eval result files)
- [ ] No reasoning-service changes (uses existing endpoints)
- [ ] Single new dependency: `recharts`

## Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Eval result JSON format changes on re-run | Low | Low | Pin to known summary.json structure; version the format |
| Backend eval API not deployed to prod | Medium | Medium | Verify EVAL-1.3 endpoints are in prod deployment; test before starting EVAL-DASH.3 |
| Recharts bundle size | Low | Low | Tree-shakeable; only import needed chart types |
| Live comparison costs (EVAL-DASH.5) | Medium | Low | Stretch goal; can ship without it; add rate limit if included |

## Definition of Done

- [ ] `/evaluation` section accessible from home page
- [ ] Model comparison dashboard shows P/R/F1 with visual charts
- [ ] Dataset explorer allows browsing synthetic articles with ground truth
- [ ] Methodology page is polished and portfolio-ready (shareable URL)
- [ ] All pages mobile responsive
- [ ] No regressions to existing functionality
- [ ] Deployed to production
