# Story EVAL-DASH.1: Evaluation Section Shell & Navigation

## Status

Ready for Review

## Story

**As a** site visitor (recruiter, hiring manager, or peer),
**I want** an "AI Evaluation" section accessible from the home page with its own sidebar navigation,
**so that** I can discover and explore the AI evaluation work that exists in this project.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `/evaluation` route renders with sidebar layout matching the existing `SidebarLayout` pattern |
| AC2 | Sidebar has 4 navigation items: Overview, Results, Dataset Explorer, Methodology |
| AC3 | Home page shows 3 primary CTAs: Knowledge Base, Article Analyzer, AI Evaluation |
| AC4 | `recharts` is installed as a project dependency and importable |
| AC5 | Mobile responsive: hamburger menu toggles sidebar overlay (same behavior as Article Analyzer) |
| AC6 | Landing page at `/evaluation` displays section overview with placeholder quick-stat cards |
| AC7 | Existing pages (Knowledge Base, Article Analyzer, Admin) remain unchanged and functional |

## Tasks / Subtasks

- [x] Task 1: Install `recharts` dependency (AC4)
  - [x] Run `pnpm add recharts` in the `frontend/` directory
  - [x] Verify import works: create a temporary test import or check `node_modules/recharts`
  - [x] No lockfile conflicts or peer dependency warnings

- [x] Task 2: Create Zustand sidebar store (AC1, AC5)
  - [x] Create `frontend/src/stores/evaluationSidebarStore.ts`
  - [x] Follow exact pattern from `articleAnalyzerSidebarStore.ts`:
    - Interface: `EvaluationSidebarState` with `isCollapsed`, `isMobileOpen`, actions: `toggle`, `collapse`, `expand`, `toggleMobile`, `closeMobile`
    - Persist middleware with key `'eval-sidebar-storage'`
    - Partialize: only persist `isCollapsed`

- [x] Task 3: Add evaluation menu config (AC2)
  - [x] Add `evaluationMenuItems` export to `frontend/src/lib/menu-config.ts`
  - [x] Import icons from `lucide-react`: suggest `BarChart3`, `Database`, `BookOpen`, `FlaskConical` (or similar)
  - [x] 4 menu items (5th added in EVAL-DASH.5 if stretch goal is implemented):
    1. Overview → `/evaluation` (icon: `FlaskConical` or `BarChart3`)
    2. Results → `/evaluation/results` (icon: `BarChart3`)
    3. Dataset Explorer → `/evaluation/datasets` (icon: `Database`)
    4. Methodology → `/evaluation/methodology` (icon: `BookOpen`)

- [x] Task 4: Create `EvaluationSidebar` component (AC1, AC2, AC5)
  - [x] Create `frontend/src/components/evaluation/EvaluationSidebar.tsx`
  - [x] Follow exact pattern from `ArticleAnalyzerSidebar.tsx`:
    - Use `BaseSidebar` from `@/components/sidebar`
    - Use `useEvaluationSidebarStore` for state
    - Use `evaluationMenuItems` from `@/lib/menu-config`
    - Header: Link to `/evaluation` with text "AI Evaluation"
    - Footer: Link back to home or Knowledge Base (follow AA pattern)
    - Pass `onNavigate={closeMobile}` to BaseSidebar
  - [x] Create `frontend/src/components/evaluation/index.ts` barrel export

- [x] Task 5: Create evaluation section layout (AC1, AC5)
  - [x] Create `frontend/src/app/evaluation/layout.tsx`
  - [x] Follow exact pattern from `article-analyzer/layout.tsx`:
    - `'use client'` directive
    - Import `SidebarLayout` from `@/components/layout`
    - Import `useEvaluationSidebarStore`
    - Import `EvaluationSidebar`
    - Suspense wrapper with skeleton fallback
    - Pass `sectionTitle="AI Evaluation"` to SidebarLayout
  - [x] Skeleton component: mobile header placeholder + content area with muted rounded divs

- [x] Task 6: Create evaluation landing page (AC6)
  - [x] Create `frontend/src/app/evaluation/page.tsx`
  - [x] Section heading: "AI Evaluation" with subheading explaining the section purpose
  - [x] 3 placeholder quick-stat cards in a responsive grid (`grid-cols-1 md:grid-cols-3`):
    1. "Articles Evaluated" — "113"
    2. "Extractors Compared" — "2" (spaCy vs Claude)
    3. "Claude F1 (Gov Domain)" — "0.61" (headline stat — Claude's best gov-domain F1, judicial branch)
  - [x] Brief description paragraph. Suggested copy: "This section showcases a systematic evaluation of entity extraction quality comparing Claude (LLM) against spaCy (statistical NLP) across U.S. government domain text. Explore the results, browse the gold dataset, and read the full methodology."
  - [x] Cards linking to the 3 sub-pages (Results, Datasets, Methodology) with short descriptions
  - [x] Use Tailwind styling consistent with existing pages

- [x] Task 7: Create placeholder route pages (AC2)
  - [x] Create `frontend/src/app/evaluation/results/page.tsx` — placeholder "Model Comparison Dashboard — Coming in EVAL-DASH.2"
  - [x] Create `frontend/src/app/evaluation/datasets/page.tsx` — placeholder "Dataset Explorer — Coming in EVAL-DASH.3"
  - [x] Create `frontend/src/app/evaluation/methodology/page.tsx` — placeholder "Evaluation Methodology — Coming in EVAL-DASH.4"
  - [x] Each placeholder page should have a heading and brief description of what will go there

- [x] Task 8: Update home page (AC3)
  - [x] Edit `frontend/src/app/page.tsx`
  - [x] Add "AI Evaluation" as a third primary CTA link alongside "Explore Knowledge Base" and "Article Analyzer"
  - [x] Link to `/evaluation`
  - [x] Same button styling as existing CTAs (`px-8 py-4 bg-primary text-primary-foreground rounded-lg ...`)
  - [x] Optionally add a quick link below (following the existing quick-links pattern)

- [x] Task 9: Verify no regressions (AC7)
  - [x] Navigate to `/knowledge-base` — renders correctly
  - [x] Navigate to `/article-analyzer` — renders correctly
  - [x] Navigate to `/evaluation` — renders with sidebar
  - [x] Click each sidebar item — navigates to correct placeholder page
  - [x] Test mobile viewport — hamburger menu works
  - [x] Home page shows all 3 CTAs

## Dev Notes

### Relevant Source Tree

```
frontend/src/
├── app/
│   ├── page.tsx                          # Home page — ADD 3rd CTA here
│   ├── article-analyzer/                 # REFERENCE PATTERN for evaluation/
│   │   ├── layout.tsx                    # SidebarLayout + Suspense pattern
│   │   ├── page.tsx                      # Landing page
│   │   ├── articles/page.tsx
│   │   └── entities/page.tsx
│   └── evaluation/                       # NEW — create this route group
│       ├── layout.tsx                    # NEW
│       ├── page.tsx                      # NEW — landing page
│       ├── results/page.tsx             # NEW — placeholder
│       ├── datasets/page.tsx            # NEW — placeholder
│       └── methodology/page.tsx         # NEW — placeholder
├── components/
│   ├── layout/
│   │   └── SidebarLayout.tsx             # REUSE — shared layout component
│   ├── sidebar/
│   │   ├── BaseSidebar.tsx               # REUSE — base sidebar component
│   │   ├── SidebarMenuItem.tsx
│   │   └── types.ts                      # MenuItemData, BaseSidebarProps interfaces
│   ├── article-analyzer/
│   │   ├── ArticleAnalyzerSidebar.tsx    # REFERENCE PATTERN
│   │   └── index.ts
│   └── evaluation/                       # NEW — create this component dir
│       ├── EvaluationSidebar.tsx         # NEW
│       └── index.ts                      # NEW — barrel export
├── stores/
│   ├── articleAnalyzerSidebarStore.ts    # REFERENCE PATTERN
│   └── evaluationSidebarStore.ts        # NEW
└── lib/
    └── menu-config.ts                    # MODIFY — add evaluationMenuItems
```

### Key Patterns to Follow

**SidebarLayout** ([SidebarLayout.tsx](frontend/src/components/layout/SidebarLayout.tsx)):
- Accepts `sidebar` (ReactNode), `sectionTitle` (string), `store` (SidebarStore interface)
- SidebarStore interface requires: `isCollapsed`, `isMobileOpen`, `toggleMobile()`, `closeMobile()`
- Handles mobile header, backdrop, overlay, desktop fixed sidebar, and main content margin

**Zustand Store Pattern** ([articleAnalyzerSidebarStore.ts](frontend/src/stores/articleAnalyzerSidebarStore.ts)):
- Uses `create<State>()(persist(...))` with Zustand persist middleware
- Unique storage key per section (e.g., `'aa-sidebar-storage'` → use `'eval-sidebar-storage'`)
- `partialize` only persists `isCollapsed` (not `isMobileOpen`)

**Sidebar Component Pattern** ([ArticleAnalyzerSidebar.tsx](frontend/src/components/article-analyzer/ArticleAnalyzerSidebar.tsx)):
- Wraps `BaseSidebar` with section-specific config
- Gets state from its Zustand store
- Gets menu items from `menu-config.ts`
- Header is a `<Link>` to section root with section title
- Footer is a `<Link>` to another section (e.g., Knowledge Base)
- Passes `onNavigate={closeMobile}` for mobile sidebar auto-close

**Menu Config Pattern** ([menu-config.ts](frontend/src/lib/menu-config.ts)):
- Export named array of `MenuItemData[]`
- Each item: `{ label, icon, href, disabled? }`
- Icons imported from `lucide-react`

**Layout Pattern** ([article-analyzer/layout.tsx](frontend/src/app/article-analyzer/layout.tsx)):
- `'use client'` directive required (uses hooks)
- Wraps content in `<Suspense fallback={<Skeleton />}>`
- Inner component uses the store and passes to `SidebarLayout`
- Pattern: `SidebarLayout` → sidebar prop + sectionTitle + store + children

**Home Page** ([page.tsx](frontend/src/app/page.tsx)):
- Primary CTAs are `<Link>` elements in a flex-wrap container with gap-4
- Same styling class string for all primary buttons
- Quick links section below with smaller, muted styling

### Data for Quick-Stat Cards

From `eval/reports/baseline/summary.json`:
- Total articles evaluated: 113 (15 judicial + 20 executive + 53 legislative + 25 CoNLL)
- Extractors compared: 2 (spaCy en_core_web_sm, Claude Sonnet)
- Headline stat: Claude Sonnet F1 = 0.614 on judicial branch (best gov-domain score)
- Context: spaCy leads on general-domain CoNLL text (F1 = 0.905) but Claude dominates on government text where domain context matters

For the landing page, use hardcoded values for now. EVAL-DASH.2 will load these dynamically.

**Note for EVAL-DASH.5:** If the stretch goal (Live Extraction Comparison) is implemented, a 5th sidebar item (`Compare → /evaluation/compare`) will need to be added to `evaluationMenuItems` in `menu-config.ts`.

### New Dependency

- **recharts**: React charting library, tree-shakeable. Install with `pnpm add recharts` in `frontend/`.
- No other new dependencies required for this story.

### Testing

**Test file locations:**
- Component tests: `frontend/src/components/evaluation/__tests__/`
- Store tests: `frontend/src/stores/__tests__/` (if tests exist for other stores)
- Page tests: `frontend/src/app/evaluation/__tests__/` (optional for placeholders)

**Testing framework:** Vitest + React Testing Library (see [tech-stack.md](docs/architecture/tech-stack.md))

**What to test:**
- `EvaluationSidebar` renders 4 menu items
- `evaluationSidebarStore` toggle/collapse/expand actions work
- `/evaluation` layout renders with sidebar
- Home page renders 3 CTA buttons (regression: still shows KB and AA)

**Existing test patterns to follow:**
- Check `frontend/src/components/article-analyzer/__tests__/` and `frontend/src/components/sidebar/__tests__/` for reference patterns

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-25 | 1.0 | Initial story draft from EVAL-DASH epic | Sarah (PO) |
| 2026-03-25 | 1.1 | Validation fixes: clarified F1 headline stat, added EVAL-DASH.5 dependency note, added landing page copy | Sarah (PO) |
| 2026-03-25 | 1.2 | Status → Approved for implementation | Sarah (PO) |
| 2026-03-25 | 2.0 | Implementation complete — all tasks done, 14 new tests + 768 full suite passing, no regressions | James (Dev) |

## Dev Agent Record

### Agent Model Used
Claude Opus 4.6 (1M context)

### Debug Log References
No debug issues encountered.

### Completion Notes List
- recharts 3.8.1 was already installed (pre-existing dependency) — verified importable
- EvaluationSidebar follows exact ArticleAnalyzerSidebar pattern with 4 menu items
- Layout uses SidebarLayout + Suspense with tailored skeleton matching landing page shape
- Landing page has hardcoded stats (113 articles, 2 extractors, 0.61 F1) — EVAL-DASH.2 will make dynamic
- Home page updated with 3rd CTA + quick link to evaluation results
- Updated existing page.test.tsx validPrefixes allowlist to include `/evaluation`
- 14 new EvaluationSidebar tests pass, 768 total tests pass, 0 regressions
- No new lint warnings introduced

### File List
| Action | File |
|--------|------|
| NEW | `frontend/src/stores/evaluationSidebarStore.ts` |
| MODIFIED | `frontend/src/lib/menu-config.ts` |
| NEW | `frontend/src/components/evaluation/EvaluationSidebar.tsx` |
| NEW | `frontend/src/components/evaluation/index.ts` |
| NEW | `frontend/src/app/evaluation/layout.tsx` |
| NEW | `frontend/src/app/evaluation/page.tsx` |
| NEW | `frontend/src/app/evaluation/results/page.tsx` |
| NEW | `frontend/src/app/evaluation/datasets/page.tsx` |
| NEW | `frontend/src/app/evaluation/methodology/page.tsx` |
| MODIFIED | `frontend/src/app/page.tsx` |
| NEW | `frontend/src/components/evaluation/__tests__/EvaluationSidebar.test.tsx` |
| MODIFIED | `frontend/src/app/__tests__/page.test.tsx` |
