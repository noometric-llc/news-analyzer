# Sprint Change Proposal: Frontend Architecture Realignment (UI-3)

**Proposal ID:** SCP-2025-12-30-001
**Date:** 2025-12-30
**Prepared by:** Sarah (Product Owner)
**Triggered by:** Architecture Review v2.5 (Data Architecture Clarification)
**Status:** APPROVED

---

## 1. Issue Summary

### Problem Statement

The frontend implementation does not align with the clarified dual-layer data architecture documented in `architecture.md` v2.5. Specifically:

1. **Route `/knowledge-base`** currently browses the `entities` table (extracted/analysis data) but should browse authoritative Knowledge Base tables (`persons`, `committees`, `government_organizations`)

2. **Article Analyzer section** does not exist in the frontend, but is defined as a primary navigation area in the architecture

3. **Terminology confusion** - "Knowledge Base" in UI-2 implementation refers to extracted entities, while architecture defines it as authoritative reference data

### Root Cause

UI-2 Epic was implemented before the architectural clarification that explicitly distinguished between:
- **Article Analyzer** (analysis layer - extracted entities)
- **Knowledge Base** (authoritative reference layer - curated data)

The UI-2 patterns are sound; they were simply applied to the wrong data layer.

### Issue Classification

- [x] Fundamental misunderstanding of existing requirements
- [x] Necessary pivot based on new information (architecture clarification)

---

## 2. Impact Summary

### Epic Impact

| Epic | Impact | Action |
|------|--------|--------|
| **UI-2** | HIGH | Mark as superseded by UI-3; patterns will be reused |
| **UI-1** | LOW | `/factbase` routes already redirect; no changes needed |
| **Phase 4-6** | ENABLED | Article Analyzer UI is prerequisite for these phases |

### Artifact Impact

| Artifact | Change Required |
|----------|-----------------|
| `docs/architecture/architecture.md` | Already updated (v2.5) |
| `docs/ROADMAP.md` | Add UI-3 epic, update UI track status |
| `docs/prd/index.md` | Add section for public navigation changes (future) |
| UI-2 Epic doc | Add "Superseded by UI-3" note |
| Frontend code | Major restructure (new epic) |

### MVP Impact

- **No MVP scope change** - MVP is 100% complete
- This is a **post-MVP alignment correction**

---

## 3. Recommended Path Forward

### Approach: Full Adjustment with Phased Delivery

Create new **Epic UI-3: Frontend Architecture Realignment** to:
1. Reconfigure Knowledge Base to browse authoritative data with hierarchical navigation
2. Create Article Analyzer section for extracted entities and future article analysis

### Phasing

#### Phase A: Knowledge Base Realignment (UI-3.A)
**Estimated Effort:** 10-13 story points

| Story | Description | Points |
|-------|-------------|--------|
| UI-3.A.1 | Reconfigure EntityBrowser to use KB tables (persons, committees, gov_orgs) | 3 |
| UI-3.A.2 | Implement hierarchical KB navigation per architecture Section 8 | 5 |
| UI-3.A.3 | Update entity type configs and API endpoints | 2 |
| UI-3.A.4 | Route restructuring and redirects | 2 |
| UI-3.A.5 | Documentation updates | 1 |

**Deliverables:**
- `/knowledge-base` browses authoritative KB tables
- Hierarchical navigation: U.S. Federal Government -> Branches -> Departments
- Entity type selector shows: Organizations, People (Members, Appointees, Judges)
- Existing patterns (EntityBrowser, EntityDetail, HierarchyView) reused

#### Phase B: Article Analyzer Foundation (UI-3.B)
**Estimated Effort:** 8-10 story points

| Story | Description | Points |
|-------|-------------|--------|
| UI-3.B.1 | Create Article Analyzer navigation shell | 3 |
| UI-3.B.2 | Move extracted entity browsing to Article Analyzer | 3 |
| UI-3.B.3 | Create Articles list page (analyzed articles) | 3 |
| UI-3.B.4 | Update hero page with dual navigation (KB + Article Analyzer) | 1 |

**Deliverables:**
- `/article-analyzer` route with navigation shell
- `/article-analyzer/entities` - browse extracted entities (moved from current `/knowledge-base`)
- `/article-analyzer/articles` - list of analyzed articles (placeholder for Phase 4)
- Hero page updated with two primary CTAs

#### Future (Phase 4+): Article Submission
- Article text input and URL scraping
- Integration with reasoning service for entity extraction
- Analysis results display with KB matching

---

## 4. Specific Proposed Edits

### 4.1 Frontend Route Changes

| Current Route | New Route | Data Source |
|---------------|-----------|-------------|
| `/knowledge-base` | `/knowledge-base` | KB tables (persons, committees, gov_orgs) |
| `/knowledge-base/[entityType]` | `/knowledge-base/[category]/[subcategory]` | Hierarchical KB navigation |
| `/knowledge-base/[entityType]/[id]` | `/knowledge-base/.../[id]` | KB record detail |
| *N/A* | `/article-analyzer` | New section |
| *N/A* | `/article-analyzer/entities` | `entities` table (moved) |
| *N/A* | `/article-analyzer/articles` | `articles` table |

---

## 5. High-Level Action Plan

| # | Action | Owner | Priority | Status |
|---|--------|-------|----------|--------|
| 1 | Create UI-3 epic document | PO (Sarah) | HIGH | Done |
| 2 | Update ROADMAP.md with UI-3 entry | PO | HIGH | Done |
| 3 | Add supersession notice to UI-2 epic | PO | MEDIUM | Done |
| 4 | Architect review of UI-3 epic | Architect (Winston) | HIGH | Pending |
| 5 | Create UI-3.A stories (KB realignment) | PO | HIGH | Pending |
| 6 | Implement UI-3.A | Dev | HIGH | Pending |
| 7 | Create UI-3.B stories (Article Analyzer) | PO | MEDIUM | Pending |
| 8 | Implement UI-3.B | Dev | MEDIUM | Pending |

---

## 6. Agent Handoff Plan

| Role | Responsibility |
|------|----------------|
| **PO (Sarah)** | Create UI-3 epic, write stories, update roadmap |
| **Architect (Winston)** | Review UI-3 epic for technical alignment |
| **Dev** | Implement stories once approved |
| **QA** | Validate implementation matches architecture |

---

## 7. Success Criteria

| Criterion | Measurement |
|-----------|-------------|
| `/knowledge-base` browses authoritative data | Manual verification - shows persons, committees, gov_orgs |
| Hierarchical navigation implemented | Can navigate: KB -> U.S. Federal Government -> Executive -> Departments |
| Article Analyzer section exists | `/article-analyzer` route accessible |
| Extracted entities moved | `/article-analyzer/entities` shows data from `entities` table |
| No broken links | Automated link checker passes |
| Architecture alignment | Frontend matches Section 8 of architecture.md |

---

## 8. Rollback Plan

If UI-3 implementation encounters blockers:
- Phase A and B are independent - can ship Phase A alone
- UI-2 routes remain functional via redirects during transition
- Pattern components are untouched - only configuration changes

---

## 9. Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | Sarah (PO) | 2025-12-30 | APPROVED |
| Stakeholder | User | 2025-12-30 | APPROVED |

---

*End of Sprint Change Proposal*
