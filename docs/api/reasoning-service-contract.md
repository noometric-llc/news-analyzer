# Noometric Intelligence — Reasoning Service API Contract

**Version:** 1.0
**Date:** 2026-04-14
**Maintained by:** Noometric LLC

This document defines the stable API surface between the public `news-analyzer`
application and the private `noometric-intelligence` reasoning service. It is the
authoritative reference for INFRA-1.3 implementation and all future work that
crosses this boundary.

---

## Base URL

| Environment | Base URL |
|---|---|
| Local dev | `http://localhost:8000` |
| Production | Set via `REASONING_SERVICE_URL` env var (TBD — INFRA-2) |

---

## Authentication

**Strategy: API Key in request header (Option B)**

All requests from `news-analyzer` to the reasoning service must include:

```
X-Noometric-API-Key: <key>
```

| Detail | Value |
|---|---|
| Header name | `X-Noometric-API-Key` |
| Key storage (news-analyzer) | `NOOMETRIC_API_KEY` environment variable |
| Key storage (reasoning service) | `NOOMETRIC_API_KEY` environment variable |
| Key rotation | Manual; rotate by updating env vars in both services |
| Error response | `401 Unauthorized` if header is missing or key is invalid |

> **Security note:** This is the appropriate auth level for a single-tenant consulting
> demo. Before exposing the reasoning service to third-party clients, upgrade to
> OAuth 2.0 / short-lived tokens. The `X-Noometric-API-Key` header is a named
> placeholder — do not use a generic name like `Authorization` so it's easy to
> find and replace later.

---

## Public API Endpoints

These are the **only** endpoints that `news-analyzer` calls. Both sides of the
boundary must treat this list as the stable contract. Changes to request/response
shape require updating this document and coordinating a version bump.

---

### 1. spaCy Entity Extraction

#### `POST /entities/extract`

**Purpose:** Extract named entities from news article text using spaCy NLP.

**Called by:**
- Nginx (`/api/eval/extract/spacy` → proxied here, 30s timeout)
- Eval harness `spacy_provider.py` (direct, dev only)

**Request:**

```json
{
  "text": "Senator Elizabeth Warren criticized the EPA's new regulations.",
  "confidence_threshold": 0.7
}
```

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `text` | string | yes | — | Article text to analyze |
| `confidence_threshold` | float [0.0–1.0] | no | `0.7` | Minimum confidence to include an entity |

**Response:**

```json
{
  "entities": [
    {
      "text": "Elizabeth Warren",
      "entity_type": "person",
      "start": 8,
      "end": 24,
      "confidence": 0.85,
      "schema_org_type": "Person",
      "schema_org_data": {
        "@context": "https://schema.org",
        "@type": "Person",
        "name": "Elizabeth Warren"
      },
      "properties": {}
    }
  ],
  "total_count": 1
}
```

| Field | Type | Description |
|---|---|---|
| `entities` | array | Extracted entities meeting the confidence threshold |
| `entities[].text` | string | Extracted entity string |
| `entities[].entity_type` | string | One of: `person`, `government_org`, `organization`, `location`, `event`, `concept` |
| `entities[].start` | int | Character offset start in source text |
| `entities[].end` | int | Character offset end in source text |
| `entities[].confidence` | float | Extraction confidence score |
| `entities[].schema_org_type` | string | Schema.org type (e.g. `Person`, `GovernmentOrganization`) |
| `entities[].schema_org_data` | object | JSON-LD representation |
| `entities[].properties` | object | Additional entity properties (may be empty) |
| `total_count` | int | Count of entities in the response |

**Timeout:** 30 seconds (spaCy is fast — typical response ~50–200ms)

---

### 2. LLM Entity Extraction

#### `POST /eval/extract/llm`

**Purpose:** Extract named entities using Claude LLM. Produces the same response
shape as `/entities/extract` to enable side-by-side comparison in the eval harness.

**Called by:**
- Nginx (`/api/eval/extract/llm` → proxied here, 60s timeout)
- Eval harness `llm_provider.py` (direct, dev only)

**Request:**

```json
{
  "text": "Senator Elizabeth Warren criticized the EPA's new regulations.",
  "model": null,
  "confidence_threshold": 0.0
}
```

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `text` | string | yes | — | Article text to analyze |
| `model` | string \| null | no | `null` | Claude model override; `null` uses the service default |
| `confidence_threshold` | float [0.0–1.0] | no | `0.0` | Minimum confidence to include entity |

**Response:** Identical shape to `/entities/extract`. Fields `schema_org_type`,
`schema_org_data`, and `properties` are set to defaults — the eval harness only
scores on `text`, `entity_type`, `start`, `end`, and `confidence`.

**Timeout:** 60 seconds (LLM API call; typical response 5–30s depending on text length)

---

### 3. Cognitive Bias Detection

#### `POST /eval/bias/detect`

**Purpose:** Detect cognitive biases and logical fallacies in text using
ontology-grounded neuro-symbolic analysis (SPARQL + Claude LLM).

**Called by:**
- Eval harness `bias_provider.py` — grounded mode (`grounded=true`)
- Eval harness `bias_provider_ungrounded.py` — ungrounded mode (`grounded=false`)

> **Note:** This endpoint is currently called by the eval harness only, not by the
> production NewsAnalyzer UI. It is included in the public contract because it
> crosses the repo boundary and should be treated as a stable interface.

**Request:**

```json
{
  "text": "The administration has always been corrupt — this is just more proof.",
  "distortion_types": null,
  "confidence_threshold": 0.0,
  "include_ontology_metadata": true,
  "grounded": true
}
```

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `text` | string | yes | — | Text to analyze for cognitive biases |
| `distortion_types` | string[] \| null | no | `null` | Filter to specific bias types; `null` = check all |
| `confidence_threshold` | float [0.0–1.0] | no | `0.0` | Minimum confidence to include annotation |
| `include_ontology_metadata` | bool | no | `true` | Attach ontology definition, source, and detection pattern to each annotation |
| `grounded` | bool | no | `true` | `true` = SPARQL-grounded LLM prompt; `false` = generic prompt (A/B eval) |

**Response:**

```json
{
  "annotations": [
    {
      "distortion_type": "hasty_generalization",
      "category": "cognitive_bias",
      "excerpt": "The administration has always been corrupt",
      "explanation": "Uses absolute language ('always') to generalize from limited evidence.",
      "confidence": 0.87,
      "ontology_metadata": {
        "definition": "Drawing a broad conclusion from a small or unrepresentative sample.",
        "academic_source": "Kahneman, 2011",
        "detection_pattern": "Look for absolute quantifiers (always, never, all, none) combined with evaluative claims."
      }
    }
  ],
  "total_count": 1,
  "distortions_checked": ["hasty_generalization", "confirmation_bias", "..."],
  "shacl_valid": true
}
```

| Field | Type | Description |
|---|---|---|
| `annotations` | array | Detected bias annotations |
| `annotations[].distortion_type` | string | Snake-case bias type identifier from the cognitive bias ontology |
| `annotations[].category` | string | Ontology category: `cognitive_bias` or `logical_fallacy` |
| `annotations[].excerpt` | string | Relevant text excerpt from the input |
| `annotations[].explanation` | string | LLM explanation of why this bias was detected |
| `annotations[].confidence` | float | Detection confidence score |
| `annotations[].ontology_metadata` | object \| null | Present when `include_ontology_metadata=true` and `grounded=true` |
| `annotations[].ontology_metadata.definition` | string | Formal ontology definition |
| `annotations[].ontology_metadata.academic_source` | string | Formatted citation |
| `annotations[].ontology_metadata.detection_pattern` | string | Pattern used to detect this bias |
| `total_count` | int | Count of annotations in response |
| `distortions_checked` | string[] | All bias types checked during this request |
| `shacl_valid` | bool | Whether the bias ontology passed SHACL validation at startup |

**Timeout:** 60 seconds (LLM API call; typical response 5–30s)

---

## Internal Endpoints

These endpoints exist in the reasoning service but are **not** part of the
`news-analyzer` → `noometric-intelligence` interface. They are implementation
details of `noometric-intelligence` and may change without notice to `news-analyzer`.

| Router prefix | Endpoints | Notes |
|---|---|---|
| `/entities/link` | `POST /link`, `POST /link/single` | Wikidata/DBpedia entity linking |
| `/entities/reason` | `POST /reason` | OWL reasoning enrichment |
| `/entities/ontology` | `GET /ontology/stats`, `POST /query/sparql` | NewsAnalyzer ontology introspection |
| `/reasoning` | `POST /query`, `POST /verify` | Prolog query, fact verification |
| `/fallacies` | `POST /detect` | Direct fallacy detection (not bias ontology) |
| `/government-orgs` | `POST /ingest`, `POST /process`, `GET /packages`, `POST /enrich`, `GET /health`, `GET /test` | Gov org data ingestion and enrichment |
| `/eval/facts` | all | Fact extraction for dataset generation |
| `/eval/articles` | all | Article generation for eval datasets |
| `/eval/batches` | `POST /`, `GET /{id}/status` | Batch orchestration; calls Java backend via `BackendClient` |
| `/eval/bias/ontology/stats` | `GET /ontology/stats` | Bias ontology statistics (internal health check) |
| `/eval/bias/ontology/validate` | `POST /ontology/validate` | SHACL validation (internal) |

### Reverse call: reasoning service → news-analyzer backend

The batch orchestrator (`/eval/batches`) calls the Java backend to persist completed
generation runs. This is **not** a news-analyzer-initiated call — it is internal
eval pipeline plumbing.

| Direction | Endpoint | Purpose |
|---|---|---|
| reasoning-service → backend | `POST /api/eval/datasets/batches` | Store completed batch results |

This reverse call is outside the scope of this contract but is documented here for
completeness.

---

## Timeout and Performance Notes

| Endpoint | Typical Response Time | Nginx Timeout | Retry Recommended? |
|---|---|---|---|
| `POST /entities/extract` | 50–200ms | 30s | No — fast; failures are hard errors |
| `POST /eval/extract/llm` | 5–30s | 60s | Yes — LLM calls can transiently fail; 1 retry with backoff |
| `POST /eval/bias/detect` | 5–30s | 60s | Yes — same as above |

**Recommended timeout values for `news-analyzer` HTTP client (INFRA-1.3):**

```
REASONING_SERVICE_CONNECT_TIMEOUT=5s
REASONING_SERVICE_READ_TIMEOUT_FAST=30s   # /entities/extract
REASONING_SERVICE_READ_TIMEOUT_LLM=60s    # /eval/extract/llm, /eval/bias/detect
```

**LLM endpoint note:** Response time scales with text length and the number of bias
types checked. For the eval harness use case (short article excerpts), 60s is
comfortable. For full-article production use, this should be revisited.

---

## Change Log

| Date | Version | Description | Author |
|---|---|---|---|
| 2026-04-14 | 1.0 | Initial contract — 3 public endpoints documented | James (Dev) / INFRA-1.2 |
