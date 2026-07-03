# Architect Handoff: ADMIN-1 PRD Review

**Date:** 2025-12-03
**From:** Sarah (Product Owner)
**To:** Architect
**PRD Location:** `docs/prd/ADMIN-1.md`
**Status:** Pending Architect Review

---

## Executive Summary

The PRD for **ADMIN-1: Admin Dashboard UI & Import Enhancements** is ready for architectural review. This enhancement transforms the admin dashboard from a flat layout to hierarchical sidebar navigation, adds GOVMAN XML import, US Code import, and unified API search/import interfaces.

---

## Technical Decisions Requiring Architect Validation

### 1. Frontend Architecture

| Decision | Proposed Approach | Rationale | Architect Input Needed |
|----------|-------------------|-----------|------------------------|
| **Sidebar State** | Zustand for collapse state | Lightweight, already in stack | Confirm or suggest alternative |
| **Routing Structure** | Nested routes under `/admin/factbase/` | Follows Next.js App Router patterns | Validate route hierarchy |
| **Reusable Component** | Generic `SearchImportPanel<T>` with TypeScript generics | Code reuse for 3+ API integrations | Review component interface design |

### 2. Backend Architecture

| Decision | Proposed Approach | Rationale | Architect Input Needed |
|----------|-------------------|-----------|------------------------|
| **XML Parsing** | StAX streaming parser | Memory efficiency for ~5MB file | Confirm or recommend JAXB/DOM |
| **New Service** | `GovmanXmlImportService` | Follows existing service patterns | Validate service boundaries |
| **Import Endpoint** | `POST /api/admin/import/govman` | RESTful, admin-scoped | Review endpoint design |
| **Search Proxy Endpoints** | `GET /api/admin/search/{source}` | Proxy to external APIs | Validate if needed vs. direct client calls |

### 3. Database Schema

| Decision | Proposed Approach | Rationale | Architect Input Needed |
|----------|-------------------|-----------|------------------------|
| **Audit Columns** | Add `import_source`, `import_timestamp`, `imported_by` to `government_organizations` | Data provenance tracking | Confirm table choice and column types |
| **Migration Version** | V21 (next available) | Additive only, no breaking changes | Confirm migration numbering |
| **GOVMAN Mapping** | `EntityId` → `external_id`, `ParentId` → `parent_id` | Use existing columns | Validate mapping strategy |

### 4. Integration Patterns

| Decision | Proposed Approach | Rationale | Architect Input Needed |
|----------|-------------------|-----------|------------------------|
| **Duplicate Detection** | Name matching + external_id comparison | 95% accuracy target | Review algorithm approach |
| **User Tracking** | `imported_by` from Spring Security context | Audit requirement | Confirm auth integration pattern |
| **Error Handling** | Return `ImportResult` with counts + error details | Consistent with existing patterns | Review error response structure |

---

## GOVMAN XML Structure Analysis

**File:** `data/GOVINFO/GOVMAN-2025-01-13.xml` (~5MB)

```xml
<GovernmentManual>
    <Entity EntityId="70" ParentId="0" SortOrder="1">
        <EntityType>Parent</EntityType>
        <Category>Legislative Branch</Category>
        <AgencyName>Congress</AgencyName>
        <MissionStatement>
            <Record>
                <Paragraph>...</Paragraph>
            </Record>
        </MissionStatement>
        <Addresses>
            <Address>
                <FooterDetails>
                    <WebAddress>http://www.congress.gov</WebAddress>
                </FooterDetails>
            </Address>
        </Addresses>
    </Entity>
</GovernmentManual>
```

**Key Mapping Questions:**
1. Should `MissionStatement` paragraphs be concatenated or stored as array?
2. How to handle multiple `Address` elements?
3. Should `LeaderShipTables`, `ProgramAndActivities` be imported or deferred?

---

## US Code Spike Requirements

**Story ADMIN-1.10** is a research spike. Architect guidance needed on:

1. **Preferred data source**: USLM XML vs. House.gov bulk data vs. API
2. **Scope**: Full US Code vs. selected titles
3. **Data model**: New table vs. extend existing `Regulation` model

**Reference:** `docs/reference/APIs and Other Sources/uscode.house.gov/USLM-User-Guide.pdf`

---

## Risk Areas Flagged for Review

| Risk | PRD Mitigation | Architect Review |
|------|----------------|------------------|
| Large XML memory pressure | StAX streaming | Confirm approach sufficient |
| US Code format unknown | Spike before implementation | Provide initial guidance |
| Sidebar breaks existing pages | Phased rollout | Review integration approach |
| Search proxy vs. direct calls | Proposed proxy endpoints | Decide if proxies needed |

---

## Questions for Architect

1. **StAX vs. JAXB**: For GOVMAN XML parsing, is StAX the right choice, or would JAXB with generated classes be more maintainable?

2. **Search Proxies**: Should we create `/api/admin/search/*` proxy endpoints, or should the frontend call existing clients directly? PRD assumes proxies for consistent error handling.

3. **US Code Guidance**: Any initial direction on US Code data source before the spike begins?

4. **Audit Schema**: Proposed adding 3 columns to `government_organizations`. Should this be a separate `import_audit` table instead for all imported records?

5. **Feature Flags**: Should new admin routes be behind a feature flag during development?

---

## Existing Patterns to Follow

For reference, the architect should validate alignment with these existing patterns:

| Pattern | Location | Applies To |
|---------|----------|------------|
| CSV Import Service | `PlumCsvImportService`, `GovOrgCsvImportService` | GOVMAN import structure |
| API Client | `CongressApiClient`, `FederalRegisterClient` | Search proxy design |
| Import Result DTO | `PlumImportResult`, `CsvImportResult` | Import response format |
| Admin Controller | `AdminSyncController` | New import controller |
| Sync Service | `RegulationSyncService` | Batch processing patterns |

---

## Deliverables Expected from Architect Review

1. **Approval or modifications** to technical decisions above
2. **ADR (optional)**: If significant architectural decisions are made
3. **Updated PRD**: Any requirement changes based on technical constraints
4. **Spike guidance**: Initial direction for US Code research

---

## How to Review

1. Read full PRD at `docs/prd/ADMIN-1.md`
2. Review GOVMAN XML sample at `data/GOVINFO/GOVMAN-2025-01-13.xml`
3. Check existing import patterns in `backend/src/main/java/org/newsanalyzer/service/`
4. Provide feedback via architect review document or inline PRD comments

---

*Prepared by Sarah (Product Owner)*
*Ready for Architect Review*
