# Story EVAL-DASH.4: Evaluation Methodology Page

## Status

Ready for Review

## Story

**As a** site visitor (recruiter, hiring manager, or peer),
**I want** a polished, portfolio-ready page explaining the evaluation methodology — entity taxonomy, gold dataset construction, evaluation metrics, fuzzy matching, results, and tools used,
**so that** I can understand the rigor behind the evaluation results and assess the project author's AI evaluation skills.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | All 8 content sections render with structured, designed content (not raw markdown) |
| AC2 | Entity taxonomy displayed visually with color-coded type cards (not just a table) |
| AC3 | Dataset construction pipeline communicated as a visual flow/diagram |
| AC4 | Key metrics (P/R/F1) are visually prominent with the formulas displayed |
| AC5 | Fuzzy matching strategy displayed with priority levels and examples |
| AC6 | Tools/skills section shows relevant technologies as badges |
| AC7 | Page is linkable (`/evaluation/methodology`) and looks professional when shared |
| AC8 | Mobile responsive |

## Tasks / Subtasks

- [x] Task 1: Design section data constants (AC1)
  - [ ] Create `frontend/src/lib/data/methodology.ts`
  - [ ] Define structured data constants for each section (content separated from presentation):
    ```typescript
    // Entity taxonomy entries
    export const ENTITY_TAXONOMY = [
      { type: 'person', description: 'Named individuals', examples: ['Elizabeth Warren', 'John Roberts'], icon: 'User' },
      { type: 'government_org', description: 'Government bodies, agencies, courts', examples: ['Senate', 'EPA', 'Supreme Court'], icon: 'Building' },
      { type: 'organization', description: 'Non-government organizations', examples: ['Georgetown University', 'AP'], icon: 'Building2' },
      { type: 'location', description: 'Geographic entities', examples: ['Washington', 'Pennsylvania'], icon: 'MapPin' },
      { type: 'event', description: 'Named events', examples: ['Civil War', 'inauguration'], icon: 'Calendar' },
      { type: 'concept', description: 'Political groups, ideologies', examples: ['Republican', 'Democratic'], icon: 'Lightbulb' },
      { type: 'legislation', description: 'Laws, bills, executive orders', examples: ['Affordable Care Act'], icon: 'FileText' },
    ];

    // Fuzzy matching priorities
    export const FUZZY_MATCHING_PRIORITIES = [
      { priority: 1, rule: 'Exact text + type match', credit: '1.0 TP', example: { extracted: 'Senate', gold: 'Senate', type: 'both government_org' } },
      { priority: 2, rule: 'Exact text + type mismatch', credit: '0.5 TP', example: { extracted: 'EPA (organization)', gold: 'EPA (government_org)', type: 'type differs' } },
      { priority: 3, rule: 'Substring containment + type match', credit: '1.0 TP', example: { extracted: 'Banking Committee', gold: 'Senate Banking Committee', type: 'substring' } },
      { priority: 4, rule: 'Substring containment + type mismatch', credit: '0.5 TP', example: { extracted: 'Banking Committee (org)', gold: 'Senate Banking Committee (gov_org)', type: 'substring + type differs' } },
      { priority: 5, rule: 'Levenshtein ≥ 0.8 + type match', credit: '1.0 TP', example: { extracted: 'John Fettermann', gold: 'John Fetterman', type: 'fuzzy match' } },
      { priority: 6, rule: 'Levenshtein ≥ 0.8 + type mismatch', credit: '0.5 TP', example: { extracted: 'John Fettermann (person)', gold: 'John Fetterman (concept)', type: 'fuzzy + type differs' } },
    ];

    // Dataset statistics
    export const DATASET_STATS = {
      branches: [
        { name: 'Legislative', articles: 53, curated: 14, entities: 308 },
        { name: 'Executive', articles: 20, curated: 15, entities: 125 },
        { name: 'Judicial', articles: 15, curated: 10, entities: 81 },
        { name: 'CoNLL-2003', articles: 25, curated: 25, entities: 87 },
      ],
      totals: { articles: 113, curated: 64, entities: 601 },
      autoEnriched: { entities: 190, articles: 88 },
    };

    // Tools used
    export const TOOLS_USED = [
      { name: 'Promptfoo', category: 'Evaluation', description: 'LLM evaluation framework' },
      { name: 'spaCy', category: 'NLP', description: 'Statistical NER pipeline' },
      { name: 'Claude API', category: 'AI', description: 'LLM entity extraction' },
      { name: 'Python', category: 'Language', description: 'Evaluation scripts & providers' },
      { name: 'Pydantic', category: 'Validation', description: 'Data model validation' },
      { name: 'TypeScript', category: 'Language', description: 'Frontend dashboard' },
      { name: 'Next.js', category: 'Framework', description: 'React framework' },
      { name: 'Recharts', category: 'Visualization', description: 'Charts & graphs' },
      { name: 'GitHub Actions', category: 'CI/CD', description: 'Automated evaluation runs' },
    ];
    ```

- [x] Task 2: Build entity taxonomy section component (AC2)
  - [ ] Create `frontend/src/components/evaluation/methodology/EntityTaxonomy.tsx`
  - [ ] Display 7 entity types as visual cards in a responsive grid (`grid-cols-2 md:grid-cols-3 lg:grid-cols-4`)
  - [ ] Each card:
    - Color accent from `ENTITY_TYPE_COLORS` (left border or background tint)
    - Lucide icon (mapped from `icon` field in data)
    - Type name as heading
    - Description text
    - Example entities as small badges
  - [ ] This is the visual centerpiece — should look polished, not like a data dump

- [x] Task 3: Build dataset construction pipeline component (AC3)
  - [ ] Create `frontend/src/components/evaluation/methodology/DatasetPipeline.tsx`
  - [ ] Visual flow diagram using styled divs (not an image):
    - 4 pipeline stages connected by arrows (`→` or CSS arrow connectors):
      1. **Knowledge Base Facts** — "Subject/predicate/object tuples from KB"
      2. **Synthetic Article Generation** — "LLM generates realistic news articles from facts (EVAL-1)"
      3. **Automated Derivation** — "Script maps predicates → entity annotations with character offsets"
      4. **Human Curation** — "64 articles manually reviewed, corrected, enriched"
    - Each stage: icon, title, brief description, count badge (e.g., "601 entities", "113 articles")
  - [ ] Below the pipeline: auto-enrichment callout ("190 entities auto-added across 88 articles")
  - [ ] Dataset statistics table: branch × articles/curated/entities (from `DATASET_STATS`)
  - [ ] Responsive: stages stack vertically on mobile, horizontal on desktop

- [x] Task 4: Build evaluation metrics section component (AC4)
  - [ ] Create `frontend/src/components/evaluation/methodology/EvalMetrics.tsx`
  - [ ] Display P/R/F1 formulas visually:
    - Use styled divs to render fraction notation (numerator/denominator with horizontal rule)
    - Precision = TP / (TP + FP)
    - Recall = TP / (TP + FN)
    - F1 = 2 × P × R / (P + R)
  - [ ] Brief explanation of each metric: "Of entities extracted, what fraction are correct?" etc.
  - [ ] Visual callout: "TP = True Positive (correct), FP = False Positive (hallucinated), FN = False Negative (missed)"
  - [ ] Styled to feel like a textbook explanation, not a code dump

- [x] Task 5: Build fuzzy matching strategy section component (AC5)
  - [ ] Create `frontend/src/components/evaluation/methodology/FuzzyMatching.tsx`
  - [ ] Introductory paragraph: why fuzzy matching matters (strict match unfairly penalizes reasonable boundary differences)
  - [ ] 3 motivating examples table (from methodology doc):
    - "Banking Committee" vs "Senate Banking Committee" — strict: FP+FN, fuzzy: TP
    - "John Fettermann" vs "John Fetterman" — strict: FP+FN, fuzzy: TP
    - "EPA" (organization) vs "EPA" (government_org) — strict: FP+FN, fuzzy: 0.5 TP
  - [ ] Priority table: 6 rows from `FUZZY_MATCHING_PRIORITIES`, styled with priority badges and example text
  - [ ] Visual emphasis: priority 1–2 (exact), 3–4 (substring), 5–6 (Levenshtein) as three groups

- [x] Task 6: Build results summary section (AC1)
  - [ ] Create `frontend/src/components/evaluation/methodology/ResultsSummary.tsx`
  - [ ] Reuse `BranchComparisonChart` from EVAL-DASH.2: import the component and pass static summary data with branch filter fixed to `'all'`. If the interactive component doesn't support a static/read-only mode cleanly, create a simplified `StaticBranchChart` that takes hardcoded data as props and renders without filter controls.
  - [ ] Aggregate results table from methodology doc (Section 4):
    - 4 rows (Legislative, Executive, Judicial, CoNLL) × 2 extractors
    - Columns: Dataset, Extractor, Precision, Recall, F1
    - Bold the winning F1 per dataset row
  - [ ] Per-entity-type results table from methodology doc (Section 4.2):
    - 6 entity type rows (person, government_org, location, concept, organization, event)
    - Columns: Entity Type | spaCy P | spaCy R | spaCy F1 | Claude P | Claude R | Claude F1
    - Data from legislative branch (largest dataset): person Claude F1=0.72 vs spaCy 0.33, government_org Claude F1=0.65 vs spaCy 0.31, etc.
    - This is compelling data — shows where Claude's domain understanding matters most
  - [ ] Key findings: 3 performance patterns as callout cards:
    1. spaCy's weakness is precision (high recall, massive FPs)
    2. Claude's advantage is disciplined extraction (4× fewer FPs)
    3. spaCy excels on CoNLL (trained domain)
  - [ ] Cost/quality table: spaCy $0.00 vs Claude ~$0.004/article, F1 improvement +94%
  - [ ] Can reference existing components — don't rebuild charts from scratch

- [x] Task 7: Build limitations & future work section (AC1)
  - [ ] Create `frontend/src/components/evaluation/methodology/LimitationsFutureWork.tsx`
  - [ ] Limitations as an honest, numbered list (4 items from methodology doc):
    1. Gold dataset size (113 articles, 64 curated)
    2. Synthetic articles may not match real-world complexity
    3. Single annotator — no inter-annotator agreement
    4. Entity type ambiguity (organization vs government_org boundary)
  - [ ] Future work as forward-looking bullet points (5 items):
    1. EVAL-3: Cognitive Bias Evaluation
    2. Larger gold dataset with multiple annotators
    3. Additional extractors (GPT-4, Gemini, larger spaCy)
    4. Active learning for annotation prioritization
    5. Prompt engineering for precision improvement
  - [ ] Tone: professionally honest — shows awareness of limitations without undermining the work

- [x] Task 8: Build tools/skills badges section (AC6)
  - [ ] Create `frontend/src/components/evaluation/methodology/ToolsBadges.tsx`
  - [ ] Display tools from `TOOLS_USED` as styled badge/pill components
  - [ ] Group by category: Evaluation, NLP, AI, Language, Framework, Visualization, CI/CD
  - [ ] Each badge: tool name, subtle category label or color coding
  - [ ] Layout: flex-wrap with gap, visually similar to skill tags on a resume
  - [ ] This section answers "what technologies were used" at a glance

- [x] Task 9: Build overview section (AC1)
  - [ ] Create `frontend/src/components/evaluation/methodology/Overview.tsx`
  - [ ] Opening section: 2–3 paragraphs summarizing:
    - What was evaluated (entity extraction, spaCy vs Claude)
    - Why (measuring NER quality on government domain text)
    - Key finding: Claude achieves ~2× spaCy's F1 on government articles, while spaCy leads on general-domain text
  - [ ] Prominent stat callout: "Claude F1: 0.60 | spaCy F1: 0.31 | Government Domain"
  - [ ] Tone: confident, technical, portfolio-ready — this is the first thing someone reads

- [x] Task 10: Compose methodology page (AC1–AC8)
  - [ ] Replace placeholder in `frontend/src/app/evaluation/methodology/page.tsx`
  - [ ] Page layout (top to bottom, clean spacing between sections):
    1. `Overview` — intro + key finding callout
    2. `EntityTaxonomy` — 7-type visual cards
    3. `DatasetPipeline` — pipeline diagram + dataset stats
    4. `EvalMetrics` — P/R/F1 with visual formulas
    5. `FuzzyMatching` — motivating examples + 6-priority table
    6. `ResultsSummary` — charts/table + key findings + cost
    7. `LimitationsFutureWork` — honest assessment
    8. `ToolsBadges` — technology skill tags
  - [ ] Section dividers: subtle horizontal rules or spacing
  - [ ] Page should feel like a technical blog post — clean, readable, minimal
  - [ ] No sidebar clutter in the content area — let the content breathe
  - [ ] Add Next.js `metadata` export for Open Graph / social sharing:
    ```typescript
    export const metadata = {
      title: 'Entity Extraction Evaluation Methodology | NewsAnalyzer',
      description: 'Systematic evaluation comparing Claude vs spaCy entity extraction on U.S. government domain text. Claude achieves 2× F1 improvement.',
      openGraph: {
        title: 'Entity Extraction Evaluation Methodology',
        description: 'Claude vs spaCy NER evaluation with precision/recall/F1 metrics on 113 government news articles.',
      },
    };
    ```
    Note: `metadata` export requires the page to be a Server Component. If the page uses client hooks, extract metadata to a `layout.tsx` for this route or use `generateMetadata`.

- [x] Task 11: Update barrel export and verify (AC1–AC8)
  - [ ] Create `frontend/src/components/evaluation/methodology/index.ts` barrel export for all section components
  - [ ] Update `frontend/src/components/evaluation/index.ts` — re-export methodology components
  - [ ] Verify all 8 sections render at `/evaluation/methodology`
  - [ ] Verify page looks professional when opened directly via URL
  - [ ] Verify mobile responsive layout
  - [ ] Verify entity taxonomy cards show correct colors from `ENTITY_TYPE_COLORS`

## Dev Notes

### Relevant Source Tree

```
frontend/src/
├── app/
│   └── evaluation/
│       └── methodology/
│           └── page.tsx                                  # MODIFY — replace placeholder
├── components/
│   └── evaluation/
│       ├── methodology/                                  # NEW — subdirectory for section components
│       │   ├── Overview.tsx                              # NEW
│       │   ├── EntityTaxonomy.tsx                        # NEW
│       │   ├── DatasetPipeline.tsx                       # NEW
│       │   ├── EvalMetrics.tsx                           # NEW
│       │   ├── FuzzyMatching.tsx                         # NEW
│       │   ├── ResultsSummary.tsx                        # NEW
│       │   ├── LimitationsFutureWork.tsx                 # NEW
│       │   ├── ToolsBadges.tsx                           # NEW
│       │   └── index.ts                                  # NEW — barrel export
│       └── index.ts                                      # MODIFY — add methodology re-export
├── lib/
│   ├── data/                                             # NEW — create this directory
│   │   └── methodology.ts                                # NEW — content data constants
│   └── utils/
│       └── evaluation.ts                                 # EXISTS (from EVAL-DASH.2/3) — ENTITY_TYPE_COLORS
```

### Content Source

All content is adapted from [docs/evaluation-methodology.md](docs/evaluation-methodology.md). This document has 7 sections:

1. Introduction (→ Overview component)
2. Gold Dataset Construction (→ DatasetPipeline component)
3. Evaluation Design (→ EvalMetrics + FuzzyMatching components)
4. Results (→ ResultsSummary component)
5. Analysis (→ merged into ResultsSummary key findings)
6. Tooling (→ ToolsBadges component)
7. Future Work (→ LimitationsFutureWork component)

The entity taxonomy (in Section 1) gets its own prominent component. The methodology doc's "Limitations" (Section 5.3) and "Future Work" (Section 7) are combined into one component.

**Content is hardcoded, not fetched from an API.** This is static editorial content that changes rarely. Separating data constants into `lib/data/methodology.ts` keeps the components focused on presentation.

### Design Guidance

**Target aesthetic:** Technical blog post / portfolio case study. Think Stripe's engineering blog or a well-formatted research paper summary. Key principles:

- **Clean whitespace** — generous padding between sections (`py-12` or `py-16`)
- **Typography hierarchy** — clear section headings (`text-2xl font-bold`), subheadings, body text
- **Visual variety** — mix of cards, tables, diagrams, callouts, badges (not just paragraphs)
- **Accent colors** — use entity type colors for the taxonomy, muted callout backgrounds for key stats
- **Minimal decoration** — no gratuitous icons or borders; let content breathe

### Reusable Components from Prior Stories

- **`ENTITY_TYPE_COLORS`** from `lib/utils/evaluation.ts` (EVAL-DASH.3) — use for taxonomy card accents
- **`BranchComparisonChart`** from EVAL-DASH.2 — can import for results summary section (or create a simplified static version)
- **`KeyFindings`** from EVAL-DASH.2 — reuse the insight card pattern

### Pipeline Diagram Implementation

Build with CSS, not an image. Suggested approach:

```tsx
// Horizontal on desktop, vertical on mobile
<div className="flex flex-col md:flex-row items-center gap-4">
  <StageCard icon={Database} title="KB Facts" count="~3,400 facts" />
  <Arrow />
  <StageCard icon={FileText} title="Article Generation" count="113 articles" />
  <Arrow />
  <StageCard icon={Cpu} title="Automated Derivation" count="601 entities" />
  <Arrow />
  <StageCard icon={UserCheck} title="Human Curation" count="64 reviewed" />
</div>
```

Use `→` character or a small SVG arrow between stages. On mobile, stages stack vertically with `↓` arrows.

### Formula Display

Render P/R/F1 formulas using styled divs (no LaTeX dependency needed):

```tsx
<div className="flex items-center gap-2">
  <span className="font-semibold">Precision =</span>
  <div className="inline-flex flex-col items-center">
    <span className="border-b border-foreground px-2">TP</span>
    <span className="px-2">TP + FP</span>
  </div>
</div>
```

### Key Numbers for Hardcoded Content

From `docs/evaluation-methodology.md` and `eval/reports/baseline/summary.json`:

| Stat | Value | Source |
|------|-------|--------|
| Total articles | 113 | methodology doc §2 |
| Curated articles | 64 | methodology doc §2 |
| Total entities | 601 | methodology doc §2 |
| Auto-enriched entities | 190 across 88 articles | methodology doc §2 |
| Claude avg F1 (gov) | ~0.60 | summary.json (avg of 0.593, 0.603, 0.614) |
| spaCy avg F1 (gov) | ~0.31 | summary.json (avg of 0.261, 0.359, 0.318) |
| spaCy F1 (CoNLL) | 0.905 | summary.json |
| Claude F1 (CoNLL) | 0.867 | summary.json |
| Claude cost/article | ~$0.004 | methodology doc §5 |
| F1 improvement | +94% | methodology doc §5 |

### Testing

**Test file locations:**
- Component tests: `frontend/src/components/evaluation/methodology/__tests__/`

**Testing framework:** Vitest + React Testing Library

**What to test:**
- `EntityTaxonomy` renders 7 entity type cards
- `DatasetPipeline` renders 4 pipeline stages
- `FuzzyMatching` renders 6 priority rows
- `ToolsBadges` renders all tools from data constant
- `ResultsSummary` renders aggregate results table
- Methodology page renders all 8 sections in correct order
- Data constants in `methodology.ts` have expected number of entries

**Testing note:** These are primarily content/presentation components with no data fetching. Tests should verify structure (correct number of cards/rows/sections), not pixel-perfect rendering.

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-25 | 1.0 | Initial story draft from EVAL-DASH epic | Sarah (PO) |
| 2026-03-25 | 1.1 | Validation fixes: clarified chart reuse strategy, added per-entity-type results table, OG meta tags for social sharing, annotated new lib/data/ directory | Sarah (PO) |
| 2026-03-25 | 1.2 | Status → Approved for implementation | Sarah (PO) |
| 2026-03-27 | 2.0 | Implementation complete — all tasks done, 25 new tests + 834 full suite passing, no regressions | James (Dev) |

## Dev Agent Record

### Agent Model Used
Claude Opus 4.6 (1M context)

### Debug Log References
- React key warning: `ResultsSummary` used bare `<>` Fragment for paired table rows. Fixed by using `<Fragment key={row.dataset}>`.
- Test failures: CSS `uppercase` class doesn't change DOM text (used lowercase in assertions), `getByText` ambiguity for short strings like `'TP'` (used regex patterns instead).

### Completion Notes List
- All content in `lib/data/methodology.ts` — 10 data constants with `as const` for type narrowing
- Added `AGGREGATE_RESULTS`, `PER_ENTITY_TYPE_RESULTS`, `COST_COMPARISON` beyond original Task 1 spec — keeps all data in one file
- Page is a Server Component (no `'use client'`) — enables Next.js `metadata` export for OG tags
- EntityTaxonomy uses `ENTITY_TYPE_COLORS` from shared utils for consistent color-coding
- Pipeline diagram uses `ArrowRight`/`ArrowDown` with responsive `hidden md:block` / `md:hidden`
- Formula display uses CSS border-bottom fraction pattern — no LaTeX dependency
- FuzzyMatching table has 3-tier background color grouping (exact/substring/Levenshtein)
- ResultsSummary includes aggregate table, per-entity-type table, 3 key findings, and cost/quality comparison — all from hardcoded constants
- `max-w-5xl` on page container for optimal reading width
- `space-y-16` between sections for generous breathing room (portfolio aesthetic)
- 25 new tests: 8 data constant validations + 17 component structure tests. 834 total passing.

### File List
| Action | File |
|--------|------|
| NEW | `frontend/src/lib/data/methodology.ts` |
| NEW | `frontend/src/components/evaluation/methodology/Overview.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/EntityTaxonomy.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/DatasetPipeline.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/EvalMetrics.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/FuzzyMatching.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/ResultsSummary.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/LimitationsFutureWork.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/ToolsBadges.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/index.ts` |
| MODIFIED | `frontend/src/components/evaluation/index.ts` |
| MODIFIED | `frontend/src/app/evaluation/methodology/page.tsx` |
| NEW | `frontend/src/components/evaluation/methodology/__tests__/MethodologySections.test.tsx` |
