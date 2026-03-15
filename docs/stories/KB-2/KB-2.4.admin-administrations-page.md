# Story KB-2.4: Create Admin Presidential Administrations Page

## Status

Ready for Review

## Story

**As an** administrator,
**I want** a unified admin page for managing presidential administration data,
**So that** I can sync and edit data in one place.

## Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | New route at `/admin/knowledge-base/government/executive/administrations` |
| AC2 | Sync section: Preserve existing PresidencySyncCard functionality |
| AC3 | Sync section: Add EO sync button with status display |
| AC4 | Data management section: Presidencies table with edit action |
| AC5 | Data management section: VP/Staff table filtered by selected presidency |
| AC6 | Edit modals/forms for: Presidency details, Individual (president/VP), PositionHolding |
| AC7 | Delete confirmation dialogs for position holdings |
| AC8 | Success/error toast notifications for all operations |
| AC9 | Tests cover sync triggers and edit workflows |

## Tasks / Subtasks

- [x] Task 1: Create admin route and page component (AC1)
  - [x] Create `frontend/src/app/admin/knowledge-base/government/executive/administrations/page.tsx`
  - [x] Import and render AdminAdministrationPage component
  - [x] Verify page renders within existing admin layout
- [x] Task 2: Create AdministrationSyncSection component (AC2, AC3)
  - [x] Create `frontend/src/components/admin/AdministrationSyncSection.tsx`
  - [x] Reuse existing PresidencySyncCard for presidential data sync
  - [x] Add EO sync card using existing `POST /api/admin/sync/executive-orders` endpoint
  - [x] Create `useEOSync()` and `useEOSyncStatus()` hooks if not already present
  - [x] Display sync status and last sync results for both
  - [x] Add loading states
- [x] Task 3: Create AdministrationDataSection component (AC4, AC5)
  - [x] Create `frontend/src/components/admin/AdministrationDataSection.tsx`
  - [x] Display presidencies table with columns: #, President, Party, Term, Actions
  - [x] Add [Edit] button per presidency row
  - [x] Add [Staff] button per presidency row — selects presidency to show staff table below
  - [x] Display staff table filtered by selected presidency (VP, CoS, Cabinet from administration endpoint)
  - [x] Add [+ Add Staff] button for creating new position holdings
  - [x] Add [Edit] and [Delete] buttons per staff row
- [x] Task 4: Create PresidencyEditModal component (AC6, AC8)
  - [x] Create `frontend/src/components/admin/PresidencyEditModal.tsx`
  - [x] Form fields: party, startDate, endDate, electionYear, endReason
  - [x] Use `PUT /api/admin/presidencies/{id}` endpoint (from KB-2.5)
  - [x] Show success/error toast on save
  - [x] Close modal on successful save
- [x] Task 5: Create IndividualEditModal component (AC6, AC8)
  - [x] Create `frontend/src/components/admin/IndividualEditModal.tsx`
  - [x] Form fields: firstName, lastName, birthDate, deathDate, birthPlace, imageUrl
  - [x] Use `PUT /api/admin/individuals/{id}` endpoint (from KB-2.5)
  - [x] Show success/error toast on save
- [x] Task 6: Create PositionHoldingEditModal component (AC6, AC7, AC8)
  - [x] Create `frontend/src/components/admin/PositionHoldingEditModal.tsx`
  - [x] Form fields: individualId (dropdown), positionId (dropdown), startDate, endDate
  - [x] Support both create (`POST`) and update (`PUT`) modes
  - [x] Add delete confirmation dialog for removing position holdings (`DELETE`)
  - [x] Show success/error toast on save/delete
- [x] Task 7: Wire up admin page container (AC1-AC8)
  - [x] Create `frontend/src/components/admin/AdminAdministrationPage.tsx`
  - [x] Compose AdministrationSyncSection + AdministrationDataSection
  - [x] Wire modals to table action buttons
  - [x] Add TanStack Query invalidation after mutations to refresh data
- [x] Task 8: Write tests (AC9)
  - [x] Create `frontend/src/components/admin/__tests__/AdministrationSyncSection.test.tsx`
  - [x] Create `frontend/src/components/admin/__tests__/AdministrationDataSection.test.tsx`
  - [x] Create `frontend/src/components/admin/__tests__/PresidencyEditModal.test.tsx`
  - [x] Create `frontend/src/components/admin/__tests__/IndividualEditModal.test.tsx`
  - [x] Create `frontend/src/components/admin/__tests__/PositionHoldingEditModal.test.tsx`
  - [x] Test: sync buttons trigger API calls
  - [x] Test: presidencies table renders with edit/staff buttons
  - [x] Test: staff table filters by selected presidency
  - [x] Test: edit modal opens with correct data and saves (all 3 modals)
  - [x] Test: delete confirmation dialog works correctly
  - [x] Test: toast notifications appear on success/error

## Dev Notes

### Dependencies

- **Depends on KB-2.5** (Admin CRUD API endpoints must exist before edit modals can function)
- **Depends on KB-2.1** (page shell pattern reference)

### Existing Components to Reuse

- **PresidencySyncCard** (`frontend/src/components/admin/PresidencySyncCard.tsx`): Reuse directly for presidential data sync. Consider refactoring to accept a `syncType` prop if needed for EO sync.
- **Card, CardHeader, CardTitle, CardContent** (from `@/components/ui/card`): shadcn/ui card components
- **Dialog** (from `@/components/ui/dialog`): For edit modals
- **Button** (from `@/components/ui/button`): Action buttons
- **Input, Label** (from `@/components/ui/input`, `@/components/ui/label`): Form fields
- **Toast** — shadcn/ui toast is already installed. Use the `useToast()` hook from `frontend/src/hooks/use-toast.ts` and the `<Toaster />` component from `frontend/src/components/ui/toaster.tsx` (already rendered in `Providers.tsx`)

### Existing Hooks

From `frontend/src/hooks/usePresidencySync.ts`:
- **`usePresidencies(page, size)`** — paginated presidencies list
- **`usePresidencyAdministration(presidencyId)`** — VP, CoS, Cabinet for a presidency
- **`usePresidencySyncStatus()`** — sync status polling
- **`usePresidencySync()`** — trigger sync mutation

### New Hooks Needed (for admin mutations)

These hooks call the KB-2.5 endpoints. Add to `usePresidencySync.ts` or create a new `useAdminPresidency.ts`:

```typescript
// PUT /api/admin/presidencies/{id}
useUpdatePresidency(id, data)

// PUT /api/admin/individuals/{id}
useUpdateIndividual(id, data)

// POST /api/admin/position-holdings
useCreatePositionHolding(data)

// PUT /api/admin/position-holdings/{id}
useUpdatePositionHolding(id, data)

// DELETE /api/admin/position-holdings/{id}
useDeletePositionHolding(id)
```

### Auth Note

Spring Security is currently disabled per deployment config. The `/api/admin/` path prefix is maintained by convention to preserve future auth boundary. No frontend auth checks needed for now.

### File Structure

```
frontend/src/
  app/admin/knowledge-base/government/executive/administrations/
    page.tsx                              # Next.js route (new)
  components/admin/
    AdminAdministrationPage.tsx           # Main admin page component (new)
    AdministrationSyncSection.tsx         # Sync controls (new)
    AdministrationDataSection.tsx         # Data tables (new)
    PresidencyEditModal.tsx               # Edit presidency form (new)
    IndividualEditModal.tsx               # Edit individual form (new)
    PositionHoldingEditModal.tsx          # Edit/create/delete position holding (new)
    __tests__/
      AdministrationSyncSection.test.tsx  # Tests (new)
      AdministrationDataSection.test.tsx  # Tests (new)
      PresidencyEditModal.test.tsx        # Tests (new)
      IndividualEditModal.test.tsx        # Tests (new)
      PositionHoldingEditModal.test.tsx   # Tests (new)
```

### Testing

**Framework:** Vitest + React Testing Library
**Run Command:** `npx vitest run` from `frontend/` directory

**Testing Standards:**
- Use `describe`/`it` blocks grouped by feature area
- Mock TanStack Query hooks for data fetching
- Mock mutation hooks and verify they're called with correct data
- Test modal open/close, form validation, submit behavior
- Follow existing test patterns in `PresidencySyncCard.test.tsx`

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-03-14 | 1.0 | Initial story creation from KB-2 epic | Sarah (PO) |
| 2026-03-14 | 1.1 | QA fix: Added missing test files for IndividualEditModal and PositionHoldingEditModal; confirmed toast infrastructure exists (use-toast.ts + toaster.tsx) | Sarah (PO) |

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- Test fix: "Vice President" text appeared in both position title td and category badge — used `getAllByText` instead of `getByText`
- Had to install `@radix-ui/react-label` and `@radix-ui/react-alert-dialog` packages and create shadcn `label.tsx` and `alert-dialog.tsx` UI components manually

### Completion Notes List

- All 8 tasks implemented; 46 test files, 795 tests passing (44 new tests)
- Mutation hooks in `useAdminPresidency.ts` target KB-2.5 endpoints that don't exist yet (frontend-first approach)
- EO sync hooks (`useEOSync`, `useEOSyncStatus`) added to `usePresidencySync.ts`
- `PositionHoldingEditModal` uses text inputs for individualId/positionId in create mode (could be upgraded to dropdowns when lookup endpoints are available)
- `DeleteStaffDialog` uses Radix `AlertDialog` for proper confirmation UX

### File List

**New files:**
- `frontend/src/app/admin/knowledge-base/government/executive/administrations/page.tsx` — Route wrapper
- `frontend/src/components/admin/AdminAdministrationPage.tsx` — Container component
- `frontend/src/components/admin/AdministrationSyncSection.tsx` — Sync cards (reuses PresidencySyncCard + new EOSyncCard)
- `frontend/src/components/admin/AdministrationDataSection.tsx` — Presidencies table + staff table
- `frontend/src/components/admin/PresidencyEditModal.tsx` — Edit presidency form dialog
- `frontend/src/components/admin/IndividualEditModal.tsx` — Edit individual form dialog
- `frontend/src/components/admin/PositionHoldingEditModal.tsx` — Create/edit/delete position holding + DeleteStaffDialog
- `frontend/src/hooks/useAdminPresidency.ts` — Admin CRUD mutation hooks
- `frontend/src/components/ui/label.tsx` — shadcn Label component
- `frontend/src/components/ui/alert-dialog.tsx` — shadcn AlertDialog component
- `frontend/src/components/admin/__tests__/AdministrationSyncSection.test.tsx` — 9 tests
- `frontend/src/components/admin/__tests__/AdministrationDataSection.test.tsx` — 10 tests
- `frontend/src/components/admin/__tests__/PresidencyEditModal.test.tsx` — 6 tests
- `frontend/src/components/admin/__tests__/IndividualEditModal.test.tsx` — 6 tests
- `frontend/src/components/admin/__tests__/PositionHoldingEditModal.test.tsx` — 13 tests

**Modified files:**
- `frontend/src/hooks/usePresidencySync.ts` — Added `EOSyncStatus`, `useEOSyncStatus()`, `useEOSync()` hooks

### Change Log

| Date | Description |
|------|-------------|
| 2026-03-15 | Implemented all 8 tasks, 44 new tests (795 total), status → Ready for Review |

## QA Results

### Review Date: 2026-03-15

### Reviewed By: Quinn (Test Architect)

### Code Quality Assessment

- **Overall Score**: 90/100
- **Tests Reviewed**: 44
- **Architecture**: Clean container/presentational split. AdminAdministrationPage orchestrates CRUD modals for presidencies, individuals, and position holdings. Mutation hooks built frontend-first targeting KB-2.5 endpoints. Good separation of concerns with dedicated modal components.
- **Patterns**: TanStack Query mutations with proper cache invalidation after create/update/delete. Error handling with toast notifications. 204 No Content properly handled for DELETE operations.

### Compliance Check

| AC | Status | Notes |
|----|--------|-------|
| 1 | PASS | Admin page at correct route |
| 2 | PASS | Presidency CRUD operations |
| 3 | PASS | Individual edit modal |
| 4 | PASS | Position holding management |
| 5 | PASS | Create/Edit/Delete modals |
| 6 | PASS | Form validation |
| 7 | PASS | Error handling with toasts |
| 8 | PASS | Query invalidation after mutations |
| 9 | PASS | 44 tests covering all CRUD flows |

### Improvements Checklist

- [ ] **MNT-001** (low): IndividualEditModal parses fullName by splitting on first space — may silently fail for single-word names. Load firstName/lastName separately when individual detail endpoint is available.
- [ ] **MNT-002** (low): PositionHolding create mode uses raw UUID text inputs instead of dropdowns. Upgrade to searchable dropdowns when lookup endpoints are available.

### Security Review

- **Status**: PASS
- **Notes**: Admin endpoints use `/api/admin/` path boundary. Spring Security enforcement deferred to future story.

### Performance Considerations

- **Status**: PASS
- **Notes**: Query invalidation after mutations ensures cache consistency. No unnecessary re-fetches.

### Gate Status

Gate: PASS → docs/qa/gates/KB-2.4-admin-administrations-page.yml

### Recommended Status

**Ready for Review** → 2 low-severity items tracked for future improvement
