# Project Brief: Evidence Store Foundation

## Executive Summary

The Evidence Store Foundation introduces a new persisted layer to NewsAnalyzer that ingests and stores news articles as reliability-scored evidence, distinct from the existing government-data Factbase, laying the groundwork for a future conversational agent that can answer political questions by citing and reasoning over that evidence rather than relying on undifferentiated LLM knowledge. It solves the current gap where article text is only ever processed transiently (never persisted, never scored, never queryable) — meaning nothing in NewsAnalyzer today can actually ground a claim in "here's what was reported, by whom, and how reliable that source is." The target market, at this stage, is internal: this is infrastructure work that unblocks the political-analysis-agent vision explored in the 2026-07-01 brainstorming session, not a user-facing feature on its own. The key value proposition is architectural: it establishes the trust boundary (evidence-backed vs. model-recalled) that every downstream trust feature — reliability scoring, audit trails, the "challenge" dialogue — depends on.

## Problem Statement

NewsAnalyzer has no persisted record of article content today. Article text is passed transiently to the reasoning service for named-entity extraction and then discarded — nothing is stored, reliability-scored, or queryable after the fact. The only article-shaped table in the system, `SyntheticArticle`, exists purely to feed the evaluation harness with synthetic ground-truth data; it has never held real ingested news.

This creates a structural gap for anything the political-analysis-agent vision depends on. The agent's entire value proposition — citing evidence, comparing how outlets covered the same event, scoring source reliability, distinguishing "what was reported" from "what the model recalls from training" — requires a persistent record of what was actually published, by whom, and when. None of that exists. Today, if two outlets covered the same press conference with opposite framing, NewsAnalyzer has no way to even notice that, let alone reason about it, because neither article is ever stored.

The existing Factbase (government master data: bills, regulations, committee membership, etc.) doesn't fill this gap — it's authoritative structured data about government entities and actions, not a record of news coverage or a substrate for source-reliability scoring.

This is blocking, not cosmetic: every Priority #1 and #2 item from the 2026-07-01 brainstorm (grounding discipline, reliability scoring, audit trails, response structure) assumes evidence exists to ground against. Without this foundation, the agent vision has nothing to stand on except undifferentiated LLM knowledge — precisely the failure mode the brainstorm identified as unacceptable.

## Proposed Solution

Build an **Evidence Store**: a new persistence layer that captures ingested articles as first-class records — source, publication date, byline/outlet, raw text, and a reliability score — with extracted entities and claims linked back to the specific article they came from. This turns the existing (currently transient) entity-extraction call into a durable pipeline: ingest → persist → extract → link → score, instead of extract-and-discard.

Two structural decisions anchor the design:
1. **Reliability scoring, not bias labeling** — each source gets a quantitative score rather than an editorial verdict, consistent with the brainstorm's "audit trail over accusation" principle. The scoring *methodology* itself is out of scope here — noometric-intelligence owns that; this layer just defines the schema/interface a score is written into and consumed from.
2. **Grounding as an enforced boundary, not a convention** — any future consumer (agent, API, UI) that answers a question must be able to distinguish "this claim traces to an Evidence Store record" from "this is ungrounded." The Evidence Store makes that distinction structurally possible by existing at all; the enforcement policy (flag/decline ungrounded claims) is designed alongside it even though the agent itself is a later phase.

This is explicitly complementary to, not a replacement for, the existing government-data Factbase — the Evidence Store holds source-attributed news coverage; the Factbase holds authoritative structured government records. A future agent would draw on both.

This solution leverages existing engineering rather than starting from zero: NewsAnalyzer already calls the reasoning service for entity extraction and already has the `Entity` model with a `source` field and Schema.org export. The Evidence Store wraps persistence and scoring around plumbing that already exists, rather than inventing a new extraction pipeline.

## Target Users

### Primary User Segment: Internal Engineering / Downstream Systems

This is an infrastructure component, so its direct "users" are the systems and developers that will consume it, not end citizens — at least in this phase. Concretely: (1) the future political-analysis agent (Priority #1/#3 downstream work) that needs to query grounded evidence rather than fall back on LLM knowledge, (2) any NewsAnalyzer developer building features that need "what was reported and by whom" (e.g., a future outlet-comparison view), and (3) the evaluation harness, which currently only has synthetic articles to validate extraction against and would benefit from real ingested content for more realistic testing. Their need is a reliable, queryable, source-attributed record — not a UI.

### Secondary User Segment: The Eventual Citizen End User

The curious, politically engaged but news-fatigued citizen described in the 2026-07-01 brainstorm — someone who wants an argument-and-counter-argument analysis with visible evidence, not a verdict. This brief doesn't serve them directly, but every schema and scoring decision made here should be evaluated against whether it will support that eventual experience (e.g., can the schema support citing "Outlet A said X, Outlet B said Y" cleanly?) without over-building for a UI that doesn't exist yet.

## Goals & Success Metrics

### Business Objectives
- Unblock the Priority #1/#3 downstream work (construct-definition and business-case proposal) by having a working evidence substrate to test against, before those initiatives commit to further design — target: ingestion pipeline operational within [timeframe TBD]
- Replace synthetic-only extraction validation with real ingested content, closing the gap where the extraction pipeline has only ever been tested against synthetic data
- Establish the grounding trust boundary (Evidence Store-backed vs. ungrounded) as an architectural pattern before any agent work begins, so it isn't retrofitted under schedule pressure later

### User Success Metrics
- A developer building on this layer can, for any given `Entity` record, trace it back to a specific persisted article with source and reliability score — with zero ambiguity about whether the underlying claim is grounded
- The eval harness can run entity-extraction validation against real ingested articles, not only `SyntheticArticle` records

### Key Performance Indicators (KPIs)
- **Ingestion success rate**: % of submitted articles successfully persisted with complete source metadata — target 100% for well-formed input, explicit failure (not silent drop) otherwise
- **Source-linkage coverage**: % of newly extracted `Entity` records with a valid, non-null link to their originating Evidence Store article — target 100% going forward (existing/legacy entities out of scope)
- **Reliability score coverage**: % of distinct ingested sources with a reliability score attached — even a placeholder/default score counts, since the scoring methodology is out of this brief's scope, but every source must have *a* score field populated, never null
- **Zero silent blending**: 100% of any consumer query against this layer either returns Evidence Store-grounded data or an explicit "no grounded evidence available" signal — never a silent fallback to ungrounded content

## MVP Scope

### Core Features (Must Have)
- **Article persistence model:** A first-class, persisted article record (source/outlet, URL, publication date, raw text, ingestion timestamp) — the missing piece identified in research. Nothing else in this brief works without this existing.
- **Ingestion pathway:** A way to submit an article (API endpoint is sufficient for MVP — no crawler/scraper required) that persists it and triggers the existing reasoning-service entity-extraction call against the stored text. Reuses the existing extraction plumbing rather than building new.
- **Entity-to-article linkage:** `Entity` records extracted from an ingested article get a real foreign-key link to that article, not just the current freeform `source` string. This is what makes "trace this claim back to its evidence" actually possible.
- **Reliability score field (schema only):** A nullable-by-default score field on the source/outlet, populated with a placeholder value for MVP. The field must exist so nothing downstream has to be redesigned later, even though the scoring methodology itself is explicitly out of scope.
- **Grounded-query interface:** A query path that returns Evidence Store-backed results with citations, or an explicit "no grounded evidence found" response — never a silent blend with ungrounded content. This is the enforceable core of the "zero silent blending" KPI.
- **Eval harness integration:** Allow the evaluation harness to validate entity extraction against real ingested articles, not only `SyntheticArticle` records. Closes the real-content testing gap identified in research.

### Out of Scope for MVP
- Reliability-scoring methodology/algorithm (belongs in noometric-intelligence)
- Construct-level bias detection
- Any conversational agent, chat interface, or "challenge" dialogue
- Audit trail UI / methodology-disclosure UI
- Automated large-scale ingestion (RSS feeds, scrapers, crawlers) — MVP ingestion is API/manual-submission only
- Outlet-comparison views or any citizen-facing UI
- Argument/counter-argument response generation

### MVP Success Criteria

MVP is complete when: a real article can be submitted via API, is persisted with source metadata, has entities extracted and linked back to it, has a (placeholder) reliability score attached to its source, and can be queried such that the result either cites the grounded evidence or explicitly states none was found — with this full path exercised by an automated test using real (not only synthetic) article content.

## Post-MVP Vision

### Phase 2 Features
Automated ingestion (moving beyond manual/API submission to scheduled feeds, with sourcing/licensing considerations addressed); real reliability-score integration once noometric-intelligence methodology exists to replace the MVP placeholder; sampling-representativeness and correlated-bias safeguards (per the brainstorm's identified risks) for any feature that aggregates across sources; an audit-trail UI surfacing sourcing and methodology per response; and the argument/counter-argument response structure designed in the brainstorm.

### Long-term Vision
A conversational political-analysis agent, grounded entirely in the Evidence Store (plus the existing government Factbase), that answers citizen questions with sourced, provisional, non-persuasive analysis — including the "challenge" dialogue mechanism for live re-argument. Its non-partisan persona is psychometrically validated and monitored for drift across model updates, consistent with the Noometric persona-profiling methodology.

### Expansion Opportunities
If the business case (Priority #3) is approved, this becomes the technical foundation for NewsAnalyzer as the flagship public case study for Noometric's LLM persona-profiling line — including the possibility of a licensable validated persona specification. None of that is committed by this brief; it's the reason the grounding and scoring architecture is being built carefully now rather than bolted on later.

## Technical Considerations

### Platform Requirements
- **Target Platforms:** Backend service only for MVP — no new client platform. Ingestion and query are API-driven (curl/internal tooling/eval harness), not UI-driven.
- **Browser/OS Support:** N/A for MVP (no UI surface added)
- **Performance Requirements:** Not a high-throughput concern at MVP scale (manual/API ingestion, not a live feed) — correctness and traceability matter more than volume at this stage

### Technology Preferences
- **Frontend:** None required for MVP. The existing Next.js 14/React 18 app is untouched; a future admin ingestion UI is a Phase 2 candidate, not MVP.
- **Backend:** Java 17 / Spring Boot 3.2.2, consistent with the existing stack — new `Article` model, repository, service, and controller following the same package structure as `Entity`/`EntityController`/`EntityService`.
- **Database:** PostgreSQL 15, using Flyway migrations for the new `articles` table (and any `source_reliability` table/column), consistent with existing schema management. JSONB available if flexible article metadata is needed, matching the `Entity.properties` pattern.
- **Hosting/Infrastructure:** No new infrastructure — deploys within the existing backend service and CI/CD pipeline.

### Architecture Considerations
- **Repository Structure:** New classes live alongside existing `model/`, `repository/`, `service/`, `controller/`, `dto/` packages — no new module or repo.
- **Service Architecture:** New `Article`-related classes wrap the *existing* reasoning-service extraction call rather than replacing it — ingest persists the article, then calls the same `POST /entities/extract` contract already documented in `docs/api/reasoning-service-contract.md`, then persists the returned entities with a real FK back to the article (replacing the current freeform `source` string for newly extracted entities).
- **Integration Requirements:** No change to the `REASONING_SERVICE_URL` contract itself — this brief only adds persistence and linkage around the existing boundary. Per CLAUDE.md, no reasoning/scoring methodology should be pulled into this repo; the reliability score field is a data slot this repo owns, not a calculation this repo performs.
- **Security/Compliance:** Real news article text raises copyright/licensing questions that synthetic data never did (storing full article text vs. transient processing). This needs a decision — store full text, excerpt/summary only, or store alongside a licensing-aware retention policy — before ingestion goes beyond a handful of test articles.

## Constraints & Assumptions

### Constraints
- **Budget:** Not separately budgeted — this is internal engineering effort, not a funded initiative with external spend
- **Timeline:** Not yet set; sequencing depends on how Priority #1 (non-partisan construct definition with noometric-intelligence) proceeds, since that work will inform what the reliability-score field ultimately needs to hold
- **Resources:** Assumed solo/small development effort (no dedicated engineering team)
- **Technical:** Must stay within the existing Java/Spring/PostgreSQL stack; must not pull reasoning/scoring/bias-detection methodology into this (public) repo per the CLAUDE.md IP boundary

### Key Assumptions
- Real article ingestion at MVP stage will be low-volume (test/curated articles), not production-scale — a copyright/licensing decision is needed before scaling beyond that
- The existing `REASONING_SERVICE_URL` / `POST /entities/extract` contract remains stable and unchanged by this work
- The reliability-score field's *schema* can be defined now with a placeholder value, without knowing the eventual scoring methodology — i.e., the Priority #1 construct-definition work won't require a schema-breaking change later
- This brief's scope (Evidence Store foundation) can proceed independently of Priority #3 (the business-case proposal) — it doesn't require that proposal's outcome to be useful, since real-content eval validation and grounding architecture have value regardless

## Risks & Open Questions

### Key Risks
- **Copyright/licensing exposure:** Persisting full article text at rest (vs. today's transient processing) may raise copyright/fair-use questions that haven't been legally reviewed. Could block or reshape the ingestion design if full-text storage isn't viable.
- **Schema lock-in before methodology exists:** Defining the reliability-score field now, ahead of Priority #1's construct-definition work, risks a mismatch that forces a migration later. Moderate risk — mitigated by keeping the field a simple placeholder.
- **IP boundary drift:** Under schedule pressure, there's a temptation to write "just a quick" scoring heuristic locally instead of waiting on noometric-intelligence. High impact if it happens — this is exactly the boundary CLAUDE.md flags as mandatory to protect.
- **Scope creep from the "more greenfield than expected" discovery:** Since article ingestion doesn't exist at all, there's a risk this "foundation" brief quietly grows into a full ingestion-pipeline project. Mitigated by the explicit Out-of-Scope list, but worth actively guarding in execution.

### Open Questions
- What's the actual sequencing/timeline relative to Priority #1 (construct definition) and Priority #3 (business case proposal)?
- Should MVP article storage retain full article text, or only excerpts/summaries, pending the licensing question?
- What will serve as the initial source of test articles for MVP validation — manually curated, or some existing small dataset?
- Does this initiative need a BMad story in `docs/stories/` before implementation begins, per this project's standard development process? (Likely yes — noted as a next step.)

### Areas Needing Further Research
- Copyright/fair-use posture for storing third-party news article text in a public-repo-adjacent system (even if the database itself isn't public, the schema and code are)
- Whether any legacy `Entity.source` freeform-string data needs a migration path once the FK-based article linkage exists, or whether this only applies going forward

## Appendices

### A. Research Summary

This brief draws on two inputs: (1) the 2026-07-01 brainstorming session (`docs/analysis/ES-1-brainstorming-session-results.md`), which established the conceptual case for treating articles as evidence, reliability-scoring sources, and enforcing a grounding boundary between evidence-backed and model-recalled claims; and (2) a targeted architecture survey conducted during this brief's drafting, which found that article ingestion does not currently exist in production (only a synthetic-data table for eval purposes), that "Factbase" is already a defined term for government master data distinct from news evidence, and that the existing `Entity` model and reasoning-service extraction contract can be reused/extended rather than replaced.

### C. References
- `docs/analysis/ES-1-brainstorming-session-results.md` — source brainstorming session
- `docs/architecture/FACTBASE_EXPANSION_ARCHITECT_HANDOFF.md` — existing Factbase architecture (government master data)
- `docs/api/reasoning-service-contract.md` — entity-extraction API contract with noometric-intelligence
- `docs/architecture/tech-stack.md`, `docs/architecture/source-tree.md` — current stack and structure
- `D:\NoometricLLC\noometric_master\docs\products\llm-persona-profiling\exploration-2026-06-29.md` — related Noometric business direction (external project, referenced for context only)

## Next Steps

### Immediate Actions
1. Create a BMad story in `docs/stories/` for this initiative per the project's standard development process (required before implementation begins)
2. Resolve the article full-text copyright/licensing question — likely needs `business-attorney` input per the Noometric agent table before any real (non-test) content is ingested
3. Confirm sequencing against the Priority #1 (construct-definition) conversation with noometric-intelligence, since it may refine what the reliability-score field needs to hold
4. Given `docs/architecture/FACTBASE_EXPANSION_ARCHITECT_HANDOFF.md` already exists and covers related schema territory, loop in the `/architect` agent to review the proposed `Article`/scoring schema against that existing handoff before implementation, to avoid conflicting patterns

### PM Handoff

This Project Brief provides the full context for Evidence Store Foundation. Please start in 'PRD Generation Mode', review the brief thoroughly to work with the user to create the PRD section by section as the template indicates, asking for any necessary clarification or suggesting improvements.
