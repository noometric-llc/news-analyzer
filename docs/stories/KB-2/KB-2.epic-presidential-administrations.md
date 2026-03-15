# Epic KB-2: Presidential Administrations

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | KB-2 |
| **Epic Name** | Presidential Administrations |
| **Epic Type** | KB UI Consolidation + Enhancement |
| **Priority** | HIGH |
| **Status** | DONE |
| **Created** | 2026-01-08 |
| **Owner** | Sarah (PO) |
| **Depends On** | KB-1 Complete, ARCH-1 Complete (Individual table refactor) |
| **Blocked By** | ~~ARCH-1~~ (completed 2026-03-14) |
| **Triggered By** | User request to consolidate President and VP pages into unified Administrations view |

## Executive Summary

Consolidate the separate President and Vice President pages into a unified **Presidential Administrations** experience. The new KB page provides administration-centric navigation (President, VP, staff, Executive Orders) with the ability to explore any historical administration. The Admin page provides centralized sync and edit capabilities.

### Current State

- **KB President Page** (`/knowledge-base/government/executive/president`):
  - Current president card with presidency number
  - Historical table of all 47 presidencies with expandable rows
  - Expandable rows show VP, CoS, Cabinet, EO count

- **KB Vice President Page** (`/knowledge-base/government/executive/vice-president`):
  - Mostly static educational content
  - No dynamic data binding

- **Admin President Page** (`/admin/knowledge-base/government/executive/president`):
  - PresidencySyncCard with sync status and trigger
  - Presidencies table with pagination

- **Admin VP Page** (`/admin/knowledge-base/government/executive/vice-president`):
  - Placeholder page (AdminPlaceholderPage component)

### Target State

- **KB Presidential Administrations Page** (`/knowledge-base/government/executive/administrations`):
  - **Current Administration Section:**
    - Current President and Vice President info prominently displayed
    - President's Staff: WH Chief of Staff, Cabinet Secretaries
    - Executive Orders written by current administration
  - **Historical Administrations Section:**
    - List of all 47 administrations (clickable)
    - Selecting one displays same full detail as current administration

- **Admin Presidential Administrations Page** (`/admin/knowledge-base/government/executive/administrations`):
  - Sync button for external API updates (Federal Register EOs)
  - CRUD controls for Presidents, Vice Presidents, Administration data
  - Data tables with edit capabilities

- **Deprecation:**
  - Remove separate KB President and VP pages (redirect to new administrations page)
  - Remove separate Admin President and VP pages (redirect to new admin page)

## Business Value

### Why This Epic Matters

1. **Unified Experience** - Users explore administrations holistically rather than separate President/VP silos
2. **Administration-Centric** - Government works by administration; UI should reflect this
3. **Reduced Navigation** - One page instead of two for both KB and Admin
4. **Educational UX** - Shows full context of each administration (leader, VP, staff, actions)
5. **Maintainability** - Fewer pages to maintain, consolidated components

### Success Metrics

| Metric | Target |
|--------|--------|
| Current administration displays completely | Yes (President, VP, CoS, Cabinet, EOs) |
| Any historical administration selectable | Yes (all 47) |
| Selected administration shows same detail as current | Yes |
| Admin sync functionality preserved | Yes |
| Admin edit controls functional | Yes (President, VP, Administration) |
| Old routes redirect to new page | Yes |

## Data Model

### Existing Entities (No Changes Required)

The KB-1 epic already created the required data model:

```
┌─────────────────┐       ┌──────────────────┐       ┌───────────────────┐
│   Individual    │       │    Presidency    │       │  ExecutiveOrder   │
├─────────────────┤       ├──────────────────┤       ├───────────────────┤
│ id (UUID)       │◄──────│ individual_id(FK)│       │ id (UUID)         │
│ first_name      │       │ id (UUID)        │◄──────│ presidency_id(FK) │
│ last_name       │       │ number (1-47)    │       │ eo_number         │
│ birth_date      │       │ start_date       │       │ title             │
│ death_date      │       │ end_date         │       │ signing_date      │
│ birth_place     │       │ party            │       │ status            │
│ ...             │       │ election_year    │       └───────────────────┘
└─────────────────┘       │ end_reason       │
        ▲                 └──────────────────┘
        │                          ▲
        │                          │ presidency_id
┌───────┴──────────────────────────┴──────────────────┐
│                  PositionHolding                     │
├──────────────────────────────────────────────────────┤
│ individual_id (FK) ──► Individual (VP, CoS, Cabinet) │
│ position_id (FK) ────► GovernmentPosition            │
│ presidency_id (FK) ──► Presidency                    │
│ start_date / end_date                                │
└──────────────────────────────────────────────────────┘
```

### Existing API Endpoints (Reuse)

| Endpoint | Purpose |
|----------|---------|
| `GET /api/presidencies` | List all presidencies (paginated) |
| `GET /api/presidencies/current` | Current presidency |
| `GET /api/presidencies/{id}` | Single presidency with VP list |
| `GET /api/presidencies/number/{number}` | Presidency by number (1-47) |
| `GET /api/presidencies/{id}/executive-orders` | EOs for presidency |
| `GET /api/presidencies/{id}/administration` | VP, CoS, Cabinet for presidency |
| `POST /api/admin/sync/presidencies` | Trigger presidency sync |
| `POST /api/admin/sync/executive-orders` | Trigger EO sync |

### New API Endpoints (Admin CRUD)

| Endpoint | Purpose |
|----------|---------|
| `PUT /api/admin/presidencies/{id}` | Update presidency record |
| `PUT /api/admin/individuals/{id}` | Update individual record (president/VP) |
| `POST /api/admin/position-holdings` | Create position holding (VP, CoS, Cabinet) |
| `PUT /api/admin/position-holdings/{id}` | Update position holding |
| `DELETE /api/admin/position-holdings/{id}` | Delete position holding |

## Scope

### In Scope

1. **KB Presidential Administrations Page**
   - Unified page at `/knowledge-base/government/executive/administrations`
   - Current administration section with President, VP, Staff, EOs
   - Historical administrations selector/list
   - Detail view for any selected administration

2. **Admin Presidential Administrations Page**
   - Unified page at `/admin/knowledge-base/government/executive/administrations`
   - Sync controls (preserve existing sync functionality)
   - CRUD forms for Presidency, Individual (President/VP), Administration data
   - Data tables with inline edit or modal edit

3. **Navigation Updates**
   - Update KB sidebar: Replace "President" and "Vice President" with "Presidential Administrations"
   - Update Admin sidebar: Same consolidation
   - Add redirects from old routes to new routes

4. **Backend Admin CRUD Endpoints**
   - Create admin endpoints for editing presidency and individual records
   - Create admin endpoints for managing position holdings

### Out of Scope

- Changes to Presidency/ExecutiveOrder/Individual entities (already complete)
- New data sources or sync integrations (existing Federal Register integration sufficient)
- Mobile app considerations
- Historical image gallery (future enhancement)

## Stories

### Story Summary

| ID | Story | Priority | Estimate | Status |
|----|-------|----------|----------|--------|
| KB-2.1 | Create KB Presidential Administrations Page Shell | P0 | 3 pts | Done ✅ |
| KB-2.2 | Implement Current Administration Section | P0 | 5 pts | Done ✅ |
| KB-2.3 | Implement Historical Administrations List & Selection | P0 | 4 pts | Done ✅ |
| KB-2.4 | Create Admin Presidential Administrations Page | P1 | 5 pts | Done ✅ |
| KB-2.5 | Implement Admin CRUD API Endpoints | P1 | 4 pts | Done ✅ |
| KB-2.6 | Navigation Updates & Route Redirects | P1 | 2 pts | Done ✅ |
| KB-2.7 | Cleanup Deprecated Pages & Components | P2 | 1 pt | Done ✅ |

**Epic Total:** 24 story points

### Dependency Graph

```
KB-2.1 (Page Shell) ──────────────────────────────────┐
    │                                                  │
    ▼                                                  ▼
KB-2.2 (Current Admin Section) ──► KB-2.3 (Historical List)
    │                                   │
    └───────────────┬───────────────────┘
                    ▼
              KB-2.5 (Admin CRUD APIs)
                    │
                    ▼
              KB-2.4 (Admin Page)
                    │
                    ▼
              KB-2.6 (Navigation & Redirects)
                    │
                    ▼
              KB-2.7 (Cleanup)
```

---

## Story Details

### KB-2.1: Create KB Presidential Administrations Page Shell

**Status:** Draft | **Estimate:** 3 pts | **Priority:** P0

**As a** Knowledge Base user,
**I want** a unified Presidential Administrations page,
**So that** I can explore administrations holistically.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | New route created at `/knowledge-base/government/executive/administrations` |
| AC2 | Page shell with header, breadcrumbs, and placeholder sections |
| AC3 | Section placeholders for: Current Administration, Historical Administrations |
| AC4 | Loading states and error handling implemented |
| AC5 | Page integrates with existing KB layout (PublicSidebar) |
| AC6 | Tests cover page rendering and basic interactions |

#### Technical Notes

**Prerequisite — ARCH-1 Cleanup:**
The frontend TypeScript DTOs in `frontend/src/hooks/usePresidencySync.ts` still reference `personId` instead of `individualId` (missed during ARCH-1 backend migration). Fix these types before building new components:
- `VicePresidentDTO.personId` → `individualId`
- `PresidencyDTO.personId` → `individualId`
- `OfficeholderDTO.personId` → `individualId`
- `CabinetMemberDTO.personId` → `individualId`

**Files to Create:**
- `frontend/src/app/knowledge-base/government/executive/administrations/page.tsx`
- `frontend/src/components/knowledge-base/AdministrationPage.tsx`

**Reuse:**
- KBBreadcrumbs component
- PublicSidebar integration
- Existing TanStack Query hooks

---

### KB-2.2: Implement Current Administration Section

**Status:** Draft | **Estimate:** 5 pts | **Priority:** P0

**As a** Knowledge Base user,
**I want** to see the current administration's complete information,
**So that** I understand who leads the government now.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Current President card displayed prominently (reuse/adapt PresidentCard) |
| AC2 | Current Vice President info displayed alongside President |
| AC3 | President's Staff section lists: WH Chief of Staff (if available), Cabinet Secretaries |
| AC4 | Executive Orders section lists EOs from current administration with pagination |
| AC5 | Staff and EOs fetched from existing APIs (`/administration`, `/executive-orders`) |
| AC6 | Loading skeletons for each section |
| AC7 | Empty states handled (e.g., no EOs yet) |
| AC8 | Tests cover data fetching and rendering |

#### Technical Notes

**New Components:**
- `frontend/src/components/knowledge-base/CurrentAdministration.tsx`
- `frontend/src/components/knowledge-base/AdministrationStaff.tsx`
- `frontend/src/components/knowledge-base/AdministrationExecutiveOrders.tsx`
- `frontend/src/components/knowledge-base/VicePresidentCard.tsx`

**API Calls:**
```typescript
// Current presidency
const { data: currentPresidency } = useCurrentPresidency();

// Administration details (VP, CoS, Cabinet)
const { data: administration } = usePresidencyAdministration(currentPresidency?.id);

// Executive Orders (paginated)
const { data: executiveOrders } = usePresidencyExecutiveOrders(currentPresidency?.id, page, size);
```

**Reuse Existing:**
- PresidentCard (adapt for side-by-side with VP)
- usePresidencyAdministration hook
- PresidencyAdministrationDTO (contains vicePresidents, chiefsOfStaff, cabinetMembers)

---

### KB-2.3: Implement Historical Administrations List & Selection

**Status:** Draft | **Estimate:** 4 pts | **Priority:** P0

**As a** Knowledge Base user,
**I want** to browse historical administrations and view their details,
**So that** I can learn about past leadership.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Historical administrations section shows list of all 47 administrations |
| AC2 | List displays: Presidency number, President name, Term dates, Party |
| AC3 | Clicking an administration shows its full details (same as current admin section) |
| AC4 | Selected administration state managed via URL query param (`?presidency=45`) for shareability and browser back support |
| AC5 | Current administration highlighted/badged in list |
| AC6 | List is sortable (by number ascending/descending) |
| AC7 | Smooth transition when switching between administrations |
| AC8 | Non-consecutive terms shown correctly (Cleveland 22 & 24, Trump 45 & 47) |
| AC9 | Tests cover list rendering, selection, and detail display |

#### Technical Notes

**Component Options:**
- **Option A:** Accordion/collapsible list (expand in place)
- **Option B:** Click to replace current section (selected state)
- **Recommended:** Option B - cleaner UX, mirrors "current" section behavior

**New Components:**
- `frontend/src/components/knowledge-base/HistoricalAdministrations.tsx`
- `frontend/src/components/knowledge-base/AdministrationListItem.tsx`
- `frontend/src/components/knowledge-base/AdministrationDetail.tsx` (shared between current & selected)

**State Management (URL query param):**
```typescript
const searchParams = useSearchParams();
const selectedNumber = searchParams.get('presidency');

// If no selection, show current. If selection, show selected.
const displayedPresidency = selectedNumber
  ? presidencies.find(p => p.number === Number(selectedNumber))
  : currentPresidency;

// Selection updates URL: ?presidency=45 (shareable, supports browser back)
```

---

### KB-2.4: Create Admin Presidential Administrations Page

**Status:** Draft | **Estimate:** 5 pts | **Priority:** P1

**As an** administrator,
**I want** a unified admin page for managing presidential administration data,
**So that** I can sync and edit data in one place.

#### Acceptance Criteria

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

#### Technical Notes

**New Components:**
- `frontend/src/app/admin/knowledge-base/government/executive/administrations/page.tsx`
- `frontend/src/components/admin/AdministrationSyncSection.tsx`
- `frontend/src/components/admin/AdministrationDataSection.tsx`
- `frontend/src/components/admin/PresidencyEditModal.tsx`
- `frontend/src/components/admin/IndividualEditModal.tsx`
- `frontend/src/components/admin/PositionHoldingEditModal.tsx`

**Reuse:**
- PresidencySyncCard (refactor to accept sync type prop)
- Existing presidencies table (add edit button column)

**ARCH-1 Note:** The `Person` entity was renamed to `Individual` in ARCH-1 (completed 2026-03-14). All references to person/Person in this epic use the `Individual` entity and `individual_id` foreign keys.

---

### KB-2.5: Implement Admin CRUD API Endpoints

**Status:** Draft | **Estimate:** 4 pts | **Priority:** P1

**As an** administrator,
**I want** API endpoints to update presidency and position holding data,
**So that** I can correct or enhance data through the admin UI.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `PUT /api/admin/presidencies/{id}` updates presidency fields |
| AC2 | `PUT /api/admin/individuals/{id}` updates individual fields (name, dates, birthplace) |
| AC3 | `POST /api/admin/position-holdings` creates new position holding |
| AC4 | `PUT /api/admin/position-holdings/{id}` updates existing position holding |
| AC5 | `DELETE /api/admin/position-holdings/{id}` removes position holding |
| AC6 | All endpoints under `/api/admin/` path prefix (auth enforcement deferred — Spring Security currently disabled per deployment config; path convention preserves future auth boundary) |
| AC7 | Validation errors return proper 400 responses with messages |
| AC8 | Audit logging for all changes (optional but recommended) |
| AC9 | Controller tests cover all endpoints |

#### Technical Notes

**Files to Create/Modify:**
- `backend/src/main/java/org/newsanalyzer/controller/AdminPresidencyController.java` (new)
- `backend/src/main/java/org/newsanalyzer/dto/PresidencyUpdateDTO.java` (new)
- `backend/src/main/java/org/newsanalyzer/dto/IndividualUpdateDTO.java` (new)
- `backend/src/main/java/org/newsanalyzer/dto/PositionHoldingCreateDTO.java` (new)
- `backend/src/main/java/org/newsanalyzer/dto/PositionHoldingUpdateDTO.java` (new)
- `backend/src/main/java/org/newsanalyzer/service/AdminPresidencyService.java` (new)

**DTO Examples:**
```java
public record PresidencyUpdateDTO(
    String party,
    LocalDate startDate,
    LocalDate endDate,
    Integer electionYear,
    PresidencyEndReason endReason
) {}

public record IndividualUpdateDTO(
    String firstName,
    String lastName,
    LocalDate birthDate,
    LocalDate deathDate,
    String birthPlace,
    String imageUrl
) {}

public record PositionHoldingCreateDTO(
    UUID individualId,
    UUID positionId,
    UUID presidencyId,
    LocalDate startDate,
    LocalDate endDate
) {}
```

---

### KB-2.6: Navigation Updates & Route Redirects

**Status:** Draft | **Estimate:** 2 pts | **Priority:** P1

**As a** user,
**I want** navigation to reflect the new unified administrations pages,
**So that** I can easily find the content.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | KB sidebar updated: "Presidential Administrations" replaces "President" and "Vice President" |
| AC2 | Admin sidebar updated: Same consolidation |
| AC3 | Old KB routes redirect to new page (`/president` → `/administrations`, `/vice-president` → `/administrations`) |
| AC4 | Old Admin routes redirect similarly |
| AC5 | Sidebar icons updated appropriately |
| AC6 | Menu configuration files updated |
| AC7 | Tests verify redirects work correctly |

#### Technical Notes

**Files to Modify:**
- `frontend/src/lib/menu-config.ts` (unified sidebar menu configuration — no separate config files exist)
- `frontend/src/app/knowledge-base/government/executive/president/page.tsx` (add redirect)
- `frontend/src/app/knowledge-base/government/executive/vice-president/page.tsx` (add redirect)
- `frontend/src/app/admin/knowledge-base/government/executive/president/page.tsx` (add redirect)
- `frontend/src/app/admin/knowledge-base/government/executive/vice-president/page.tsx` (add redirect)

**Redirect Pattern:**
```tsx
// In old page.tsx files
import { redirect } from 'next/navigation';

export default function PresidentPage() {
  redirect('/knowledge-base/government/executive/administrations');
}
```

---

### KB-2.7: Cleanup Deprecated Pages & Components

**Status:** Draft | **Estimate:** 1 pt | **Priority:** P2

**As a** developer,
**I want** deprecated President/VP pages removed,
**So that** the codebase stays clean.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Old KB President page deleted (after redirect is in place for 1 release) |
| AC2 | Old KB Vice President page deleted |
| AC3 | Old Admin President page deleted |
| AC4 | Old Admin Vice President page deleted |
| AC5 | Unused components identified and removed if not used elsewhere |
| AC6 | No broken imports or references remain |
| AC7 | Build succeeds with no warnings about deleted files |

#### Technical Notes

**Files to Delete (after redirects confirmed working):**
- `frontend/src/app/knowledge-base/government/executive/president/page.tsx`
- `frontend/src/app/knowledge-base/government/executive/vice-president/page.tsx`
- `frontend/src/app/admin/knowledge-base/government/executive/president/page.tsx`
- `frontend/src/app/admin/knowledge-base/government/executive/vice-president/page.tsx`

**Components to Review (may be reused, don't delete blindly):**
- PresidentCard (likely reused in new page)
- PresidencyTable (may be reused or adapted)
- PresidencySyncCard (reused in new admin page)

---

## UI Mockups

### KB Presidential Administrations Page

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Knowledge Base > Government > Executive > Presidential Administrations  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ CURRENT ADMINISTRATION (47TH)                                    │   │
│  ├──────────────────────────┬──────────────────────────────────────┤   │
│  │ [Portrait]               │ [Portrait]                           │   │
│  │ PRESIDENT                │ VICE PRESIDENT                       │   │
│  │ Donald J. Trump          │ JD Vance                             │   │
│  │ Republican               │ Since Jan 20, 2025                   │   │
│  │ Since Jan 20, 2025       │                                      │   │
│  └──────────────────────────┴──────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ ADMINISTRATION STAFF                                             │   │
│  ├─────────────────────────────────────────────────────────────────┤   │
│  │ White House Chief of Staff                                       │   │
│  │   • Susie Wiles (Jan 2025 - present)                            │   │
│  │                                                                  │   │
│  │ Cabinet Secretaries                                              │   │
│  │   • Secretary of State: Marco Rubio                             │   │
│  │   • Secretary of Treasury: Scott Bessent                        │   │
│  │   • Secretary of Defense: Pete Hegseth                          │   │
│  │   • ...                                                          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ EXECUTIVE ORDERS (showing 1-10 of 42)            [1] [2] [3] ►  │   │
│  ├─────────────────────────────────────────────────────────────────┤   │
│  │ EO 14156 - Border Security Emergency             Jan 20, 2025   │   │
│  │ EO 14157 - Energy Independence                   Jan 20, 2025   │   │
│  │ EO 14158 - Regulatory Review                     Jan 21, 2025   │   │
│  │ ...                                                              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ═══════════════════════════════════════════════════════════════════   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ HISTORICAL ADMINISTRATIONS                        [Sort: Recent] │   │
│  ├─────────────────────────────────────────────────────────────────┤   │
│  │ #   President              Party         Term                    │   │
│  │ ─── ────────────────────── ───────────── ─────────────────────  │   │
│  │ 47  Donald J. Trump ★      Republican    2025-present           │   │
│  │ 46  Joseph R. Biden        Democrat      2021-2025              │   │
│  │ 45  Donald J. Trump        Republican    2017-2021              │   │
│  │ 44  Barack Obama           Democrat      2009-2017              │   │
│  │ 43  George W. Bush         Republican    2001-2009              │   │
│  │ ...                                                              │   │
│  │ 1   George Washington      Independent   1789-1797              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  (Click any row to view that administration's full details above)       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Admin Presidential Administrations Page

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Admin > Knowledge Base > Executive > Presidential Administrations       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ DATA SYNC                                                        │   │
│  ├─────────────────────────────────────────────────────────────────┤   │
│  │                                                                  │   │
│  │  Presidential Data          Executive Orders                    │   │
│  │  ┌──────────────────┐      ┌──────────────────┐                │   │
│  │  │ Status: Ready    │      │ Status: Ready    │                │   │
│  │  │ Last: 1hr ago    │      │ Last: 2hr ago    │                │   │
│  │  │ [Sync Now]       │      │ [Sync Now]       │                │   │
│  │  └──────────────────┘      └──────────────────┘                │   │
│  │                                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ PRESIDENCIES                                        [+ Add New] │   │
│  ├─────────────────────────────────────────────────────────────────┤   │
│  │ #   President         Party       Term          Actions         │   │
│  │ ─── ───────────────── ─────────── ───────────── ──────────────  │   │
│  │ 47  Donald J. Trump   Republican  2025-present  [Edit] [Staff]  │   │
│  │ 46  Joseph R. Biden   Democrat    2021-2025     [Edit] [Staff]  │   │
│  │ 45  Donald J. Trump   Republican  2017-2021     [Edit] [Staff]  │   │
│  │ ...                                                              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ STAFF FOR: 47th Administration (Trump)          [+ Add Staff]   │   │
│  ├─────────────────────────────────────────────────────────────────┤   │
│  │ Position             Person          Term          Actions      │   │
│  │ ─────────────────── ─────────────── ───────────── ────────────  │   │
│  │ Vice President       JD Vance        2025-present  [Edit] [Del] │   │
│  │ Chief of Staff       Susie Wiles     2025-present  [Edit] [Del] │   │
│  │ Sec. of State        Marco Rubio     2025-present  [Edit] [Del] │   │
│  │ ...                                                              │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Breaking existing bookmarks | LOW | HIGH | Implement redirects in KB-2.6 |
| Performance with 47 presidencies | LOW | LOW | Existing pagination and lazy loading |
| Admin edit validation complexity | MEDIUM | MEDIUM | Use DTO validation annotations |
| Missing Chiefs of Staff data | LOW | MEDIUM | Graceful empty states, admin can add |
| Non-consecutive term confusion | LOW | LOW | Clear UI labeling (Cleveland 22 vs 24) |

## Definition of Done

- [x] KB-2.1: Page shell with layout and placeholders
- [x] KB-2.2: Current administration section fully functional
- [x] KB-2.3: Historical administrations list with selection
- [x] KB-2.4: Admin page with sync and edit capabilities
- [x] KB-2.5: Admin CRUD endpoints tested
- [x] KB-2.6: Navigation updated, redirects working
- [x] KB-2.7: Deprecated pages cleaned up
- [x] All tests pass (backend + frontend)
- [x] 47 administrations navigable
- [x] Admin edit workflow functional
- [x] ROADMAP.md updated with KB-2 entry

## Related Documentation

- [KB-1 Epic (Presidency Data Model)](../KB-1/KB-1.epic-potus-data.md)
- [UI-6 Epic (Executive Branch Hierarchy)](../UI-6/UI-6.epic-executive-branch-hierarchy.md)
- [Presidency Entity](../../backend/src/main/java/org/newsanalyzer/model/Presidency.java)
- [PositionHolding Entity](../../backend/src/main/java/org/newsanalyzer/model/PositionHolding.java)

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-01-08 | 1.0 | Initial epic creation | Sarah (PO) |
| 2026-03-14 | 1.1 | Updated for ARCH-1 completion: Person→Individual rename, person_id→individual_id, PersonUpdateDTO→IndividualUpdateDTO, unblocked | John (PM) |
| 2026-03-14 | 1.2 | Architect review: Added KB-2.1 prerequisite (frontend personId→individualId fix), KB-2.3 URL query param state, KB-2.5 AC6 auth clarification, KB-2.6 sidebar config file correction | Winston (Architect) |
| 2026-03-15 | 1.3 | **Epic DONE**: All 7 stories implemented and QA-reviewed. All gates PASS (avg score 96). 754 tests passing. 4 low-severity items tracked for future improvement. DoD complete. | Sarah (PO) |

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | Sarah (PO) | 2026-01-08 | DRAFTED |
| Architect | Winston | 2026-03-14 | APPROVED (with modifications) |
| Developer | James (Dev) | 2026-03-15 | IMPLEMENTED |
| QA | Quinn (Test Architect) | 2026-03-15 | PASS (all 7 gates) |
| Product Owner | Sarah (PO) | 2026-03-15 | ACCEPTED — Epic DONE |

---

*End of Epic Document*
