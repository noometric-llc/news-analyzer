-- =====================================================================
-- Migration: V47__create_article_bias_annotations.sql
-- Description: Create the article_bias_annotations table — structured
--              storage for each individual cognitive-bias/logical-fallacy
--              annotation returned by the reasoning service's
--              POST /eval/bias/detect endpoint (raw signal, not yet
--              aggregated into Article.reliabilityScore).
-- Author: James (Dev Agent)
-- Date: 2026-07-06
-- Story: ES-1.4 Bias/Fallacy Annotation Integration
-- =====================================================================

CREATE TABLE article_bias_annotations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- CASCADE (not SET NULL, unlike entities.article_id): an annotation has
    -- no independent meaning without its source article — it's a note about
    -- specific text in that article, not a standalone concept — so deleting
    -- the article should delete its annotations too, not orphan them.
    article_id UUID NOT NULL
        REFERENCES evidence_articles(id)
        ON DELETE CASCADE,

    distortion_type VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    excerpt TEXT NOT NULL,
    explanation TEXT NOT NULL,
    confidence REAL NOT NULL,

    -- Nullable per the reasoning-service contract: only present when
    -- include_ontology_metadata=true and grounded=true (both of which this
    -- story's client always requests, but the contract documents it as
    -- optional, so the schema doesn't assume it's always there).
    ontology_metadata JSONB
);

CREATE INDEX idx_article_bias_annotations_article_id ON article_bias_annotations(article_id);

COMMENT ON TABLE article_bias_annotations IS 'Structured per-article bias/fallacy annotations from POST /eval/bias/detect — raw signal, not yet aggregated into a reliability score';
COMMENT ON COLUMN article_bias_annotations.distortion_type IS 'Snake-case ontology identifier for the detected bias/fallacy, stored as-is from the contract response';
COMMENT ON COLUMN article_bias_annotations.category IS 'cognitive_bias or logical_fallacy, per the bias ontology';
COMMENT ON COLUMN article_bias_annotations.ontology_metadata IS 'definition/academic_source/detection_pattern, present only when the reasoning service was called with include_ontology_metadata=true and grounded=true';
