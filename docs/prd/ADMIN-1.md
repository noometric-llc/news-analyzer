# NewsAnalyzer Brownfield Enhancement PRD

**Document Version:** 1.1
**Created:** 2025-12-03
**Author:** Sarah (Product Owner)
**Status:** Draft
**Template:** brownfield-prd-template-v2

---

## Table of Contents

1. [Intro Project Analysis and Context](#1-intro-project-analysis-and-context)
2. [Requirements](#2-requirements)
3. [User Interface Enhancement Goals](#3-user-interface-enhancement-goals)
4. [Technical Constraints and Integration Requirements](#4-technical-constraints-and-integration-requirements)
5. [Epic and Story Structure](#5-epic-and-story-structure)
6. [Epic Details](#6-epic-details)
7. [Change Log](#7-change-log)

---

## 1. Intro Project Analysis and Context

### 1.1 Analysis Source

**Source:** IDE-based analysis + existing documentation

Available documentation analyzed:
- `docs/architecture/tech-stack.md` - Comprehensive technology stack
- `docs/architecture/source-tree.md` - Full source tree structure
- `docs/architecture/coding-standards.md` - Coding conventions
- `docs/ROADMAP.md` - Master project roadmap (v2.7)
- `docs/analysis/ADMIN_DASHBOARD_IMPROVEMENTS_PROJECT_BRIEF.md` - Enhancement request
- `docs/analysis/FACTBASE_EXPANSION_PO_SUMMARY.md` - Factbase context

### 1.2 Current Project State

**NewsAnalyzer v2** is a polyglot fact-checking platform consisting of:

| Service | Technology | Purpose | Status |
|---------|------------|---------|--------|
| **Backend API** | Java 17 / Spring Boot 3.2 | REST API, business logic, data persistence | Production-ready |
| **Frontend** | Next.js 14 / TypeScript | User interface, admin dashboard | Production-ready |
| **Reasoning Service** | Python 3.11 / FastAPI | NLP extraction, OWL reasoning | Production-ready |
| **Database** | PostgreSQL 15 + JSONB | Entity storage, government data | 20 Flyway migrations |

**Current MVP Status:** ~95% complete

**Completed Factbase Expansion Epics:**
- FB-1: Congressional Data (Members, Committees)
- FB-1-UI: Frontend for Congressional Data
- FB-2-GOV: Government Organization Sync
- FB-2: Executive Branch Data (PLUM Appointees)
- FB-3: Regulatory Data (Federal Register)

### 1.3 Available Documentation Analysis

| Documentation | Status | Location |
|--------------|--------|----------|
| Tech Stack Documentation | Complete | `docs/architecture/tech-stack.md` |
| Source Tree/Architecture | Complete | `docs/architecture/source-tree.md` |
| Coding Standards | Complete | `docs/architecture/coding-standards.md` |
| API Documentation | OpenAPI/Swagger | Auto-generated at `/swagger-ui.html` |
| External API Documentation | Partial | `docs/reference/APIs and Other Sources/` |
| UX/UI Guidelines | Missing | Not formalized |
| Technical Debt Documentation | Tracked | In ROADMAP.md and QA-2 epic |

### 1.4 Enhancement Scope Definition

#### Enhancement Type

- [x] **New Feature Addition** - GOVMAN XML import, US Code import
- [x] **Major Feature Modification** - Admin dashboard UI restructure
- [x] **UI/UX Overhaul** - Sidebar navigation, unified API search pattern

#### Enhancement Description

The Admin Dashboard requires restructuring from a flat card-based layout to a hierarchical sidebar navigation supporting the platform's expanded factbase capabilities. Additionally, new data import capabilities (GOVMAN XML, US Code) and a unified search/import interface for existing external APIs are needed.

#### Impact Assessment

- [x] **Moderate Impact** (some existing code changes)

**Rationale:** The backend APIs largely exist (Congress.gov, Federal Register, Legislators). Primary work is:
1. Frontend restructure (new sidebar component, page reorganization)
2. New backend service for GOVMAN XML parsing
3. New frontend components for unified API search/import pattern

Existing functionality (sync cards, data models) remains intact.

### 1.5 Goals and Background Context

#### Goals

- Enable intuitive admin navigation with hierarchical expandable sidebar
- Import 100% of GOVMAN entities via XML parser
- Provide unified search/preview/import UI for all external API integrations
- Support future admin feature expansion (Article Search, Workflows, Analysis)
- Complete the "3 branches" factbase coverage with GOVMAN organizational structure
- Import US Code data to complete Federal Laws & Regulations section

#### Background Context

The NewsAnalyzer platform has successfully integrated multiple authoritative government data sources (Congress.gov, Federal Register, PLUM appointees) through the Factbase Expansion track. However, the admin dashboard UI has not evolved to match this expanded functionality. The current flat card layout becomes unwieldy as data sources multiply.

The Government Manual (GOVMAN) XML represents a critical missing data source that provides the official organizational structure of all three federal branches - exactly what's needed to complete the factbase foundation. This enhancement positions the platform for its next phase of development while making the admin experience intuitive and scalable.

---

## 2. Requirements

### 2.1 Functional Requirements

| ID | Requirement |
|----|-------------|
| **FR1** | The admin dashboard SHALL display a collapsible sidebar navigation with hierarchical menu structure supporting at least 3 levels of nesting. |
| **FR2** | The sidebar navigation SHALL include main category Factbase (active). Future categories (Article Search, Workflows) SHALL be hidden until implemented. |
| **FR3** | The Factbase category SHALL contain subcategories for Government Entities (Executive, Legislative, Judicial), Federal Laws & Regulations, and placeholder items for future entity types. |
| **FR4** | The system SHALL parse GOVMAN XML files and extract Entity records including EntityId, ParentId, Category, AgencyName, MissionStatement, and WebAddress. |
| **FR5** | The system SHALL import GOVMAN entities into the GovernmentOrganization table, establishing parent-child relationships via ParentId mapping. |
| **FR6** | The admin dashboard SHALL provide a unified "Search & Import" UI pattern for each external API (Congress.gov, Federal Register, Legislators Repo). |
| **FR7** | The Search & Import UI SHALL support: search with relevant filters, results preview with source attribution, and import options (Preview/Edit, Merge with existing, Direct import). |
| **FR8** | The system SHALL detect potential duplicate records during import and present merge options to the administrator. |
| **FR9** | The system SHALL validate records against database constraints before import and display validation errors to the user. |
| **FR10** | All imported records SHALL include `import_source` field; existing `created_by`/`created_at` fields serve as import audit trail. |
| **FR11** | The system SHALL import US Code data from admin-uploaded XML files downloaded from uscode.house.gov, processing one title at a time for verification. |
| **FR12** | US Code records SHALL be stored with title, section, text content, and effective date information. |
| **FR13** | The Federal Laws & Regulations subcategory SHALL include both Regulations (Federal Register) and US Code sections. |

### 2.2 Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| **NFR1** | GOVMAN XML parsing SHALL complete within 60 seconds for the full Government Manual file (~5MB). |
| **NFR2** | The sidebar navigation SHALL render within 100ms and not block main content loading. |
| **NFR3** | Search & Import UI SHALL display results within 3 seconds of query submission (dependent on external API response times). |
| **NFR4** | Duplicate detection SHALL achieve ≥95% accuracy using name matching and external identifier comparison. |
| **NFR5** | The admin dashboard restructure SHALL not increase frontend bundle size by more than 15%. |
| **NFR6** | All new components SHALL follow existing shadcn/ui patterns and Tailwind CSS conventions. |
| **NFR7** | US Code import format research spike SHALL be completed before implementation begins (format is TBD per project brief). |

### 2.3 Compatibility Requirements

| ID | Requirement |
|----|-------------|
| **CR1** | **Existing API Compatibility**: All existing REST endpoints (/api/members, /api/committees, /api/government-orgs, /api/regulations, /api/appointees) SHALL remain unchanged and backward compatible. |
| **CR2** | **Database Schema Compatibility**: New migrations SHALL only ADD columns/tables; existing tables (government_organizations, persons, regulations) SHALL NOT have columns removed or renamed. |
| **CR3** | **UI/UX Consistency**: New admin pages SHALL use the existing component library (shadcn/ui), color scheme, and spacing conventions established in FB-1-UI. |
| **CR4** | **Integration Compatibility**: Existing sync services (MemberSyncService, CommitteeSyncService, RegulationSyncService, PlumCsvImportService) SHALL continue to function without modification. |

---

## 3. User Interface Enhancement Goals

### 3.1 Integration with Existing UI

#### Current State

The admin dashboard (`frontend/src/app/admin/page.tsx`) is a **single flat page** with:

| Section | Components | Purpose |
|---------|------------|---------|
| Data Overview | 5 `SyncStatusCard` components in a grid | Show counts for Members, Committees, Enriched Members, Gov Orgs, PLUM |
| Manual Sync Actions | 5 `SyncButton` components | Trigger sync operations |
| Data Import | `CsvImportButton` | Import Legislative/Judicial orgs via CSV |
| Enrichment Status | `EnrichmentStatus` component | Show enrichment progress |

#### Existing Design Patterns to Preserve

| Pattern | Source | Usage in Enhancement |
|---------|--------|---------------------|
| **shadcn/ui Card** | `@/components/ui/card` | Continue using for content panels |
| **shadcn/ui Button** | `@/components/ui/button` | Continue using for actions |
| **TailwindCSS Grid** | `grid gap-4 sm:grid-cols-2 lg:grid-cols-5` | Maintain responsive grid patterns |
| **useIsAdmin hook** | `@/hooks/useIsAdmin` | Continue using for access control |
| **React Query hooks** | `useMembers`, `useCommittees`, etc. | Continue using for data fetching |
| **Color scheme** | Existing Tailwind config | No changes to color palette |

### 3.2 Modified/New Screens and Views

#### New Components

| Component | Type | Purpose |
|-----------|------|---------|
| `AdminSidebar` | Layout | Collapsible hierarchical navigation |
| `AdminLayout` | Layout | Wrapper providing sidebar + main content area |
| `SidebarMenuItem` | UI | Expandable menu item with icon, label, children |
| `SearchImportPanel` | Feature | Reusable search/preview/import pattern for external APIs |
| `GovmanImportPage` | Page | GOVMAN XML upload and import workflow |
| `UsCodeImportPage` | Page | US Code import workflow (after spike) |
| `ApiSearchPage` | Page | Template for Congress.gov, Fed Register, Legislators search |
| `ImportPreviewModal` | UI | Preview/edit record before import |
| `MergeConflictModal` | UI | Display duplicate detection, allow merge decisions |

#### Modified Components

| Component | Modification |
|-----------|--------------|
| `admin/page.tsx` | Becomes dashboard overview; move sync controls to respective subcategory pages |
| `AdminPage` wrapper | Wrap with `AdminLayout` to include sidebar |

#### New Page Structure

```
/admin                          → Dashboard overview (summary cards only)
/admin/factbase/executive       → Executive Branch hub
/admin/factbase/executive/agencies    → Agencies & Departments (Gov Orgs)
/admin/factbase/executive/positions   → Positions & Appointees (PLUM)
/admin/factbase/executive/govman      → GOVMAN XML Import (NEW)
/admin/factbase/legislative     → Legislative Branch hub
/admin/factbase/legislative/members   → Members sync/search
/admin/factbase/legislative/committees → Committees sync/search
/admin/factbase/judicial        → Judicial Branch hub
/admin/factbase/judicial/courts       → Courts (from CSV/GOVMAN)
/admin/factbase/regulations     → Federal Laws & Regulations hub
/admin/factbase/regulations/federal-register → Fed Register search/import
/admin/factbase/regulations/us-code   → US Code import (NEW)
```

### 3.3 UI Consistency Requirements

| Requirement | Specification |
|-------------|---------------|
| **Sidebar Width** | 256px expanded, 64px collapsed (icon-only) |
| **Sidebar Position** | Fixed left, full viewport height |
| **Collapse Behavior** | Toggle button + auto-collapse on mobile (<768px) |
| **Active State** | Highlighted background + accent color on active menu item |
| **Icon Library** | Lucide React (already in project) |
| **Typography** | Match existing `text-muted-foreground`, `font-semibold` patterns |
| **Spacing** | Use existing Tailwind spacing scale (4, 6, 8 units) |
| **Animation** | Subtle transitions for expand/collapse (150-200ms ease) |
| **Responsive** | Sidebar hidden on mobile; hamburger menu to toggle |

#### Visual Hierarchy

```
Factbase                          [icon: Database]
├── Government Entities           [icon: Building2]
│   ├── Executive Branch          [icon: Landmark]
│   │   ├── Agencies & Departments
│   │   ├── Positions & Appointees
│   │   └── GOVMAN Import
│   ├── Legislative Branch        [icon: Scale]
│   │   ├── Members
│   │   └── Committees
│   └── Judicial Branch           [icon: Gavel]
│       └── Courts
└── Federal Laws & Regulations    [icon: ScrollText]
    ├── Regulations (Federal Register)
    └── US Code
```

---

## 4. Technical Constraints and Integration Requirements

### 4.1 Existing Technology Stack

| Layer | Technology | Version | Notes |
|-------|------------|---------|-------|
| **Frontend** | Next.js | 14.1.0 | App Router |
| | TypeScript | 5.3.3 | Strict mode |
| | React Query | 5.17.19 | Server state management |
| | shadcn/ui | Latest | Component library |
| | Tailwind CSS | 3.4.1 | Styling |
| | Lucide React | 0.314.0 | Icons |
| **Backend** | Java | 17 LTS | Required for Spring Boot 3.x |
| | Spring Boot | 3.2.2 | REST API framework |
| | Spring Data JPA | (managed) | Database access |
| **Database** | PostgreSQL | 15+ | JSONB support |
| | Flyway | (managed) | Schema migrations |
| **External APIs** | Congress.gov | Existing | `CongressApiClient` |
| | Federal Register | Existing | `FederalRegisterClient` |
| | Legislators Repo | Existing | `LegislatorsRepoClient` |

### 4.2 Integration Approach

#### Database Integration Strategy

| Aspect | Approach |
|--------|----------|
| **New Tables** | None required; GOVMAN and US Code data maps to existing `government_organizations` table |
| **New Columns** | Add `import_source VARCHAR(50)` to `government_organizations` (use existing `created_by`/`created_at` for audit) |
| **New Migration** | V21 or next available; additive only |
| **GOVMAN Mapping** | `EntityId` → `external_id`, `ParentId` → `parent_id`, `Category` → `branch` |

#### API Integration Strategy

| Aspect | Approach |
|--------|----------|
| **New Service** | `GovmanXmlImportService` - Parse GOVMAN XML, validate, import |
| **New Service** | `UsCodeImportService` - (after spike determines format) |
| **New Endpoints** | POST `/api/admin/import/govman` - Upload and import GOVMAN XML |
| | POST `/api/admin/import/us-code` - Import US Code data |
| | GET `/api/admin/search/congress` - Proxy search to Congress.gov |
| | GET `/api/admin/search/federal-register` - Proxy search to Federal Register |
| | GET `/api/admin/search/legislators` - Proxy search to Legislators Repo |
| **Existing Endpoints** | No modifications; all remain backward compatible |

#### Frontend Integration Strategy

| Aspect | Approach |
|--------|----------|
| **Routing** | Add nested routes under `/admin/factbase/` using Next.js App Router |
| **Layout** | Create `admin/layout.tsx` with `AdminSidebar` component |
| **State** | Continue using React Query for server state; Zustand for sidebar collapse state |
| **Components** | New components in `src/components/admin/` following existing patterns |
| **Hooks** | New hooks in `src/hooks/` for import operations |

#### Testing Integration Strategy

| Aspect | Approach |
|--------|----------|
| **Backend Unit Tests** | Add tests for `GovmanXmlImportService` in `backend/src/test/` |
| **API Integration Tests** | Add tests in `api-tests/` for new import endpoints |
| **Frontend** | Manual testing (frontend test framework not yet established per QA-2) |

### 4.3 Code Organization and Standards

#### File Structure - Backend (new files)

```
backend/src/main/java/org/newsanalyzer/
├── controller/
│   └── AdminImportController.java      # NEW: Import endpoints
├── dto/
│   ├── GovmanEntityRecord.java         # NEW: XML parsed record
│   └── ImportResult.java               # NEW: Import operation result
├── service/
│   ├── GovmanXmlImportService.java     # NEW: XML parser + importer
│   └── UsCodeImportService.java        # NEW: US Code importer (after spike)
└── resources/db/migration/
    └── V21__add_import_audit_fields.sql  # NEW: Audit columns
```

#### File Structure - Frontend (new files)

```
frontend/src/
├── app/admin/
│   ├── layout.tsx                      # NEW: Admin layout with sidebar
│   ├── factbase/
│   │   ├── executive/
│   │   │   ├── page.tsx                # Executive branch hub
│   │   │   ├── agencies/page.tsx       # Gov orgs page (moved)
│   │   │   ├── positions/page.tsx      # PLUM page (moved)
│   │   │   └── govman/page.tsx         # NEW: GOVMAN import
│   │   ├── legislative/
│   │   │   ├── page.tsx                # Legislative hub
│   │   │   ├── members/page.tsx        # Members (moved)
│   │   │   └── committees/page.tsx     # Committees (moved)
│   │   ├── judicial/page.tsx           # Judicial hub
│   │   └── regulations/
│   │       ├── page.tsx                # Regulations hub
│   │       ├── federal-register/page.tsx  # Fed Register search
│   │       └── us-code/page.tsx        # NEW: US Code import
├── components/admin/
│   ├── AdminSidebar.tsx                # NEW: Sidebar navigation
│   ├── SidebarMenuItem.tsx             # NEW: Menu item component
│   ├── SearchImportPanel.tsx           # NEW: Reusable search/import
│   ├── ImportPreviewModal.tsx          # NEW: Preview before import
│   └── MergeConflictModal.tsx          # NEW: Duplicate handling
└── hooks/
    ├── useGovmanImport.ts              # NEW: GOVMAN import hook
    └── useApiSearch.ts                 # NEW: Generic API search hook
```

#### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Backend services | `{Feature}Service` | `GovmanXmlImportService` |
| Backend DTOs | `{Feature}DTO` or descriptive | `GovmanEntityRecord` |
| Frontend pages | `page.tsx` in route folder | `govman/page.tsx` |
| Frontend components | PascalCase | `AdminSidebar.tsx` |
| Frontend hooks | `use{Feature}` | `useGovmanImport` |

### 4.4 Deployment and Operations

#### Build Process Integration

| Aspect | Impact |
|--------|--------|
| **Backend** | No changes to Maven build; new services auto-discovered by Spring |
| **Frontend** | No changes to Next.js build; new pages auto-routed by App Router |
| **Database** | Flyway auto-runs new migration on startup |

#### Configuration Management

| Config | Location | New Values |
|--------|----------|------------|
| **GOVMAN file path** | `application.yml` | `newsanalyzer.import.govman-path: ${GOVMAN_XML_PATH:data/GOVINFO/GOVMAN-2025-01-13.xml}` |
| **US Code source** | `application.yml` | TBD after spike |
| **Import limits** | `application.yml` | `newsanalyzer.import.batch-size: 100` |

### 4.5 Risk Assessment and Mitigation

#### Technical Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| GOVMAN XML structure changes | Medium | Low | Version-check XML; fail gracefully on unknown elements |
| US Code format unknown | High | High | **Spike required** before implementation |
| Large XML file memory pressure | Medium | Low | JAXB parsing acceptable for 5MB file; add memory monitoring |
| Duplicate detection false positives | Medium | Medium | Conservative matching; require user confirmation |

#### Integration Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Sidebar breaks existing admin pages | High | Low | Thorough regression testing; phased rollout |
| New routes conflict with existing | Low | Low | Use `/admin/factbase/` prefix for all new routes |
| External API changes | Medium | Low | Existing clients already handle this; no new risk |

#### Known Technical Debt Impact

| Debt Item | Impact on Enhancement |
|-----------|----------------------|
| H2/PostgreSQL JSONB incompatibility | New services should include proper PostgreSQL tests |
| No frontend testing framework | Manual testing required; consider adding basic Vitest tests |
| 17 disabled repository tests | Does not block enhancement; tracked in QA-2 |

#### Mitigation Strategies

1. **US Code Spike First**: Complete research spike before any US Code implementation
2. **Incremental Delivery**: Deliver sidebar + one branch first, then expand
3. **Feature Flag**: Consider hiding new admin routes behind feature flag during development
4. **Rollback Plan**: New migrations are additive; can deploy without enabling UI routes

---

## 5. Epic and Story Structure

### 5.1 Epic Structure Decision

**Decision: Single Comprehensive Epic**

| Factor | Assessment |
|--------|------------|
| **Goal Unity** | All components serve "Admin Dashboard Improvements" - single cohesive objective |
| **Dependencies** | Sidebar must exist before new pages; sequential delivery required |
| **Team Context** | Single development track (not multiple parallel teams) |
| **Risk Profile** | Incremental stories allow early validation of UI approach |

**Epic:** `ADMIN-1: Admin Dashboard UI & Import Enhancements`

### 5.2 Story Sequence

| # | Story | Description | Risk Level |
|---|-------|-------------|------------|
| 1 | Sidebar Navigation Component | Create AdminSidebar, AdminLayout, integrate with existing page | Medium |
| 2 | Page Restructure - Executive Branch | Move Gov Orgs and PLUM to new routes under /admin/factbase/executive | Low |
| 3 | Page Restructure - Legislative Branch | Move Members and Committees to new routes | Low |
| 4 | GOVMAN XML Parser Service | Backend service to parse GOVMAN XML into entities | Medium |
| 5 | GOVMAN Import UI | Frontend page for uploading and importing GOVMAN data | Low |
| 6 | Unified Search/Import Component | Reusable SearchImportPanel for external API searches | Medium |
| 7 | API Search - Congress.gov | Implement search UI using SearchImportPanel for Congress.gov | Low |
| 8 | API Search - Federal Register | Implement search UI for Federal Register | Low |
| 9 | API Search - Legislators Repo | Implement search UI for Legislators Repo | Low |
| 10 | US Code Research Spike | Research US Code data format, API availability, import approach | Low |
| 11 | US Code Import Implementation | Implement backend import based on spike findings | Low |
| 12 | Judicial Branch & Cleanup | Add Judicial section, final polish, documentation | Low |
| 13 | US Code Frontend | Hierarchical tree display, file upload, single-title import workflow | Low |

### 5.3 Story Dependencies

```
Story 1 (Sidebar)
    ├── Story 2 (Executive Restructure)
    │   └── Story 5 (GOVMAN UI) ← Story 4 (GOVMAN Parser)
    ├── Story 3 (Legislative Restructure)
    └── Story 6 (SearchImportPanel)
        ├── Story 7 (Congress.gov Search)
        ├── Story 8 (Fed Register Search)
        └── Story 9 (Legislators Search)

Story 10 (US Code Spike)
    └── Story 11 (US Code Backend)
        └── Story 13 (US Code Frontend)

Story 12 (Judicial & Cleanup) - depends on Stories 1-9
```

---

## 6. Epic Details

### Epic ADMIN-1: Admin Dashboard UI & Import Enhancements

**Epic Goal:** Transform the admin dashboard from a flat card-based layout to a hierarchical sidebar navigation, add GOVMAN XML and US Code import capabilities, and provide unified search/import interfaces for all external APIs.

**Integration Requirements:**
- All existing sync functionality must continue to work
- Existing API endpoints remain unchanged
- Database schema changes are additive only
- UI follows established shadcn/ui patterns

---

### Story ADMIN-1.1: Sidebar Navigation Component

**As an** administrator,
**I want** a collapsible sidebar navigation in the admin dashboard,
**so that** I can easily navigate between different admin functions as the platform grows.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | AdminSidebar component renders with Factbase as the only visible top-level category |
| AC2 | Sidebar supports 3 levels of nesting (Category → Subcategory → Page) |
| AC3 | Sidebar collapses to icon-only view (64px width) when collapse button is clicked |
| AC4 | Sidebar expands to full view (256px width) when expand button is clicked |
| AC5 | Active menu item is visually highlighted with accent color |
| AC6 | Sidebar state (collapsed/expanded) persists across page navigation |
| AC7 | On mobile (<768px), sidebar is hidden by default with hamburger toggle |
| AC8 | AdminLayout wraps existing admin page content without breaking current functionality |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Existing `/admin` page renders correctly within new AdminLayout |
| IV2 | All existing SyncStatusCards display and function as before |
| IV3 | All existing SyncButtons trigger syncs successfully |
| IV4 | Page load time does not increase by more than 200ms |

---

### Story ADMIN-1.2: Page Restructure - Executive Branch

**As an** administrator,
**I want** Executive Branch admin functions organized under a dedicated section,
**so that** I can find agency and appointee management in a logical location.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Route `/admin/factbase/executive` displays Executive Branch hub page |
| AC2 | Route `/admin/factbase/executive/agencies` displays Government Organizations (moved from main admin) |
| AC3 | Route `/admin/factbase/executive/positions` displays PLUM Appointees (moved from main admin) |
| AC4 | Sidebar shows Executive Branch with Agencies and Positions as children |
| AC5 | GovOrgSyncStatusCard and related sync buttons function on new agencies page |
| AC6 | PlumSyncCard and related functionality works on new positions page |
| AC7 | Breadcrumb navigation shows current location (Admin > Factbase > Executive > Agencies) |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Gov Org sync operation completes successfully from new page location |
| IV2 | PLUM sync operation completes successfully from new page location |
| IV3 | Existing deep links to admin (if any) redirect appropriately or show guidance |
| IV4 | All React Query cache keys continue to work correctly |

---

### Story ADMIN-1.3: Page Restructure - Legislative Branch

**As an** administrator,
**I want** Legislative Branch admin functions organized under a dedicated section,
**so that** I can manage Congressional data in a logical location.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Route `/admin/factbase/legislative` displays Legislative Branch hub page |
| AC2 | Route `/admin/factbase/legislative/members` displays Members management |
| AC3 | Route `/admin/factbase/legislative/committees` displays Committees management |
| AC4 | Sidebar shows Legislative Branch with Members and Committees as children |
| AC5 | Member sync functionality works on new members page |
| AC6 | Committee sync functionality works on new committees page |
| AC7 | Membership sync functionality works from appropriate location |
| AC8 | Enrichment sync and status display correctly |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Members sync completes and updates count correctly |
| IV2 | Committees sync completes and updates count correctly |
| IV3 | Enrichment status displays accurate percentages |
| IV4 | All existing hooks (useMemberCount, useCommitteeCount, etc.) function correctly |

---

### Story ADMIN-1.4: GOVMAN XML Parser Service

**As a** system,
**I want** to parse Government Manual XML files,
**so that** administrators can import official government organizational structure.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | GovmanXmlImportService parses GOVMAN XML using JAXB with annotated classes for type safety |
| AC2 | Parser extracts: EntityId, ParentId, SortOrder, EntityType, Category, AgencyName |
| AC3 | Parser extracts: MissionStatement (concatenated paragraphs), WebAddress |
| AC4 | Parser builds parent-child relationships from ParentId references |
| AC5 | Service maps Category to Branch enum (Legislative, Executive, Judicial) |
| AC6 | Service validates records before import (required fields, valid parent references) |
| AC7 | Service returns ImportResult with counts: total, imported, skipped, errors |
| AC8 | Import sets import_source='GOVMAN'; existing created_by/created_at fields capture audit info |
| AC9 | Duplicate detection identifies existing records by name or external_id |
| AC10 | Full GOVMAN file (~5MB) parses within 60 seconds |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Imported organizations appear in existing Government Organizations API |
| IV2 | Parent-child relationships queryable via existing hierarchy endpoints |
| IV3 | Existing manually-created organizations are not overwritten |
| IV4 | Database constraints are not violated during import |

---

### Story ADMIN-1.5: GOVMAN Import UI

**As an** administrator,
**I want** a UI to upload and import GOVMAN XML files,
**so that** I can populate the factbase with official government structure data.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Route `/admin/factbase/executive/govman` displays GOVMAN import page |
| AC2 | Page includes file upload component accepting .xml files |
| AC3 | Upload validates file is XML before sending to backend |
| AC4 | Progress indicator shows during import operation |
| AC5 | Results display: total parsed, imported, skipped (duplicates), errors |
| AC6 | Error details are expandable to show specific validation failures |
| AC7 | Preview mode available: parse and display records without importing |
| AC8 | Confirmation dialog before actual import with record count |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | After import, Government Organizations page shows new records |
| IV2 | Branch filter correctly categorizes imported organizations |
| IV3 | Import does not affect other admin functions |

---

### Story ADMIN-1.6: Unified Search/Import Component

**As a** developer,
**I want** a reusable SearchImportPanel component,
**so that** I can implement consistent search/import UIs for multiple external APIs.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | SearchImportPanel accepts props: apiName, searchEndpoint, filterConfig, resultRenderer |
| AC2 | Component renders search input with configurable filters |
| AC3 | Component displays results in a consistent card/table format |
| AC4 | Each result shows source attribution (e.g., "From Congress.gov") |
| AC5 | Each result has action buttons: Preview, Import, Compare (if duplicate detected) |
| AC6 | Preview action opens ImportPreviewModal with editable fields |
| AC7 | Import action validates and imports with confirmation |
| AC8 | Compare action opens MergeConflictModal showing existing vs. new record |
| AC9 | Component handles loading, error, and empty states |
| AC10 | Component is fully typed with TypeScript generics for result type |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Component does not break when no results returned |
| IV2 | Component handles API errors gracefully with retry option |
| IV3 | Import operations respect existing database constraints |

---

### Story ADMIN-1.7: API Search - Congress.gov

**As an** administrator,
**I want** to search and import data from Congress.gov,
**so that** I can find and add specific Congressional records on demand.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Route `/admin/factbase/legislative/members/search` displays Congress.gov search |
| AC2 | Search filters include: name, state, party, chamber, congress number |
| AC3 | Results display member name, state, party, current status |
| AC4 | Preview shows full member details from Congress.gov API |
| AC5 | Import creates/updates Person record with Congress.gov data |
| AC6 | Duplicate detection matches on bioguideId |
| AC7 | Search respects Congress.gov rate limits (visual indicator if throttled) |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Imported members appear in Members listing page |
| IV2 | Existing member records are updated, not duplicated |
| IV3 | Automatic sync continues to work independently |

---

### Story ADMIN-1.8: API Search - Federal Register

**As an** administrator,
**I want** to search and import data from the Federal Register,
**so that** I can find and add specific regulations on demand.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Route `/admin/factbase/regulations/federal-register/search` displays Fed Register search |
| AC2 | Search filters include: keyword, agency, document type, date range |
| AC3 | Results display document title, agency, publication date, document number |
| AC4 | Preview shows full document details including abstract |
| AC5 | Import creates Regulation record with proper agency linkage |
| AC6 | Duplicate detection matches on document_number |
| AC7 | Agency linkage service automatically links to existing GovernmentOrganization |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Imported regulations appear in Regulations listing |
| IV2 | Agency linkage correctly associates regulations with gov orgs |
| IV3 | Full-text search indexes new regulations |

---

### Story ADMIN-1.9: API Search - Legislators Repo

**As an** administrator,
**I want** to search the unitedstates/congress-legislators repository,
**so that** I can find and import enrichment data for members.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Route `/admin/factbase/legislative/members/enrich` displays Legislators search |
| AC2 | Search filters include: name, bioguideId, state |
| AC3 | Results display member name with available enrichment data (social media, IDs) |
| AC4 | Preview shows all available enrichment fields |
| AC5 | Import updates existing Person record with enrichment data |
| AC6 | Match on bioguideId (required - cannot import without match) |
| AC7 | Shows which fields will be added/updated before import |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Enrichment data appears on Member detail pages |
| IV2 | Enrichment status percentage updates after import |
| IV3 | Does not overwrite manually corrected data (if flagged) |

---

### Story ADMIN-1.10: US Code Research Spike

**As a** developer,
**I want** to research US Code data formats and sources,
**so that** we can plan the implementation approach.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Document available US Code data sources (USLM, bulk data, APIs) |
| AC2 | Analyze data format (XML, JSON, other) with sample structures |
| AC3 | Identify mapping to existing data model or required extensions |
| AC4 | Estimate implementation effort (S/M/L) |
| AC5 | Recommend approach: bulk import, API integration, or hybrid |
| AC6 | Document any blockers or risks discovered |
| AC7 | Spike results documented in `docs/research/US_CODE_SPIKE_FINDINGS.md` |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Spike findings are reviewed and approved before Story ADMIN-1.11 begins |
| IV2 | No code changes in this story - research only |

---

### Story ADMIN-1.11: US Code Import Implementation

**As an** administrator,
**I want** to import US Code data,
**so that** the factbase includes federal statutory law references.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Implementation approach follows spike recommendations |
| AC2 | Route `/admin/factbase/regulations/us-code` displays US Code import |
| AC3 | Import captures: title, section, heading, text (or summary), effective date |
| AC4 | US Code records stored with appropriate data model (per spike) |
| AC5 | Import sets import_source='USCODE'; existing created_by/created_at fields capture audit info |
| AC6 | Basic search/filter available for imported US Code sections |

*(Detailed acceptance criteria to be refined after spike)*

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | US Code data accessible via API |
| IV2 | Integration with existing regulation/law views (if applicable) |
| IV3 | No impact on existing Federal Register functionality |

---

### Story ADMIN-1.12: Judicial Branch & Final Polish

**As an** administrator,
**I want** the Judicial Branch section completed and overall polish applied,
**so that** the admin dashboard is production-ready.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Route `/admin/factbase/judicial` displays Judicial Branch hub |
| AC2 | Judicial organizations (from GOVMAN import) display correctly |
| AC3 | Sidebar navigation is complete with all sections |
| AC4 | Main `/admin` page shows summary dashboard with counts from all sections |
| AC5 | All pages have consistent loading and error states |
| AC6 | Responsive design verified on mobile, tablet, desktop |
| AC7 | Accessibility basics verified (keyboard navigation, focus states) |
| AC8 | Documentation updated: README, deployment guide if needed |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Full regression: all sync operations work |
| IV2 | Full regression: all import operations work |
| IV3 | Full regression: all search operations work |
| IV4 | Performance: admin pages load within 2 seconds |

---

### Epic Summary

| Metric | Value |
|--------|-------|
| **Total Stories** | 13 |
| **Backend Stories** | 3 (ADMIN-1.4, ADMIN-1.10, ADMIN-1.11) |
| **Frontend Stories** | 8 (ADMIN-1.1, 1.2, 1.3, 1.5, 1.6, 1.7-1.9, 1.13) |
| **Full-Stack Stories** | 2 (ADMIN-1.7, 1.8 if new endpoints needed) |
| **Research Stories** | 1 (ADMIN-1.10) |
| **Estimated Complexity** | Medium-Large |

---

## 7. Change Log

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-12-03 | 1.0 | Initial PRD creation | Sarah (PO) |
| 2025-12-03 | 1.1 | Applied architect review modifications: JAXB over StAX, simplified audit columns, updated acceptance criteria | Winston (Architect) |
| 2025-12-10 | 1.2 | Added ADMIN-1.13 (US Code Frontend) via Correct Course process; updated FR11 to admin-upload approach | Sarah (PO) |

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | Sarah | 2025-12-03 | Draft |
| Architect | Winston | 2025-12-03 | Approved with Modifications |
| Stakeholder | | | Pending Approval |

---

*End of PRD Document*
