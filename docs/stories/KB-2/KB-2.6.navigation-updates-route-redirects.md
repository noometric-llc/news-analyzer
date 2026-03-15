# Story KB-2.6: Navigation Updates & Route Redirects

## Status

Ready for Review

## Story

**As a** user,
**I want** navigation to reflect the new unified administrations pages,
**So that** I can easily find the content.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | KB sidebar updated: "Presidential Administrations" replaces "President" and "Vice President" |
| AC2 | Admin sidebar updated: Same consolidation |
| AC3 | Old KB routes redirect to new page (`/president` → `/administrations`, `/vice-president` → `/administrations`) |
| AC4 | Old Admin routes redirect similarly |
| AC5 | Sidebar icons updated appropriately |
| AC6 | Menu configuration files updated |
| AC7 | Tests verify redirects work correctly |

## Tasks / Subtasks

- [x] Task 1: Update menu-config.ts for KB sidebar (AC1, AC5, AC6)
  - [x] Open `frontend/src/lib/menu-config.ts`
  - [x] In the KB sidebar section under Executive, replace "President" and "Vice President" entries with single "Presidential Administrations" entry
  - [x] Set route to `/knowledge-base/government/executive/administrations`
  - [x] Choose appropriate Lucide icon (e.g., `Landmark` or `Building`)
  - [x] Preserve correct menu ordering
- [x] Task 2: Update menu-config.ts for Admin sidebar (AC2, AC5, AC6)
  - [x] In the Admin sidebar section under Executive, replace "President" and "Vice President" entries with single "Presidential Administrations" entry
  - [x] Set route to `/admin/knowledge-base/government/executive/administrations`
  - [x] Use matching icon
- [x] Task 3: Add KB route redirects (AC3)
  - [x] Modify `frontend/src/app/knowledge-base/government/executive/president/page.tsx` to redirect to `/knowledge-base/government/executive/administrations`
  - [x] Modify `frontend/src/app/knowledge-base/government/executive/vice-president/page.tsx` to redirect to `/knowledge-base/government/executive/administrations`
  - [x] Use Next.js `redirect()` from `next/navigation` (server-side redirect)
- [x] Task 4: Add Admin route redirects (AC4)
  - [x] Modify `frontend/src/app/admin/knowledge-base/government/executive/president/page.tsx` to redirect to `/admin/knowledge-base/government/executive/administrations`
  - [x] Modify `frontend/src/app/admin/knowledge-base/government/executive/vice-president/page.tsx` to redirect to `/admin/knowledge-base/government/executive/administrations`
  - [x] Use Next.js `redirect()` from `next/navigation`
- [x] Task 5: Write tests (AC7)
  - [x] Test: KB sidebar renders "Presidential Administrations" link
  - [x] Test: KB sidebar does NOT render separate "President" or "Vice President" links
  - [x] Test: Admin sidebar renders consolidated link
  - [x] Test: Old president route redirects to administrations page
  - [x] Test: Old vice-president route redirects to administrations page

## Dev Notes

### Key File

**`frontend/src/lib/menu-config.ts`** — This is the **single unified file** that defines both the KB sidebar and Admin sidebar menus. There are no separate `kbSidebarConfig.ts` or `adminSidebarConfig.ts` files (this was confirmed during architect review).

### Redirect Pattern

Next.js App Router supports server-side redirects using `redirect()` from `next/navigation`:

```tsx
// In old page.tsx files
import { redirect } from 'next/navigation';

export default function PresidentPage() {
  redirect('/knowledge-base/government/executive/administrations');
}
```

This issues a 307 redirect by default. The old page components can be stripped down to just the redirect — no imports of the original components needed.

### Dependencies

- **Depends on KB-2.1** (new administrations route must exist before redirects point to it)
- **Depends on KB-2.4** (admin administrations route must exist for admin redirects)
- Should be done **after** KB-2.2 and KB-2.3 so the destination page has content

### Files to Modify

| File | Change |
|------|--------|
| `frontend/src/lib/menu-config.ts` | Replace President + VP entries with Presidential Administrations (both KB and Admin sections) |
| `frontend/src/app/knowledge-base/government/executive/president/page.tsx` | Replace content with redirect |
| `frontend/src/app/knowledge-base/government/executive/vice-president/page.tsx` | Replace content with redirect |
| `frontend/src/app/admin/knowledge-base/government/executive/president/page.tsx` | Replace content with redirect |
| `frontend/src/app/admin/knowledge-base/government/executive/vice-president/page.tsx` | Replace content with redirect |

### Testing

**Framework:** Vitest + React Testing Library
**Run Command:** `npx vitest run` from `frontend/` directory

**Testing Standards:**
- Use `describe`/`it` blocks
- For sidebar tests, render the sidebar component and assert link text/hrefs
- For redirect tests, mock `next/navigation` `redirect` function and verify it's called with correct path
- Existing sidebar tests (if any) may need updating to reflect new menu structure

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-14 | 1.0 | Initial story creation from KB-2 epic | Sarah (PO) |
| 2026-03-15 | 1.1 | Implementation complete — all 5 tasks done, 788 tests passing | James (Dev Agent) |

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Old subsections.test.tsx had President/VP render tests that broke after redirect conversion — removed those tests (redirect behavior covered by new redirects.test.ts files)
- Full regression: 48 files, 788 tests passing

### Completion Notes List

- KB sidebar: Replaced "President of the United States" + "Vice President of the United States" with single "Presidential Administrations" entry using `Landmark` icon
- Admin sidebar: Same consolidation in AdminSidebar.tsx (note: admin sidebar uses inline menu array, not menu-config.ts)
- All 4 old routes (KB + Admin, president + vice-president) replaced with `redirect()` from `next/navigation`
- Removed unused `Crown` and `UserCircle` imports from both menu files
- Updated existing menu-config.test.ts to expect 5 Executive Branch sub-sections (was 6)
- Added explicit "does NOT have separate President or Vice President entries" test
- Updated existing subsections.test.tsx to remove President/VP render tests (now redirects)

### File List

**Modified Files:**
- `frontend/src/lib/menu-config.ts` — Replaced President + VP entries with Presidential Administrations
- `frontend/src/components/admin/AdminSidebar.tsx` — Same consolidation for admin sidebar
- `frontend/src/app/knowledge-base/government/executive/president/page.tsx` — Replaced with redirect
- `frontend/src/app/knowledge-base/government/executive/vice-president/page.tsx` — Replaced with redirect
- `frontend/src/app/admin/knowledge-base/government/executive/president/page.tsx` — Replaced with redirect
- `frontend/src/app/admin/knowledge-base/government/executive/vice-president/page.tsx` — Replaced with redirect
- `frontend/src/lib/__tests__/menu-config.test.ts` — Updated Executive Branch assertions
- `frontend/src/components/admin/__tests__/AdminSidebar.test.tsx` — Added KB-2.6 consolidation tests
- `frontend/src/app/knowledge-base/government/executive/__tests__/subsections.test.tsx` — Removed President/VP render tests

**New Files:**
- `frontend/src/app/knowledge-base/government/executive/__tests__/redirects.test.ts` — KB redirect tests
- `frontend/src/app/admin/knowledge-base/government/executive/__tests__/redirects.test.ts` — Admin redirect tests

## QA Results

### Review Date: 2026-03-15

### Reviewed By: Quinn (Test Architect)

### Code Quality Assessment

- **Overall Score**: 100/100
- **Tests Reviewed**: 10
- **Architecture**: Clean sidebar consolidation — separate President and Vice President entries replaced with single "Presidential Administrations" using Landmark icon. All 4 redirect stubs use server-side `redirect()` from `next/navigation` (no `'use client'` directive), which is optimal for SEO and performance.
- **Patterns**: Admin sidebar correctly updated in `AdminSidebar.tsx` (not `menu-config.ts` as story initially assumed). Unused imports (Crown, UserCircle) properly cleaned up.

### Compliance Check

| AC | Status | Notes |
|----|--------|-------|
| 1 | PASS | Sidebar entries consolidated to single "Presidential Administrations" |
| 2 | PASS | Landmark icon from Lucide |
| 3 | PASS | Public sidebar updated in menu-config.ts |
| 4 | PASS | Admin sidebar updated in AdminSidebar.tsx |
| 5 | PASS | /president → /administrations redirect (public + admin) |
| 6 | PASS | /vice-president → /administrations redirect (public + admin) |
| 7 | PASS | 10 new tests covering all redirects and sidebar changes |

### Improvements Checklist

- [x] No improvements needed — clean implementation

### Security Review

- **Status**: PASS
- **Notes**: No security implications — navigation/redirect changes only. Server-side redirects prevent exposure of old page content.

### Performance Considerations

- **Status**: PASS
- **Notes**: Server-side `redirect()` is optimal — no client-side rendering of old pages. Redirect happens before any component tree is built.

### Gate Status

Gate: PASS → docs/qa/gates/KB-2.6-navigation-updates-route-redirects.yml

### Recommended Status

**Ready for Review** → No changes needed
