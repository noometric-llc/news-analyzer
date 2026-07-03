# NewsAnalyzer v2 - Fullstack Architecture Document

**Version:** 2.5
**Date:** 2025-12-30
**Author:** Winston (Architect Agent)
**Status:** ✅ APPROVED FOR IMPLEMENTATION

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-11-19 | 1.0 | Initial greenfield architecture | Winston (Architect) |
| 2025-11-28 | 2.0 | FB-1 Epic: Congressional data models (Person, Committee, GovernmentPosition, PositionHolding) | Winston (Architect) |
| 2025-11-30 | 2.1 | FB-1.x Stories: Members API, Committees API, Admin Dashboard | Winston (Architect) |
| 2025-12-01 | 2.2 | FB-2 Epic: Executive branch data models (AppointmentType, Branch, PLUM import) | Winston (Architect) |
| 2025-12-01 | 2.3 | FB-2.4: Appointee Lookup API, Executive Position endpoints | Winston (Architect) |
| 2025-12-02 | 2.4 | FB-3 Epic: Regulation data models (Regulation, RegulationAgency, DocumentType), 8 regulation API endpoints, agency linkage service, federal_register_agency_id on GovernmentOrganization | Winston (Architect) |
| 2025-12-30 | 2.5 | **Data Architecture Clarification:** Added Section 2 (Data Architecture Overview) explaining dual-layer model (Article Analyzer vs Knowledge Base). Updated Frontend Architecture with navigation structure. Clarified terminology: Knowledge Base = authoritative reference data, Article Analyzer = analysis layer. | Winston (Architect) |

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Data Architecture Overview](#2-data-architecture-overview)
3. [High-Level Architecture](#3-high-level-architecture)
4. [Tech Stack](#4-tech-stack)
5. [Data Models](#5-data-models)
6. [API Specification](#6-api-specification)
7. [Components](#7-components)
8. [Frontend Architecture](#8-frontend-architecture)
9. [Backend Architecture](#9-backend-architecture)
10. [Deployment Architecture](#10-deployment-architecture)
11. [Development Workflow](#11-development-workflow)

---

## 1. Introduction

This document outlines the complete fullstack architecture for **NewsAnalyzer v2**, a greenfield rewrite that learns from the failures of the original implementation. The platform analyzes news articles, blogs, and social media posts to detect:

- **Factual accuracy** against authoritative sources
- **Logical fallacies** in reasoning
- **Cognitive biases** in writing

This unified architecture combines backend systems, frontend implementation, and their integration, serving as the single source of truth for AI-driven development.

### Key Architectural Principles (Learned from V1 Failure)

1. **Generalized Entity Model** - All entities (government, person, org, location) in unified schema
2. **Ontology-First Design** - Schema.org compatibility from day 1
3. **Minimal Database Footprint** - PostgreSQL + Redis only (no Neo4j, MongoDB, Elasticsearch sprawl)
4. **Service-Based Integration** - HTTP APIs instead of subprocess calls
5. **Reasoning-Ready** - Architecture supports Prolog/logical reasoning from start
6. **Independence & Transparency** - European hosting, open-source stack, no US tech giants

### What Went Wrong in V1

The original NewsAnalyzer project failed due to:

1. **Data Model Myopia** - Designed database schema around government entities as "first-class citizens" rather than generalized entity model
2. **Premature Specialization** - Government entities treated differently than other entities (persons, organizations, locations)
3. **Discovery-Driven Complexity** - Business requirement for "reasoning error detection" discovered AFTER building relational schema, leading to late ontology/Prolog adoption
4. **Database Sprawl** - 5 databases (PostgreSQL, Neo4j, MongoDB, Redis, Elasticsearch) with complex synchronization
5. **Integration Brittleness** - Java ↔ Python bridge via filesystem/subprocess, fragile and slow
6. **Architecture Mismatch** - Modular monolith appropriate for early stage, but ontology/reasoning requirements demanded different foundation

**Critical Lesson Learned:**
> "Design your data model for the BROADEST set of information sources from day one, not the first source you implement."

### Salvageable Components from V1

**Keep These Patterns:**
- ✅ Modular monolith architecture (appropriate for small projects)
- ✅ Python entity tagger concept (SQLite + pattern matching)
- ✅ Flyway database migrations
- ✅ Spring Boot service layer patterns
- ✅ Claude API integration

**Discard These Patterns:**
- ❌ Government-entity-first data model
- ❌ Five-database architecture
- ❌ Java → Python subprocess integration
- ❌ Separate entity types (GovernmentEntity, Person, Organization)

---

## 2. Data Architecture Overview

This section explains the dual-layer data architecture that separates **article analysis** (extracted, variable-confidence data) from the **Knowledge Base** (authoritative, curated reference data).

### The Two Data Layers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           NEWSANALYZER                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────┐       ┌────────────────────────────────────┐│
│  │     ARTICLE ANALYZER       │       │         KNOWLEDGE BASE             ││
│  │     (Analysis Layer)       │       │   (Authoritative Reference)        ││
│  ├────────────────────────────┤       ├────────────────────────────────────┤│
│  │                            │       │                                    ││
│  │  • Input articles          │       │  • Curated, verified data          ││
│  │  • Extract entities        │ ───►  │  • Hierarchical browsing           ││
│  │  • Variable confidence     │promote│  • Official sources                ││
│  │  • Links TO Knowledge Base │via    │  • Ground truth for reasoning      ││
│  │                            │queue  │                                    ││
│  │  Tables:                   │       │  Tables:                           ││
│  │  - entities (extracted)    │       │  - persons                         ││
│  │  - articles                │       │  - committees                      ││
│  │  - claims                  │       │  - government_organizations        ││
│  │  - entity_mentions         │       │  - government_positions            ││
│  │                            │       │  - statutes                        ││
│  │                            │       │  - regulations                     ││
│  │                            │       │  - news_sources                    ││
│  └────────────────────────────┘       └────────────────────────────────────┘│
│                                                                              │
│  User Flow: Article → Analysis → Entities → Match to KB → Verify claim      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Layer 1: Article Analyzer (Analysis Layer)

**Purpose:** Process and analyze articles submitted by users, extracting entities and claims for fact-checking.

| Characteristic | Description |
|----------------|-------------|
| **Data Source** | User-submitted articles, LLM extraction, NER |
| **Confidence** | Variable (0.0-1.0), tracked per entity |
| **Primary Use** | Article analysis workflow |
| **Tables** | `entities`, `articles`, `claims`, `entity_mentions`, `analyses` |

**User Workflow:**
1. User submits article (text, URL, source)
2. Reasoning service extracts entities from article text
3. Extracted entities are matched against Knowledge Base
4. User reviews analysis results with KB context

### Layer 2: Knowledge Base (Authoritative Reference)

**Purpose:** Curated, verified reference data that serves as ground truth for both human browsing and AI reasoning.

| Characteristic | Description |
|----------------|-------------|
| **Data Sources** | Congress.gov, OPM PLUM, Federal Register, manual curation |
| **Confidence** | High (1.0), verified from official sources |
| **Primary Use** | Reference browsing AND reasoning service queries |
| **Tables** | `persons`, `committees`, `government_organizations`, `government_positions`, `statutes`, `regulations`, `news_sources` |

**Dual Purpose - Knowledge Base serves two consumers:**

| Consumer | How KB is Used |
|----------|----------------|
| **Human Users (UI Browsing)** | Explore hierarchical structure to understand relationships, verify claims manually |
| **Reasoning Service (AI Analysis)** | Query KB as ground truth to match extracted entities, verify claims, discover relationships |

### How the Layers Connect

1. **Entity Resolution:** When the reasoning service extracts "Chuck Schumer" from an article, it queries the KB to find the authoritative `persons` record with `bioguide_id='S000148'`.

2. **Context Enrichment:** If an article mentions "Southern District of Florida", the KB provides context: parent circuit (11th Circuit), judges, jurisdiction.

3. **Claim Verification:** The reasoning service uses KB relationships to verify claims like "DOJ is part of the Executive Branch" → query `government_organizations` hierarchy.

4. **Entity Promotion:** High-confidence extracted entities can be promoted to the KB via admin review queue (future feature).

### Terminology Clarification

| Term | Meaning | NOT |
|------|---------|-----|
| **Knowledge Base** | Authoritative, curated reference data | Extracted entities from articles |
| **Article Analyzer** | Article processing and entity extraction | Knowledge Base browsing |
| **Extracted Entity** | Entity found in article text (variable confidence) | Authoritative KB record |
| **KB Record** | Verified entry in Knowledge Base tables | Analysis output |

---

## 3. High-Level Architecture

### Technical Summary

NewsAnalyzer v2 uses a **modern fullstack TypeScript/Java architecture** with a focus on simplicity and maintainability. The system employs:

- **Architecture Style:** Modular monolith with clear service boundaries, designed for potential microservice extraction if needed
- **Frontend:** Next.js 14 with React Server Components and TypeScript for type-safe, performant UI
- **Backend:** Spring Boot 3.2 (Java 17) with REST API and service layer architecture
- **NLP Service:** Python FastAPI microservice for entity extraction and Prolog reasoning
- **Data Layer:** PostgreSQL (primary database) + Redis (caching) - avoiding the V1 database sprawl
- **Ontology Support:** Schema.org JSON-LD as native data format, with JSONB columns for flexible entity properties
- **Reasoning Engine:** Python FastAPI microservice running Prolog (SWI-Prolog) for logical fallacy detection
- **Infrastructure:** Hetzner Cloud (Germany) for independent European hosting

This architecture achieves PRD goals by: (1) Unifying entity management across all types, (2) Enabling LLM-driven analysis via JSON-LD, (3) Supporting logical reasoning through Prolog integration, (4) Maintaining rapid development velocity with proven frameworks.

### Platform and Infrastructure Choice

**Platform:** Hetzner Cloud (Germany) + Docker Compose

**Key Services:**
- **Hetzner VPS CX41:** 4 vCPU, 16GB RAM, 160GB SSD - €15.50/month
- **Hetzner Cloud Volumes:** Persistent storage for PostgreSQL data
- **Docker Compose:** Container orchestration for all services
- **Nginx:** Reverse proxy and SSL termination
- **Let's Encrypt:** Free SSL certificates
- **Cloudflare:** DNS and DDoS protection (optional)

**Deployment Host and Regions:**
- Hetzner: Falkenstein, Germany (eu-central) or Helsinki, Finland (eu-north)

**Rationale:**
1. **Independence:** German company, no US tech giant ties
2. **Transparency:** Self-hosted, full control over infrastructure
3. **Data Sovereignty:** EU hosting, GDPR-compliant by default
4. **Cost:** 3-5x cheaper than AWS/Azure/GCP
5. **Credibility:** For news analysis, hosting independence matters for trust

### Repository Structure

**Structure:** Monorepo (single repository with multiple packages)

**Package Organization:**

```
newsanalyzer-v2/
├── backend/                      # Spring Boot backend
│   ├── src/main/java/com/newsanalyzer/
│   │   ├── controller/           # REST controllers
│   │   ├── service/              # Business logic
│   │   ├── repository/           # Spring Data JPA repositories
│   │   ├── model/                # JPA entities
│   │   └── config/               # Spring configuration
│   ├── src/main/resources/
│   │   ├── application.yml       # Spring config
│   │   └── db/migration/         # Flyway migrations
│   └── pom.xml                   # Maven dependencies
│
├── frontend/                     # Next.js frontend
│   ├── src/
│   │   ├── app/                  # Next.js 14 app router
│   │   ├── components/           # React components
│   │   ├── lib/                  # API client, utilities
│   │   ├── hooks/                # Custom React hooks
│   │   ├── stores/               # Zustand state stores
│   │   └── types/                # TypeScript types
│   └── package.json              # npm dependencies
│
├── reasoning-service/            # Python FastAPI service
│   ├── app/
│   │   ├── api/                  # FastAPI routes
│   │   ├── services/             # Entity tagger, Prolog
│   │   ├── models/               # Pydantic models
│   │   ├── prolog/               # Prolog rules
│   │   └── db/                   # Entity reference DB
│   └── requirements.txt          # Python dependencies
│
├── docs/                         # Documentation
│   ├── architecture.md           # This document
│   └── newsanalyzer-brownfield-analysis.md
│
├── .github/workflows/            # CI/CD
│   └── deploy.yml                # GitHub Actions pipeline
│
├── nginx/                        # Nginx configuration
│   └── nginx.conf
│
├── docker-compose.yml            # Local development
├── docker-compose.prod.yml       # Production deployment
└── README.md
```

### High-Level Architecture Diagram

```mermaid
graph TB
    subgraph "User Layer"
        Browser[Web Browser]
        Mobile[Mobile Browser]
    end

    subgraph "Hetzner VPS (Germany)"
        Nginx[Nginx Reverse Proxy]

        subgraph "Docker Compose Stack"
            NextJS[Next.js Frontend]
            SpringBoot[Spring Boot Backend]
            FastAPI[Python FastAPI Service]
            PostgreSQL[(PostgreSQL 15)]
            Redis[(Redis 7)]
        end
    end

    subgraph "External Services"
        Claude[Anthropic Claude API]
        FedReg[Federal Register API]
        CongressAPI[Congress.gov API]
        PLUM[OPM PLUM CSV]
        UnitedStatesIO[UnitedStates.io<br/>Legislators Repo]
        GitHub[GitHub/Codeberg<br/>Source Control]
    end

    Browser --> Nginx
    Mobile --> Nginx
    Nginx --> NextJS
    Nginx --> SpringBoot

    NextJS --> SpringBoot
    SpringBoot --> PostgreSQL
    SpringBoot --> Redis
    SpringBoot --> FastAPI
    SpringBoot --> Claude

    FastAPI --> PostgreSQL
    FastAPI --> FedReg

    SpringBoot --> CongressAPI
    SpringBoot --> PLUM
    SpringBoot --> UnitedStatesIO

    GitHub -.Mirror.-> Codeberg[Codeberg.org<br/>European Mirror]

    style Nginx fill:#00897b
    style SpringBoot fill:#6db33f
    style FastAPI fill:#009688
    style PostgreSQL fill:#336791
    style Claude fill:#8b5cf6
    style GitHub fill:#181717
    style Codeberg fill:#2185d0
```

### Architectural Patterns

- **Jamstack Architecture:** Next.js SSG/ISR for content pages, client-side hydration for interactivity - _Rationale:_ Fast page loads, SEO-friendly, reduces server load

- **API-First Design:** All backend logic exposed via REST API routes, frontend consumes APIs - _Rationale:_ Enables future mobile apps, third-party integrations

- **Repository Pattern:** Abstract database access behind repositories (EntityRepository, ClaimRepository) - _Rationale:_ Testability, potential migration from current database if needed

- **Service Layer Pattern:** Business logic in services (EntityService, AnalysisService), controllers stay thin - _Rationale:_ Separation of concerns, reusable logic

- **Unified Entity Model:** Single `entities` table with polymorphic types, JSONB for type-specific properties - _Rationale:_ Fixes V1 government-entity-first mistake, supports all entity types

- **Microservice for Complex Logic:** Python FastAPI for NLP/Prolog operations, HTTP API integration - _Rationale:_ Language-appropriate tool choice (Python for NLP, Prolog for reasoning)

- **Schema.org Native Format:** Entities stored as JSON-LD, compatible with ontology standards - _Rationale:_ LLM-friendly, future-proofs for ontology/reasoning requirements

- **Stateless API Routes:** No server-side sessions, JWT-based auth - _Rationale:_ Serverless-friendly, horizontal scalability

---

## 4. Tech Stack

This is the **DEFINITIVE technology selection** for the entire project - the single source of truth for all development.

### Technology Stack Table

| Category | Technology | Version | Purpose | Rationale |
|----------|-----------|---------|---------|-----------|
| **Frontend Language** | TypeScript | 5.3+ | Type-safe JavaScript for frontend | Prevents runtime errors, improves IDE support, shared types |
| **Frontend Framework** | Next.js | 14.x (App Router) | React meta-framework with SSR/SSG | Built-in routing, image optimization, SEO |
| **UI Component Library** | Shadcn/UI | Latest | Radix UI + Tailwind components | Accessible, customizable, copy-paste (not npm dependency) |
| **State Management** | Zustand | 4.x | Lightweight React state management | Simpler than Redux, no boilerplate, works with RSC |
| **Backend Language** | Java | 17 LTS | Primary backend language | Your expertise, proven in V1, enterprise-grade |
| **Backend Framework** | Spring Boot | 3.2+ | Production-ready Java framework | Familiar from V1, excellent ecosystem, battle-tested |
| **API Style** | REST (JSON) | - | HTTP REST APIs | Simple, cacheable, universally supported |
| **Database** | PostgreSQL | 15+ | Primary relational database | JSONB for flexible entities, full-text search, recursive CTEs |
| **Cache** | Redis | 7+ | Response caching and sessions | Proven, Spring Data Redis integration |
| **File Storage** | Local FS / S3 Compatible | - | Article PDFs, images | Simple local storage for MVP |
| **Authentication** | Spring Security + JWT | - | User authentication | Industry standard, fine-grained control |
| **Python Service** | Python | 3.11+ | NLP and Prolog reasoning service | Best for NLP/Prolog, HTTP API to Java backend |
| **Python Framework** | FastAPI | 0.109+ | High-performance async API framework | Auto-generated docs, Pydantic validation |
| **ORM** | Spring Data JPA (Hibernate) | - | Database access layer | Familiar from V1, mature, PostgreSQL optimized |
| **Database Migration** | Flyway | 9.x+ | Schema versioning | You already used this successfully in V1 |
| **Build Tool** | Maven | 3.9+ | Java build and dependency management | Familiar from V1, battle-tested |
| **Frontend Testing** | Vitest + React Testing Library | Vitest 1.x, RTL 14.x | Fast unit tests for components | 10x faster than Jest, standard for React |
| **Backend Testing** | JUnit 5 + Mockito | Latest | Java unit and integration tests | Standard Java testing |
| **E2E Testing** | Playwright | 1.40+ | End-to-end browser testing | Cross-browser, TypeScript, industry standard |
| **Package Manager (FE)** | pnpm | 8.x | Fast package manager for frontend | Saves disk space, faster than npm |
| **CI/CD** | GitHub Actions | - | Automated testing and deployment | Maven build, Docker build, deploy to Hetzner |
| **Monitoring** | Spring Boot Actuator + Prometheus | - | Metrics, health checks, alerts | Spring-native, industry standard observability |
| **Logging** | Logback (SLF4J) | - | Structured logging | Spring Boot default, proven, configurable |
| **CSS Framework** | Tailwind CSS | 3.4+ | Utility-first CSS framework | Fast development, standard for modern UIs |
| **Form Validation (FE)** | Zod | 3.x | TypeScript schema validation | Type-safe forms, runtime validation |
| **Validation (BE)** | Jakarta Bean Validation | - | Java request validation | Spring Boot native, declarative validation |
| **Entity Tagger (Python)** | SQLite + Pattern Matching | - | Government entity extraction | Reuse V1 algorithm (works well), HTTP API to Spring |
| **Reasoning Engine** | SWI-Prolog (via PySwip) | 9.x | Logical fallacy detection | Python FastAPI service, HTTP to Spring |
| **LLM Integration** | Anthropic Claude API (Java SDK) | Claude 3.5 Sonnet | Article analysis, claim extraction | Java SDK available, same as V1 |
| **API Documentation** | SpringDoc OpenAPI | 2.x | Auto-generated API docs | Swagger UI, generates from Spring annotations |
| **Source Control (Primary)** | GitHub | - | Git hosting, CI/CD | Best ecosystem, public/auditable |
| **Source Control (Mirror)** | Codeberg | - | European backup mirror | German non-profit, maximum independence |
| **Container Registry** | GitHub Container Registry | - | Docker image hosting | Free with GitHub |
| **Hosting** | Hetzner Cloud | - | VPS hosting | German, independent, EU data |
| **Reverse Proxy** | Nginx | Latest | HTTP routing, SSL | Open source |
| **SSL Certificates** | Let's Encrypt | - | HTTPS | Non-profit, free |

### Notable Exclusions (What We're NOT Using)

- ❌ **Neo4j** - Replaced with PostgreSQL recursive CTEs (simpler, V1 lesson learned)
- ❌ **MongoDB** - Replaced with PostgreSQL JSONB (simpler, V1 lesson learned)
- ❌ **Elasticsearch** - Replaced with PostgreSQL full-text search (sufficient for MVP)
- ❌ **Redux** - Replaced with Zustand (simpler, less boilerplate)
- ❌ **Vercel/Railway** - Replaced with Hetzner (independence, transparency)

---

## 5. Data Models

Based on the brownfield analysis, the V1 failure was **premature specialization** - treating government entities, persons, and organizations as separate models. We're fixing this with a **unified entity model** from day 1.

### Core Design Principles

1. ✅ **Unified Entity Table** - Generic entities table for articles, news sources, claims
2. ✅ **Specialized Government Models** - Person, GovernmentPosition, PositionHolding, Committee for government data (FB-1, FB-2)
3. ✅ **Regulatory Data Models** - Regulation, RegulationAgency for Federal Register integration (FB-3)
4. ✅ **JSONB for Flexibility** - Type-specific properties stored as JSONB where needed
5. ✅ **Schema.org Compatible** - JSON-LD format, ontology-ready from start
6. ✅ **Relationship-First** - Explicit relationship tables (CommitteeMembership, PositionHolding, RegulationAgency)
7. ✅ **Reasoning-Ready** - Structure supports Prolog queries, logical analysis
8. ✅ **Multi-Branch Support** - Single GovernmentPosition model supports Legislative, Executive, Judicial branches

### Entity Model (Unified)

**Purpose:** Single table for ALL entity types - fixes V1 government-entity-first mistake

**TypeScript Interface:**

```typescript
interface Entity {
  id: string; // UUID
  entityType: 'government_org' | 'person' | 'organization' | 'location' | 'event' | 'concept';
  name: string;
  properties: Record<string, any>; // Type-specific properties
  schemaOrgType: string; // e.g., 'GovernmentOrganization', 'Person'
  schemaOrgData: object; // JSON-LD representation
  source: 'federal_register' | 'wikidata' | 'manual' | 'llm_extracted';
  confidenceScore: number; // 0.0-1.0
  verified: boolean;
  createdAt: Date;
  updatedAt: Date;
}
```

**Java Entity:**

```java
@Entity
@Table(name = "entities")
public class Entity {
    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(nullable = false, length = 500)
    private String name;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> properties;

    @Column(name = "schema_org_type")
    private String schemaOrgType;

    @Type(JsonBinaryType.class)
    @Column(name = "schema_org_data", columnDefinition = "jsonb")
    private Map<String, Object> schemaOrgData;

    @Column(length = 100)
    private String source;

    @Column(name = "confidence_score")
    private Float confidenceScore = 1.0f;

    private Boolean verified = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### Government Data Models (FB-1 & FB-2)

The following specialized models support Congressional and Executive branch data:

#### Person Model

**Purpose:** Represents individuals (Congressional members, executive appointees, etc.)

```java
@Entity
@Table(name = "persons")
public class Person {
    @Id
    private UUID id;

    private String bioguideId;      // Congress.gov ID (legislators)
    private String firstName;
    private String lastName;
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Chamber currentChamber;  // HOUSE, SENATE (for legislators)

    private String currentState;
    private String currentParty;

    @Enumerated(EnumType.STRING)
    private DataSource dataSource;   // CONGRESS_GOV, PLUM_CSV, etc.

    // Social media handles
    private String twitterHandle;
    private String facebookId;
    private String youtubeId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### GovernmentPosition Model

**Purpose:** Unified position model for all government branches (Legislative, Executive, Judicial)

```java
@Entity
@Table(name = "government_positions")
public class GovernmentPosition {
    @Id
    private UUID id;

    private String title;

    @Enumerated(EnumType.STRING)
    private Branch branch;           // LEGISLATIVE, EXECUTIVE, JUDICIAL

    @Enumerated(EnumType.STRING)
    private PositionType positionType; // ELECTED, APPOINTED, CAREER

    // Legislative-specific fields
    @Enumerated(EnumType.STRING)
    private Chamber chamber;         // HOUSE, SENATE
    private String state;
    private Integer district;
    private Integer senateClass;

    // Executive-specific fields (FB-2)
    @Enumerated(EnumType.STRING)
    private AppointmentType appointmentType; // PAS, PA, NA, CA, XS
    private String payPlan;
    private String payGrade;
    private String location;
    private LocalDate expirationDate;

    private UUID organizationId;     // Link to GovernmentOrganization
}
```

#### PositionHolding Model

**Purpose:** Temporal junction table linking Person to GovernmentPosition

```java
@Entity
@Table(name = "position_holdings")
public class PositionHolding {
    @Id
    private UUID id;

    private UUID personId;
    private UUID positionId;

    private LocalDate startDate;
    private LocalDate endDate;       // Null = currently held

    private Integer congress;        // For legislative terms (e.g., 118)
    private Integer tenure;          // For executive (from PLUM)

    @Enumerated(EnumType.STRING)
    private DataSource dataSource;

    private String sourceReference;  // URL or identifier
}
```

#### AppointmentType Enum (FB-2)

```java
public enum AppointmentType {
    PAS("PAS", "Presidential Appointment with Senate Confirmation"),
    PA("PA", "Presidential Appointment without Senate Confirmation"),
    NA("NA", "Noncareer Appointment"),
    CA("CA", "Career Appointment"),
    XS("XS", "Schedule C - Expected to change with administration");
}
```

#### Committee Model

```java
@Entity
@Table(name = "committees")
public class Committee {
    @Id
    private UUID id;

    private String thomasId;
    private String name;
    private String systemCode;

    @Enumerated(EnumType.STRING)
    private CommitteeChamber chamber;  // HOUSE, SENATE, JOINT

    @Enumerated(EnumType.STRING)
    private CommitteeType type;        // STANDING, SELECT, SPECIAL, OTHER

    private UUID parentCommitteeId;    // For subcommittees
}
```

### Regulatory Data Models (FB-3)

The following specialized models support Federal Register regulatory data for fact-checking:

#### Regulation Model

**Purpose:** Represents a Federal Register regulatory document (rules, proposed rules, notices, presidential documents)

```java
@Entity
@Table(name = "regulations")
public class Regulation {
    @Id
    private UUID id;

    private String documentNumber;     // Federal Register document number (unique)
    private String title;
    private String documentAbstract;   // Summary text

    @Enumerated(EnumType.STRING)
    private DocumentType documentType; // RULE, PROPOSED_RULE, NOTICE, etc.

    private LocalDate publicationDate;
    private LocalDate effectiveOn;     // For final rules
    private LocalDate signingDate;     // For presidential documents

    private String regulationIdNumber; // RIN from OMB

    @JdbcTypeCode(SqlTypes.JSON)
    private List<CfrReference> cfrReferences;  // CFR citations (JSONB)

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> docketIds;    // Associated docket IDs (JSONB)

    private String sourceUrl;          // Federal Register URL
    private String pdfUrl;
    private String htmlUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### DocumentType Enum (FB-3)

```java
public enum DocumentType {
    RULE("Final Rule", "Final rules that have the force of law"),
    PROPOSED_RULE("Proposed Rule", "Notice of Proposed Rulemaking (NPRM)"),
    NOTICE("Notice", "Agency notices and announcements"),
    PRESIDENTIAL_DOCUMENT("Presidential Document", "Executive orders, proclamations, etc."),
    OTHER("Other", "Other document types");
}
```

#### CfrReference (Embeddable JSONB Object)

```java
public class CfrReference {
    private Integer title;     // CFR Title (e.g., 40 for Environment)
    private Integer part;      // CFR Part number
    private String section;    // CFR Section (optional)

    // Returns "40 CFR 60" or "40 CFR 60.5"
    public String getFullCitation() { ... }
}
```

#### RegulationAgency Model (Many-to-Many Junction Table)

**Purpose:** Links Regulation to GovernmentOrganization (many regulations can have multiple issuing agencies)

```java
@Entity
@Table(name = "regulation_agencies")
@IdClass(RegulationAgencyId.class)
public class RegulationAgency {
    @Id
    private UUID regulationId;

    @Id
    private UUID organizationId;

    private String agencyNameRaw;    // Original name from Federal Register API
    private boolean primaryAgency;   // First agency is typically primary

    @ManyToOne(fetch = FetchType.LAZY)
    private Regulation regulation;

    @ManyToOne(fetch = FetchType.LAZY)
    private GovernmentOrganization organization;
}
```

#### GovernmentOrganization Model

```java
@Entity
@Table(name = "government_organizations")
public class GovernmentOrganization {
    @Id
    private UUID id;

    private String officialName;
    private String shortName;
    private String acronym;

    @Enumerated(EnumType.STRING)
    private GovernmentBranch branch;   // EXECUTIVE, LEGISLATIVE, JUDICIAL

    @Enumerated(EnumType.STRING)
    private OrganizationType orgType;  // DEPARTMENT, AGENCY, COMMISSION, etc.

    private UUID parentOrganizationId;

    // FB-3: Federal Register integration
    private Integer federalRegisterAgencyId;  // For high-confidence agency linkage

    private String schemaOrgType;
}
```

### EntityRelationship Model

**Purpose:** Explicit relationship table for all entity connections

```typescript
interface EntityRelationship {
  id: string;
  sourceEntityId: string;
  targetEntityId: string;
  relationshipType:
    | 'parent_of'
    | 'child_of'
    | 'works_for'
    | 'member_of'
    | 'located_in'
    | 'mentioned_with'
    | 'caused_by'
    | 'refutes'
    | 'supports';
  properties: Record<string, any>;
  confidenceScore: number;
  verified: boolean;
  createdAt: Date;
  updatedAt: Date;
}
```

### Article Model

```typescript
interface Article {
  id: string;
  url: string;
  title: string;
  content: string;
  author?: string;
  publishedAt: Date;
  sourceId: string; // NewsSource ID
  articleType: 'news' | 'opinion' | 'blog' | 'social_media';
  language: string;
  metadata: {
    imageUrl?: string;
    summary?: string;
    tags?: string[];
  };
  analysisStatus: 'pending' | 'analyzing' | 'completed' | 'failed';
  createdAt: Date;
  updatedAt: Date;
}
```

### Claim Model

```typescript
interface Claim {
  id: string;
  articleId: string;
  claimText: string;
  claimType: 'factual' | 'predictive' | 'normative' | 'causal';
  extractionMethod: 'llm' | 'manual' | 'pattern';
  verificationStatus: 'unverified' | 'true' | 'false' | 'misleading' | 'unverifiable';
  verifiabilityStatus: 'verifiable' | 'unverifiable' | 'opinion';
  evidence: {
    sources?: Array<{
      url: string;
      title: string;
      relevance: number;
    }>;
    reasoning?: string;
  };
  confidenceScore: number;
  createdAt: Date;
  updatedAt: Date;
}
```

### NewsSource Model

```typescript
interface NewsSource {
  id: string;
  name: string;
  url: string;
  sourceType: 'news_outlet' | 'blog' | 'social_media' | 'government' | 'academic';
  biasRating: 'left' | 'center_left' | 'center' | 'center_right' | 'right' | 'unknown';
  reliabilityScore: number; // 0.0-1.0
  metadata: {
    foundedYear?: number;
    owner?: string;
    description?: string;
    country?: string;
  };
  createdAt: Date;
  updatedAt: Date;
}
```

### Analysis Model

```typescript
interface Analysis {
  id: string;
  articleId: string;
  analysisType: 'bias_detection' | 'logical_fallacy' | 'framing_analysis' | 'sentiment' | 'causal_analysis';
  results: {
    // Type-specific results
    emotionalLanguage?: Array<{ text: string; emotion: string; intensity: number }>;
    fallacies?: Array<{ type: string; text: string; explanation: string }>;
    frame?: string;
    summary?: string;
  };
  performedBy: 'claude_api' | 'prolog_service' | 'manual';
  confidenceScore: number;
  createdAt: Date;
}
```

### Data Model Relationships Diagram

```mermaid
erDiagram
    %% Government Data Models (FB-1 & FB-2)
    Person ||--o{ PositionHolding : "holds"
    GovernmentPosition ||--o{ PositionHolding : "held_by"
    Person ||--o{ CommitteeMembership : "member_of"
    Committee ||--o{ CommitteeMembership : "has_member"
    Committee ||--o{ Committee : "has_subcommittee"
    GovernmentOrganization ||--o{ GovernmentPosition : "contains"
    GovernmentOrganization ||--o{ GovernmentOrganization : "has_child"

    %% Regulatory Data Models (FB-3)
    Regulation ||--o{ RegulationAgency : "issued_by"
    GovernmentOrganization ||--o{ RegulationAgency : "issues"

    %% Generic Entity Models
    Entity ||--o{ EntityRelationship : "source"
    Entity ||--o{ EntityRelationship : "target"
    Entity ||--o{ EntityMention : "mentioned_in"

    Article ||--o{ EntityMention : "contains"
    Article ||--o{ Claim : "contains"
    Article ||--o{ Analysis : "analyzed_by"
    Article }o--|| NewsSource : "published_by"

    Claim ||--o{ EntityRelationship : "refutes/supports"

    User ||--o{ Article : "saved_by"

    Person {
        uuid id PK
        varchar bioguide_id UK
        varchar first_name
        varchar last_name
        varchar full_name
        varchar current_chamber
        varchar current_state
        varchar current_party
        varchar data_source
    }

    GovernmentPosition {
        uuid id PK
        varchar title
        varchar branch
        varchar position_type
        varchar chamber
        varchar state
        int district
        int senate_class
        varchar appointment_type
        varchar pay_plan
        varchar pay_grade
        uuid organization_id FK
    }

    PositionHolding {
        uuid id PK
        uuid person_id FK
        uuid position_id FK
        date start_date
        date end_date
        int congress
        int tenure
        varchar data_source
    }

    Committee {
        uuid id PK
        varchar thomas_id UK
        varchar name
        varchar system_code
        varchar chamber
        varchar type
        uuid parent_committee_id FK
    }

    CommitteeMembership {
        uuid id PK
        uuid person_id FK
        uuid committee_id FK
        varchar role
        int congress
        boolean is_current
    }

    GovernmentOrganization {
        uuid id PK
        varchar official_name
        varchar short_name
        varchar acronym
        varchar branch
        varchar org_type
        uuid parent_organization_id FK
        int federal_register_agency_id
    }

    Regulation {
        uuid id PK
        varchar document_number UK
        varchar title
        text abstract
        varchar document_type
        date publication_date
        date effective_on
        date signing_date
        varchar regulation_id_number
        jsonb cfr_references
        jsonb docket_ids
        varchar source_url
        varchar pdf_url
        varchar html_url
    }

    RegulationAgency {
        uuid regulation_id PK_FK
        uuid organization_id PK_FK
        varchar agency_name_raw
        boolean is_primary_agency
    }

    Entity {
        uuid id PK
        varchar entity_type
        varchar name
        jsonb properties
        varchar schema_org_type
        jsonb schema_org_data
        varchar source
        float confidence_score
        boolean verified
    }

    EntityRelationship {
        uuid id PK
        uuid source_entity_id FK
        uuid target_entity_id FK
        varchar relationship_type
        jsonb properties
    }

    Article {
        uuid id PK
        varchar url
        varchar title
        text content
        uuid source_id FK
        varchar article_type
        varchar analysis_status
    }

    Claim {
        uuid id PK
        uuid article_id FK
        text claim_text
        varchar claim_type
        varchar verification_status
        jsonb evidence
    }

    NewsSource {
        uuid id PK
        varchar name
        varchar url
        varchar source_type
        varchar bias_rating
        float reliability_score
    }

    Analysis {
        uuid id PK
        uuid article_id FK
        varchar analysis_type
        jsonb results
    }

    User {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar role
    }

    EntityMention {
        uuid id PK
        uuid article_id FK
        uuid entity_id FK
        varchar mention_text
    }
```

---

## 6. API Specification

The REST API follows OpenAPI 3.0 specification. Full spec available in separate file, key endpoints summarized below.

### Base URL

- **Development:** `http://localhost:8080/api/v2`
- **Production:** `https://api.newsanalyzer.org/api/v2`

### Authentication

All authenticated endpoints require JWT Bearer token:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Key Endpoints

#### Entity Endpoints

```
GET    /entities                    # List entities (paginated, filterable)
GET    /entities/{id}               # Get entity by ID
POST   /entities                    # Create entity (admin only)
PUT    /entities/{id}               # Update entity (admin only)
DELETE /entities/{id}               # Delete entity (admin only)
GET    /entities/{id}/relationships # Get entity relationships
POST   /entities/search             # Advanced entity search
```

#### Members Endpoints (FB-1)

```
GET    /members                     # List all members (paginated)
GET    /members/{id}                # Get member by ID
GET    /members/search?q={query}    # Search members by name
GET    /members/by-chamber/{chamber}         # Filter by chamber (house/senate)
GET    /members/by-state/{state}             # Filter by state
GET    /members/by-chamber/{chamber}/state/{state}  # Combined filter
GET    /members/{id}/committees     # Get member's committee assignments
GET    /members/{id}/terms          # Get member's term history
GET    /members/count               # Get total member count
```

#### Committees Endpoints (FB-1)

```
GET    /committees                  # List all committees (paginated)
GET    /committees/{id}             # Get committee by ID
GET    /committees/search?q={query} # Search committees by name
GET    /committees/by-chamber/{chamber}      # Filter by chamber
GET    /committees/{id}/members     # Get committee members
GET    /committees/{id}/subcommittees        # Get subcommittees
GET    /committees/count            # Get total committee count
```

#### Government Organizations Endpoints (FB-1)

```
GET    /government-orgs             # List all organizations (paginated)
GET    /government-orgs/{id}        # Get organization by ID
GET    /government-orgs/search?q={query}     # Search organizations
GET    /government-orgs/by-branch/{branch}   # Filter by branch
GET    /government-orgs/count       # Get total count
```

#### Appointees Endpoints (FB-2)

```
GET    /appointees                  # List all appointees (paginated)
GET    /appointees/{id}             # Get appointee by ID
GET    /appointees/search?q={query} # Search by name or title
GET    /appointees/by-agency/{orgId}         # Filter by agency
GET    /appointees/by-type/{type}            # Filter by appointment type (PAS, PA, etc.)
GET    /appointees/cabinet          # Get Cabinet members
GET    /appointees/count            # Get total appointee count
```

#### Executive Positions Endpoints (FB-2)

```
GET    /positions/executive         # List all executive positions (paginated)
GET    /positions/executive/vacant  # List vacant positions
GET    /positions/executive/count   # Get total position count
GET    /positions/executive/vacant/count     # Get vacant position count
```

#### Regulations Endpoints (FB-3)

```
GET    /regulations                 # List regulations (paginated, sorted by publication date)
GET    /regulations/{documentNumber}         # Get regulation by Federal Register document number
GET    /regulations/search?q={query}         # Full-text search (titles and abstracts)
GET    /regulations/by-agency/{orgId}        # Filter by issuing government agency
GET    /regulations/by-type/{type}           # Filter by document type (RULE, PROPOSED_RULE, etc.)
GET    /regulations/by-date-range?start={date}&end={date}  # Filter by publication date range
GET    /regulations/effective-on/{date}      # Get rules effective on a specific date
GET    /regulations/cfr/{title}/{part}       # Filter by CFR citation (e.g., 40 CFR 60)
```

#### Admin Sync Endpoints (FB-1 & FB-2 & FB-3)

```
POST   /admin/sync/members          # Trigger Congress.gov member sync
GET    /admin/sync/members/status   # Get member sync status
POST   /admin/sync/committees       # Trigger Congress.gov committee sync
GET    /admin/sync/committees/status         # Get committee sync status
POST   /admin/sync/government-orgs  # Trigger Federal Register org sync
GET    /admin/sync/government-orgs/status    # Get org sync status
POST   /admin/sync/plum             # Trigger PLUM CSV import (FB-2)
GET    /admin/sync/plum/status      # Get PLUM sync status
GET    /admin/sync/plum/last-result # Get last PLUM import result
POST   /admin/sync/regulations      # Trigger Federal Register regulation sync (FB-3)
GET    /admin/sync/regulations/status         # Get regulation sync status
POST   /admin/linkage/relink-all    # Re-run agency linkage for all regulations (FB-3)
GET    /admin/linkage/statistics    # Get agency linkage match statistics (FB-3)
```

#### Article Endpoints

```
GET    /articles                    # List articles
GET    /articles/{id}               # Get article details
POST   /articles                    # Submit article for analysis
POST   /articles/{id}/analyze       # Trigger analysis (async)
GET    /articles/{id}/claims        # Get claims from article
GET    /articles/{id}/entities      # Get entities mentioned
```

#### Claim Endpoints

```
GET    /claims                      # List claims
GET    /claims/{id}                 # Get claim details
POST   /claims/{id}/verify          # Verify claim (authenticated)
```

#### Analysis Endpoints

```
GET    /analyses                    # List analyses
GET    /analyses/{id}               # Get analysis details
```

#### Source Endpoints

```
GET    /sources                     # List news sources
GET    /sources/{id}                # Get source details
POST   /sources                     # Create source (admin only)
```

#### Auth Endpoints

```
POST   /auth/register               # Register new user
POST   /auth/login                  # Login (returns JWT)
GET    /auth/me                     # Get current user
```

### Response Format

All successful responses follow this structure:

```json
{
  "data": { /* response data */ },
  "page": { /* pagination info (if applicable) */ }
}
```

All error responses follow this structure:

```json
{
  "error": {
    "code": "ENTITY_NOT_FOUND",
    "message": "Entity with ID abc-123 not found",
    "details": { "entityId": "abc-123" },
    "timestamp": "2025-11-19T10:30:00Z",
    "requestId": "req-xyz-789"
  }
}
```

---

## 7. Components

### Component Overview

```mermaid
C4Container
    title NewsAnalyzer v2 - Container Diagram

    Person(user, "User", "Analyzes news articles")

    System_Boundary(newsanalyzer, "NewsAnalyzer v2") {
        Container(web, "Next.js Frontend", "TypeScript, React, Tailwind", "User interface")
        Container(api, "Spring Boot API", "Java 17, Spring Boot 3.2", "REST API, business logic")
        Container(reasoning, "Python Service", "FastAPI, SWI-Prolog", "Entity extraction, reasoning")
        ContainerDb(db, "PostgreSQL", "PostgreSQL 15", "Primary data store")
        ContainerDb(cache, "Redis", "Redis 7", "Session cache, API cache")
    }

    System_Ext(claude, "Claude API", "LLM for article analysis")
    System_Ext(fedreg, "Federal Register API", "Government entity data")

    Rel(user, web, "Uses", "HTTPS")
    Rel(web, api, "API calls", "JSON/REST")
    Rel(api, db, "Reads/writes", "JDBC")
    Rel(api, cache, "Cache", "Redis Protocol")
    Rel(api, reasoning, "HTTP calls", "JSON/REST")
    Rel(api, claude, "LLM calls", "HTTPS")
    Rel(reasoning, db, "Reads/writes", "JDBC")
    Rel(reasoning, fedreg, "Fetches entities", "HTTPS")
```

### 1. Next.js Frontend

**Responsibility:** User-facing web application for article submission, analysis viewing, and entity browsing

**Key Features:**
- Article submission and browsing
- Entity browser and search
- Claim verification UI
- Analysis results visualization
- User authentication

**Technology Stack:**
- Next.js 14 (App Router)
- TypeScript
- Shadcn/UI + Tailwind CSS
- Zustand (state management)
- SWR (data fetching)

### 2. Spring Boot Backend

**Responsibility:** Core business logic, API orchestration, data persistence, authentication

**Key Features:**
- Unified entity management (ALL entity types)
- Article lifecycle orchestration
- Claim extraction coordination
- Authentication & authorization (JWT)
- Database access (PostgreSQL)
- Cache management (Redis)

**Technology Stack:**
- Spring Boot 3.2
- Java 17
- Spring Data JPA
- Spring Security
- Flyway (migrations)

### 3. Python Reasoning Service

**Responsibility:** NLP-heavy operations, entity extraction, logical reasoning with Prolog

**Key Features:**
- Entity extraction using pattern matching (from V1)
- Logical fallacy detection (Prolog)
- Causal reasoning analysis
- Federal Register API integration

**Technology Stack:**
- FastAPI 0.109+
- Python 3.11
- SWI-Prolog (PySwip)
- SQLite (entity reference DB)

### 4. PostgreSQL Database

**Responsibility:** Primary data store for all entities, articles, claims, analyses

**Key Features:**
- Unified entity table (JSONB for flexibility)
- Full-text search (pg_trgm extension)
- Recursive CTEs for graph queries (replaces Neo4j)
- ACID transactions

### 5. Redis Cache

**Responsibility:** Session storage, API response caching, rate limiting

**Cache Strategy:**
- Session data: 24-hour TTL
- Entity search results: 1-hour TTL
- Article analysis results: 7-day TTL

---

## 8. Frontend Architecture

### Application Navigation Structure

The frontend is organized into two primary navigation areas that reflect the dual-layer data architecture:

```
NewsAnalyzer
│
├── Article Analyzer                    ← Analysis Layer (extracted data)
│   ├── Analyze Article                 ← Submit new article for analysis
│   └── Articles                        ← List of analyzed articles
│       └── [Article Detail]
│           ├── Article Text
│           ├── Extracted Entities      ← Shows KB matches where found
│           ├── News Source             ← Link to KB News Source entry
│           └── Actions                 ← AI analysis (bias check, etc.)
│
├── Knowledge Base                      ← Authoritative Reference Layer
│   ├── News Sources                    ← Managed list of news sources
│   │   └── [Source Detail]
│   │       ├── Source Information      ← Name, reliability, bias scores
│   │       └── Individuals             ← Reporters, editors
│   ├── U.S. Federal Government
│   │   ├── U.S. Code                   ← Federal statutes
│   │   │   └── [Title → Chapter → Section]
│   │   └── Branches
│   │       ├── Executive
│   │       │   ├── Executive Offices
│   │       │   ├── Executive Departments
│   │       │   ├── Independent Agencies
│   │       │   └── Government Corporations
│   │       ├── Congressional
│   │       │   ├── Senate
│   │       │   ├── House of Representatives
│   │       │   ├── Committees
│   │       │   └── Support Services
│   │       └── Judicial
│   │           ├── Supreme Court
│   │           ├── Courts of Appeals → [Circuits → Districts]
│   │           └── District Courts
│   ├── International Organizations     ← (Future)
│   ├── State Governments               ← (Future)
│   ├── Notable Organizations           ← (Future)
│   └── Universities                    ← (Future)
│
└── [Admin]                             ← Administrative functions
    ├── Data Sync                       ← Import from external sources
    └── Review Queue                    ← Promote entities to KB (Future)
```

**Navigation Principles:**

1. **Article Analyzer** contains all article analysis workflows - users submit articles and view analysis results here
2. **Knowledge Base** is for browsing authoritative reference data - users explore this to understand relationships and verify facts
3. Extracted entities (from Article Analyzer) link TO Knowledge Base entries when matches are found
4. The Knowledge Base is hierarchical, reflecting real-world organizational structures

### Component Organization

```
frontend/src/
├── app/                         # Next.js 14 App Router
│   ├── (auth)/                  # Auth route group
│   │   ├── login/
│   │   └── register/
│   ├── (dashboard)/             # Protected route group
│   │   ├── articles/
│   │   ├── entities/
│   │   ├── claims/
│   │   └── sources/
│   └── layout.tsx               # Root layout
│
├── components/                  # React components
│   ├── ui/                      # Shadcn/UI base components
│   ├── entities/                # Entity-specific components
│   ├── articles/                # Article components
│   ├── claims/                  # Claim components
│   ├── analysis/                # Analysis display
│   └── shared/                  # Shared components
│
├── lib/                         # Utilities
│   ├── api-client.ts            # API client wrapper
│   ├── auth.ts                  # Auth helpers
│   └── utils.ts
│
├── hooks/                       # Custom React hooks
│   ├── useArticles.ts
│   ├── useEntities.ts
│   └── useAuth.ts
│
├── stores/                      # Zustand state stores
│   ├── authStore.ts
│   └── uiStore.ts
│
└── types/                       # TypeScript types
    ├── api.ts
    └── entities.ts
```

### State Management

**Strategy:**
- **Server State:** SWR for API data fetching with caching
- **Client State:** Zustand for auth, UI preferences
- **Local State:** useState for component-specific UI

**Example Zustand Store:**

```typescript
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,

      login: async (email, password) => {
        const response = await fetch('/api/v2/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });
        const { user, token } = await response.json();
        set({ user, token, isAuthenticated: true });
      },

      logout: () => {
        set({ user: null, token: null, isAuthenticated: false });
      },
    }),
    { name: 'auth-storage' }
  )
);
```

### Routing

**Next.js 14 App Router with Route Groups:**

- `(auth)/` - Public authentication routes
- `(dashboard)/` - Protected dashboard routes with auth check
- `(marketing)/` - Public marketing pages

**Protected Route Pattern:**

```typescript
// app/(dashboard)/layout.tsx
export default function DashboardLayout({ children }) {
  const { isAuthenticated } = useAuthStore();

  useEffect(() => {
    if (!isAuthenticated) {
      redirect('/login');
    }
  }, [isAuthenticated]);

  return (
    <div className="flex h-screen">
      <Sidebar />
      <div className="flex-1">
        <Header />
        <main>{children}</main>
      </div>
    </div>
  );
}
```

---

## 9. Backend Architecture

### Service Architecture

**Unified Entity Service (Fixes V1 Fatal Flaw):**

```java
@Service
@Transactional
public class EntityService {

    private final EntityRepository entityRepository;
    private final EntityRelationshipRepository relationshipRepository;
    private final ReasoningServiceClient reasoningClient;

    /**
     * UNIFIED entity service - handles ALL entity types
     * (government_org, person, organization, location, event, concept)
     *
     * This fixes V1's mistake of separate GovernmentEntityService,
     * PersonService, OrganizationService
     */

    public Entity createEntity(EntityCreateDto dto) {
        validateEntityProperties(dto.getEntityType(), dto.getProperties());

        Entity entity = new Entity();
        entity.setEntityType(dto.getEntityType());
        entity.setName(dto.getName());
        entity.setProperties(dto.getProperties());
        entity.setSchemaOrgData(generateJsonLd(dto));
        entity.setConfidenceScore(1.0f);

        return entityRepository.save(entity);
    }

    public Page<Entity> findEntities(
        EntityType type,
        String search,
        Boolean verified,
        Pageable pageable
    ) {
        if (search != null && !search.isEmpty()) {
            return entityRepository.fullTextSearch(search, type, verified, pageable);
        }
        return entityRepository.findByFilters(type, verified, pageable);
    }

    public List<Entity> extractEntitiesFromText(String text) {
        // Call Python FastAPI service
        EntityExtractionResponse response = reasoningClient.extractEntities(text);
        // Process and save entities
        return saveExtractedEntities(response);
    }
}
```

### Database Architecture

**Schema Design (PostgreSQL):**

```sql
-- Unified entity table (fixes V1 government-entity-first mistake)
CREATE TABLE entities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type VARCHAR(50) NOT NULL,
    name VARCHAR(500) NOT NULL,
    properties JSONB DEFAULT '{}'::jsonb,
    schema_org_type VARCHAR(255),
    schema_org_data JSONB,
    source VARCHAR(100),
    confidence_score REAL DEFAULT 1.0,
    verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Full-text search support
ALTER TABLE entities ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(properties->>'description', '')), 'B')
    ) STORED;

-- Indexes
CREATE INDEX idx_entities_type ON entities(entity_type);
CREATE INDEX idx_entities_search_vector ON entities USING GIN(search_vector);
CREATE INDEX idx_entities_properties ON entities USING GIN(properties);
```

**Graph Queries using Recursive CTEs (Replaces Neo4j):**

```sql
-- Find relationship path between entities (replaces Neo4j Cypher)
WITH RECURSIVE relationship_path AS (
    SELECT
        id, source_entity_id, target_entity_id, relationship_type,
        1 as depth,
        ARRAY[source_entity_id, target_entity_id] as path
    FROM entity_relationships
    WHERE source_entity_id = :startId

    UNION ALL

    SELECT
        r.id, r.source_entity_id, r.target_entity_id, r.relationship_type,
        rp.depth + 1,
        rp.path || r.target_entity_id
    FROM entity_relationships r
    JOIN relationship_path rp ON r.source_entity_id = rp.target_entity_id
    WHERE rp.depth < :maxDepth
    AND NOT (r.target_entity_id = ANY(rp.path))
)
SELECT * FROM relationship_path
WHERE target_entity_id = :endId
ORDER BY depth LIMIT 1;
```

### Authentication and Authorization

**JWT Authentication Flow:**

1. User submits credentials to `/api/v2/auth/login`
2. Spring Security validates password (BCrypt)
3. Backend generates JWT token (24-hour expiry)
4. Frontend stores token in Zustand (persisted to localStorage)
5. Subsequent requests include `Authorization: Bearer {token}` header
6. JWT filter validates token on each request

**Spring Security Configuration:**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v2/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v2/entities/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v2/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v2/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

## 10. Deployment Architecture

### Infrastructure Overview

```
Hetzner Cloud (Germany)
├── VPS: CX41 (4 vCPU, 16GB RAM, €15.50/month)
├── Volume: 100GB SSD for PostgreSQL data
└── Docker Compose Stack:
    ├── Nginx (reverse proxy, SSL termination)
    ├── Next.js Frontend
    ├── Spring Boot Backend
    ├── FastAPI Python Service
    ├── PostgreSQL 15
    └── Redis 7
```

### Docker Compose Production

```yaml
version: '3.8'

services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
    depends_on:
      - frontend
      - backend
    restart: unless-stopped

  frontend:
    image: ghcr.io/yourusername/newsanalyzer-frontend:latest
    environment:
      - NODE_ENV=production
      - NEXT_PUBLIC_API_URL=https://api.newsanalyzer.org/api/v2
    restart: unless-stopped

  backend:
    image: ghcr.io/yourusername/newsanalyzer-backend:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/newsanalyzer
      - JWT_SECRET=${JWT_SECRET}
      - CLAUDE_API_KEY=${CLAUDE_API_KEY}
    depends_on:
      - postgres
      - redis
    restart: unless-stopped

  reasoning:
    image: ghcr.io/yourusername/newsanalyzer-reasoning:latest
    environment:
      - DATABASE_URL=postgresql://postgres:5432/newsanalyzer
    depends_on:
      - postgres
    restart: unless-stopped

  postgres:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=newsanalyzer
      - POSTGRES_USER=${DB_USERNAME}
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - /mnt/HC_Volume_1/postgres:/var/lib/postgresql/data
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    volumes:
      - /mnt/HC_Volume_1/redis:/data
    restart: unless-stopped
```

### CI/CD Pipeline (GitHub Actions)

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build Backend
        run: |
          cd backend
          ./mvnw clean package -DskipTests

      - name: Build Frontend
        run: |
          cd frontend
          pnpm install
          pnpm build

      - name: Build Docker Images
        run: docker compose -f docker-compose.prod.yml build

      - name: Push to Registry
        run: docker compose -f docker-compose.prod.yml push

      - name: Deploy to Hetzner
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.HETZNER_HOST }}
          username: ${{ secrets.HETZNER_USER }}
          key: ${{ secrets.HETZNER_SSH_KEY }}
          script: |
            cd /opt/newsanalyzer
            docker compose -f docker-compose.prod.yml pull
            docker compose -f docker-compose.prod.yml up -d
```

### Environments

| Environment | Frontend URL | Backend URL | Purpose |
|-------------|--------------|-------------|---------|
| Development | `http://localhost:3000` | `http://localhost:8080/api/v2` | Local development |
| Staging | `https://staging.newsanalyzer.org` | `https://staging-api.newsanalyzer.org/api/v2` | Pre-production testing |
| Production | `https://newsanalyzer.org` | `https://api.newsanalyzer.org/api/v2` | Live environment |

---

## 11. Development Workflow

### Prerequisites

```bash
- Java 17+ (OpenJDK or Temurin)
- Node.js 20+ and pnpm
- Python 3.11+
- Docker Desktop
- Git
```

### Initial Setup

```bash
# Clone repository
git clone https://github.com/yourusername/newsanalyzer-v2.git
cd newsanalyzer-v2

# Start databases
docker-compose -f docker-compose.dev.yml up -d postgres redis

# Backend setup
cd backend
./mvnw clean install
./mvnw flyway:migrate

# Frontend setup
cd ../frontend
pnpm install

# Python service setup
cd ../reasoning-service
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Development Commands

```bash
# Terminal 1: Databases
docker-compose -f docker-compose.dev.yml up postgres redis

# Terminal 2: Spring Boot backend
cd backend
./mvnw spring-boot:run -Dspring.profiles.active=dev
# http://localhost:8080

# Terminal 3: Python reasoning service
cd reasoning-service
uvicorn app.main:app --reload --port 8000
# http://localhost:8000

# Terminal 4: Next.js frontend
cd frontend
pnpm dev
# http://localhost:3000
```

### Testing

```bash
# Backend tests
cd backend
./mvnw test                    # Unit tests
./mvnw verify                  # Integration tests
./mvnw jacoco:report           # Coverage report

# Frontend tests
cd frontend
pnpm test                      # Unit tests
pnpm test:e2e                  # E2E tests

# Python tests
cd reasoning-service
pytest
```

---

## Appendix A: V1 vs V2 Comparison

| Aspect | V1 (Failed) | V2 (Greenfield) | Improvement |
|--------|-------------|-----------------|-------------|
| **Entity Model** | Separate tables (government_entities, persons, organizations) | Unified entities table with JSONB | ✅ Simplified, flexible |
| **Databases** | 5 (PostgreSQL, Neo4j, MongoDB, Redis, Elasticsearch) | 2 (PostgreSQL, Redis) | ✅ 60% reduction |
| **Graph Queries** | Neo4j with dual-write sync | PostgreSQL recursive CTEs | ✅ Simpler, ACID guarantees |
| **Python Integration** | Java subprocess (500ms latency) | HTTP API (50ms latency) | ✅ 10x faster |
| **Ontology Support** | Late retrofit attempt (failed) | Schema.org JSON-LD from day 1 | ✅ Built-in |
| **Hosting** | AWS/Vercel consideration | Hetzner (Germany) | ✅ Independent, transparent |
| **Frontend** | Create React App | Next.js 14 | ✅ Modern, performant |
| **Deployment** | Complex | Docker Compose | ✅ Simple |
| **Cost (MVP)** | ~$100-300/month | ~€15-30/month (~$17-33) | ✅ 80% cost reduction |

---

## Appendix B: Key Architectural Decisions

### Decision 1: Unified Entity Model

**Problem:** V1 had separate models for GovernmentEntity, Person, Organization

**Solution:** Single `entities` table with `entity_type` column and JSONB `properties`

**Rationale:**
- Supports all entity types without schema changes
- JSONB provides type-specific flexibility
- Schema.org JSON-LD stored alongside normalized data
- Easier to add new entity types (events, concepts)

### Decision 2: PostgreSQL Only (No Neo4j)

**Problem:** V1 used PostgreSQL + Neo4j dual-write with sync complexity

**Solution:** PostgreSQL recursive CTEs for graph queries

**Rationale:**
- Recursive CTEs handle 99% of relationship queries
- ACID transactions across entities and relationships
- One less database to deploy/manage
- Simpler application code

### Decision 3: Python FastAPI Service (Not Subprocess)

**Problem:** V1 used Java subprocess to call Python (500ms latency, fragile)

**Solution:** Python FastAPI HTTP service

**Rationale:**
- HTTP is standard, debuggable, cacheable
- 10x faster (50ms vs 500ms)
- Can scale Python service independently
- Better error handling

### Decision 4: Hetzner Cloud (Not AWS/Vercel)

**Problem:** Platform independence and transparency for news analysis

**Solution:** Hetzner Cloud (Germany) with Docker Compose

**Rationale:**
- German company, no US tech ties
- EU data sovereignty (GDPR-compliant)
- 80% cost reduction vs AWS
- Transparency mission alignment

### Decision 5: Spring Boot + Next.js (Not Full Node.js)

**Problem:** Architect has Java expertise, frontend requires JavaScript

**Solution:** Spring Boot backend + Next.js frontend

**Rationale:**
- Leverage existing Java/Spring expertise
- Next.js best-in-class for React
- Clear separation of concerns
- Industry-standard stack

---

## Appendix C: Implementation Status & Future Enhancements

**Foundation (Complete - Nov 2025):**
- ✅ Unified entity management (Entity model)
- ✅ Schema.org JSON-LD support
- ✅ PostgreSQL + Redis infrastructure
- ✅ Spring Boot backend with REST APIs
- ✅ Next.js frontend with React Query

**Epic FB-1: Congressional Data (Complete - Dec 2025):**
- ✅ Person model with Congress.gov integration
- ✅ GovernmentPosition model (multi-branch)
- ✅ PositionHolding temporal tracking
- ✅ Committee and CommitteeMembership models
- ✅ GovernmentOrganization model
- ✅ Members API (paginated, searchable)
- ✅ Committees API with member listings
- ✅ Admin sync dashboard (React)

**Epic FB-2: Executive Branch Data (Complete - Dec 2025):**
- ✅ AppointmentType enum (PAS, PA, NA, CA, XS)
- ✅ Executive position fields on GovernmentPosition
- ✅ PLUM CSV import service
- ✅ Admin PLUM sync UI
- ✅ Appointees API (search, filter by type/agency)
- ✅ Executive Positions API (vacant position tracking)
- ✅ Cabinet member detection

**Epic FB-3: Regulatory Data (Complete - Dec 2025):**
- [x] Federal Register API document fetching (extended FederalRegisterClient)
- [x] Regulation entity model with full-text search (PostgreSQL tsvector)
- [x] DocumentType enum (RULE, PROPOSED_RULE, NOTICE, PRESIDENTIAL_DOCUMENT, OTHER)
- [x] CfrReference embeddable for CFR citations
- [x] RegulationAgency many-to-many junction table
- [x] AgencyLinkageService with 5-level matching strategy (>95% match rate)
- [x] Daily sync scheduler (configurable cron)
- [x] 8 REST API endpoints for regulation lookup
- [x] federal_register_agency_id field on GovernmentOrganization
- [x] V17-V20 database migrations

**Future Phases:**

**Phase 2 (Enhanced Analysis):**
- Logical fallacy detection (Prolog)
- Bias detection improvements
- Cross-source claim verification
- User workbench features

**Phase 3 (Scale):**
- Horizontal scaling (multiple VPS instances)
- Advanced caching strategies
- Real-time analysis via WebSockets
- Mobile-responsive improvements

**Phase 4 (Advanced Features):**
- Browser extension
- API for third-party integrations
- Advanced analytics dashboard
- Multi-language support

---

## Appendix D: Success Metrics

**How we'll know this architecture succeeds where V1 failed:**

1. **Development Velocity:** Time to implement new entity types < 1 day (vs V1: 1 week)
2. **Database Complexity:** 2 databases (vs V1: 5 databases)
3. **Deployment Time:** < 5 minutes (vs V1: 30+ minutes)
4. **Infrastructure Cost:** < €30/month MVP (vs V1: $100-300/month)
5. **Python Integration Latency:** < 100ms (vs V1: 500ms)
6. **Schema Evolution:** JSONB changes require no migration (vs V1: full migration)
7. **Ontology Support:** Native from day 1 (vs V1: impossible retrofit)

---

## Conclusion

This architecture document represents a **comprehensive greenfield design** that learns from the failures of NewsAnalyzer V1. By adopting a unified entity model, minimizing database complexity, ensuring proper service integration, and prioritizing independence through European hosting, we've created a foundation that is:

- ✅ **Simple** - 2 databases instead of 5, Docker Compose instead of Kubernetes
- ✅ **Flexible** - JSONB for type-specific properties, extensible entity model
- ✅ **Independent** - Hetzner hosting, open-source stack, no vendor lock-in
- ✅ **Maintainable** - Clear service boundaries, proven technologies
- ✅ **Future-Proof** - Schema.org from day 1, Prolog-ready architecture

The architecture is ready for implementation and will serve as the single source of truth for AI-driven development of NewsAnalyzer v2.

---

**Document Status:** ✅ Complete and Approved
**Next Steps:** Begin implementation following this architecture
**Reference Documents:**
- `docs/newsanalyzer-brownfield-analysis.md` - V1 failure analysis
- `docs/prd/index.md` - Product requirements index (per-epic PRDs in `docs/prd/`)
- OpenAPI specification (to be generated from SpringDoc annotations)
