-- Migration V46: Add Entity-to-Article Foreign Key
-- Purpose: Link extracted entities to the article they were extracted from
-- Author: James (Dev Agent)
-- Date: 2026-07-03
-- Story: ES-1.1 Article Persistence Model
-- Mirrors V4__add_entity_gov_org_link.sql's pattern for the Entity-to-Article relation.

-- Add article_id column to entities table
ALTER TABLE entities
ADD COLUMN article_id UUID;

-- Add foreign key constraint
ALTER TABLE entities
ADD CONSTRAINT fk_entities_article
    FOREIGN KEY (article_id)
    REFERENCES evidence_articles(id)
    ON DELETE SET NULL;  -- If article deleted, set entity link to NULL (don't cascade delete entities)

-- Add index for query performance
CREATE INDEX idx_entities_article_id ON entities(article_id);

-- Add comment for documentation
COMMENT ON COLUMN entities.article_id IS 'Foreign key to evidence_articles table - links extracted entities back to the Evidence Store article they were extracted from';
