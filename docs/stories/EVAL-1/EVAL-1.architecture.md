# EVAL-1: Pipeline Architecture

**Created:** 2026-03-15
**Author:** Winston (Architect)
**Status:** APPROVED
**Epic:** EVAL-1 — KB Fact Extraction & Synthetic Article Generator

---

## Overview

This document defines the architecture for the EVAL-1 pipeline: extracting structured facts from the Knowledge Base, generating synthetic news articles with controlled perturbations via the Claude API, and storing labeled test datasets for downstream LLM evaluation.

**Key architectural decisions:**
- Facts-first perturbation (modify facts before article generation, not after)
- Shared `models/` and `clients/` packages in reasoning-service
- Constrained `FactPredicate` enum for type-safe perturbation logic
- JSONB storage for flexible fact/ground-truth payloads
- Backend API as data source (not direct DB access)

---

## 1. Module Structure

All EVAL-1 code follows the **vertical slice** pattern — grouped by feature, not layer.

```
reasoning-service/app/
├── api/                              # EXISTING — untouched
│   ├── entities.py
│   ├── reasoning.py
│   ├── fallacies.py
│   ├── government_orgs.py
│   └── eval/                         # NEW — EVAL track endpoints
│       ├── __init__.py
│       ├── facts.py                  # GET  /eval/facts/{entity_type}/{id}
│       │                             # GET  /eval/facts/sample?branch=...
│       ├── articles.py               # POST /eval/articles/generate
│       │                             # GET  /eval/articles/{id}
│       └── batches.py                # POST /eval/batches
│                                     # GET  /eval/batches/{id}/status
│
├── services/                         # EXISTING — untouched
│   ├── entity_extractor.py
│   ├── owl_reasoner.py
│   ├── schema_mapper.py
│   ├── gov_org_ingestion.py
│   ├── gov_org_validator.py
│   └── eval/                         # NEW — EVAL business logic
│       ├── __init__.py
│       ├── fact_extractor.py         # Queries backend APIs → Fact tuples
│       ├── fact_set_builder.py       # Assembles coherent FactSets by entity/topic
│       ├── article_generator.py      # Claude API prompt construction + generation
│       ├── perturbation_engine.py    # Applies controlled perturbations to FactSets
│       └── batch_orchestrator.py     # Coordinates batch runs end-to-end
│
├── models/                           # NEW — shared Pydantic models
│   ├── __init__.py
│   └── eval.py                       # Fact, FactSet, ArticleTestCase, Perturbation, etc.
│
└── clients/                          # NEW — typed API clients
    ├── __init__.py
    └── backend_client.py             # Async HTTPX client for backend REST APIs
```

### Router Registration (main.py)

```python
from app.api.eval import facts, articles, batches

app.include_router(facts.router, prefix="/eval/facts", tags=["eval-facts"])
app.include_router(articles.router, prefix="/eval/articles", tags=["eval-articles"])
app.include_router(batches.router, prefix="/eval/batches", tags=["eval-batches"])
```

---

## 2. Data Models (models/eval.py)

### Core Enums

```python
from enum import Enum

class GovernmentBranch(str, Enum):
    LEGISLATIVE = "legislative"
    EXECUTIVE = "executive"
    JUDICIAL = "judicial"

class FactPredicate(str, Enum):
    """Constrained set of fact predicates. Enables type-safe perturbation logic."""
    # Legislative — Congressional Members
    PARTY_AFFILIATION = "party_affiliation"
    STATE = "state"
    DISTRICT = "district"
    CHAMBER = "chamber"
    COMMITTEE_MEMBERSHIP = "committee_membership"
    LEADERSHIP_ROLE = "leadership_role"
    TERM_START = "term_start"
    TERM_END = "term_end"

    # Executive — Presidencies & Administration
    PRESIDENCY_NUMBER = "presidency_number"
    VICE_PRESIDENT = "vice_president"
    CABINET_POSITION = "cabinet_position"
    EXECUTIVE_ORDER = "executive_order"
    CHIEF_OF_STAFF = "chief_of_staff"
    AGENCY_HEAD = "agency_head"

    # Judicial
    COURT = "court"
    APPOINTING_PRESIDENT = "appointing_president"
    CONFIRMATION_DATE = "confirmation_date"
    COURT_LEVEL = "court_level"

    # Cross-branch — Statutes (context only, no perturbation)
    STATUTE_REFERENCE = "statute_reference"
    STATUTE_SUBJECT = "statute_subject"

class FactConfidence(str, Enum):
    TIER_1 = "tier_1"   # Official government source
    TIER_2 = "tier_2"   # Enrichment/derived source

class PerturbationType(str, Enum):
    NONE = "none"                           # Faithful — no perturbation
    WRONG_PARTY = "wrong_party"             # Swap D↔R
    WRONG_COMMITTEE = "wrong_committee"     # Assign to incorrect committee
    WRONG_STATE = "wrong_state"             # Incorrect state/district
    HALLUCINATE_ROLE = "hallucinate_role"   # Invent role not in KB
    OUTDATED_INFO = "outdated_info"         # Use superseded info
    CONFLATE_INDIVIDUALS = "conflate_individuals"  # Mix two people's attributes

class ArticleType(str, Enum):
    NEWS_REPORT = "news_report"
    BREAKING_NEWS = "breaking_news"
    OPINION = "opinion"
    ANALYSIS = "analysis"

class Difficulty(str, Enum):
    EASY = "easy"           # Obvious errors (wrong party for well-known figure)
    MEDIUM = "medium"       # Subtle errors (wrong committee, plausible but false)
    HARD = "hard"           # Requires specific knowledge (conflated individuals)
    ADVERSARIAL = "adversarial"  # Designed to fool (outdated but once-true info)
```

### Core Models

```python
from pydantic import BaseModel, Field
from datetime import date
from uuid import UUID

class Fact(BaseModel):
    """A single verifiable statement extracted from the KB."""
    subject: str                                    # "John Fetterman"
    subject_id: UUID | None = None                  # Backend entity UUID
    predicate: FactPredicate                        # PARTY_AFFILIATION
    object: str                                     # "Democratic"
    entity_type: str                                # "CongressionalMember"
    branch: GovernmentBranch                        # LEGISLATIVE
    data_source: str                                # "CONGRESS_GOV"
    confidence: FactConfidence = FactConfidence.TIER_1
    valid_from: date | None = None                  # Temporal bounds
    valid_to: date | None = None

class FactSet(BaseModel):
    """A coherent collection of facts about an entity/topic."""
    topic: str                                      # "Senator John Fetterman"
    primary_entity_id: UUID | None = None
    branch: GovernmentBranch
    facts: list[Fact]
    related_entity_ids: list[UUID] = Field(default_factory=list)

    def get_fact(self, predicate: FactPredicate) -> Fact | None:
        """Get first fact with given predicate."""
        return next((f for f in self.facts if f.predicate == predicate), None)

    def get_facts(self, predicate: FactPredicate) -> list[Fact]:
        """Get all facts with given predicate."""
        return [f for f in self.facts if f.predicate == predicate]

class PerturbedFactSet(BaseModel):
    """A FactSet with controlled modifications + label of what changed."""
    original: FactSet
    perturbed: FactSet
    perturbation_type: PerturbationType
    changed_facts: list[dict]                       # [{predicate, original_value, perturbed_value}]
    difficulty: Difficulty

class ArticleTestCase(BaseModel):
    """A generated article with full ground-truth label."""
    article_text: str
    article_type: ArticleType
    source_facts: FactSet                           # Original facts used
    is_faithful: bool                               # True if no perturbation
    perturbation_type: PerturbationType = PerturbationType.NONE
    changed_facts: list[dict] = Field(default_factory=list)
    expected_findings: list[str] = Field(default_factory=list)  # What eval SHOULD catch
    difficulty: Difficulty = Difficulty.MEDIUM
    model_used: str = ""                            # e.g., "claude-sonnet-4-20250514"
    tokens_used: int = 0

class BatchConfig(BaseModel):
    """Configuration for a batch generation run."""
    branch: GovernmentBranch | None = None          # None = all branches
    entity_count: int = 10                          # Entities to generate for
    perturbation_types: list[PerturbationType] = Field(
        default_factory=lambda: [p for p in PerturbationType if p != PerturbationType.NONE]
    )
    article_types: list[ArticleType] = Field(
        default_factory=lambda: [ArticleType.NEWS_REPORT]
    )
    model: str = "claude-sonnet-4-20250514"         # Default to Sonnet for cost
    dry_run: bool = False                           # Log prompts, skip API calls

class BatchResult(BaseModel):
    """Result of a batch generation run."""
    batch_id: UUID
    articles_generated: int
    faithful_count: int
    perturbed_count: int
    total_tokens: int
    model_used: str
    duration_seconds: float
    errors: list[str] = Field(default_factory=list)
```

---

## 3. Backend API Client (clients/backend_client.py)

Typed async HTTPX client. Only wraps the endpoints needed for fact extraction.

### Endpoints Consumed

| Branch | Backend Endpoint | Returns | Used For |
|--------|-----------------|---------|----------|
| Legislative | `GET /api/members?page={p}&size={s}` | `Page<MemberDTO>` | Member list for fact extraction |
| Legislative | `GET /api/members/{bioguideId}` | `MemberDTO` | Single member details |
| Legislative | `GET /api/members/{bioguideId}/committees` | `Page<CommitteeMembership>` | Committee assignments |
| Legislative | `GET /api/members/{bioguideId}/terms` | `List<PositionHolding>` | Term history (for temporal bounds) |
| Legislative | `GET /api/committees` | `Page<Committee>` | Committee names (for WRONG_COMMITTEE) |
| Executive | `GET /api/presidencies` | `Page<PresidencyDTO>` | All presidencies |
| Executive | `GET /api/presidencies/current` | `PresidencyDTO` | Current presidency |
| Executive | `GET /api/presidencies/{id}/administration` | `AdministrationDTO` | VP, Cabinet, CoS |
| Executive | `GET /api/presidencies/{id}/executive-orders` | `Page<ExecutiveOrderDTO>` | EOs for a presidency |
| Executive | `GET /api/appointees/cabinet` | `List<AppointeeDTO>` | Current cabinet members |
| Executive | `GET /api/government-organizations/cabinet-departments` | `List<GovOrg>` | Department names |
| Judicial | `GET /api/judges?page={p}&size={s}` | `Page<JudgeDTO>` | Judge list |

### Client Design

```python
class BackendClient:
    """Typed async client for the NewsAnalyzer backend API."""

    def __init__(self, base_url: str | None = None):
        self._base_url = base_url or settings.backend_url  # from pydantic-settings
        self._client = httpx.AsyncClient(
            base_url=self._base_url,
            timeout=30.0,
            headers={"Accept": "application/json"},
        )

    async def close(self):
        await self._client.aclose()

    # --- Legislative ---
    async def get_members(self, page: int = 0, size: int = 100) -> dict:
        resp = await self._client.get("/api/members", params={"page": page, "size": size})
        resp.raise_for_status()
        return resp.json()

    async def get_member(self, bioguide_id: str) -> dict:
        ...

    async def get_member_committees(self, bioguide_id: str) -> list[dict]:
        ...

    async def get_member_terms(self, bioguide_id: str) -> list[dict]:
        ...

    async def get_committees(self, page: int = 0, size: int = 100) -> dict:
        ...

    # --- Executive ---
    async def get_presidencies(self, page: int = 0, size: int = 50) -> dict:
        ...

    async def get_current_presidency(self) -> dict:
        ...

    async def get_administration(self, presidency_id: str) -> dict:
        ...

    async def get_executive_orders(self, presidency_id: str, page: int = 0, size: int = 50) -> dict:
        ...

    async def get_cabinet(self) -> list[dict]:
        ...

    async def get_cabinet_departments(self) -> list[dict]:
        ...

    # --- Judicial ---
    async def get_judges(self, page: int = 0, size: int = 100, **filters) -> dict:
        ...
```

**Configuration** via `pydantic-settings` (already in use):

```python
# app/config.py (or extend existing settings)
class Settings(BaseSettings):
    backend_url: str = "http://localhost:8080"
    anthropic_api_key: str = ""              # ANTHROPIC_API_KEY env var
    eval_default_model: str = "claude-sonnet-4-20250514"
    eval_rate_limit_rpm: int = 50            # Requests per minute to Claude API
    eval_max_batch_size: int = 50            # Max entities per batch
```

---

## 4. Service Layer Design

### 4.1 Fact Extractor (services/eval/fact_extractor.py)

Transforms backend API responses into `Fact` tuples.

```python
class FactExtractor:
    """Extracts structured facts from backend KB data."""

    def __init__(self, client: BackendClient):
        self._client = client

    async def extract_member_facts(self, bioguide_id: str) -> FactSet:
        """Extract all facts about a congressional member."""
        member = await self._client.get_member(bioguide_id)
        committees = await self._client.get_member_committees(bioguide_id)
        terms = await self._client.get_member_terms(bioguide_id)

        facts = []
        facts.append(Fact(
            subject=member["fullName"],
            subject_id=member.get("individualId"),
            predicate=FactPredicate.PARTY_AFFILIATION,
            object=member["party"],
            entity_type="CongressionalMember",
            branch=GovernmentBranch.LEGISLATIVE,
            data_source="CONGRESS_GOV",
        ))
        # ... STATE, DISTRICT, CHAMBER, COMMITTEE_MEMBERSHIP, etc.

        return FactSet(
            topic=f"{member['chamber']} member {member['fullName']}",
            primary_entity_id=member.get("individualId"),
            branch=GovernmentBranch.LEGISLATIVE,
            facts=facts,
        )

    async def extract_presidency_facts(self, presidency_id: str) -> FactSet:
        """Extract all facts about a presidency/administration."""
        ...

    async def extract_judge_facts(self, judge_id: str) -> FactSet:
        """Extract all facts about a federal judge."""
        ...

    async def extract_random_sample(
        self, branch: GovernmentBranch | None = None, count: int = 10
    ) -> list[FactSet]:
        """Extract fact sets for a random sample of entities."""
        ...
```

### 4.2 Fact Set Builder (services/eval/fact_set_builder.py)

Higher-level assembly of related facts across entity types.

```python
class FactSetBuilder:
    """Builds rich FactSets by composing facts across related entities."""

    def __init__(self, extractor: FactExtractor):
        self._extractor = extractor

    async def build_legislative_set(self, bioguide_id: str) -> FactSet:
        """Build a FactSet for a member including committee and statute context."""
        member_facts = await self._extractor.extract_member_facts(bioguide_id)
        # Optionally enrich with statute references relevant to their committees
        return member_facts

    async def build_executive_set(self, presidency_id: str) -> FactSet:
        """Build a FactSet for a presidency including administration and EOs."""
        ...

    async def build_entity_pool(
        self, branch: GovernmentBranch, count: int
    ) -> list[FactSet]:
        """Build a pool of FactSets for batch generation.
        Pool is larger than needed so perturbation engine can pick
        secondary entities for CONFLATE_INDIVIDUALS."""
        ...
```

### 4.3 Perturbation Engine (services/eval/perturbation_engine.py)

Applies controlled modifications to fact sets. **All logic is deterministic and testable without the LLM.**

```python
class PerturbationEngine:
    """Applies controlled factual perturbations to FactSets."""

    # Data needed for realistic perturbations
    PARTIES = ["Democratic", "Republican", "Independent"]
    STATES = [...]  # All 50 states

    def perturb(
        self, fact_set: FactSet, perturbation: PerturbationType,
        entity_pool: list[FactSet] | None = None,
    ) -> PerturbedFactSet:
        """Apply a single perturbation to a FactSet.

        Args:
            fact_set: The original, faithful facts
            perturbation: Which type of error to introduce
            entity_pool: Other FactSets available for CONFLATE_INDIVIDUALS
        """
        match perturbation:
            case PerturbationType.WRONG_PARTY:
                return self._wrong_party(fact_set)
            case PerturbationType.WRONG_COMMITTEE:
                return self._wrong_committee(fact_set)
            case PerturbationType.WRONG_STATE:
                return self._wrong_state(fact_set)
            case PerturbationType.HALLUCINATE_ROLE:
                return self._hallucinate_role(fact_set)
            case PerturbationType.OUTDATED_INFO:
                return self._outdated_info(fact_set)
            case PerturbationType.CONFLATE_INDIVIDUALS:
                if not entity_pool:
                    raise ValueError("CONFLATE_INDIVIDUALS requires entity_pool")
                return self._conflate(fact_set, entity_pool)

    def _wrong_party(self, fact_set: FactSet) -> PerturbedFactSet:
        """Swap party affiliation to a different party."""
        party_fact = fact_set.get_fact(FactPredicate.PARTY_AFFILIATION)
        if not party_fact:
            raise ValueError(f"No PARTY_AFFILIATION fact in set: {fact_set.topic}")

        other_parties = [p for p in self.PARTIES if p != party_fact.object]
        new_party = random.choice(other_parties)

        perturbed_facts = self._replace_fact(
            fact_set.facts, FactPredicate.PARTY_AFFILIATION, new_party
        )

        return PerturbedFactSet(
            original=fact_set,
            perturbed=FactSet(**{**fact_set.model_dump(), "facts": perturbed_facts}),
            perturbation_type=PerturbationType.WRONG_PARTY,
            changed_facts=[{
                "predicate": "party_affiliation",
                "original_value": party_fact.object,
                "perturbed_value": new_party,
            }],
            difficulty=Difficulty.EASY,  # Party is usually well-known
        )

    # ... _wrong_committee, _wrong_state, _hallucinate_role, _outdated_info, _conflate
```

**Perturbation → Difficulty mapping:**

| Perturbation | Default Difficulty | Reasoning |
|---|---|---|
| WRONG_PARTY | EASY | Party affiliation is widely known |
| WRONG_STATE | EASY | State is basic biographical info |
| WRONG_COMMITTEE | MEDIUM | Committee assignments are less well-known |
| HALLUCINATE_ROLE | MEDIUM | Plausible but fabricated — requires KB lookup |
| OUTDATED_INFO | HARD | Was once true, requires temporal awareness |
| CONFLATE_INDIVIDUALS | ADVERSARIAL | Blends real facts from two real people |

### 4.4 Article Generator (services/eval/article_generator.py)

Constructs prompts and calls Claude API.

```python
from anthropic import AsyncAnthropic

class ArticleGenerator:
    """Generates synthetic news articles from FactSets via Claude API."""

    def __init__(self, api_key: str | None = None, model: str | None = None):
        self._client = AsyncAnthropic(api_key=api_key or settings.anthropic_api_key)
        self._model = model or settings.eval_default_model

    async def generate(
        self, fact_set: FactSet, article_type: ArticleType
    ) -> tuple[str, int]:
        """Generate a single article. Returns (article_text, tokens_used)."""
        prompt = self._build_prompt(fact_set, article_type)

        if settings.eval_dry_run:
            return f"[DRY RUN] Prompt:\n{prompt}", 0

        response = await self._client.messages.create(
            model=self._model,
            max_tokens=1500,
            messages=[{"role": "user", "content": prompt}],
        )
        text = response.content[0].text
        tokens = response.usage.input_tokens + response.usage.output_tokens
        return text, tokens

    def _build_prompt(self, fact_set: FactSet, article_type: ArticleType) -> str:
        """Construct the generation prompt from facts."""
        facts_text = "\n".join(
            f"- {f.subject} | {f.predicate.value} | {f.object}"
            for f in fact_set.facts
        )

        style_instruction = {
            ArticleType.NEWS_REPORT: "Write in the style of an AP or Reuters news report. Neutral, factual tone. Include a dateline.",
            ArticleType.BREAKING_NEWS: "Write as a breaking news alert. Urgent tone, concise, lead with the most important fact.",
            ArticleType.OPINION: "Write as a newspaper opinion column. Include the author's perspective, but embed the provided facts.",
            ArticleType.ANALYSIS: "Write as a political analysis piece. Provide context and interpretation around the facts.",
        }[article_type]

        return f"""You are a news article generator for an AI evaluation system. Generate a realistic
news article using ONLY the facts provided below. Do not add any facts not listed here.
The article should read like a real published article — natural prose, not a fact list.

{style_instruction}

FACTS:
{facts_text}

REQUIREMENTS:
- Use ALL provided facts naturally in the article
- Do NOT invent any additional factual claims
- Article length: 200-400 words
- Include realistic but fictional quotes if appropriate for the article type
- The article should feel like it could appear in a major news outlet"""
```

### 4.5 Batch Orchestrator (services/eval/batch_orchestrator.py)

Coordinates end-to-end batch generation.

```python
class BatchOrchestrator:
    """Coordinates batch generation of synthetic test articles."""

    def __init__(
        self,
        fact_set_builder: FactSetBuilder,
        perturbation_engine: PerturbationEngine,
        article_generator: ArticleGenerator,
        backend_client: BackendClient,
    ):
        self._builder = fact_set_builder
        self._perturber = perturbation_engine
        self._generator = article_generator
        self._backend = backend_client

    async def run_batch(self, config: BatchConfig) -> BatchResult:
        """Execute a full batch generation run."""
        start_time = time.time()
        batch_id = uuid4()
        test_cases: list[ArticleTestCase] = []
        total_tokens = 0
        errors: list[str] = []

        # 1. Build entity pool (fetch more than needed for CONFLATE)
        pool_size = config.entity_count + 5  # extras for conflation
        entity_pool = await self._builder.build_entity_pool(
            branch=config.branch, count=pool_size
        )

        # 2. For each entity, generate faithful + perturbed articles
        for fact_set in entity_pool[:config.entity_count]:
            for article_type in config.article_types:
                try:
                    # Faithful article
                    text, tokens = await self._generator.generate(fact_set, article_type)
                    total_tokens += tokens
                    test_cases.append(ArticleTestCase(
                        article_text=text,
                        article_type=article_type,
                        source_facts=fact_set,
                        is_faithful=True,
                        model_used=config.model,
                        tokens_used=tokens,
                    ))

                    # Perturbed articles
                    for perturbation in config.perturbation_types:
                        perturbed = self._perturber.perturb(
                            fact_set, perturbation, entity_pool
                        )
                        text, tokens = await self._generator.generate(
                            perturbed.perturbed, article_type
                        )
                        total_tokens += tokens
                        test_cases.append(ArticleTestCase(
                            article_text=text,
                            article_type=article_type,
                            source_facts=perturbed.original,
                            is_faithful=False,
                            perturbation_type=perturbation,
                            changed_facts=perturbed.changed_facts,
                            expected_findings=[
                                f"Incorrect {c['predicate']}: "
                                f"stated '{c['perturbed_value']}', "
                                f"should be '{c['original_value']}'"
                                for c in perturbed.changed_facts
                            ],
                            difficulty=perturbed.difficulty,
                            model_used=config.model,
                            tokens_used=tokens,
                        ))

                    # Rate limiting
                    await asyncio.sleep(60 / settings.eval_rate_limit_rpm)

                except Exception as e:
                    errors.append(f"Error generating for {fact_set.topic}: {str(e)}")

        # 3. Store results via backend API
        if not config.dry_run:
            await self._store_batch(batch_id, config, test_cases)

        return BatchResult(
            batch_id=batch_id,
            articles_generated=len(test_cases),
            faithful_count=sum(1 for tc in test_cases if tc.is_faithful),
            perturbed_count=sum(1 for tc in test_cases if not tc.is_faithful),
            total_tokens=total_tokens,
            model_used=config.model,
            duration_seconds=time.time() - start_time,
            errors=errors,
        )

    async def _store_batch(
        self, batch_id: UUID, config: BatchConfig, test_cases: list[ArticleTestCase]
    ):
        """POST batch results to backend for persistent storage."""
        # POST /api/eval/datasets/batches
        await self._backend.post_batch(batch_id, config, test_cases)
```

---

## 5. Database Schema (Backend — EVAL-1.3)

### New Flyway Migration

```sql
-- V41__create_eval_tables.sql

-- Batch metadata
CREATE TABLE generation_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch          VARCHAR(20),                    -- LEGISLATIVE, EXECUTIVE, JUDICIAL, or NULL (all)
    model_used      VARCHAR(50) NOT NULL,
    config_json     JSONB NOT NULL,                 -- Full BatchConfig
    articles_count  INTEGER NOT NULL DEFAULT 0,
    faithful_count  INTEGER NOT NULL DEFAULT 0,
    perturbed_count INTEGER NOT NULL DEFAULT 0,
    total_tokens    INTEGER NOT NULL DEFAULT 0,
    duration_seconds DOUBLE PRECISION,
    errors          JSONB,                          -- List of error strings
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Generated articles with ground truth
CREATE TABLE synthetic_articles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id            UUID NOT NULL REFERENCES generation_batches(id) ON DELETE CASCADE,
    article_text        TEXT NOT NULL,
    article_type        VARCHAR(30) NOT NULL,       -- NEWS_REPORT, OPINION, ANALYSIS, BREAKING_NEWS
    is_faithful         BOOLEAN NOT NULL,
    perturbation_type   VARCHAR(30),                -- NULL if faithful
    difficulty          VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    source_facts        JSONB NOT NULL,             -- FactSet JSON
    ground_truth        JSONB NOT NULL,             -- changed_facts + expected_findings
    model_used          VARCHAR(50) NOT NULL,
    tokens_used         INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for query API
CREATE INDEX idx_synth_articles_batch ON synthetic_articles(batch_id);
CREATE INDEX idx_synth_articles_perturbation ON synthetic_articles(perturbation_type);
CREATE INDEX idx_synth_articles_faithful ON synthetic_articles(is_faithful);
CREATE INDEX idx_synth_articles_difficulty ON synthetic_articles(difficulty);
CREATE INDEX idx_synth_articles_type ON synthetic_articles(article_type);
-- GIN index on source_facts for branch filtering
CREATE INDEX idx_synth_articles_branch ON synthetic_articles USING GIN (source_facts jsonb_path_ops);
```

### Backend Entity Classes

```
backend/src/main/java/org/newsanalyzer/
├── model/
│   ├── eval/
│   │   ├── GenerationBatch.java        # JPA entity
│   │   └── SyntheticArticle.java       # JPA entity
├── repository/
│   ├── eval/
│   │   ├── GenerationBatchRepository.java
│   │   └── SyntheticArticleRepository.java
├── service/
│   ├── eval/
│   │   └── EvalDatasetService.java     # CRUD + query operations
├── controller/
│   ├── eval/
│   │   └── EvalDatasetController.java  # REST API
└── dto/
    ├── eval/
    │   ├── GenerationBatchDTO.java
    │   ├── SyntheticArticleDTO.java
    │   └── DatasetQueryRequest.java    # Filter parameters
```

### Backend API Endpoints (New)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/eval/datasets/batches` | Store a completed batch |
| `GET` | `/api/eval/datasets/batches` | List all batches (paginated) |
| `GET` | `/api/eval/datasets/batches/{id}` | Get batch with summary stats |
| `GET` | `/api/eval/datasets/batches/{id}/articles` | Get articles in batch (paginated) |
| `GET` | `/api/eval/datasets/articles` | Query articles with filters |
| `GET` | `/api/eval/datasets/articles/{id}` | Get single article with ground truth |
| `GET` | `/api/eval/datasets/stats` | Aggregate stats (counts by perturbation, branch, difficulty) |
| `DELETE` | `/api/eval/datasets/batches/{id}` | Delete a batch and its articles |

---

## 6. Data Flow Diagram

```
                    ┌──────────────────────┐
                    │   Batch Orchestrator  │
                    │   (POST /eval/batches)│
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │  Fact Set Builder     │
                    │                       │
                    │  Calls FactExtractor  │
                    │  for N entities       │
                    └──────────┬───────────┘
                               │ Backend REST API calls
                    ┌──────────▼───────────┐
                    │  Backend API          │
                    │  /api/members         │
                    │  /api/presidencies    │
                    │  /api/judges          │
                    │  (existing, read-only)│
                    └──────────┬───────────┘
                               │ FactSets
                    ┌──────────▼───────────┐
                    │  Perturbation Engine  │
                    │                       │
                    │  Original FactSet     │
                    │  → 6 PerturbedFactSets│
                    │  (deterministic,      │
                    │   no LLM needed)      │
                    └──────────┬───────────┘
                               │ 1 faithful + 6 perturbed FactSets
                    ┌──────────▼───────────┐
                    │  Article Generator    │
                    │                       │
                    │  FactSet → Prompt     │
                    │  Prompt → Claude API  │
                    │  Claude → Article text│
                    │                       │
                    │  7 articles per entity│
                    └──────────┬───────────┘
                               │ ArticleTestCases
                    ┌──────────▼───────────┐
                    │  Backend API          │
                    │  POST /api/eval/      │
                    │  datasets/batches     │
                    │                       │
                    │  → PostgreSQL         │
                    │  (generation_batches, │
                    │   synthetic_articles) │
                    └──────────────────────┘
```

---

## 7. New Dependencies

### reasoning-service/requirements.txt (additions)

```
# EVAL-1: Synthetic Article Generation
anthropic>=0.40.0                    # Claude API SDK
```

All other dependencies (httpx, pydantic, pytest-asyncio) are already present.

### Backend (pom.xml)

No new dependencies. Uses existing Spring Data JPA, Jackson JSONB (Hypersistence Utils), and Flyway.

---

## 8. Configuration & Environment

### New Environment Variables

| Variable | Service | Default | Purpose |
|----------|---------|---------|---------|
| `ANTHROPIC_API_KEY` | reasoning-service | (required) | Claude API authentication |
| `EVAL_DEFAULT_MODEL` | reasoning-service | `claude-sonnet-4-20250514` | Default model for generation |
| `EVAL_RATE_LIMIT_RPM` | reasoning-service | `50` | Max Claude API calls per minute |
| `EVAL_MAX_BATCH_SIZE` | reasoning-service | `50` | Max entities per batch run |
| `EVAL_DRY_RUN` | reasoning-service | `false` | Skip API calls, log prompts only |

### Docker Compose Changes

Add `ANTHROPIC_API_KEY` to reasoning-service environment in both dev and production compose files. No new containers.

---

## 9. Testing Strategy

| Layer | Framework | What's Tested |
|-------|-----------|---------------|
| **Fact models** | pytest | Pydantic validation, serialization, helper methods |
| **Fact extractor** | pytest + httpx mock | API response → Fact mapping for each entity type |
| **Perturbation engine** | pytest | Each perturbation type produces correct changes, difficulty ratings |
| **Article generator** | pytest | Prompt construction (no API calls); dry-run mode |
| **Batch orchestrator** | pytest-asyncio | End-to-end flow with mocked API + mocked Claude |
| **Backend client** | pytest + httpx mock | Request construction, error handling, pagination |
| **Backend entities** | JUnit + H2 | Repository CRUD, query methods |
| **Backend API** | JUnit + MockMvc | Endpoint request/response validation |
| **Integration** | REST Assured | Cross-service batch → storage round-trip |

---

## 10. Observability

All new endpoints and service calls are automatically instrumented via OTel (already configured). Additional custom spans recommended:

| Span | Purpose |
|------|---------|
| `eval.batch.run` | Full batch duration, entity count, article count |
| `eval.fact_extraction` | Time to extract facts for one entity |
| `eval.article_generation` | Time per Claude API call, tokens used |
| `eval.perturbation` | Time per perturbation (should be <1ms, useful for debugging) |

Token usage metrics should be exported to Prometheus for cost tracking:

```python
# Custom metric for Grafana dashboard
eval_tokens_total = Counter("eval_tokens_total", "Total Claude API tokens used", ["model", "batch_id"])
```

---

## Appendix: Perturbation-Predicate Compatibility Matrix

Not all perturbation types apply to all predicates. The perturbation engine must validate compatibility:

| Perturbation | Applicable Predicates | Branch |
|---|---|---|
| WRONG_PARTY | PARTY_AFFILIATION | Legislative, Executive |
| WRONG_COMMITTEE | COMMITTEE_MEMBERSHIP | Legislative |
| WRONG_STATE | STATE, DISTRICT | Legislative |
| HALLUCINATE_ROLE | LEADERSHIP_ROLE, CABINET_POSITION, COURT | All |
| OUTDATED_INFO | Any with valid_from/valid_to set | All |
| CONFLATE_INDIVIDUALS | Any (mixes facts from two entities) | All |

When a perturbation type is incompatible with a given FactSet (e.g., WRONG_COMMITTEE for a judicial FactSet), the perturbation engine should **skip gracefully** and log a warning, not raise an error. The batch orchestrator counts skips in the result.
