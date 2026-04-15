# NewsAnalyzer

A public AI evaluation showcase by [Noometric LLC](https://noometric.com).

NewsAnalyzer demonstrates production-grade evaluation techniques for AI-powered news analysis:
- **Entity extraction evaluation** — Promptfoo harness with precision/recall/F1, 113-article gold dataset
- **Cognitive bias detection evaluation** — Ontology-grounded neuro-symbolic evaluation
- **Full-stack observability** — OpenTelemetry + Grafana LGTM stack across all services

This repository contains the evaluation framework and application shell.
The Noometric Intelligence reasoning layer (cognitive bias ontology, entity extraction, proprietary detection methodology) is maintained separately by Noometric LLC and called via the `REASONING_SERVICE_URL` API.

> **Full local setup** requires access to the Noometric Intelligence service.
> Contact [noometric.com](https://noometric.com) for access or to learn about Noometric's AI evaluation consulting.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Architecture](https://img.shields.io/badge/Architecture-Documented-green.svg)](docs/architecture.md)
[![Eval Pipeline](https://github.com/noometric-llc/news-analyzer/actions/workflows/eval.yml/badge.svg)](https://github.com/noometric-llc/news-analyzer/actions/workflows/eval.yml)
[![API Tests](https://github.com/noometric-llc/news-analyzer/actions/workflows/api-tests.yml/badge.svg)](https://github.com/noometric-llc/news-analyzer/actions/workflows/api-tests.yml)
[![Production](https://img.shields.io/badge/Production-Live-brightgreen.svg)](http://newsanalyzer.org)

---

## About This Project

NewsAnalyzer is a production AI platform with a complete evaluation
and reliability infrastructure — built to demonstrate how AI systems
should be tested, measured, and monitored in production.

The project includes a Promptfoo-based evaluation pipeline that
compares extractors against a 113-article gold dataset with
precision/recall/F1 scoring, full-stack observability with
OpenTelemetry and Grafana, and 6 CI/CD pipelines with automated
quality gates. Every engineering decision — structured data modeling
for bias mitigation, OWL-based semantic reasoning, evaluation
methodology — reflects real tradeoffs made for real reasons,
documented in the
[brownfield analysis](docs/newsanalyzer-brownfield-analysis.md).

Built by [Steve Kosuth-Wood](https://www.linkedin.com/in/steve-kosuth-wood-532a909/)

---

## 🌍 Mission: Transparency & Independence

NewsAnalyzer v2 is built with **transparency** and **independence** as core principles:

- **🇪🇺 European Hosting:** Hetzner Cloud (Germany) - no US tech giants
- **🔓 Open Source:** 100% open-source stack (PostgreSQL, Redis, Nginx, Docker)
- **📖 Public Code:** Auditable on GitHub, mirrored to Codeberg (European non-profit)
- **🛡️ Data Sovereignty:** All data hosted in EU, GDPR-compliant

For a news analysis platform, independence from major tech companies ensures unbiased operation and protects against conflicts of interest.

---


## 🎯 What It Does

### AI Evaluation & Reliability
- **📊 Evaluation Pipeline** - Promptfoo harness comparing spaCy NER baseline vs. Claude LLM extraction with automated regression detection in CI/CD
- **🗂️ Gold Dataset** - 113 curated articles (601 entities) across 4 domains with character-offset annotations and entity type validation
- **📏 Scoring Methodology** - Precision/recall/F1 with 6-priority fuzzy matching (exact, substring, Levenshtein) and partial credit for type mismatches
- **📈 Evaluation Dashboard** - Interactive model comparison, gold dataset explorer, and methodology documentation
- **🔍 Observability** - OpenTelemetry auto-instrumentation across all 3 services, Grafana LGTM stack, distributed tracing, 6 pre-provisioned dashboards
- **🚦 CI/CD Quality Gates** - 6 GitHub Actions workflows: per-service builds, cross-service API tests, evaluation pipeline, and production deployment

### News Analysis Platform
- **👤 Entity Extraction** - People, organizations, locations, events from text with Schema.org JSON-LD
- **📊 Smart Classification** - 9 entity types with automatic government detection
- **🧠 OWL Reasoning** - Semantic inference and entity classification via SWI-Prolog
- **🎨 Interactive Visualization** - Real-time entity display with filtering

---


## 🧪 AI Evaluation Pipeline

NewsAnalyzer includes a complete evaluation infrastructure for
measuring and comparing AI extraction quality — the same class of
tooling used in production AI reliability engineering.

### Gold Dataset

113 curated articles with 601 manually validated entity annotations
across 4 evaluation domains:

| Domain | Articles | Purpose |
|--------|----------|---------|
| Legislative | 53 | Congressional entities, bills, votes |
| Executive | 20 | Executive branch, agencies, officials |
| Judicial | 15 | Federal courts, judges, rulings |
| CoNLL-2003 | 25 | General-domain NER sanity check |

Each annotation includes character offsets, entity types (7 types),
difficulty ratings, and perturbation labels. Dataset was constructed
via synthetic generation with ground-truth facts, then manually
curated and validated.

### Evaluation Harness

Built on [Promptfoo](https://promptfoo.dev/) with a custom entity
scorer implementing 6-priority fuzzy matching:

1. Exact text + type match → full credit
2. Exact text + type mismatch → partial credit
3. Substring containment + type match/mismatch
4. Levenshtein similarity (≥0.8 threshold) + type match/mismatch
5. No match → false positive or false negative

35 pytest tests validate scorer correctness. The harness runs
in CI/CD via GitHub Actions on every commit touching evaluation code.

### Baseline Results

Comparing spaCy NER (rule-based baseline) against Claude (LLM extraction):

| Domain | spaCy F1 | Claude F1 | Improvement |
|--------|----------|-----------|-------------|
| Legislative | 0.261 | 0.593 | **2.3x** |
| Executive | 0.359 | 0.603 | **1.7x** |
| Judicial | 0.318 | 0.614 | **1.9x** |
| CoNLL (general) | 0.905 | 0.867 | spaCy wins |

**Key finding:** Claude delivers 1.7-2.3x F1 improvement on
government-domain text, but spaCy outperforms on general-domain
NER — demonstrating that model selection depends on domain, not
just model capability. This is the kind of empirical finding that
only emerges from systematic evaluation.

### Evaluation Dashboard

Interactive frontend for exploring evaluation results:

- **Model Comparison** — Side-by-side metrics with per-entity-type
  breakdown charts (Recharts)
- **Gold Dataset Explorer** — Searchable article browser with
  branch, difficulty, and perturbation filters
- **Methodology Documentation** — Scoring strategy, entity taxonomy,
  pipeline architecture, limitations

---

## Why Structured Data — The Bias Problem

Most AI fact-checking approaches make the same architectural mistake: 
ingest reference documents as vectors and let the LLM reason over 
retrieved text chunks.

The problem: LLMs don't neutrally extract facts from documents. They 
pattern-match on text, inheriting whatever framing and implicit bias 
exists in how source material was written. An AI reasoning over a 
government document written by a policy supporter will "see" that 
policy differently than if the same facts had been written by a critic.

NewsAnalyzer addresses this by forcing all reference data through 
explicit structured data models before the AI layer touches it. A 
senator's voting record isn't stored as a document — it's structured 
objects with defined fields. The AI works with facts, not text.

This design decision came directly from applying experimental psychology 
methodology to AI system design: controlled measurement requires 
isolating variables. Unstructured text is an uncontrolled variable.

The tradeoff: every new data source requires explicit data modeling. 
There are no shortcuts that maintain evaluation integrity. This is a 
deliberate choice of reliability over development velocity.

---

## Data Source Hierarchy

NewsAnalyzer distinguishes between two categories of reference data, 
and treats them differently in analysis:

### Tier 1: Official Sources (Factual Ground Truth)
Primary government sources used as the authoritative basis for 
factual accuracy verdicts. When NewsAnalyzer flags a claim as 
inaccurate, it is because an official source directly contradicts it.

| Source | Data | Authority |
|--------|------|-----------|
| congress.gov | Bills, votes, legislative activity | U.S. Congress |
| house.gov / senate.gov | Member records, committees | U.S. Legislature |
| govinfo.gov | Authenticated federal documents | GPO |
| BLS | Employment, inflation, wage data | Dept. of Labor |
| BEA | GDP, economic indicators | Dept. of Commerce |
| FEC | Campaign finance records | Fed. Election Commission |


### Tier 2: Enrichment Sources (Planned — Phase 2)
Designed but not yet implemented. Will provide entity enrichment 
and contextual relationships — useful for understanding connections 
between entities, but explicitly not used as the basis for factual 
accuracy verdicts.

When implemented, analysis outputs drawing on Tier 2 sources will 
be flagged differently from Tier 1-grounded verdicts, preserving 
the distinction between authoritative fact and contextual inference.

| Source | Data | Limitation |
|--------|------|------------|
| Wikidata | Entity relationships, identifiers | Community-maintained |
| DBpedia | Structured Wikipedia data | Derivative of Wikipedia |

### Why This Matters

A system that treats Wikidata and an official congressional 
voting record as equivalent reference sources will produce 
analysis that sounds authoritative but isn't. The accuracy 
of a factual verdict is only as good as the authority of 
its source.

This hierarchy is enforced architecturally — Tier 1 and Tier 2 
data are designed to flow through separate pathways so the analysis 
layer always knows the authority level of any fact before 
constructing a verdict. The Tier 2 pathway is planned for Phase 2.

---

## 🏗️ Architecture Overview

**Greenfield rewrite** that learns from V1's mistakes (see [brownfield analysis](docs/newsanalyzer-brownfield-analysis.md)):

- **Frontend:** Next.js 14 + TypeScript + Tailwind CSS
- **Backend:** Spring Boot 3.2 (Java 17) REST API
- **Reasoning Service:** [Noometric Intelligence](https://noometric.com) — private API (entity extraction, bias detection, OWL reasoning)
- **Databases:** PostgreSQL + Redis (only 2 databases - V1 had 5!)
- **Observability:** OpenTelemetry + Grafana (Prometheus, Loki, Tempo)
- **Infrastructure:** Docker Compose on Hetzner Cloud
- **CI/CD:** GitHub Actions

**Key Innovation:** Unified entity model (all entities in one table with JSONB) - fixes V1's government-entity-first mistake.

See full [Architecture Document](docs/architecture.md) for details.

---

## 📁 Repository Structure

```
newsanalyzer-v2/
├── eval/                 # AI Evaluation Pipeline
│   ├── assertions/       # Custom Promptfoo scorers (entity_scorer.py + tests)
│   ├── datasets/         # Gold datasets (113 articles) + derivation scripts
│   ├── reports/          # Baseline evaluation results (JSON + HTML)
│   └── promptfooconfig.yaml
├── backend/              # Spring Boot Java backend (REST API)
├── frontend/             # Next.js TypeScript frontend + evaluation dashboard
│   └── ontology/         # OWL ontologies (NewsAnalyzer + cognitive bias)
├── deploy/               # Docker Compose, Dockerfiles, observability config
│   ├── dev/              # Local development (hot reload)
│   ├── production/       # Hetzner Cloud deployment + nginx
│   └── observability/    # OTel Collector, Prometheus, Loki, Tempo, Grafana dashboards
├── docs/                 # Architecture & documentation
│   ├── architecture/     # Tech stack, source tree, coding standards
│   └── stories/          # 26 epics & user stories (BMAD framework)
├── .github/workflows/    # 6 CI/CD pipelines (incl. eval harness)
└── docker-compose.dev.yml  # Infra-only dev stack (Postgres + Redis + Observability)
```

---

## 🚀 Quick Start

> **Live Demo:** [newsanalyzer.org](http://newsanalyzer.org) — 
> running on Hetzner Cloud, Germany
- Note: Live demo may be behind current code base.

### Prerequisites

- **Java 17+** (OpenJDK or Temurin)
- **Node.js 20+** and pnpm
- **Python 3.11+**
- **Docker Desktop**
- **Git**

### 1. Clone Repository

```bash
git clone https://github.com/noometric-llc/news-analyzer.git
cd news-analyzer
```

### 2. Start Infrastructure

```bash
docker compose -f docker-compose.dev.yml up -d
```

This starts PostgreSQL, Redis, and the full observability stack (OTel Collector, Prometheus, Loki, Tempo, Grafana). Grafana is available at [http://localhost:3001](http://localhost:3001) with pre-provisioned dashboards.

### 3. Run Backend

```bash
cd backend
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
./mvnw clean install
./mvnw flyway:migrate  # Run database migrations
./mvnw spring-boot:run -Dspring.profiles.active=dev
# Backend runs on http://localhost:8080
```

### 4. Run Frontend

```bash
cd frontend
cp .env.local.example .env.local
pnpm install
pnpm dev
# Frontend runs on http://localhost:3000
```

### 5. Start Reasoning Service

The reasoning intelligence layer is maintained by [Noometric LLC](https://noometric.com)
as a private service. To run the full stack locally:

1. **Request access** to `noometric-llc/noometric-intelligence` (contact Noometric LLC)
2. **Clone and start** the reasoning service:

```bash
git clone https://github.com/noometric-llc/noometric-intelligence.git
cd noometric-intelligence/reasoning-service
docker build -t noometric-reasoning .
docker run -p 8000:8000 -e ANTHROPIC_API_KEY=your-key noometric-reasoning
```

3. **Set the URL** in your environment:

```bash
export REASONING_SERVICE_URL=http://localhost:8000
```

> **No access?** Entity extraction and bias detection features require the
> reasoning service. A public hosted endpoint is planned (INFRA-2). Until then,
> the application will start but reasoning-dependent features will be unavailable.

---

## 🧪 Testing

**1,700+ automated tests** across the stack with CI/CD quality gates on every PR.

```bash
# Backend tests (765+ Java tests)
cd backend
./mvnw test                 # Unit tests
./mvnw verify               # Integration tests
./mvnw jacoco:report        # Coverage report (70% threshold enforced in CI)

# Frontend tests (687+ TypeScript tests, incl. 88 eval dashboard tests)
cd frontend
pnpm test                   # Unit tests (Vitest)
pnpm test:e2e               # E2E tests (Playwright)

# Reasoning service tests — run from noometric-intelligence clone
# cd noometric-intelligence/reasoning-service && pytest

# Evaluation pipeline tests
cd eval
pytest assertions/          # Scorer correctness (70 tests)
python datasets/scripts/validate_gold.py  # Gold dataset schema validation
```

---

## 📚 Documentation

- **[Architecture Document](docs/architecture.md)** - Complete fullstack architecture
- **[Brownfield Analysis](docs/newsanalyzer-brownfield-analysis.md)** - V1 failure analysis and lessons learned
- **[Evaluation Methodology](docs/stories/EVAL-2/)** - Gold dataset construction, scoring strategy, baseline results
- **[Observability Architecture](docs/stories/OBS-1/)** - OpenTelemetry instrumentation and Grafana dashboard design
- **[API Documentation](http://localhost:8080/swagger-ui.html)** - OpenAPI/Swagger UI (when backend running)

---

## 🔐 Security & Privacy

- **HTTPS Only:** Let's Encrypt SSL certificates in production
- **JWT Authentication:** Stateless, secure token-based auth
- **Rate Limiting:** Nginx rate limiting (10 req/s API, 5 req/m login)
- **Security Headers:** HSTS, X-Frame-Options, CSP, XSS protection
- **GDPR Compliant:** EU hosting, data sovereignty

---

## 🌐 Source Control & Production

- **Primary Repository:** [GitHub](https://github.com/noometric-llc/news-analyzer)
- **Production Site:** [newsanalyzer.org](http://newsanalyzer.org)
- **Production Docs:** [docs/deployment/PRODUCTION_ENVIRONMENT.md](docs/deployment/PRODUCTION_ENVIRONMENT.md)

Production is hosted on Hetzner Cloud (Germany) for data sovereignty and independence.

---

## 🤝 Contributing

We welcome contributions! Here's how:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

**Development Guidelines:**
- Follow existing code style and patterns
- Add tests for new features
- Update documentation
- Ensure all tests pass before submitting PR

---

## 📊 Project Status

> **Live Demo:** [newsanalyzer.org](http://newsanalyzer.org) — 
> running on Hetzner Cloud, Germany

**Development is sequenced by source reliability:** Tier 1 official 
sources (Phase 1, Phase 3) before Tier 2 enrichment sources (Phase 2) 
— ensuring the factual foundation is solid before adding contextual 
enrichment.

**Current Phase:** AI Evaluation Complete — EVAL-1 ✅ EVAL-2 ✅ EVAL-DASH ✅

### ✅ EVAL-1: Knowledge Base & Synthetic Article Generation (COMPLETE)
- ✅ Structured fact extraction from government data sources
- ✅ Labeled synthetic article generator with ground-truth entity annotations
- ✅ Fact set storage APIs for evaluation dataset construction

### ✅ EVAL-2: Entity Extraction Evaluation Harness (COMPLETE)
- ✅ Gold dataset: 113 articles, 601 entities, 4 domains (legislative, executive, judicial, CoNLL)
- ✅ Promptfoo integration with custom entity scorer (6-priority fuzzy matching)
- ✅ Dual-extractor comparison: spaCy NER baseline vs. Claude LLM
- ✅ Precision/recall/F1 metrics with per-entity-type breakdowns
- ✅ Baseline results: Claude 2.3x F1 improvement on government domain
- ✅ CI/CD pipeline — evaluation runs on every commit touching eval code

### ✅ EVAL-DASH: AI Evaluation Portfolio Dashboard (COMPLETE)
- ✅ Model comparison dashboard with Recharts visualizations
- ✅ Gold dataset explorer with search, filtering, and article detail views
- ✅ Evaluation methodology documentation (scoring, taxonomy, pipeline, limitations)
- ✅ Integrated into main application navigation

### ✅ OBS-1: Full-Stack Observability (COMPLETE)
- ✅ OpenTelemetry Collector + Grafana LGTM stack (Prometheus, Loki, Tempo)
- ✅ Auto-instrumentation across all 3 services (Java Agent, Python SDK, Next.js)
- ✅ Distributed tracing with W3C Trace Context + log-to-trace correlation
- ✅ 6 pre-provisioned Grafana dashboards (Service Overview, Backend JVM, Reasoning Service, Distributed Traces, Log Explorer, Home)
- ✅ Client-side Core Web Vitals monitoring (LCP, CLS, INP)

### ✅ Phase 1: Schema.org Foundation (COMPLETE)
- ✅ PostgreSQL schema with JSONB unified entity model
- ✅ Java backend with Entity CRUD, Python entity extraction (spaCy + Schema.org)
- ✅ 9 entity types with automatic government detection
- ✅ Full Schema.org JSON-LD integration + interactive frontend

### ✅ Phase 3: OWL Reasoning (COMPLETE)
- ✅ Custom NewsAnalyzer ontology (7 classes, 10 properties)
- ✅ SWI-Prolog integration for formal logical inference
- ✅ Ontology-based entity classification, consistency validation, SPARQL queries

### 🚧 EVAL-3: Cognitive Bias & Logical Fallacy Evaluation (NEXT)
- OWL ontology for cognitive biases and logical fallacies (13 distortions)
- SHACL shape validation for bias annotations
- Ontology-grounded bias detector (SPARQL → prompt → Claude → SHACL validate)
- Bias evaluation harness with gold dataset and A/B comparison

---

## 📝 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

Open source for transparency and community benefit.

---

## 🙋 Support & Contact

- **Documentation:** [docs/](docs/)
- **Issues:** [GitHub Issues](https://github.com/noometric-llc/news-analyzer/issues)
- **Discussions:** [GitHub Discussions](https://github.com/noometric-llc/news-analyzer/discussions)

---

## 🔗 Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Frontend** | Next.js + TypeScript | 14.x |
| **Backend** | Spring Boot (Java) | 3.2.x |
| **Python Service** | FastAPI + SWI-Prolog | 0.109+ |
| **Database** | PostgreSQL | 15+ |
| **Cache** | Redis | 7+ |
| **Observability** | OpenTelemetry, Grafana, Prometheus, Loki, Tempo | latest |
| **Hosting** | Hetzner Cloud (Germany) | - |
| **CI/CD** | GitHub Actions | - |

---

## Learning From V1: The Brownfield Analysis

NewsAnalyzer v2 is a complete greenfield rewrite of a failed 
first attempt. Rather than quietly discarding v1, the failure 
was systematically documented in a 
[full brownfield analysis](docs/analysis/newsanalyzer-brownfield-analysis.md )
that drove every significant architectural decision in v2.

The core lesson:

> *"Design your data model for the BROADEST set of information 
> sources from day one, not the first source you implement."*

The fatal flaw in v1 was treating government entities as 
architecturally special — building separate database schemas, 
service layers, and models for each entity type — rather than 
recognizing that a senator, a corporation, and a geographic 
location are all just *entities with different properties*. 
This decision made the system increasingly rigid as requirements 
evolved, eventually making the ontology and reasoning 
requirements impossible to retrofit without a complete rewrite.

| V1 Mistake | Root Cause | V2 Solution |
|---|---|---|
| 5 databases (PG, Neo4j, Mongo, Redis, ES) | Premature optimization for use cases that never materialized | PostgreSQL + Redis only |
| Separate models per entity type | Government-entity-first thinking | Unified JSONB entity model |
| Java → Python via subprocess (500ms) | Integration designed as afterthought | HTTP API (50ms, 10x faster) |
| Late ontology discovery | Requirements discovered after architecture locked | Schema.org designed in from day one |
| Unstructured document ingestion | Standard RAG approach | Structured data models — prevents LLM bias contamination |

The last row is the one not present in most architectural 
post-mortems: discovering that the standard approach to AI 
knowledge bases produces evaluations contaminated by source 
document framing — and that structured data modeling is the 
correct mitigation.


---

## ⭐ Star History

If you find this project useful, please star it on GitHub!

---

**Built with ❤️ for transparent, reliable AI — evaluated and measured, not just deployed.**

*Hosted independently in Europe 🇪🇺 • Open Source 🔓 • AI Evaluation Pipeline 📊 • Full-Stack Observability 🔍*
