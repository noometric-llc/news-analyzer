# Epic ES-1: Evidence Store Foundation

## Epic Overview

| Field | Value |
|-------|-------|
| **Epic ID** | ES-1 |
| **Epic Name** | Evidence Store Foundation |
| **Epic Type** | Infrastructure / Data Platform |
| **Priority** | TBD — not yet formally prioritized against other tracks |
| **Status** | Draft |
| **Created** | 2026-07-02 |
| **Owner** | Sarah (PO) |

## Executive Summary

Establish a persisted, source-attributed Evidence Store in NewsAnalyzer — article storage, entity-to-article linkage, and real bias/fallacy signal from the existing (but previously production-unused) reasoning-service `/eval/bias/detect` endpoint. This is the technical foundation for the political-analysis-agent vision explored in `docs/analysis/ES-1-brainstorming-session-results.md`: nothing in NewsAnalyzer today can ground a claim in "here's what was reported, by whom, and how reliable that source is," because article text is currently processed and discarded rather than persisted.

## Business Value

### Why This Epic Matters

1. **Unblocks the agent vision** — every downstream trust feature (audit trail, reliability scoring, the "challenge" dialogue) from the originating brainstorm assumes persisted, scored evidence exists. Nothing else can be built until this does.
2. **Upgrades a placeholder into real signal** — research during PRD drafting found `/eval/bias/detect` already exists as a stable, documented contract endpoint, unused in production. This epic makes NewsAnalyzer its first production caller, rather than shipping a null placeholder as originally scoped in the project brief.
3. **Closes a real testing gap** — the entity-extraction pipeline has only ever been validated against synthetic data (`SyntheticArticle`); this epic enables validation against real ingested content.
4. **Establishes a reusable pattern** — `ReasoningServiceClient` becomes the first Java-side reasoning-service caller and the reference implementation for any future one.
5. **Strategic optionality** — per `docs/analysis/ES-1-project-brief.md`, this may also become the technical foundation for NewsAnalyzer as a public case study for Noometric's LLM persona-profiling business direction, though that business decision is explicitly out of this epic's scope (see `docs/analysis/ES-1-project-brief.md` Priority #3).

### Success Metrics

*(Carried from `docs/analysis/ES-1-project-brief.md`'s KPIs — these predate the PRD and remain the epic's north star.)*

| Metric | Target | Measurement |
|--------|--------|-------------|
| Ingestion success rate | 100% for well-formed input | Article persistence success/failure tracking |
| Source-linkage coverage | 100% of newly extracted entities | % of `Entity` records with valid `article_id` |
| Reliability score field coverage | 100% (placeholder-null acceptable) | Every source has *a* score field populated, never absent |
| Zero silent blending | 100% | Grounded-query responses always cite evidence or explicitly say none was found |

## Scope

*(Carried from `docs/analysis/ES-1-project-brief.md`'s MVP Scope, refined during PRD/architecture drafting.)*

### In Scope

- Persisted `Article` model and schema (source, url, publicationDate, rawText, ingestion timestamps)
- API-driven ingestion (manual/API submission — no crawler or scheduled feed)
- Entity-to-article linkage via a real foreign key, replacing the freeform `source` string for newly extracted entities
- Real bias/fallacy annotation via `/eval/bias/detect`, stored per-article (raw signal, not aggregated)
- Grounded-query interface with zero silent blending between evidence-backed and ungrounded content
- Eval harness validation against real ingested articles, alongside the existing synthetic-data path

### Out of Scope

- Reliability-scoring *methodology* (belongs in `noometric-intelligence`, per the CLAUDE.md IP boundary)
- Cross-article/cross-source reliability aggregation (deferred — sampling-representativeness and correlated-bias risk, per the originating brainstorm)
- Any conversational agent, chat interface, or "challenge" dialogue
- Automated large-scale ingestion (RSS feeds, scrapers)
- Any citizen-facing UI
- Circuit breaker / automated retry-triggering for failed extraction or bias-detection (deferred to Phase 2, per architecture review)

## Architecture

### Technology Stack

*(No new technology — see `docs/architecture/ES-1-ARCHITECT-HANDOFF.md` Tech Stack section for full rationale.)*

| Component | Technology | Notes |
|-----------|------------|-------|
| Backend | Java 17 / Spring Boot 3.2.2 | Existing stack, no version change |
| HTTP Client | `RestTemplate` via `RestTemplateBuilder` | New `ReasoningServiceClient` — first Java-side reasoning-service caller, mirrors the verified `CongressApiClient` pattern |
| Database | PostgreSQL 15 + Flyway | Two new tables (`evidence_articles`, `article_bias_annotations`), one additive FK on `entities` |
| Reasoning Service | `noometric-intelligence` (private) | Consumes existing `/entities/extract` and `/eval/bias/detect` — no contract changes |

### Project Structure

*(See `docs/architecture/ES-1-ARCHITECT-HANDOFF.md` Source Tree for full detail — no new top-level packages, everything lands in the existing `model/`, `repository/`, `service/`, `controller/`, `dto/` structure.)*

## Stories

### Story Summary

| ID | Story | Status |
|----|-------|--------|
| ES-1.1 | [Article Persistence Model](ES-1.1.article-persistence-model.md) | Ready for Done |
| ES-1.2 | [Article Ingestion API (Persistence Only)](ES-1.2.article-ingestion-api.md) | Ready for Done |
| ES-1.3 | [Entity Extraction Integration](ES-1.3.entity-extraction-integration.md) | Ready for Done |
| ES-1.4 | [Bias/Fallacy Annotation Integration](ES-1.4.bias-fallacy-annotation-integration.md) | Ready for Done ⚠ (pre-merge blocker open — see story) |
| ES-1.5 | Grounded-Query Interface | Not yet drafted |
| ES-1.6 | [Eval Harness Real-Article Integration](ES-1.6.eval-harness-real-article-integration.md) | Ready for Done |

*(Full acceptance criteria for ES-1.2 through ES-1.6 are defined in `docs/prd/ES-1.md`; they will be drafted as individual story files following the ES-1.1 pattern before implementation.)*

### Dependency Graph

```
ES-1.1 (Schema)
    │
    ▼
ES-1.2 (Ingestion API)
    │
    ├──────────────┐
    ▼              ▼
ES-1.3         ES-1.4
(Extraction)   (Bias Detection)
    │              │
    └──────┬───────┘
           │
           ├───────────────┐
           ▼                ▼
        ES-1.5           ES-1.6
     (Grounded Query)  (Eval Harness Integration)
```

ES-1.3 and ES-1.4 are logically independent of each other (both depend only on ES-1.2) but are sequenced serially in the PRD specifically so bias-detection's failure mode — the first production use of an LLM-backed, previously-unused endpoint — can't take down extraction if something goes wrong.

**Sequencing deviation (2026-07-07):** ES-1.6 was drafted and sequenced as a sibling of ES-1.5 rather than depending on it, per an explicit product decision. ES-1.6's actual PRD acceptance criteria only require reading persisted `Article`/`Entity`/`ArticleBiasAnnotation` data (all complete as of ES-1.4) — none of them require ES-1.5's grounded-query endpoint or its NFR4 "no silent blending" semantics. The original diagram's arrow reflected an assumed build order, not a hard technical dependency. See `ES-1.6.eval-harness-real-article-integration.md`'s Sequencing Note for the full rationale.

## Acceptance Criteria (Epic Level)

1. **Schema Foundation:** `evidence_articles` and `article_bias_annotations` tables exist; `entities.article_id` is a nullable, additive FK — verified via Flyway migration review and IV1-style regression tests.
2. **Ingestion Pipeline:** An article submitted via API is persisted, has entities extracted and linked, and has bias annotations attached — end-to-end, verified by at least one real (non-synthetic) test article (per ES-1.6). **✓ Verified** — `EvalRealArticleIntegrationTest` (ES-1.6) proves this end-to-end against real Postgres.
3. **Zero Silent Blending:** The grounded-query interface always distinguishes evidence-backed results from "no grounded evidence found" — never an ambiguous response.
4. **Zero Regression:** The full existing `mvn test` suite passes unchanged; `/api/entities` and `/api/government-orgs` behavior is unaffected.
5. **Contract Documentation Updated:** `docs/api/reasoning-service-contract.md` reflects NewsAnalyzer's backend as a new production caller of `/eval/bias/detect`.

## Risks & Mitigations

*(Carried from `docs/architecture/ES-1-ARCHITECT-HANDOFF.md`'s Checklist Results Report — top risks, resolved status noted.)*

| Risk | Impact | Status | Mitigation |
|------|--------|--------|------------|
| `ArticleService` transaction boundary holding a DB connection across ~90s of external HTTP calls | High | **Resolved** | Explicit guidance: persistence steps and external calls must not share a `@Transactional` method (architecture doc, Component Architecture) |
| Unauthenticated, LLM-cost-bearing `/api/articles` endpoint | High | **Resolved** | Basic rate limiting added alongside the request-size cap (architecture doc, Security Integration) |
| No retry-triggering mechanism despite "retriable" being asserted | Medium | Open, deferred to Phase 2 | Explicitly scoped out rather than silently assumed handled |
| No circuit breaker for reasoning-service calls | Medium | Open, deferred to Phase 2 | Tolerable at MVP's manual/low-volume scope |
| `/eval/bias/detect` rate limits/quotas unconfirmed | Medium | Open — pre-merge blocker | ES-1.4 implementation proceeded on an explicit, documented PO risk acceptance (2026-07-06) rather than a confirmed answer. Must still be confirmed with `noometric-intelligence` (or that acceptance explicitly reconfirmed) before ES-1.4 merges to `main` — see the story's own pre-merge blocker callout |
| Article full-text copyright/retention posture unresolved | Low-Medium | Open — needs legal input | Flagged for `business-attorney` per CLAUDE.md's Noometric agent table, before real (non-test) content ingestion scales |

## Definition of Done

- [ ] All 6 stories (ES-1.1–ES-1.6) drafted, implemented, and merged (ES-1.5 not yet drafted)
- [ ] Full existing test suite passes with zero regressions (true at each story's point-in-time to date — 875 Java + 84 Python as of ES-1.6; final check pending ES-1.5)
- [x] At least one real ingested article demonstrates the full pipeline end-to-end — `EvalRealArticleIntegrationTest` (ES-1.6)
- [x] `reasoning-service-contract.md` updated to reflect the new production caller — done in ES-1.4 for `/eval/bias/detect`
- [ ] Reasoning-service rate-limit/quota question resolved before ES-1.4 merges — still an explicit PO risk acceptance, not a confirmed answer
- [ ] Code reviewed and follows `coding-standards.md` conventions throughout (true for all stories completed so far; final check pending ES-1.5)

## Related Documentation

- [Project Brief](../../analysis/ES-1-project-brief.md) — original business framing and priority sequencing
- [Brainstorming Session Results](../../analysis/ES-1-brainstorming-session-results.md) — originating ideation session
- [PRD](../../prd/ES-1.md) — full functional/non-functional requirements
- [Architecture](../../architecture/ES-1-ARCHITECT-HANDOFF.md) — technical design, component architecture, checklist validation
- [Reasoning Service Contract](../../api/reasoning-service-contract.md) — the API boundary this epic consumes
- [Factbase Expansion Architect Handoff](../../architecture/FACTBASE_EXPANSION_ARCHITECT_HANDOFF.md) — the precedent this epic's schema/migration approach mirrors

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-07-03 | 1.0 | Initial epic creation, consolidating brief/PRD/architecture into the project's standard epic-overview format | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-03 | 1.1 | ES-1.1 status updated to Ready for Review; table rename reconciled (`articles` → `evidence_articles`) after Story ES-1.1 implementation discovered a pre-existing, unused table of that name from `V1__initial_schema.sql` | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-04 | 1.2 | ES-1.1 completed — QA gate PASS (quality score 100), status updated to Ready for Done. ES-1.2 drafted and linked. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-04 | 1.3 | ES-1.2 completed — QA gate PASS (quality score 95), status updated to Ready for Done. Two of six ES-1 stories now done. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-04 | 1.4 | ES-1.3 drafted and linked — first story to build ReasoningServiceClient and wire in the deferred rate-limiting requirement (AC6, carried from ES-1.2) | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-06 | 1.5 | ES-1.3 completed — QA found and Dev fixed two real CONCERNS (NOOMETRIC_API_KEY never reaching the container in any docker-compose environment; mid-batch entity extraction failures leaving orphaned entities under a FAILED status), re-reviewed to QA gate PASS (quality score 100), status updated to Ready for Done. Three of six ES-1 stories now done. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-06 | 1.6 | ES-1.4 drafted and linked. Explicit product decision: drafting/implementation may proceed despite the epic risk table's unconfirmed `/eval/bias/detect` rate-limit/quota question, but the story is blocked from merging until that's resolved — tracked as Task 1, separate from the code tasks, so it can't be silently dropped. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-06 | 1.7 | ES-1.4 validated (GO, readiness 9/10) and approved — cleared for dev agent pickup. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-07 | 1.8 | ES-1.4 completed — QA independently traced a claimed "production crash" finding to the existing broad exception handler already covering it gracefully, then closed the one real gap underneath (a missing test), re-reviewed to QA gate PASS (quality score 100), status updated to Ready for Done. Four of six ES-1 stories now done. Risks table updated: `/eval/bias/detect` rate-limit/quota question remains an explicit pre-merge blocker, not resolved by dev-complete status. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-07 | 1.9 | ES-1.6 drafted and linked out of sequence, ahead of ES-1.5 — per explicit product decision, since ES-1.6's actual ACs don't require ES-1.5's grounded-query endpoint. Dependency graph updated to show ES-1.5/ES-1.6 as siblings, not a chain. Also narrowed ES-1.6's scope from the PRD's literal "validation" wording to read-access/smoke-test only, since real articles have no curated ground truth for precision/recall scoring — that curation work is deferred, not silently dropped (tracked in the story and in `docs/evaluation-methodology.md`'s Future Work section once implemented). | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-08 | 2.0 | ES-1.6 validated (GO, readiness 10/10) and approved — cleared for dev agent pickup. | Sarah (PO) / Steve Kosuth-Wood |
| 2026-07-09 | 2.1 | ES-1.6 completed — QA gate PASS (quality score 100), status updated to Ready for Done. Five of six ES-1 stories now done (only ES-1.5 remains, not yet drafted). Two epic-level Definition of Done items checked off: real-article end-to-end pipeline demonstration, and reasoning-service-contract.md's production-caller update. | Sarah (PO) / Steve Kosuth-Wood |

## Architectural Review Summary

**Review Date:** 2026-07-02
**Reviewer:** Winston (Architect)
**Verdict:** APPROVED (architecture document), Medium-High readiness

### Strengths Identified

- Schema design directly mirrors the verified, historical `V3`/`V4` two-migration precedent from the Factbase expansion
- `ReasoningServiceClient` design verified against actual codebase behavior (no existing Java-side reasoning-service caller existed — confirmed by direct code search, not assumed) rather than the aspirational `@Retryable` pattern documented but never implemented elsewhere
- Transaction-boundary risk (long-held DB connection across external HTTP calls) identified and resolved during checklist review, not left as a latent bug
- Unauthenticated, cost-bearing endpoint risk identified and mitigated with rate limiting during the same review

### Recommendations (Non-Blocking)

1. Add a sequence diagram for the ingest→extract→bias-detect→persist flow (only a static component diagram exists currently)
2. Consider ADR entries for the two significant new decisions (Java-side reasoning-service client shape; monolith-first), matching this project's existing `docs/architecture/adr/` practice
3. Define alerting thresholds for new failure-rate metrics before wider real-world use

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | Sarah (PO) | 2026-07-03 | Drafted |
| Architect | Winston (Architect) | 2026-07-02 | Architecture APPROVED |
| PO Master Checklist | Sarah (PO) | 2026-07-02 | APPROVED (post should-fix resolution) |
| Tech Lead | _TBD_ | _Pending_ | _Pending_ |

---

*End of Epic Document*
