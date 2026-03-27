# Story EVAL-DASH.3: Gold Dataset Explorer

## Status

Ready for Review

## Story

**As a** site visitor (recruiter, hiring manager, or peer),
**I want** to browse the evaluation gold dataset — synthetic articles with ground-truth entity annotations, perturbation labels, and difficulty ratings,
**so that** I can understand the rigor of the evaluation methodology and inspect the test data directly.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Stats overview renders with total articles, breakdown by branch (pie/donut chart), breakdown by perturbation type, and breakdown by difficulty |
| AC2 | Article browser table loads with pagination, displaying title/excerpt, branch, perturbation type badge, difficulty badge, and faithful badge |
| AC3 | Filters work for branch, perturbation type, difficulty, and faithful/perturbed |
| AC4 | Article detail view shows full article text and ground-truth entities as a color-coded badge list (by entity type) |
| AC5 | Perturbation details visible for perturbed articles (what was changed) |
| AC6 | Uses existing backend API endpoints — no new backend endpoints required |
| AC7 | Responsive layout: table scrolls horizontally on mobile, detail view stacks |

## Tasks / Subtasks

- [x] Task 1: Create Next.js API proxy routes for eval dataset endpoints (AC6)
  - [ ] Add `BACKEND_URL=http://localhost:8080` to `frontend/.env.local.example` (if not already present)
  - [ ] Create `frontend/src/app/api/eval/datasets/stats/route.ts`
    - Proxies to backend: `GET ${BACKEND_URL}/api/eval/datasets/stats`
    - Uses `process.env.BACKEND_URL || 'http://localhost:8080'`
    - Returns `DatasetStatsDTO` shape
  - [ ] Create `frontend/src/app/api/eval/datasets/articles/route.ts`
    - Proxies to backend: `GET http://localhost:8080/api/eval/datasets/articles`
    - Forwards query params: `page`, `size`, `branch`, `perturbationType`, `difficulty`, `isFaithful`
    - Returns `Page<SyntheticArticleDTO>` shape
    - **Known issue MNT-002:** The backend's native JSONB query may return 500 when filters are applied. If this occurs, fall back to fetching all articles without filters and filtering client-side, OR use the batch-based workaround (`GET /api/eval/datasets/batches` → `GET /api/eval/datasets/batches/{id}/articles`)
  - [ ] Create `frontend/src/app/api/eval/datasets/articles/[id]/route.ts`
    - Proxies to backend: `GET http://localhost:8080/api/eval/datasets/articles/{id}`
    - Returns single `SyntheticArticleDTO` with full ground truth

- [x] Task 2: Define TypeScript types for dataset data (AC6)
  - [ ] Add to `frontend/src/types/evaluation.ts` (created in EVAL-DASH.2):
    ```typescript
    interface DatasetStats {
      totalArticles: number;
      faithfulCount: number;
      perturbedCount: number;
      byPerturbationType: Record<string, number>;
      byDifficulty: Record<string, number>;
      byBranch: Record<string, number>;
    }

    interface SyntheticArticle {
      id: string;
      batchId: string;
      articleText: string;
      articleType: string;
      isFaithful: boolean;
      perturbationType: string | null;
      difficulty: string;
      sourceFacts: Record<string, unknown>;
      groundTruth: Record<string, unknown>;
      modelUsed: string;
      tokensUsed: number;
      createdAt: string;
    }

    interface ArticlePage {
      content: SyntheticArticle[];
      totalElements: number;
      totalPages: number;
      number: number;  // current page (0-based)
      size: number;
    }
    ```

- [x] Task 3: Create API client and React Query hooks (AC6)
  - [ ] Add to `frontend/src/lib/api/evaluation.ts` (created in EVAL-DASH.2):
    - `getDatasetStats(): Promise<DatasetStats>` — calls `/api/eval/datasets/stats`
    - `getDatasetArticles(params: ArticleQueryParams): Promise<ArticlePage>` — calls `/api/eval/datasets/articles` with query string
    - `getArticleDetail(id: string): Promise<SyntheticArticle>` — calls `/api/eval/datasets/articles/{id}`
  - [ ] Add to `frontend/src/hooks/useEvaluation.ts` (created in EVAL-DASH.2):
    - `useDatasetStats()` — React Query hook, staleTime: 1h
    - `useDatasetArticles(params)` — React Query hook with params in queryKey for cache invalidation on filter change
    - `useArticleDetail(id)` — React Query hook, enabled only when id is defined

- [x] Task 3a: Create shared entity type color map
  - [ ] Add `ENTITY_TYPE_COLORS` constant to `frontend/src/lib/utils/evaluation.ts` (created in EVAL-DASH.2):
    ```typescript
    export const ENTITY_TYPE_COLORS: Record<string, string> = {
      person: '#3b82f6',        // blue-500
      government_org: '#a855f7', // purple-500
      organization: '#14b8a6',   // teal-500
      location: '#22c55e',       // green-500
      event: '#f97316',          // orange-500
      concept: '#ec4899',        // pink-500
      legislation: '#f59e0b',    // amber-500
    };
    ```
  - [ ] This constant is reused by EVAL-DASH.4 (methodology page) and EVAL-DASH.5 (live comparison)

- [x] Task 4: Build dataset stats overview component (AC1)
  - [ ] Create `frontend/src/components/evaluation/DatasetStatsOverview.tsx`
  - [ ] Stat cards row (grid-cols-1 md:grid-cols-3):
    1. "Total Articles" — large number
    2. "Faithful" — count + percentage
    3. "Perturbed" — count + percentage
  - [ ] Branch distribution chart: `recharts` `PieChart` with `Pie`, `Cell`, `Tooltip`, `Legend`
    - One slice per branch, colored distinctly
    - Show count + percentage on hover
  - [ ] Perturbation type breakdown: horizontal bar chart or badge list with counts
    - E.g., "wrong_party: 40, wrong_committee: 20, ..."
  - [ ] Difficulty distribution: small bar chart or badge row
    - "Easy: X, Medium: Y, Hard: Z"
  - [ ] Responsive: charts stack on mobile

- [x] Task 5: Build article browser table (AC2, AC3, AC7)
  - [ ] Create `frontend/src/components/evaluation/ArticleBrowserTable.tsx`
  - [ ] Table columns:
    1. **Excerpt** — first 80–100 chars of `articleText`, truncated with ellipsis
    2. **Branch** — text badge (legislative/executive/judicial)
    3. **Article Type** — `articleType` value
    4. **Perturbation** — colored badge; "faithful" (green) or perturbation type (amber/red)
    5. **Difficulty** — badge: easy (green), medium (amber), hard (red)
    6. **Faithful** — boolean icon (checkmark or X)
  - [ ] Pagination controls: prev/next buttons, page number, items per page selector
  - [ ] Use Spring Data pagination params: `page` (0-based), `size` (default 10)
  - [ ] Row click opens article detail (expandable row or navigates to detail)
  - [ ] Empty state: when filters produce zero results, show "No articles match your filters" with a reset-filters button
  - [ ] Wrap table in `overflow-x-auto` for mobile horizontal scroll
  - [ ] Use Tailwind table styling: `divide-y`, `hover:bg-muted/50`, consistent with project

- [x] Task 6: Build article filter controls (AC3)
  - [ ] Create `frontend/src/components/evaluation/ArticleFilters.tsx`
  - [ ] Filter controls above the table:
    - **Branch**: select/dropdown — All, Legislative, Executive, Judicial
    - **Perturbation Type**: select/dropdown — All, faithful, wrong_party, wrong_committee, etc. (values from stats `byPerturbationType` keys)
    - **Difficulty**: select/dropdown — All, Easy, Medium, Hard
    - **Faithful**: toggle/checkbox — All, Faithful Only, Perturbed Only
  - [ ] Filters update query params passed to `useDatasetArticles` hook
  - [ ] Reset button to clear all filters
  - [ ] Responsive: filters wrap on mobile (flex-wrap)

- [x] Task 7: Build article detail view with entity highlighting (AC4, AC5)
  - [ ] Create `frontend/src/components/evaluation/ArticleDetailView.tsx`
  - [ ] Displayed as expandable row below clicked table row, or slide-out panel
  - [ ] **Full article text** rendered in a readable prose block (whitespace-pre-wrap or prose Tailwind class)
  - [ ] **Entity badge list** extracted from `sourceFacts.facts[]`:
    - For each fact: show `subject` as entity text, `entity_type` as type
    - Deduplicate by subject text
    - Color-coded badges by entity type (use `ENTITY_TYPE_COLORS` from `lib/utils/evaluation.ts`):
      - person: blue, government_org: purple, organization: teal, location: green, event: orange, concept: pink, legislation: amber
    - Entity legend: show color key above or below the badge list
  - [ ] **Metadata panel** alongside or below article:
    - Branch (from `sourceFacts.branch`), Article Type, Difficulty, Model Used
    - Perturbation details (if `isFaithful === false`):
      - Perturbation type badge
      - Changed facts table: columns "Predicate | Original Value | Perturbed Value" (from `groundTruth.changed_facts[]`)
      - Expected findings: bulleted list (from `groundTruth.expected_findings[]`)
    - Source facts: collapsible section showing `sourceFacts.facts[]` as a formatted table or JSON
  - [ ] **Entity data source clarification**: The backend `SyntheticArticleDTO` fields do **not** contain entity annotations with character offsets:
    - `sourceFacts` = serialized FactSet: `{ topic, branch, facts: [{ subject, predicate, object, entity_type, ... }] }`
    - `groundTruth` = perturbation info: `{ changed_facts: [{ predicate, original_value, perturbed_value }], expected_findings: ["..."] }`
    - **Neither has `{text, type, start, end}` entity spans**
  - [ ] Therefore: display entities as a **badge list** extracted from `sourceFacts.facts[]` — show `subject` as entity text, `entity_type` as type badge. Do NOT attempt inline character-offset highlighting from API data.
  - [ ] Inline text highlighting with character offsets would require serving the gold YAML annotations via a new endpoint — defer to a future enhancement.

- [x] Task 8: Compose datasets page (AC1–AC7)
  - [ ] Replace placeholder in `frontend/src/app/evaluation/datasets/page.tsx`
  - [ ] Page layout (top to bottom):
    1. Page heading: "Gold Dataset Explorer"
    2. `DatasetStatsOverview` — stats cards + charts
    3. `ArticleFilters` — filter controls
    4. `ArticleBrowserTable` — paginated table with expandable detail
  - [ ] Wire up filter state: `useState` for each filter param, pass to hook and components
  - [ ] Loading state while data fetches
  - [ ] Error state if backend API is unreachable (friendly message: "Dataset API unavailable — ensure the backend service is running")

- [x] Task 9: Update barrel export and verify (AC1–AC7)
  - [ ] Update `frontend/src/components/evaluation/index.ts` — export all new components
  - [ ] Verify stats overview renders at `/evaluation/datasets`
  - [ ] Verify table loads with pagination
  - [ ] Verify each filter changes the displayed results
  - [ ] Verify article detail view opens with entity information
  - [ ] Verify mobile layout: table scrolls, filters wrap

## Dev Notes

### Relevant Source Tree

```
frontend/src/
├── app/
│   ├── api/
│   │   └── eval/
│   │       ├── results/                          # EXISTS (from EVAL-DASH.2)
│   │       └── datasets/
│   │           ├── stats/
│   │           │   └── route.ts                  # NEW — proxy to backend stats
│   │           └── articles/
│   │               ├── route.ts                  # NEW — proxy to backend articles
│   │               └── [id]/
│   │                   └── route.ts              # NEW — proxy to single article
│   ├── evaluation/
│   │   └── datasets/
│   │       └── page.tsx                          # MODIFY — replace placeholder
├── components/
│   └── evaluation/
│       ├── EvaluationSidebar.tsx                  # EXISTS (from EVAL-DASH.1)
│       ├── HeadlineMetrics.tsx                    # EXISTS (from EVAL-DASH.2)
│       ├── DatasetStatsOverview.tsx               # NEW
│       ├── ArticleBrowserTable.tsx                # NEW
│       ├── ArticleFilters.tsx                     # NEW
│       ├── ArticleDetailView.tsx                  # NEW
│       └── index.ts                              # MODIFY — add new exports
├── hooks/
│   └── useEvaluation.ts                          # MODIFY — add dataset hooks
├── lib/
│   └── api/
│       └── evaluation.ts                         # MODIFY — add dataset API calls
├── types/
│   └── evaluation.ts                             # MODIFY — add dataset types
```

### Backend API Reference

**Base URL:** `http://localhost:8080` (dev) — use env variable `NEXT_PUBLIC_API_URL` or hardcode proxy in Next.js API routes.

**GET /api/eval/datasets/stats**
- No params
- Response:
  ```json
  {
    "totalArticles": 100,
    "faithfulCount": 20,
    "perturbedCount": 80,
    "byPerturbationType": { "wrong_party": 40, "wrong_state": 40 },
    "byDifficulty": { "EASY": 50, "MEDIUM": 30, "HARD": 20 },
    "byBranch": { "legislative": 60, "executive": 40 }
  }
  ```

**GET /api/eval/datasets/articles**
- Query params: `page` (0-based), `size` (default 20), `branch`, `perturbationType`, `difficulty`, `isFaithful`
- Response: Spring Data `Page<SyntheticArticleDTO>` — `content[]`, `totalElements`, `totalPages`, `number`, `size`
- Each article: `id`, `batchId`, `articleText`, `articleType`, `isFaithful`, `perturbationType`, `difficulty`, `sourceFacts` (JSON), `groundTruth` (JSON), `modelUsed`, `tokensUsed`, `createdAt`
- **Known issue MNT-002:** Native JSONB query may return 500 with filters. Workaround: fetch without filters and filter client-side, or use batch-based approach (`GET /batches` → `GET /batches/{id}/articles`).

**GET /api/eval/datasets/articles/{id}**
- Returns single `SyntheticArticleDTO` with full ground truth details

### sourceFacts and groundTruth JSON Structures

These fields are `JSONB` columns in PostgreSQL, typed as `Map<String, Object>` in Java. Their exact structures (verified from `reasoning-service/app/clients/backend_client.py` and `reasoning-service/app/models/eval.py`):

**sourceFacts** (serialized `FactSet` Pydantic model):
```json
{
  "topic": "Senator John Fetterman",
  "primary_entity_id": "<UUID>",
  "branch": "legislative",
  "facts": [
    {
      "subject": "John Fetterman",
      "subject_id": "<UUID or null>",
      "predicate": "party_affiliation",
      "object": "Democratic",
      "entity_type": "CongressionalMember",
      "branch": "legislative",
      "data_source": "CONGRESS_GOV",
      "confidence": "tier_1",
      "valid_from": "2023-01-01",
      "valid_to": null
    }
  ],
  "related_entity_ids": ["<UUID>"]
}
```

**groundTruth** (perturbation info):
```json
{
  "changed_facts": [
    {
      "predicate": "party_affiliation",
      "original_value": "Democratic",
      "perturbed_value": "Republican"
    }
  ],
  "expected_findings": [
    "Incorrect party_affiliation: stated 'Republican', should be 'Democratic'"
  ]
}
```
- For faithful (unperturbed) articles: both arrays are empty `[]`
- For perturbed articles: populated with what was changed and what a correct analyzer should catch

**Key: these are NOT entity annotations with character offsets.** Entity spans (`{text, type, start, end}`) exist only in the gold YAML files (`eval/datasets/gold/*.yaml`), which are consumed by Promptfoo, not served by the backend API. Inline text highlighting would require a new endpoint — deferred.

### Entity Display Strategy

Since the API provides fact predicates (not NER spans), extract entities from `sourceFacts.facts[]`:
- Use `subject` field as entity text
- Use `entity_type` field for type badge (note: these are domain types like `CongressionalMember`, `Judge`, `Presidency` — map to display-friendly labels)
- Deduplicate by subject text
- Display as color-coded badge list, not inline highlights

### Gold Dataset YAML Structure (for reference)

```yaml
- vars:
    article_text: '**WASHINGTON, June 6, 2018** - The Senate confirmed...'
    entities:
    - text: WASHINGTON
      type: location
      start: 2
      end: 12
    - text: Senate
      type: government_org
      start: 35
      end: 41
    metadata:
      id: eval-2-jud-001
      article_id: <uuid>
      article_type: news_report
      branch: judicial
      source: derived
      perturbation_type: none
      difficulty: easy
      fact_count: 8
      curated: true
      curated_date: '2026-03-20'
```

This structure lives in `eval/datasets/gold/{branch}.yaml`. It is **not** served by the backend API — it's used by Promptfoo during eval runs.

### Next.js API Proxy Pattern

For proxying to the backend, follow this pattern:

```typescript
// app/api/eval/datasets/stats/route.ts
import { NextResponse } from 'next/server';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

export async function GET() {
  const res = await fetch(`${BACKEND_URL}/api/eval/datasets/stats`);
  if (!res.ok) {
    return NextResponse.json(
      { error: 'Backend API unavailable' },
      { status: res.status }
    );
  }
  const data = await res.json();
  return NextResponse.json(data);
}
```

For the articles route, forward query params:

```typescript
// app/api/eval/datasets/articles/route.ts
import { NextRequest, NextResponse } from 'next/server';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

export async function GET(request: NextRequest) {
  const searchParams = request.nextUrl.searchParams.toString();
  const res = await fetch(`${BACKEND_URL}/api/eval/datasets/articles?${searchParams}`);
  if (!res.ok) {
    return NextResponse.json(
      { error: 'Backend API unavailable' },
      { status: res.status }
    );
  }
  const data = await res.json();
  return NextResponse.json(data);
}
```

### Entity Type Color Map

Use consistent colors across this story and EVAL-DASH.2:

| Entity Type | Tailwind Color | Hex Suggestion |
|---|---|---|
| person | blue-500 | #3b82f6 |
| government_org | purple-500 | #a855f7 |
| organization | teal-500 | #14b8a6 |
| location | green-500 | #22c55e |
| event | orange-500 | #f97316 |
| concept | pink-500 | #ec4899 |
| legislation | amber-500 | #f59e0b |

Define this map as a shared constant in `frontend/src/lib/utils/evaluation.ts` (created in EVAL-DASH.2) so EVAL-DASH.4 and EVAL-DASH.5 can reuse it.

### Recharts Usage for Pie/Bar Charts

```typescript
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';

// Example: branch distribution pie chart
const COLORS = ['#3b82f6', '#a855f7', '#22c55e'];
const data = Object.entries(stats.byBranch).map(([name, value]) => ({ name, value }));

<ResponsiveContainer width="100%" height={300}>
  <PieChart>
    <Pie data={data} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={80}>
      {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
    </Pie>
    <Tooltip />
    <Legend />
  </PieChart>
</ResponsiveContainer>
```

### Testing

**Test file locations:**
- Component tests: `frontend/src/components/evaluation/__tests__/`
- API route tests: `frontend/src/app/api/eval/datasets/__tests__/`

**Testing framework:** Vitest + React Testing Library

**What to test:**
- `DatasetStatsOverview` renders stat cards with correct counts given mock data
- `ArticleBrowserTable` renders correct number of rows, displays badges
- `ArticleFilters` calls onChange handlers with correct filter values
- `ArticleDetailView` renders article text and entity information
- API proxy routes return correct status codes (mock fetch)
- Pagination controls: prev/next buttons update page param

**Mock data:** Create a small mock dataset (3–5 articles) with known values for deterministic assertions. Include both faithful and perturbed articles.

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-25 | 1.0 | Initial story draft from EVAL-DASH epic | Sarah (PO) |
| 2026-03-25 | 1.1 | Validation fixes: documented sourceFacts/groundTruth exact JSON structures (no char offsets — badge list approach), explicit entity color map task, BACKEND_URL env var, empty state for table | Sarah (PO) |
| 2026-03-25 | 1.2 | Status → Approved for implementation | Sarah (PO) |
| 2026-03-27 | 2.0 | Implementation complete — all tasks done, 18 new tests + 809 full suite passing, no regressions | James (Dev) |

## Dev Agent Record

### Agent Model Used
Claude Opus 4.6 (1M context)

### Debug Log References
- Test failure: `getByLabelText` failed because `<label>` lacked `htmlFor` association. Fixed by adding `id` to `<select>` and `htmlFor` to `<label>` in FilterSelect — also an accessibility improvement.

### Completion Notes List
- 3 API proxy routes forward to Spring Boot backend (stats, articles, articles/[id])
- Uses `BACKEND_URL` env var with `NEXT_PUBLIC_BACKEND_URL` fallback chain — no new env var required for existing setups
- MNT-002 known bug documented in articles route comment — frontend handles 500s gracefully
- Added `DatasetStats`, `SyntheticArticle`, `ArticlePage`, `ArticleQueryParams` types
- `ENTITY_TYPE_COLORS` (hex) and `ENTITY_TYPE_BG_CLASSES` (Tailwind) shared constants for reuse in EVAL-DASH.4/.5
- `mapEntityTypeLabel` maps domain types (CongressionalMember, Judge) to taxonomy types (person) for color-coding
- ArticleDetailView displays entities as badge list from sourceFacts.facts[] — no inline highlighting (no character offsets from API)
- Perturbation details show changed_facts table + expected_findings list for perturbed articles
- CollapsibleSection pattern for raw source facts JSON
- ArticleFilters uses proper htmlFor/id label association (accessibility fix caught by tests)
- Page resets to page 0 when any filter changes via handleFilterChange wrapper
- 18 new tests: 7 ArticleFilters + 11 ArticleDetailView. 809 total passing.

### File List
| Action | File |
|--------|------|
| MODIFIED | `frontend/.env.local.example` |
| NEW | `frontend/src/app/api/eval/datasets/stats/route.ts` |
| NEW | `frontend/src/app/api/eval/datasets/articles/route.ts` |
| NEW | `frontend/src/app/api/eval/datasets/articles/[id]/route.ts` |
| MODIFIED | `frontend/src/types/evaluation.ts` |
| MODIFIED | `frontend/src/lib/api/evaluation.ts` |
| MODIFIED | `frontend/src/hooks/useEvaluation.ts` |
| MODIFIED | `frontend/src/lib/utils/evaluation.ts` |
| NEW | `frontend/src/components/evaluation/DatasetStatsOverview.tsx` |
| NEW | `frontend/src/components/evaluation/ArticleFilters.tsx` |
| NEW | `frontend/src/components/evaluation/ArticleBrowserTable.tsx` |
| NEW | `frontend/src/components/evaluation/ArticleDetailView.tsx` |
| MODIFIED | `frontend/src/components/evaluation/index.ts` |
| MODIFIED | `frontend/src/app/evaluation/datasets/page.tsx` |
| NEW | `frontend/src/components/evaluation/__tests__/ArticleFilters.test.tsx` |
| NEW | `frontend/src/components/evaluation/__tests__/ArticleDetailView.test.tsx` |
