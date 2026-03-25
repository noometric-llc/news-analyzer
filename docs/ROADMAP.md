# NewsAnalyzer Project Roadmap

**Document Version:** 4.5
**Created:** 2025-11-25
**Last Updated:** 2026-03-25
**Status:** Active

---

## Executive Summary

NewsAnalyzer v2 is a complete redesign from v1's failed architecture, implementing a unified entity model with Schema.org JSON-LD and optional OWL reasoning. This roadmap consolidates the project vision, completed work, and future milestones.

**Project Vision:** An open-source, transparent platform for news analysis, fact-checking, and media literacy.

**Architecture Health:** 8.5/10 (per architectural assessment 2025-11-23)

---

## Quick Status Dashboard

### Core Platform Phases

| Phase | Status | Progress | Description |
|-------|--------|----------|-------------|
| **Phase 1** | Complete | 100% | Core Entity Model & Services |
| **Phase 2** | Planned | 0% | Semantic Web Enrichment (Wikidata/DBpedia) |
| **Phase 3** | Complete | 100% | OWL Reasoning |
| **Phase 4** | Planned | 0% | Bias Detection & Content Analysis |
| **Phase 5** | Planned | 0% | News Source Management |
| **Phase 6** | Planned | 0% | Fact Validation |

### Factbase Expansion Track

| Epic | Status | Progress | Description |
|------|--------|----------|-------------|
| **FB-1** | Complete | 100% | Congressional Data (Members, Committees) |
| **FB-1-UI** | Complete | 100% | Frontend for Congressional Data |
| **FB-2-GOV** | Complete | 100% | Government Organization Sync |
| **FB-2** | Complete | 100% | Executive Branch Data (PLUM Appointees) |
| **FB-3** | Complete | 100% | Regulatory Data (Federal Register) |
| **ADMIN-1** | Complete | 100% | Admin Dashboard & Data Import (Supersedes FB-4, FB-5, FB-6) |

### Quality Assurance Track

| Epic | Status | Progress | Description |
|------|--------|----------|-------------|
| **QA-1** | Complete | 100% | API Integration Testing Framework |
| **QA-2** | Complete | 100% | Test Infrastructure Improvements |

### User Experience Track

| Epic | Status | Progress | Description |
|------|--------|----------|-------------|
| **UI-1** | Complete | 100% | Public Navigation & Factbase Pages |
| **UI-2** | Complete (Superseded) | 100% | Knowledge Explorer UI Refactoring |
| **UI-3** | Complete | 100% | Frontend Architecture Realignment |
| **UI-4** | Complete | 100% | Public Sidebar Integration |
| **UI-5** | Complete | 100% | KB Sidebar Reorganization & U.S. Code |
| **UI-6** | Complete | 100% | Executive Branch Hierarchical Navigation |

### Architecture Track

| Epic | Status | Progress | Description |
|------|--------|----------|-------------|
| **ARCH-1** | Complete | 100% | Individual Table Refactor (Person → Individual + CongressionalMember) |

### Knowledge Base Track

| Epic | Status | Progress | Description |
|------|--------|----------|-------------|
| **KB-1** | Complete | 100% | President of the United States Data |
| **KB-2** | Complete | 100% | Presidential Administrations |

### AI Evaluation Track

| Epic | Status | Progress | Description |
|------|--------|----------|-------------|
| **EVAL-1** | Complete | 100% | KB Fact Extraction & Synthetic Article Generator |
| **EVAL-2** | Complete | 100% | Entity Extraction Evaluation Harness (Promptfoo + Precision/Recall/F1) |
| **EVAL-DASH** | In Progress | 0% | AI Evaluation Portfolio Dashboard (surfaces EVAL-1/EVAL-2 work in frontend) |
| **EVAL-3** | Planned | 0% | Cognitive Bias & Logical Fallacy Evaluation via Ontology |

> **EVAL-2 Note:** ~~Build an entity extraction evaluation harness...~~ **COMPLETE (2026-03-25).** Gold dataset (64 curated articles + 25 CoNLL), dual-extractor comparison (spaCy vs Claude), Promptfoo harness with fuzzy-matching scorer, 104 pytest tests. Key result: Claude F1=0.60 vs spaCy F1=0.31 on government articles; spaCy wins on general newswire (0.91 vs 0.87).
>
> **EVAL-DASH Note:** Frontend dashboard surfacing EVAL-1/EVAL-2 work for portfolio visibility. Currently all evaluation artifacts are backend-only — invisible to site visitors. This epic adds an `/evaluation` section with model comparison charts, dataset explorer, and a polished methodology page (the URL to send recruiters). 5 stories (~1-1.5 weeks), 4 required + 1 stretch. Prioritized on 2026-03-25 for immediate job search impact.
>
> **EVAL-3 Note:** Extends the OWL ontology with academically-grounded cognitive bias and logical fallacy definitions. LLM evaluation prompts are grounded in SPARQL-retrieved definitions rather than relying on the model's internal training knowledge — making analysis auditable and traceable to cited academic sources. Builds on EVAL-2's evaluation framework. Concept originated from architecture discussion on 2026-03-16.

### Overall MVP Status

| Milestone | Status | Notes |
|-----------|--------|-------|
| **MVP** | **Complete** | 100% - All Factbase Expansion epics complete including ADMIN-1 |

---

## Table of Contents

### Core Platform Phases
1. [Phase 1: Core Entity Model & Services](#phase-1-core-entity-model--services)
2. [Phase 2: Semantic Web Enrichment](#phase-2-semantic-web-enrichment)
3. [Phase 3: OWL Reasoning](#phase-3-owl-reasoning)
4. [Phase 4: Bias Detection & Content Analysis](#phase-4-bias-detection--content-analysis)
5. [Phase 5: News Source Management](#phase-5-news-source-management)
6. [Phase 6: Fact Validation](#phase-6-fact-validation)

### Parallel Tracks
7. [Factbase Expansion Track](#factbase-expansion-track)
8. [Quality Assurance Track](#quality-assurance-track)
9. [User Experience Track](#user-experience-track)
10. [Architecture Track](#architecture-track-1)
11. [Knowledge Base Track](#knowledge-base-track-1)

### Reference
12. [Future Vision](#future-vision)
13. [Architecture Overview](#architecture-overview)
14. [Documentation Index](#documentation-index)

---

## Phase 1: Core Entity Model & Services

**Status:** COMPLETE
**Dates:** 2025-11-20 to 2025-11-24

### Overview

Establishes the unified entity model with Schema.org support across all three services (Java backend, Python reasoning service, Next.js frontend).

### Sub-Phases

#### Phase 1.2: Java Entity Model with Schema.org Support

**Status:** Complete (2025-11-20)
**Documentation:** `docs/workSummaries/PHASE_1.2_COMPLETE.md`

**Deliverables:**
- EntityType enum (PERSON, GOVERNMENT_ORG, ORGANIZATION, LOCATION, EVENT, CONCEPT)
- Entity JPA model with dual-layer design (`entity_type` + `schema_org_type`)
- JSONB columns for flexible properties and Schema.org JSON-LD
- EntityRepository with 8 custom query methods
- SchemaOrgMapper service for auto-generating JSON-LD
- EntityService with business logic
- EntityController with 11 REST endpoints
- Database migrations (Flyway V1-V3)
- 61/65 tests passing

#### Phase 1.3: Python Entity Extraction Service

**Status:** Complete (2025-11-20)
**Documentation:** `docs/workSummaries/PHASE_1.3_COMPLETE.md`

**Deliverables:**
- spaCy-based NER (Named Entity Recognition)
- Schema.org type mapping
- Entity extraction API endpoint
- Confidence filtering and statistics

#### Phase 1.4: Enhanced Schema.org Entity Extraction

**Status:** Complete (2025-11-21)
**Documentation:** `docs/workSummaries/PHASE_1.4_COMPLETE.md`

**Deliverables:**
- Enhanced schema mapper with government keyword detection
- 9 entity types (added Legislation, PoliticalParty, NewsMedia)
- POST /entities/extract endpoint with Schema.org mapping

#### Phase 1.5: Frontend Entity Display

**Status:** Complete (2025-11-21)
**Documentation:** `docs/workSummaries/PHASE_1.5_COMPLETE.md`

**Deliverables:**
- TypeScript type definitions
- API client for dual service integration
- EntityCard component (React/Next.js)
- Entity extraction page
- Type-based filtering with counts
- Expandable JSON-LD viewer
- Confidence threshold control
- TailwindCSS styling

#### Phase 1.6: Entity-to-GovernmentOrganization Linking

**Status:** Complete (2025-11-23)
**Documentation:** `docs/workSummaries/PHASE_1.6_COMPLETE.md`

**Deliverables:**
- Master Data Management pattern implementation
- V4 database migration (government_org_id FK)
- Entity validation workflow (3 methods)
- Name standardization ("EPA" → "Environmental Protection Agency")
- Property enrichment from authoritative data
- 2 new REST API validation endpoints
- 8 new unit tests (100% passing)

### Phase 1 Success Criteria: ALL MET

- [x] Unified entity model across all services
- [x] Schema.org JSON-LD representation
- [x] Full CRUD REST API
- [x] Entity extraction from text
- [x] Frontend visualization
- [x] Government organization linking
- [x] 69/73 tests passing (94.5%)

---

## Phase 2: Semantic Web Enrichment

**Status:** PLANNED
**Priority:** MEDIUM (Factbase Expansion track takes priority)
**Estimated Effort:** 3-4 sprints (6-8 weeks)
**Implementation Plan:** `docs/stories/PHASE_2/PHASE_2_IMPLEMENTATION_PLAN.md`

### Overview

Enhance entities with **generic knowledge base** data (Wikidata, DBpedia) and prepare for web publishing. This phase focuses on semantic web interoperability and SEO optimization.

> **Note:** This is distinct from the [Factbase Expansion Track](#factbase-expansion-track) which integrates **authoritative government data sources** (Congress.gov, Federal Register, PLUM). Both tracks enrich entities but from different source types.

**Epic Index:** `docs/stories/PHASE_2/PHASE_2_EPIC_INDEX.md`

### Planned Features

#### 2.1 External Entity Linking

**Description:** Link extracted entities to authoritative external knowledge bases.

**Tasks:**
- [ ] Wikidata integration (SPARQL endpoint queries)
- [ ] DBpedia linking (RDF resources)
- [ ] Entity disambiguation using external identifiers
- [ ] Store external IDs (wikidata_id, dbpedia_uri) in properties JSONB
- [ ] Confidence scoring for external matches

**API Endpoints:**
- `POST /api/entities/{id}/link/wikidata` - Link entity to Wikidata
- `POST /api/entities/{id}/link/dbpedia` - Link entity to DBpedia
- `GET /api/entities/{id}/external-data` - Get enriched external data

#### 2.2 Schema.org Property Expansion

**Description:** Expand entities with rich Schema.org properties from external sources.

**Tasks:**
- [ ] Fetch additional properties from Wikidata
- [ ] Map Wikidata properties to Schema.org vocabulary
- [ ] Support for nested Schema.org objects (e.g., Person → worksFor → Organization)
- [ ] Relationship extraction between entities

#### 2.3 JSON-LD Publishing

**Description:** Prepare entities for web publishing and SEO.

**Tasks:**
- [ ] Generate valid JSON-LD output
- [ ] JSON-LD validation against Schema.org
- [ ] Public entity pages with embedded JSON-LD
- [ ] Sitemap generation for entities
- [ ] Canonical URLs for entities

#### 2.4 Schema.org Validation

**Description:** Ensure all Schema.org data is valid and complete.

**Tasks:**
- [ ] Implement Schema.org validation service
- [ ] Required property checking per type
- [ ] Property value type validation
- [ ] Validation API endpoint
- [ ] Bulk validation for existing entities

### Phase 2 Success Criteria

- [ ] 50%+ of GOVERNMENT_ORG entities linked to Wikidata
- [ ] External enrichment adds 3+ properties per linked entity
- [ ] JSON-LD passes Google Structured Data Testing Tool
- [ ] All existing entities pass Schema.org validation

### Dependencies

- Phase 1.6 complete (entity validation workflow)
- External API access (Wikidata SPARQL, DBpedia)

---

## Phase 3: OWL Reasoning

**Status:** COMPLETE
**Dates:** 2025-11-21
**Documentation:** `docs/workSummaries/PHASE_3_OWL_REASONING.md`

### Overview

Advanced semantic reasoning using OWL (Web Ontology Language) for automated entity classification and inference.

### Deliverables

#### 3.1 Custom NewsAnalyzer Ontology

**File:** `reasoning-service/ontology/newsanalyzer.ttl`

**Custom Classes (extending Schema.org):**
- LegislativeBody (Congress, Senate, Parliament)
- ExecutiveAgency (EPA, FDA, DOJ)
- JudicialBody (Supreme Court, District Courts)
- Legislator (Senators, Representatives)
- PoliticalParty (Democratic Party, Republican Party)
- Legislation (Bills, laws, regulations)
- NewsMedia (News outlets and media organizations)

**Custom Properties:**
- hasJurisdiction, passedLegislation, sponsoredBy
- memberOf, affiliatedWith, regulatedBy, reportedBy
- politicalOrientation, biasScore, credibilityScore

#### 3.2 OWL Reasoner Service

**File:** `reasoning-service/app/services/owl_reasoner.py`

**Features:**
- Entity addition to knowledge graph
- OWL-RL inference engine
- Entity classification (get inferred types)
- Entity enrichment with inference
- Consistency checking
- SPARQL query support
- Graph export (Turtle, N-Triples, JSON-LD)

#### 3.3 Inference Rules

1. **Legislator by Membership:** Anyone memberOf a LegislativeBody is a Legislator
2. **Government by Jurisdiction:** Any org with hasJurisdiction is a GovernmentOrganization
3. **Legislative Body by Action:** Any org that passedLegislation is a LegislativeBody
4. **Executive Agency by Regulation:** Any org regulatedBy points to is an ExecutiveAgency

#### 3.4 API Endpoints

- `POST /entities/reason` - Apply OWL reasoning to enrich entities
- `GET /entities/ontology/stats` - Get ontology statistics
- `POST /entities/query/sparql` - Execute SPARQL queries

### Phase 3 Success Criteria: ALL MET

- [x] Custom ontology defined and loaded
- [x] OWL-RL reasoning operational
- [x] Automatic entity classification working
- [x] SPARQL queries functional
- [x] Consistency checking implemented

---

## Phase 4: Bias Detection & Content Analysis

**Status:** PLANNED
**Priority:** HIGH
**Target:** Post-Phase 2

### Overview

Implement core business requirement for bias detection and content analysis in news articles.

### Planned Features

#### 4.1 Bias Detection

**Business Requirement:** BR-003 (Bias Detection)

**Tasks:**
- [ ] Emotional language detection
- [ ] Loaded terminology identification
- [ ] Omission of context recognition
- [ ] Bias scoring algorithm
- [ ] Explainable bias indicators

**API Endpoints:**
- `POST /api/content/analyze-bias` - Analyze text for bias
- `GET /api/content/{id}/bias-report` - Get bias analysis results

#### 4.2 Logical Fallacy Detection

**Tasks:**
- [ ] Define common fallacy patterns
- [ ] NLP-based fallacy detection
- [ ] Confidence scoring for detected fallacies
- [ ] Educational explanations per fallacy type

#### 4.3 Content Analysis Pipeline

**Tasks:**
- [ ] Article ingestion endpoint
- [ ] Entity extraction integration
- [ ] Bias analysis integration
- [ ] Fallacy detection integration
- [ ] Combined analysis report

### Phase 4 Success Criteria

- [ ] Detect 10+ types of bias indicators
- [ ] Identify 5+ logical fallacy types
- [ ] Accuracy >80% on validation dataset
- [ ] Analysis completes in <5 seconds per article

### Dependencies

- Phase 2 complete (entity enrichment)
- Training data for bias/fallacy models

---

## Phase 5: News Source Management

**Status:** PLANNED
**Priority:** MEDIUM
**Target:** Post-Phase 4

### Overview

Implement core business requirement for news source tracking and reliability scoring.

### Planned Features

#### 5.1 News Source Database

**Business Requirement:** BR-001 (News Source Management)

**Tasks:**
- [ ] NewsSource entity model
- [ ] Source metadata (domain, ownership, funding)
- [ ] Historical reliability tracking
- [ ] Source categorization (mainstream, independent, etc.)

#### 5.2 Reliability Scoring

**Business Requirement:** BR-004 (Source Reliability Tracking)

**Tasks:**
- [ ] Define reliability metrics
- [ ] Historical accuracy tracking
- [ ] Score calculation algorithm
- [ ] Score update workflow
- [ ] Transparent methodology documentation

#### 5.3 Source Profile Pages

**Tasks:**
- [ ] Source detail view in frontend
- [ ] Reliability trend charts
- [ ] Related entities display
- [ ] Historical analysis timeline

### Phase 5 Success Criteria

- [ ] 1,000+ news sources in database
- [ ] Reliability scores for top 100 US sources
- [ ] Historical tracking operational
- [ ] Public source profiles available

### Dependencies

- Phase 4 complete (bias detection for score calculation)
- External data sources for source metadata

---

## Phase 6: Fact Validation

**Status:** PLANNED
**Priority:** HIGH
**Target:** Post-Phase 5

### Overview

Core differentiator - cross-reference claims against authoritative sources.

### Planned Features

#### 6.1 Claim Extraction

**Business Requirement:** BR-002 (Fact Validation)

**Tasks:**
- [ ] Factual claim extraction from text
- [ ] Claim classification (statistical, temporal, attribution)
- [ ] Claim confidence scoring

#### 6.2 Authoritative Source Integration

**Tasks:**
- [ ] Government API integration (congress.gov, data.gov)
- [ ] Official statistics sources
- [ ] Academic database integration
- [ ] Fact-checker organization data

#### 6.3 Validation Engine

**Tasks:**
- [ ] Claim-to-evidence matching
- [ ] Confidence scoring for validations
- [ ] Contradiction detection
- [ ] Evidence presentation

### Phase 6 Success Criteria

- [ ] Extract claims from 80%+ of news articles
- [ ] Integration with 5+ authoritative sources
- [ ] Validation accuracy >90% on test set
- [ ] Results include evidence citations

### Dependencies

- Phase 5 complete (source reliability informs validation)
- External API access (government, academic)

---

## Factbase Expansion Track

**Status:** IN PROGRESS
**Priority:** HIGH
**Description:** Integrate authoritative government data sources into NewsAnalyzer's factbase for fact-checking claims about government officials, organizations, and positions.

> **Relationship to Phase 2:** While Phase 2 focuses on generic semantic web enrichment (Wikidata/DBpedia), the Factbase Expansion track integrates **authoritative, domain-specific sources** (Congress.gov, Federal Register, PLUM). Both enrich entities but serve different purposes.

### Completed Epics

#### FB-1: Congressional Data Integration ✅

**Status:** COMPLETE
**Documentation:** [`docs/stories/FB-1/FB-1.epic-congressional-data.md`](stories/FB-1/FB-1.epic-congressional-data.md)

**Deliverables:**
- Congress.gov API integration for member and committee data
- Person entity with Congressional member data (535+ members)
- Committee entity and membership relationships
- Position history and term tracking
- Daily sync scheduler
- unitedstates/congress-legislators enrichment (social media, external IDs)

**Stories:** FB-1.0, FB-1.1, FB-1.2, FB-1.3, FB-1.4 (all complete)

#### FB-2-GOV: Government Organization Sync ✅

**Status:** COMPLETE
**Documentation:** [`docs/stories/FB-2-GOV/FB-2-GOV.epic-government-org-sync.md`](stories/FB-2-GOV/FB-2-GOV.epic-government-org-sync.md)

**Deliverables:**
- Federal Register API integration (~300 executive agencies)
- CSV import for Legislative/Judicial branches
- Admin dashboard sync UI
- API integration tests

**Stories:** FB-2-GOV.1, FB-2-GOV.2, FB-2-GOV.3, FB-2-GOV.4 (all complete)

#### FB-1-UI: Frontend for Congressional Data ✅

**Status:** COMPLETE
**Documentation:** [`docs/stories/FB-1-UI/FB-1-UI.epic-frontend-congressional.md`](stories/FB-1-UI/FB-1-UI.epic-frontend-congressional.md)

**Deliverables:**
- shadcn/ui component library integration
- Members listing and search page
- Committees listing with hierarchy
- Member detail page with term history
- Admin sync dashboard

**Stories:** FB-1-UI.1 ✅, FB-1-UI.2 ✅, FB-1-UI.3 ✅, FB-1-UI.4 ✅, FB-1-UI.5 ✅

#### FB-2: Executive Branch Data (PLUM Appointees) ✅

**Status:** COMPLETE
**Completion Date:** 2025-12-01
**Documentation:** [`docs/stories/FB-2/FB-2.epic-executive-branch-data.md`](stories/FB-2/FB-2.epic-executive-branch-data.md)
**Architect Review:** [`docs/qa/FB-2-architect-review.md`](qa/FB-2-architect-review.md)

**Technical Approach:** CSV import from OPM PLUM data (discovered during spike - 50% effort reduction vs web scraper).

**Deliverables:**
- PlumCsvImportService with OpenCSV parsing
- Executive appointee data (PAS, PA, NA, CA, XS positions) - 21,000+ records
- GovernmentPosition entity extended with Branch enum (LEGISLATIVE, EXECUTIVE, JUDICIAL)
- AppointmentType enum for position classification (PAS, PA, NA, CA, XS)
- Admin dashboard PLUM sync UI (PlumSyncCard component)
- Appointee lookup API (7 endpoints)
- Executive Positions API (4 endpoints)
- Cabinet member detection (23 position patterns)
- Vacant position tracking

**Stories (all Complete):**
- FB-2.1: PLUM CSV Import Service ✅
- FB-2.2: Executive Position Data Model ✅
- FB-2.3: Admin PLUM Sync UI ✅
- FB-2.4: Appointee Lookup API Endpoints ✅

#### FB-3: Regulatory Data (Federal Register) ✅

**Status:** COMPLETE
**Completion Date:** 2025-12-02
**Documentation:** [`docs/stories/FB-3/FB-3.epic-regulatory-data.md`](stories/FB-3/FB-3.epic-regulatory-data.md)

**Deliverables:**
- Extended FederalRegisterClient for document fetching
- Regulation entity model with full-text search (PostgreSQL tsvector)
- Agency linkage service with 5-level matching strategy (>95% match rate)
- Daily sync scheduler (configurable cron)
- 8 REST API endpoints for regulation lookup
- V17-V20 database migrations

**Stories (All Complete):**
- FB-3.1: Federal Register API Integration ✅
- FB-3.2: Regulation Data Model & Storage ✅
- FB-3.3: Agency Linkage Service ✅
- FB-3.4: Regulation Lookup API Endpoints ✅

**Quality Gates:** All 4 stories passed QA review with quality scores 95-100.

#### ADMIN-1: Admin Dashboard & Data Import Improvements ✅

**Status:** COMPLETE
**Completion Date:** 2025-12-12
**Documentation:** [`docs/stories/ADMIN-1/ADMIN-1.epic-admin-dashboard-improvements.md`](stories/ADMIN-1/ADMIN-1.epic-admin-dashboard-improvements.md)

**Supersedes:** FB-4 (Admin Dashboard Redesign), FB-5 (Authoritative Data Import), FB-6 (API Search & Import UI)

**Deliverables:**
- Collapsible 3-level sidebar navigation with Factbase menu structure
- Executive, Legislative, and Judicial branch admin pages
- GOVMAN XML import service and admin UI
- US Code XML parser, import service, and hierarchical tree view
- Reusable SearchImportPanel component framework
- Congress.gov member search with duplicate detection
- Federal Register document search with agency linkage
- Legislators repository enrichment with field diff preview

**Stories (all Complete):**
- ADMIN-1.1: Sidebar Navigation Component ✅
- ADMIN-1.2: Executive Branch Restructure ✅
- ADMIN-1.3: Legislative Branch Restructure ✅
- ADMIN-1.4: GOVMAN XML Parser Service ✅
- ADMIN-1.5: GOVMAN Import UI ✅
- ADMIN-1.6: Unified Search/Import Component ✅
- ADMIN-1.7: Congress.gov Search ✅
- ADMIN-1.8: Federal Register Search ✅
- ADMIN-1.9: Legislators Repo Search ✅
- ADMIN-1.10: US Code Research Spike ✅
- ADMIN-1.11: US Code Import Backend ✅
- ADMIN-1.12: Judicial Branch Final Polish ✅
- ADMIN-1.13: US Code Frontend ✅

**Quality Gates:** All 13 stories passed QA review with average score 93/100. 150+ unit tests added.

#### KB-1: President of the United States Data ✅

**Status:** COMPLETE
**Completion Date:** 2026-01-07
**Documentation:** [`docs/stories/KB-1/KB-1.epic-potus-data.md`](stories/KB-1/KB-1.epic-potus-data.md)
**Depends On:** UI-6 Complete (Executive Branch sub-pages exist)

**Deliverables:**
- New `Presidency` entity with Person/term separation (handles non-consecutive terms: Cleveland 22/24, Trump 45/47)
- New `ExecutiveOrder` entity linked to Presidency
- Extended `Person` entity with death_date, birth_place fields
- Extended `PositionHolding` with presidency_id for VP/Cabinet tracking
- Static seed data for all 47 presidencies with VP information
- Federal Register API integration for Executive Orders sync
- PresidentialSyncService with idempotent sync
- ExecutiveOrderSyncService with rate-limited API calls
- 7 Presidency API endpoints (list, current, by-id, by-number, EOs, administration)
- 4 Executive Order sync endpoints
- Admin sync UI (PresidencySyncCard component)
- KB President page with current president card, sortable historical table, expandable rows
- V30-V33 database migrations

**Stories (6 total, 24 points - all complete):**
- KB-1.0: Extend PositionHolding and DataSource (1 pt) ✅
- KB-1.1: Create Presidency and ExecutiveOrder Entities (4 pts) ✅
- KB-1.2: Implement Presidential Data Sync Service (5 pts) ✅
- KB-1.3: Create Presidency API Endpoints (3 pts) ✅
- KB-1.4: Build Admin Sync UI for President Page (2 pts) ✅
- KB-1.5: Implement KB President Page with Historical Table (6 pts) ✅
- KB-1.6: Integrate Executive Orders Sync (3 pts) ✅

**Quality Gates:** All 6 stories passed development. 18 backend tests for EO sync, 49 frontend tests for KB page.

### Planned Epics

*All planned Factbase Expansion work is complete. Future epics may include state-level regulatory data or additional government data sources.*

---

## Quality Assurance Track

**Status:** COMPLETE
**Priority:** HIGH (Infrastructure)

### QA-1: API Integration Testing Framework ✅

**Status:** COMPLETE
**Documentation:** [`docs/stories/QA-1/QA-1.epic-api-testing-framework.md`](stories/QA-1/QA-1.epic-api-testing-framework.md)

**Deliverables:**
- REST Assured test project at `api-tests/`
- Backend API tests (Entity, Government Organization endpoints)
- Reasoning Service API tests (extraction, reasoning, linking)
- Database integration for test data management
- GitHub Actions CI/CD pipeline
- Cross-service integration tests

**Stories:** QA-1.1 through QA-1.6 (all complete)

### QA-2: Test Infrastructure Improvements ✅

**Status:** COMPLETE
**Completion Date:** 2025-12-30
**Documentation:** [`docs/stories/QA-2/QA-2.epic-test-infrastructure.md`](stories/QA-2/QA-2.epic-test-infrastructure.md)

**Pre-Completed (discovered during architect review):**
- Testcontainers for repository tests (39+ tests using PostgreSQL container)
- Frontend testing framework (Vitest + Testing Library)
- CI/CD workflows (5 GitHub Actions pipelines operational)

**Deliverables:**
- 105 new frontend component tests (166 total)
- CI coverage thresholds (JaCoCo 70% backend, Vitest 30% frontend baseline)
- Comprehensive CI/CD documentation (`.github/workflows/README.md`)

**Stories (all complete):**
- QA-2.1-2.3: Pre-epic implementation ✅
- QA-2.4: Expand Frontend Test Coverage (3 pts) ✅
- QA-2.5: Add CI Coverage Thresholds (2 pts) ✅
- QA-2.6: CI/CD Documentation (1 pt) ✅

---

## User Experience Track

**Status:** IN PROGRESS
**Priority:** HIGH
**Description:** Create public-facing user interfaces that make the factbase accessible to users unfamiliar with government structure.

### UI-1: Public Navigation & Factbase Pages ✅

**Status:** COMPLETE
**Completion Date:** 2025-12-26
**Documentation:** [`docs/stories/UI-1/UI-1.epic-public-navigation-ux.md`](stories/UI-1/UI-1.epic-public-navigation-ux.md)

**Business Value:**
- Improved discoverability for users unfamiliar with government domain
- Educational context on each page explaining data categories
- Scalable navigation structure for future expansion
- Seamless admin access via gear icon

**Deliverables:**
- Collapsible sidebar navigation at `/factbase`
- Hero landing page with "Explore Factbase" CTA
- People pages: Congressional Members, Executive Appointees, Federal Judges
- Organization pages: Executive, Legislative, Judicial branches
- Shared sidebar components (extracted from admin)
- Educational content headers for each page
- Legislative branch org data (~15 orgs via CSV)
- Judicial branch org data (~120 orgs via CSV)
- Federal judges data (pending FJC API research)

**Stories (12 total, 47 points):**
- UI-1.1: Shared Sidebar Components (5 pts) ✅
- UI-1.2: Factbase Layout & Landing Update (5 pts) ✅
- UI-1.3: Menu Configuration System (3 pts) ✅
- UI-1.4: Content Page Template (3 pts) ✅
- UI-1.5: Congressional Members Page (3 pts) ✅
- UI-1.6: Executive Appointees Page (3 pts) ✅
- UI-1.7: Federal Judges Page (5 pts) ✅
- UI-1.8: Federal Government Org Pages (5 pts) ✅
- UI-1.9: Populate Legislative Branch Orgs (3 pts) ✅
- UI-1.10: Populate Judicial Branch Orgs (3 pts) ✅
- UI-1.11: Federal Judges Data Research & Import (8 pts) ✅
- UI-1.12: Admin Access Link (1 pt) ✅

**Quality Gates:** All 12 stories passed QA review.

---

### UI-2: Knowledge Explorer UI Refactoring ✅ (Superseded)

**Status:** COMPLETE (Superseded by UI-3)
**Completion Date:** 2025-12-29
**Documentation:** [`docs/stories/UI-2/UI-2.epic-knowledge-explorer.md`](stories/UI-2/UI-2.epic-knowledge-explorer.md)

> **Note:** UI-2 patterns (EntityBrowser, EntityDetail, HierarchyView) are preserved and reused in UI-3.
> Route structure realigned to match architecture v2.5 dual-layer model.
> See [UI-3 Epic](stories/UI-3/UI-3.epic-frontend-realignment.md) for details.

**Relationship to UI-1:**
- **Uses:** Data imports from UI-1.9, UI-1.10, UI-1.11 (Legislative/Judicial orgs, Federal Judges)
- **Supersedes:** UI-1 page components (replaces bespoke pages with reusable patterns)
- **Preserves:** User bookmarks via redirects from `/factbase/*` to `/knowledge-base/*`

**Business Value:**
- Unified Knowledge Explorer entry point eliminates navigation confusion
- Reusable pattern components reduce time to add new entity types
- Configuration-driven UI enables rapid extensibility
- Clean architecture supports future data domains (corporations, universities, etc.)

**Deliverables:**
- KnowledgeExplorer shell with EntityTypeSelector and ViewModeSelector
- Reusable EntityBrowser pattern (list/grid with filtering, sorting, pagination)
- Reusable EntityDetail pattern (configuration-driven detail pages)
- Reusable HierarchyView pattern (tree visualization)
- Cross-entity SearchBar component
- Migration of Government Orgs to new patterns
- Migration of ALL People types (Judges, Members, Appointees) to new patterns
- Cleanup of deprecated factbase code

**Stories (8 total, all complete):**
- UI-2.1: Knowledge Explorer Shell & Navigation ✅
- UI-2.2: EntityBrowser Pattern Component ✅
- UI-2.3: EntityDetail Pattern Component ✅
- UI-2.4: HierarchyView Pattern Component ✅
- UI-2.5: Cross-Entity Search ✅
- UI-2.6: Migrate Government Organizations ✅
- UI-2.7: Migrate People (Judges, Members, Appointees) ✅
- UI-2.8: Cleanup & Documentation ✅

**Quality Gates:** All 8 stories passed QA review.

---

### UI-3: Frontend Architecture Realignment ✅

**Status:** COMPLETE
**Completion Date:** 2025-12-31
**Documentation:** [`docs/stories/UI-3/UI-3.epic-frontend-realignment.md`](stories/UI-3/UI-3.epic-frontend-realignment.md)
**Depends On:** UI-2 Complete (reuses pattern components)
**Supersedes:** UI-2 route structure (preserves patterns)
**Triggered By:** Architecture Review v2.5 (Data Architecture Clarification)

**Business Value:**
- Aligns frontend with documented dual-layer data architecture
- Enables Phases 4-6 (Article Analyzer is prerequisite)
- Resolves terminology confusion (Knowledge Base = authoritative data)

**Deliverables:**
- Knowledge Base reconfigured to browse authoritative data (persons, committees, gov_orgs)
- Hierarchical KB navigation (Government → Branches → Departments)
- Article Analyzer section with navigation shell, entities page, articles list
- Hero page with balanced dual-navigation CTAs
- 396 frontend tests passing

**Stories (10 total, 25 points):**

*Phase A: Knowledge Base Realignment* ✅ **COMPLETE**
- UI-3.A.1: Reconfigure EntityBrowser for KB Tables (3 pts) ✅
- UI-3.A.2: Implement Hierarchical KB Navigation (5 pts) ✅
- UI-3.A.3: Update Entity Type Configs (2 pts) ✅ *(merged into A.1)*
- UI-3.A.4: Route Restructuring & Redirects (2 pts) ✅
- UI-3.A.5: Phase A Documentation (1 pt) ✅
- UI-3.A.6: Update Test Coverage (2 pts) ✅ *(achieved across A.1, A.2, A.4 - 106 tests)*

*Phase B: Article Analyzer Foundation* ✅ **COMPLETE**
- UI-3.B.1: Article Analyzer Navigation Shell (3 pts) ✅
- UI-3.B.2: Move Extracted Entities to Article Analyzer (3 pts) ✅
- UI-3.B.3: Articles List Page (3 pts) ✅
- UI-3.B.4: Hero Page Dual Navigation (1 pt) ✅

---

### UI-4: Public Sidebar Integration ✅

**Status:** COMPLETE
**Created:** 2026-01-01
**Completion Date:** 2026-01-02
**Documentation:** [`docs/stories/UI-4/UI-4.epic-sidebar-integration.md`](stories/UI-4/UI-4.epic-sidebar-integration.md)
**Depends On:** UI-3 Complete (layout structure exists)
**Triggered By:** User feedback: "There is no sidebar in the UI"

**Business Value:**
- Consistent UX between Admin and public sections
- Better mobile navigation with slide-in sidebar
- Improved discoverability via hierarchical navigation
- Architecture Section 8 compliance

**Deliverables:**
- Shared `SidebarLayout` component (extracted from Admin pattern)
- Knowledge Base sidebar integration with PublicSidebar
- Article Analyzer sidebar with new ArticleAnalyzerSidebar component
- Mobile responsive sidebars (slide-in overlay)
- Menu configuration updates matching actual routes
- 39 new mobile responsiveness tests
- 666 lines of deprecated code removed

**Stories (6 total, 13 points - all complete):**
- UI-4.0: Shared Sidebar Layout Component (2 pts) ✅
- UI-4.1: Knowledge Base Sidebar Integration (3 pts) ✅
- UI-4.2: Article Analyzer Sidebar (3 pts) ✅
- UI-4.3: Menu Configuration Updates (2 pts) ✅
- UI-4.4: Mobile Responsiveness Testing (2 pts) ✅
- UI-4.5: Cleanup Deprecated Components (1 pt) ✅

**Quality Gates:** All 6 stories passed QA review. 544 frontend tests passing.

---

### UI-5: KB Sidebar Reorganization & U.S. Code Integration ✅

**Status:** COMPLETE
**Created:** 2026-01-02
**Completion Date:** 2026-01-03
**Documentation:** [`docs/stories/UI-5/UI-5.epic-kb-sidebar-uscode.md`](stories/UI-5/UI-5.epic-kb-sidebar-uscode.md)
**Depends On:** UI-4 Complete (sidebar infrastructure exists)
**Triggered By:** User feedback: Sidebar structure needs reorganization; U.S. Code missing from v1 migration

**Business Value:**
- Terminology precision: "U.S. Federal Government" matches official naming
- Logical structure: "Branches" grouping enables future expansion
- Feature recovery: Restores U.S. Code browsing from v1
- Component reuse: Admin's UsCodeTreeView adapted for public use

**Deliverables:**
- Sidebar reorganization: "Government" → "U.S. Federal Government"
- "Branches" non-clickable grouping with nested Executive/Legislative/Judicial
- "U.S. Code (Federal Laws)" new menu item
- Public U.S. Code browse page at `/knowledge-base/government/us-code`
- Hierarchical Title → Chapter → Section navigation
- External links to official sources (uscode.house.gov)
- 19 new tests for U.S. Code page
- 585 frontend tests passing

**Stories (2 total, 5 points - all complete):**
- UI-5.1: Reorganize KB Sidebar Navigation (2 pts) ✅
- UI-5.2: Public U.S. Code Browse Page (3 pts) ✅

**Quality Gates:** All 2 stories passed QA review.

---

### UI-6: Executive Branch Hierarchical Navigation ✅

**Status:** COMPLETE
**Created:** 2026-01-03
**Completion Date:** 2026-01-05
**Documentation:** [`docs/stories/UI-6/UI-6.epic-executive-branch-hierarchy.md`](stories/UI-6/UI-6.epic-executive-branch-hierarchy.md)
**Depends On:** UI-5 Complete ✅
**Triggered By:** User feedback: Executive Branch needs sub-section navigation

**Business Value:**
- Navigational clarity for Executive Branch structure
- Constitutional accuracy (Article II reference)
- Educational UX teaching government organization
- Replaces flat list with structured hierarchy

**Deliverables:**
- Executive Branch sidebar expansion with 6 sub-sections
- Executive Branch hub page with Article II description + navigation cards
- 6 new pages: President, VP, EOP, Cabinet, Independent Agencies, Corporations
- Backend enum extension for GOVERNMENT_CORPORATION type (V29 migration)
- Breadcrumb updates for new hierarchy
- 54 new tests (38 subsections + 16 hub page)
- 623 frontend tests passing, 590 backend tests passing

**Stories (4 total, 12 points - all complete):**
- UI-6.0: Executive Organization Classification Updates (1 pt) ✅
- UI-6.1: Expand Executive Branch Sidebar Navigation (2 pts) ✅
- UI-6.2: Redesign Executive Branch Landing Page (3 pts) ✅
- UI-6.3: Create Executive Branch Sub-Section Pages (6 pts) ✅

**Quality Gates:** All 4 stories passed development.

---

## Architecture Track

### Epic ARCH-1: Individual Table Refactor

**Status:** COMPLETE ✅
**Completed:** 2026-03-14
**Priority:** HIGH (Foundational)
**Estimate:** 36 story points (9 stories)
**Blocks:** KB-2 (now unblocked)

#### Overview

Refactor the data model to establish a clean separation between **universal individual data** (name, birth date, biographical info) and **role-specific data** (Congressional membership, judicial appointments, etc.). This creates a true "single point of reference" for any person in the system, regardless of their roles.

**Current Problem:** The `persons` table serves dual purposes - both as general person storage and Congress-specific member tracking. This causes:
- Congress-specific fields (bioguide_id, chamber, state) are nullable noise for non-Congress persons
- Default dataSource is CONGRESS_GOV even for presidents
- No clean extension point for future person types (CEOs, journalists, etc.)

**Target Architecture:**
```
individuals (master table)
    ├── congressional_members (Congress-specific, FK to individuals)
    ├── presidencies (FK to individuals)
    └── position_holdings (FK to individuals)
```

#### Stories

| ID | Story | Est | Priority |
|----|-------|-----|----------|
| ARCH-1.1 | Create Individual Entity and Table | 3 pts | P0 |
| ARCH-1.2 | Create CongressionalMember Entity | 4 pts | P0 |
| ARCH-1.3 | Migrate Existing Data | 7 pts | P0 |
| ARCH-1.4 | Update Presidency/PositionHolding References | 5 pts | P0 |
| ARCH-1.5 | Update CommitteeMembership Reference | 2 pts | P1 |
| ARCH-1.6 | Update Services Layer | 5 pts | P1 |
| ARCH-1.7 | Update DTOs and Controllers | 4 pts | P1 |
| ARCH-1.8 | Update Frontend Types | 3 pts | P1 |
| ARCH-1.9 | Verification and Cleanup | 2 pts | P2 |

**Epic Document:** `docs/stories/ARCH-1/ARCH-1.epic-individual-table-refactor.md`

---

## Knowledge Base Track

### Epic KB-1: President of the United States Data

**Status:** COMPLETE ✅
**Completed:** 2026-01-07

See Factbase Expansion Track for details.

### Epic KB-2: Presidential Administrations

**Status:** COMPLETE ✅
**Completed:** 2026-03-15
**Priority:** HIGH
**Estimate:** 24 story points (7 stories)
**Depends On:** ARCH-1 (completed 2026-03-14)

#### Overview

Consolidate the separate President and Vice President pages into a unified **Presidential Administrations** experience. The new KB page provides administration-centric navigation (President, VP, staff, Executive Orders) with the ability to explore any historical administration.

**Key Features:**
- **KB Page:** Current administration at top (President, VP, Staff, EOs), historical administrations below (clickable to view details)
- **Admin Page:** Unified sync and CRUD controls for presidential data

#### Stories

| ID | Story | Est | Priority |
|----|-------|-----|----------|
| KB-2.1 | Create KB Presidential Administrations Page Shell | 3 pts | P0 |
| KB-2.2 | Implement Current Administration Section | 5 pts | P0 |
| KB-2.3 | Implement Historical Administrations List | 4 pts | P0 |
| KB-2.4 | Create Admin Presidential Administrations Page | 5 pts | P1 |
| KB-2.5 | Implement Admin CRUD API Endpoints | 4 pts | P1 |
| KB-2.6 | Navigation Updates & Route Redirects | 2 pts | P1 |
| KB-2.7 | Cleanup Deprecated Pages | 1 pt | P2 |

**Epic Document:** `docs/stories/KB-2/KB-2.epic-presidential-administrations.md`

---

## Future Vision

### Long-Term Goals (Year 2-3)

Based on business requirements document objectives:

#### Platform Maturity
- 10,000+ news sources in database
- Advanced ML models for analysis
- Mobile native applications

#### Ecosystem Development
- Plugin/extension architecture
- Third-party integrations
- API marketplace

#### Community Growth
- 100,000+ monthly active users
- 500+ GitHub contributors
- 50+ institutional partnerships

#### Research Platform
- Academic API access
- Bulk data export
- Research paper publications

### Technical Debt to Address

> **Status:** All test infrastructure debt resolved via [QA-2 Epic](stories/QA-2/QA-2.epic-test-infrastructure.md) ✅

- [x] ~~Resolve H2/PostgreSQL JSONB incompatibility in tests~~ → Fixed with Testcontainers (QA-2.1)
- [x] Add Testcontainers for repository tests → 39+ tests using PostgreSQL container
- [x] Implement frontend testing → 166 tests with Vitest + Testing Library
- [x] Add CI/CD pipeline documentation → `.github/workflows/README.md`
- [x] Production deployment guide → `docs/deployment/DEPLOYMENT_GUIDE.md`

---

## Architecture Overview

### Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Frontend** | Next.js 14 + TypeScript | User interface |
| **Backend** | Spring Boot 3.2 + Java 17 | REST API, business logic |
| **Reasoning** | FastAPI + Python 3.11 | NLP, OWL reasoning |
| **Database** | PostgreSQL 14 + JSONB | Entity storage |
| **ORM/Migrations** | JPA + Flyway | Database management |

### Service Ports

| Service | Port | URL |
|---------|------|-----|
| Frontend | 3000 | http://localhost:3000 |
| Java Backend | 8080 | http://localhost:8080 |
| Python Reasoning | 8000 | http://localhost:8000 |
| PostgreSQL | 5432 | localhost:5432 |

### Key Design Decisions

1. **Unified Entity Model:** All entity types in one table, NOT government-first
2. **Dual-Layer Design:** `entity_type` (internal) + `schema_org_type` (semantic web)
3. **JSONB Storage:** Flexible properties, no rigid schema
4. **Master Data Management:** Authoritative government_organizations linked to transient entities
5. **HTTP APIs:** Services communicate via REST, NOT subprocesses

**Reference:** `docs/architecture/entity-vs-government-org-design.md`

---

## Documentation Index

### Architecture Documents

| Document | Location | Description |
|----------|----------|-------------|
| Architecture Overview | `docs/architecture/architecture.md` | General architecture |
| Entity vs GovOrg Design | `docs/architecture/entity-vs-government-org-design.md` | Two-table pattern explanation |
| Architectural Assessment | `docs/architecture/ARCHITECTURAL_ASSESSMENT_2025-11-23.md` | Health check (8.5/10) |
| Schema.org Integration | `docs/schema-org-owl-integration.md` | **Foundation document** - Schema.org + OWL strategy |

### Phase Implementation Plans

| Phase | Document | Status |
|-------|----------|--------|
| 2 | `docs/stories/PHASE_2/PHASE_2_IMPLEMENTATION_PLAN.md` | Ready for Implementation |

### Factbase Expansion Epics

| Epic | Document | Status |
|------|----------|--------|
| FB-1 | `docs/stories/FB-1/FB-1.epic-congressional-data.md` | ✅ Complete |
| FB-1-UI | `docs/stories/FB-1-UI/FB-1-UI.epic-frontend-congressional.md` | ✅ Complete |
| FB-2-GOV | `docs/stories/FB-2-GOV/FB-2-GOV.epic-government-org-sync.md` | ✅ Complete |
| FB-2 | `docs/stories/FB-2/FB-2.epic-executive-branch-data.md` | ✅ Complete |
| FB-3 | `docs/stories/FB-3/FB-3.epic-regulatory-data.md` | ✅ Complete |
| ADMIN-1 | `docs/stories/ADMIN-1/ADMIN-1.epic-admin-dashboard-improvements.md` | ✅ Complete |

### Quality Assurance Epics

| Epic | Document | Status |
|------|----------|--------|
| QA-1 | `docs/stories/QA-1/QA-1.epic-api-testing-framework.md` | ✅ Complete |
| QA-2 | `docs/stories/QA-2/QA-2.epic-test-infrastructure.md` | ✅ Complete |

### User Experience Epics

| Epic | Document | Status |
|------|----------|--------|
| UI-1 | `docs/stories/UI-1/UI-1.epic-public-navigation-ux.md` | ✅ Complete |
| UI-2 | `docs/stories/UI-2/UI-2.epic-knowledge-explorer.md` | ✅ Complete (Superseded) |
| UI-3 | `docs/stories/UI-3/UI-3.epic-frontend-realignment.md` | ✅ Complete |
| UI-4 | `docs/stories/UI-4/UI-4.epic-sidebar-integration.md` | ✅ Complete |
| UI-5 | `docs/stories/UI-5/UI-5.epic-kb-sidebar-uscode.md` | ✅ Complete |
| UI-6 | `docs/stories/UI-6/UI-6.epic-executive-branch-hierarchy.md` | ✅ Complete |

### Architecture Epics

| Epic | Document | Status |
|------|----------|--------|
| ARCH-1 | `docs/stories/ARCH-1/ARCH-1.epic-individual-table-refactor.md` | ✅ Complete |

### Knowledge Base Epics

| Epic | Document | Status |
|------|----------|--------|
| KB-1 | `docs/stories/KB-1/KB-1.epic-potus-data.md` | ✅ Complete |
| KB-2 | `docs/stories/KB-2/KB-2.epic-presidential-administrations.md` | ✅ Complete |

### Phase Completion Summaries

| Phase | Document | Status |
|-------|----------|--------|
| 1.2 | `docs/workSummaries/PHASE_1.2_COMPLETE.md` | Complete |
| 1.3 | `docs/workSummaries/PHASE_1.3_COMPLETE.md` | Complete |
| 1.4 | `docs/workSummaries/PHASE_1.4_COMPLETE.md` | Complete |
| 1.5 | `docs/workSummaries/PHASE_1.5_COMPLETE.md` | Complete |
| 1.6 | `docs/workSummaries/PHASE_1.6_COMPLETE.md` | Complete |
| 3 | `docs/workSummaries/PHASE_3_OWL_REASONING.md` | Complete |

### Deployment & Setup

| Document | Location | Description |
|----------|----------|-------------|
| Deployment Guide | `docs/deployment/DEPLOYMENT_GUIDE.md` | Full setup instructions |
| Quick Start | `docs/deployment/QUICKSTART.md` | Fast setup |
| Python 3.11 Setup | `docs/deployment/PYTHON_311_SETUP.md` | Python requirements |

### Analysis & Learning

| Document | Location | Description |
|----------|----------|-------------|
| V1 Analysis | `docs/analysis/newsanalyzer-brownfield-analysis.md` | What went wrong in v1 |
| Business Requirements | `docs/previousProject/business-requirements.md` | Full business vision |

### Reference Materials

| Document | Location | Description |
|----------|----------|-------------|
| GovInfo API Findings | `docs/reference/APIs and Other Sources/GOVINFO_API_FINDINGS.md` | API research |
| Congress.gov Glossary | `docs/reference/APIs and Other Sources/congress.gov/Glossary of Legislative Terms.md` | Legislative terminology |

---

## Change Log

| Date | Version | Changes |
|------|---------|---------|
| 2025-11-25 | 1.0 | Initial roadmap consolidation |
| 2025-11-30 | 2.0 | Added Factbase Expansion & QA tracks; Renamed Phase 2 to Semantic Web Enrichment; Updated status dashboard with actual progress |
| 2025-12-01 | 2.1 | Marked FB-1-UI as COMPLETE (all 5 stories done); Updated MVP to ~80% |
| 2025-12-01 | 2.2 | Clarified technical debt status; Added QA-2 epic reference; Moved FB-1-UI files to subfolder |
| 2025-12-01 | 2.3 | FB-2 Architect Review complete: CSV import approach approved, Branch enum added to data model, all 4 stories ready for development |
| 2025-12-01 | 2.4 | FB-3 story files created (4 stories), epic moved to FB-3 folder, submitted for Architect review |
| 2025-12-01 | 2.5 | **FB-2 Epic COMPLETE**: All 4 stories implemented - PLUM CSV import, executive position model, admin sync UI, appointee lookup API (11 endpoints); Architect review approved (5/5); MVP updated to ~90% |
| 2025-12-01 | 2.6 | **FB-3 Architect Review Complete**: Approved with modifications (migration versions V17-V19, added missing repo methods); Ready for development |
| 2025-12-02 | 2.7 | **FB-3 Epic COMPLETE**: All 4 stories done - Federal Register API integration, Regulation data model & storage, Agency linkage service (5-level matching, >95% match rate), Regulation lookup API (8 endpoints); All QA gates passed (scores 95-100); MVP updated to ~95% |
| 2025-12-12 | 2.8 | **ADMIN-1 Epic COMPLETE**: All 13 stories done - Admin dashboard redesign, GOVMAN/US Code import, API search/import UI (Congress.gov, Federal Register, Legislators repo); Supersedes FB-4, FB-5, FB-6 draft epics; **MVP 100% COMPLETE** |
| 2025-12-17 | 2.9 | Added **User Experience Track** with **UI-1 Epic** (Public Navigation & User Experience) - 12 stories, 47 points, ready for development |
| 2025-12-26 | 3.0 | **UI-1 Epic COMPLETE**: All 12 stories done - Public sidebar, factbase pages, federal judges import; Added **UI-2 Epic** (Knowledge Explorer UI Refactoring) - 8 stories, replaces bespoke UI-1 pages with reusable pattern components |
| 2025-12-29 | 3.1 | **UI-2 Epic COMPLETE**: All 8 stories done - Knowledge Explorer shell, EntityBrowser/EntityDetail/HierarchyView patterns, cross-entity search, migration of Gov Orgs and People (judges/members/appointees), cleanup & documentation; Documentation sync performed to reflect completed status |
| 2025-12-30 | 3.2 | **QA-2 Epic COMPLETE**: All stories done - 105 new frontend tests (166 total), CI coverage thresholds (JaCoCo 70%, Vitest 30%), CI/CD documentation README; All technical debt resolved; Quality Assurance Track 100% complete |
| 2025-12-30 | 3.3 | **UI-3 Epic PLANNED**: Frontend Architecture Realignment - triggered by architecture v2.5 review clarifying dual-layer data model; UI-2 marked as superseded (patterns preserved); 9 stories planned (23 pts) in 2 phases |
| 2025-12-31 | 3.4 | **UI-3 Phase A Progress**: UI-3.A.1 (EntityBrowser KB config) and UI-3.A.2 (Hierarchical KB Navigation) complete - KB landing page, government branch navigation, breadcrumbs with 43 tests; UI-3 now 22% complete (2/9 stories) |
| 2025-12-31 | 3.5 | **UI-3 Phase A Near Complete**: UI-3.A.3 (merged into A.1) and UI-3.A.4 (Route Redirects) complete - factbase redirects updated for hierarchical routes, 17 redirect tests added; UI-3 now 52% complete (4/9 stories, Phase A: 4/5) |
| 2025-12-31 | 3.6 | **UI-3 Phase A COMPLETE**: UI-3.A.5 (Phase A Documentation) complete - epic updated, architecture verified, route docs verified, component JSDoc verified; Phase A: 6 stories done (15 pts), 106 new tests; UI-3 now 67% complete (6/10 stories) |
| 2026-01-01 | 3.7 | **UI-4 Epic APPROVED**: Public Sidebar Integration - addresses gap where PublicSidebar component was built but never integrated into KB/AA layouts; 6 stories (13 pts); Architect review added UI-4.0 (shared SidebarLayout component) to reduce duplication |
| 2026-01-02 | 3.8 | **UI-4 Epic COMPLETE**: All 6 stories done - Shared SidebarLayout component, KB/AA sidebar integration, menu config updates, 39 mobile responsiveness tests, deprecated component cleanup (666 lines removed); User Experience Track 100% complete; 544 frontend tests passing |
| 2026-01-03 | 3.9 | **UI-5 Epic COMPLETE**: All 2 stories done - KB sidebar reorganization ("Government" → "U.S. Federal Government", "Branches" grouping), public U.S. Code browse page with hierarchical tree view; 19 new tests; 585 frontend tests passing |
| 2026-01-04 | 4.0 | **UI-6 Epic APPROVED**: Executive Branch Hierarchical Navigation - 4 stories (12 pts) adding 6 sub-sections (President, VP, EOP, Cabinet, Independent Agencies, Corporations); includes backend enum extension for GOVERNMENT_CORPORATION; User Experience Track status updated to IN PROGRESS |
| 2026-01-05 | 4.1 | **UI-6 Epic COMPLETE**: All 4 stories done - Backend GOVERNMENT_CORPORATION enum (V29 migration), sidebar expansion (6 sub-sections with icons), Executive Branch hub page (Article II reference, 6 nav cards), 6 sub-section pages (President, VP, EOP, Cabinet, Independent Agencies, Corporations); 54 new tests; 623 frontend tests passing, 590 backend tests passing |
| 2026-01-07 | 4.2 | **KB-1 Epic COMPLETE**: All 6 stories done - Presidency and ExecutiveOrder entities (V30-V33 migrations), Person/term separation for non-consecutive terms (Cleveland 22/24, Trump 45/47), Presidential data sync from seed file (47 presidencies), Executive Orders sync from Federal Register API, Admin sync UI, KB President page with historical table; 18 backend tests for EO sync, 49 frontend tests for KB page |
| 2026-01-08 | 4.3 | **New Tracks Added**: Architecture Track (ARCH-1) and Knowledge Base Track (KB-1, KB-2). **ARCH-1 Epic APPROVED**: Individual Table Refactor (36 pts, 9 stories) - separates universal person data from role-specific data (Person → Individual + CongressionalMember); approved by Winston with 7 modifications. **KB-2 Epic DRAFTED**: Presidential Administrations (24 pts, 7 stories) - consolidates President/VP pages into unified administrations view; blocked by ARCH-1. KB-1 moved from Factbase Expansion to Knowledge Base Track. |
| 2026-03-14 | 4.4 | **ARCH-1 Epic COMPLETE**: All 9 stories done - Individual table refactor (Person→Individual, person_id→individual_id), CongressionalMember two-entity pattern, Chamber enum extraction, full migration (V34-V42), 765 backend tests passing. **KB-2 Unblocked**: Updated epic for ARCH-1 data model changes (Person→Individual, PersonUpdateDTO→IndividualUpdateDTO). Documentation Index updated. |
| 2026-03-15 | 4.5 | **KB-2 Epic COMPLETE**: All 7 stories done — unified Presidential Administrations page (KB page + Admin page), Current/Historical administration views with URL state management, Admin CRUD API endpoints (Spring Boot), navigation consolidation (Landmark icon), 4 route redirects, deprecated component cleanup. 25 backend tests, 729 frontend tests (754 total). All 7 QA gates PASS (avg score 96). 4 low-severity items tracked for future improvement. Knowledge Base Track 100% complete. |

---

## Approval

| Role | Name | Date |
|------|------|------|
| Product Owner | Sarah (PO Agent) | 2025-11-25 |
| Architect | Winston (SA Agent) | TBD |
| Technical Lead | TBD | TBD |

---

*End of Roadmap Document*
