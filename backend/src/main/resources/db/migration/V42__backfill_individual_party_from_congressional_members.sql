-- Backfill Individual.party from CongressionalMember.party
-- The MemberSyncService stored party on CongressionalMember but not on Individual.
-- This migration copies existing party values to the Individual record so both
-- entities stay consistent with the two-entity pattern.

UPDATE individuals i
SET party = cm.party,
    updated_at = NOW()
FROM congressional_members cm
WHERE cm.individual_id = i.id
  AND cm.party IS NOT NULL
  AND i.party IS NULL;
