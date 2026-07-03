-- =====================================================================
-- Migration: V45__create_evidence_articles.sql
-- Description: Create the evidence_articles table — the Evidence Store's
--              core record. Named to avoid collision with the pre-existing,
--              unused "articles" table from V1__initial_schema.sql (a dead
--              V1-era design never wired to any application code — verified
--              via full codebase search, zero references). Also distinct
--              from government_organizations (authoritative Factbase data)
--              and synthetic_articles (eval-only synthetic data): this
--              table holds real ingested news article content.
-- Author: James (Dev Agent)
-- Date: 2026-07-03
-- Story: ES-1.1 Article Persistence Model
-- =====================================================================

CREATE TABLE evidence_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    source_name VARCHAR(255) NOT NULL,
    url VARCHAR(1000),
    publication_date TIMESTAMP,
    raw_text TEXT NOT NULL,
    ingested_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Tracked separately so a bias-detection failure never masks or is masked
    -- by an extraction failure (see ArticleStatus / ArticleStatusConverter).
    extraction_status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (extraction_status IN ('pending', 'success', 'failed')),
    bias_detection_status VARCHAR(20) NOT NULL DEFAULT 'pending'
        CHECK (bias_detection_status IN ('pending', 'success', 'failed')),

    -- Deferred cross-article aggregation field (FR7). Always NULL at MVP —
    -- the column exists now so no future migration is needed once the
    -- reliability-scoring methodology (owned by noometric-intelligence) lands.
    reliability_score REAL
);

CREATE INDEX idx_evidence_articles_source_name ON evidence_articles(source_name);
CREATE INDEX idx_evidence_articles_publication_date ON evidence_articles(publication_date);

COMMENT ON TABLE evidence_articles IS 'Persisted, source-attributed news articles — the Evidence Store''s core record';
COMMENT ON COLUMN evidence_articles.extraction_status IS 'Outcome of the /entities/extract call for this article';
COMMENT ON COLUMN evidence_articles.bias_detection_status IS 'Outcome of the /eval/bias/detect call for this article; best-effort, tracked independently of extraction_status';
COMMENT ON COLUMN evidence_articles.reliability_score IS 'Deferred cross-article reliability score; always NULL at MVP';
