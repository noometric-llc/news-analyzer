-- Add appointment metadata fields to position_holdings for judicial positions.
-- These fields are parsed from the FJC CSV but were not previously stored.

ALTER TABLE position_holdings
    ADD COLUMN IF NOT EXISTS appointing_president VARCHAR(100),
    ADD COLUMN IF NOT EXISTS party_of_appointing_president VARCHAR(50),
    ADD COLUMN IF NOT EXISTS aba_rating VARCHAR(50),
    ADD COLUMN IF NOT EXISTS nomination_date DATE,
    ADD COLUMN IF NOT EXISTS confirmation_date DATE;

-- Index on appointing president for filtering
CREATE INDEX IF NOT EXISTS idx_holding_appointing_president
    ON position_holdings(appointing_president)
    WHERE appointing_president IS NOT NULL;
