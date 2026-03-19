-- V41__create_eval_tables.sql
-- EVAL-1.3: Test Dataset Storage & Management
-- Creates tables for storing synthetic articles and generation batch metadata.

-- Batch metadata: one row per batch generation run
CREATE TABLE generation_batches (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch           VARCHAR(20),
    model_used       VARCHAR(50) NOT NULL,
    config_json      JSONB NOT NULL,
    articles_count   INTEGER NOT NULL DEFAULT 0,
    faithful_count   INTEGER NOT NULL DEFAULT 0,
    perturbed_count  INTEGER NOT NULL DEFAULT 0,
    total_tokens     INTEGER NOT NULL DEFAULT 0,
    duration_seconds DOUBLE PRECISION,
    errors           JSONB,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Generated articles with ground-truth labels
CREATE TABLE synthetic_articles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id          UUID NOT NULL REFERENCES generation_batches(id) ON DELETE CASCADE,
    article_text      TEXT NOT NULL,
    article_type      VARCHAR(30) NOT NULL,
    is_faithful       BOOLEAN NOT NULL,
    perturbation_type VARCHAR(30),
    difficulty        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    source_facts      JSONB NOT NULL,
    ground_truth      JSONB NOT NULL,
    model_used        VARCHAR(50) NOT NULL,
    tokens_used       INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for query API
CREATE INDEX idx_synth_articles_batch ON synthetic_articles(batch_id);
CREATE INDEX idx_synth_articles_perturbation ON synthetic_articles(perturbation_type);
CREATE INDEX idx_synth_articles_faithful ON synthetic_articles(is_faithful);
CREATE INDEX idx_synth_articles_difficulty ON synthetic_articles(difficulty);
CREATE INDEX idx_synth_articles_type ON synthetic_articles(article_type);

-- GIN index on source_facts for branch filtering via JSONB
CREATE INDEX idx_synth_articles_branch ON synthetic_articles USING GIN (source_facts jsonb_path_ops);
