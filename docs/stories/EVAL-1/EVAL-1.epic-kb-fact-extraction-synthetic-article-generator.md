# Epic EVAL-1: KB Fact Extraction & Synthetic Article Generator

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | EVAL-1 |
| **Epic Name** | KB Fact Extraction & Synthetic Article Generator |
| **Track** | AI Evaluation |
| **Epic Type** | New Feature — Reasoning Service Enhancement |
| **Priority** | HIGH |
| **Status** | DONE |
| **Created** | 2026-03-15 |
| **Owner** | Sarah (PO) |
| **Depends On** | KB-2 Complete (Presidential Administrations), ARCH-1 Complete (Individual table refactor) |
| **Blocked By** | None — all prerequisites complete |
| **Triggered By** | Strategic initiative to build AI evaluation capabilities aligned with AI Evaluation Engineer role requirements |

## Executive Summary

Build an automated pipeline in the reasoning-service (Python/FastAPI) that extracts structured facts from the Knowledge Base across all three branches of the federal government, and uses those facts to generate realistic synthetic news articles with controlled factual perturbations — producing labeled test datasets for downstream LLM evaluation.

This is the first of three epics in the **AI Evaluation Track**:

| Order | Epic | Status | Delivers |
|-------|------|--------|----------|
| **EVAL-1** | KB Fact Extraction & Synthetic Article Generator | **DONE** | Structured facts + labeled synthetic articles |
| **EVAL-2** | RAG Analysis Pipeline | PLANNED | Vector embeddings, context retrieval, LLM analysis |
| **EVAL-3** | LLM Evaluation Framework | PLANNED | Evaluation scoring, benchmarks, metrics dashboard |

### Motivation

AI Evaluation Engineer roles require demonstrated experience with:
- Synthetic dataset generation and adversarial test case design
- Python AI/ML engineering (not just scripting)
- LLM API integration (Anthropic SDK)
- Data pipeline automation
- Ground-truth labeling and benchmark creation

This epic directly builds those skills while producing the foundational data layer that EVAL-2 and EVAL-3 depend on.

## Business Value

1. **Portfolio value** — Demonstrates synthetic dataset engineering, a top skill in 7 of 9 target AI Evaluation job descriptions
2. **Foundation for evaluation** — EVAL-2 (RAG) and EVAL-3 (Evaluation) cannot function without test articles and ground-truth labels
3. **Leverages existing KB investment** — All the government data loaded across FB-1 through FB-3, KB-1, and KB-2 becomes input for AI evaluation work
4. **Python depth** — Transitions reasoning-service from thin spaCy wrapper to substantive AI engineering codebase

## Existing System Context

### What We Have (Relevant KB Data)

| Branch | Entity Types | Data Sources | Coverage |
|--------|-------------|--------------|----------|
| **Legislative** | Congressional Members, Committees, Committee Memberships, Statutes | Congress.gov API, unitedstates/congress-legislators | Current + historical members, all committees |
| **Executive** | Presidencies, Executive Orders, Cabinet, Government Organizations, Regulations, Appointees | Federal Register API, Static Seed JSON, OPM PLUM | All 47 presidencies, current agencies, appointees |
| **Judicial** | Judges, Courts | Static data | Federal judges |

### Integration Points

- **Reads from:** Backend REST APIs (existing, no changes needed)
- **Writes to:** PostgreSQL via new backend endpoints (new tables for generated articles + labels)
- **Uses:** Claude API (Anthropic SDK) for article generation
- **No impact on:** Existing KB pages, admin pages, sync pipelines, or frontend

## Stories

### EVAL-1.1: KB Fact Extractor Module

**Goal:** Build a Python module in the reasoning-service that queries backend APIs for entities across all three government branches and produces structured Fact tuples.

**Scope:**
- Pydantic models: `Fact(subject, predicate, object, entity_type, branch, data_source, confidence)`
- Async API client for backend REST endpoints
- Fact extraction logic per entity type:
  - **Legislative:** Member name, party, state, district, chamber, committees, leadership roles
  - **Executive:** President name, term number, party, VP, cabinet members, executive orders, agencies
  - **Judicial:** Judge name, court, appointing president, confirmation date
- Fact set builder: given a topic/entity, assemble a coherent set of related facts
- pytest coverage for all extractors

**Status: Done** (implemented 2026-03-16, QA PASS 2026-03-17)

**Acceptance Criteria:**
- [x] Fact extractor produces valid Fact tuples for at least 3 entity types per branch
- [x] Facts include source attribution (data_source field)
- [x] Fact sets can be assembled by entity (e.g., all facts about a specific senator)
- [x] Fact sets can be assembled by topic (e.g., "Banking Committee membership")
- [x] Async API client handles pagination and error cases
- [x] pytest tests verify extraction logic against known KB data
- [x] FastAPI endpoint exposes fact extraction for manual inspection

### EVAL-1.2: Synthetic Article Generator with Perturbation Engine

**Goal:** Build a Python module that takes Fact sets, generates realistic news articles via the Claude API, and applies controlled perturbations to produce labeled test datasets.

**Scope:**
- Prompt engineering module: constructs generation prompts from Fact sets
- Article types: news report, breaking news, opinion/editorial, analysis piece
- Perturbation engine with 6 perturbation types:
  - `WRONG_PARTY` — Swap party affiliation (D/R)
  - `WRONG_COMMITTEE` — Assign to incorrect committee
  - `WRONG_STATE` — Incorrect state or district
  - `HALLUCINATE_ROLE` — Invent a role or history not in KB
  - `OUTDATED_INFO` — Use superseded information (e.g., former title)
  - `CONFLATE_INDIVIDUALS` — Mix attributes of two different people
- For each article, generate: faithful version + N perturbed variants
- Ground-truth label structure: `ArticleTestCase(article_text, source_facts, perturbations_applied, expected_findings, difficulty)`
- Batch generation: configurable batch size, async execution, rate limiting
- Dry-run mode: outputs prompts without calling Claude API (cost control)
- Anthropic SDK integration with structured output parsing

**Acceptance Criteria:**
- [x] Generates realistic articles that read like actual news reporting
- [x] All 6 perturbation types produce articles with exactly one controlled error
- [x] Each article paired with complete ground-truth label
- [x] Batch generation runs unattended with configurable parameters
- [x] Dry-run mode works without API calls
- [x] Rate limiting prevents API throttling
- [x] Article type (news, opinion, etc.) affects writing style but preserves factual content
- [x] pytest tests verify perturbation logic (unit tests, no API calls needed)

### EVAL-1.3: Test Dataset Storage & Management

**Goal:** Add database tables and backend API endpoints for storing and retrieving generated test articles with their ground-truth labels.

**Scope:**
- New Flyway migrations for:
  - `synthetic_articles` table (article text, type, generation metadata)
  - `article_ground_truth` table (source facts, perturbations, expected findings)
  - `generation_batches` table (batch parameters, date, statistics)
- Backend (Java/Spring Boot) additions:
  - Entity classes, repositories, DTOs
  - REST endpoints for CRUD operations on test datasets
  - Query endpoints: filter by perturbation type, government branch, difficulty level, batch ID
- Reasoning-service integration: after batch generation, POST results to backend for storage

**Acceptance Criteria:**
- [x] Generated articles persist across service restarts
- [x] Ground-truth labels fully stored with FK to source article
- [x] Batch metadata tracks generation parameters and statistics
- [x] Query API supports filtering by perturbation type, branch, and difficulty
- [x] Existing database schema unaffected (new tables only)
- [x] JUnit tests for new repository and service classes
- [x] API integration tested (REST Assured or equivalent)

## Compatibility Requirements

- [x] Existing APIs remain unchanged — Fact Extractor is read-only against existing endpoints
- [x] Database schema changes are backward compatible — new tables only, no modifications
- [x] UI changes follow existing patterns — No UI changes in this epic
- [x] Performance impact is minimal — Batch generation is async, doesn't affect serving path

## Risk Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Claude API costs from batch generation | Medium | Medium | Configurable batch sizes, dry-run mode, rate limiting, cost tracking per batch |
| Generated articles not realistic enough | Low | Medium | Iterative prompt engineering; article quality review step before storing |
| KB data gaps produce thin fact sets | Low | Low | Fact set validation: minimum fact count before generation proceeds |
| Backend API changes break fact extractor | Low | Medium | Version-aware API client; integration tests catch breaking changes |

**Rollback Plan:** All additions are new tables and new modules. Drop tables, remove Python modules, remove Java classes — zero impact on existing system.

## Technical Notes

### Python Dependencies (reasoning-service)
- `anthropic` — Claude API SDK
- `pydantic` — Already in use, for Fact and ArticleTestCase models
- `httpx` — Async HTTP client for backend API calls (already in use)
- `pytest-asyncio` — For async test coverage

### No New Infrastructure Required
- PostgreSQL: existing instance, new tables only
- No new services or containers
- No vector store needed yet (that's EVAL-2)

## Definition of Done

- [x] Fact extraction covers all 3 government branches with structured output _(EVAL-1.1)_
- [x] Article generator produces faithful and perturbed variants _(EVAL-1.2: 4 article types, 6 perturbation types)_
- [x] All 6 perturbation types implemented and tested _(EVAL-1.2: 74 tests, QA PASS 2026-03-17)_
- [x] Batch generation runs unattended with configurable parameters _(EVAL-1.2: BatchOrchestrator + dry-run mode)_
- [x] Generated datasets stored with full ground-truth labels _(EVAL-1.3: synthetic_articles + generation_batches tables)_
- [x] Query API supports filtering by multiple dimensions _(EVAL-1.3: 8 REST endpoints with perturbation/branch/difficulty/batch filters)_
- [x] Existing functionality verified — no regressions _(EVAL-1.1: 254 passed → EVAL-1.3: 673 backend + 335 reasoning-service)_
- [x] pytest coverage for all new Python modules _(EVAL-1.1: 93 tests + EVAL-1.2: 74 tests = 167 new pytest tests)_
- [x] JUnit coverage for all new Java classes _(EVAL-1.3: 26 JUnit tests — 13 service + 13 controller, QA PASS 2026-03-19)_
- [x] No new infrastructure dependencies introduced _(all epics — PostgreSQL existing, no new containers)_
