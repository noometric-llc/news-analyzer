-- Individuals and Congressional Members Seed Data
-- Insert sample Congress members for API integration tests
-- Part of ARCH-1.7: Updated for Individual + CongressionalMember dual-entity model

-- First, insert Individuals (biographical data)
INSERT INTO individuals (id, first_name, last_name, middle_name, party, birth_date, gender, image_url, primary_data_source, created_at, updated_at)
VALUES
    -- Senate members
    ('aaaaaaaa-0000-1111-1111-111111111111', 'Bernard', 'Sanders', NULL, 'Independent', '1941-09-08', 'M', 'https://bioguide.congress.gov/bioguide/photo/S/S000033.jpg', 'CONGRESS_GOV', NOW(), NOW()),
    ('bbbbbbbb-0000-1111-1111-111111111111', 'Addison', 'McConnell', 'Mitchell', 'Republican', '1942-02-20', 'M', 'https://bioguide.congress.gov/bioguide/photo/M/M000355.jpg', 'CONGRESS_GOV', NOW(), NOW()),
    ('cccccccc-0000-1111-1111-111111111111', 'Elizabeth', 'Warren', 'Ann', 'Democratic', '1949-06-22', 'F', 'https://bioguide.congress.gov/bioguide/photo/W/W000817.jpg', 'CONGRESS_GOV', NOW(), NOW()),
    ('dddddddd-0000-1111-1111-111111111111', 'Rafael', 'Cruz', 'Edward', 'Republican', '1970-12-22', 'M', 'https://bioguide.congress.gov/bioguide/photo/C/C001098.jpg', 'CONGRESS_GOV', NOW(), NOW()),
    -- House members
    ('eeeeeeee-0000-1111-1111-111111111111', 'Nancy', 'Pelosi', NULL, 'Democratic', '1940-03-26', 'F', 'https://bioguide.congress.gov/bioguide/photo/P/P000197.jpg', 'CONGRESS_GOV', NOW(), NOW()),
    ('ffffffff-0000-1111-1111-111111111111', 'Alexandria', 'Ocasio-Cortez', NULL, 'Democratic', '1989-10-13', 'F', 'https://bioguide.congress.gov/bioguide/photo/O/O000172.jpg', 'CONGRESS_GOV', NOW(), NOW()),
    ('11111111-0000-1111-1111-111111111111', 'Jim', 'Jordan', NULL, 'Republican', '1964-02-17', 'M', 'https://bioguide.congress.gov/bioguide/photo/J/J000289.jpg', 'CONGRESS_GOV', NOW(), NOW()),
    ('22222222-0000-1111-1111-111111111111', 'Al', 'Green', NULL, 'Democratic', '1947-09-01', 'M', 'https://bioguide.congress.gov/bioguide/photo/G/G000553.jpg', 'CONGRESS_GOV', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET updated_at = NOW();

-- Then, insert Congressional Members (Congress-specific data linked to Individuals)
INSERT INTO congressional_members (id, individual_id, bioguide_id, chamber, state, party, data_source, created_at, updated_at)
VALUES
    -- Senate members
    ('aaaaaaaa-1111-1111-1111-111111111111', 'aaaaaaaa-0000-1111-1111-111111111111', 'S000033', 'SENATE', 'VT', 'Independent', 'CONGRESS_GOV', NOW(), NOW()),
    ('bbbbbbbb-1111-1111-1111-111111111111', 'bbbbbbbb-0000-1111-1111-111111111111', 'M000355', 'SENATE', 'KY', 'Republican', 'CONGRESS_GOV', NOW(), NOW()),
    ('cccccccc-1111-1111-1111-111111111111', 'cccccccc-0000-1111-1111-111111111111', 'W000817', 'SENATE', 'MA', 'Democratic', 'CONGRESS_GOV', NOW(), NOW()),
    ('dddddddd-1111-1111-1111-111111111111', 'dddddddd-0000-1111-1111-111111111111', 'C001098', 'SENATE', 'TX', 'Republican', 'CONGRESS_GOV', NOW(), NOW()),
    -- House members
    ('eeeeeeee-1111-1111-1111-111111111111', 'eeeeeeee-0000-1111-1111-111111111111', 'P000197', 'HOUSE', 'CA', 'Democratic', 'CONGRESS_GOV', NOW(), NOW()),
    ('ffffffff-1111-1111-1111-111111111111', 'ffffffff-0000-1111-1111-111111111111', 'O000172', 'HOUSE', 'NY', 'Democratic', 'CONGRESS_GOV', NOW(), NOW()),
    ('11111111-2222-1111-1111-111111111111', '11111111-0000-1111-1111-111111111111', 'J000289', 'HOUSE', 'OH', 'Republican', 'CONGRESS_GOV', NOW(), NOW()),
    ('22222222-2222-1111-1111-111111111111', '22222222-0000-1111-1111-111111111111', 'G000553', 'HOUSE', 'TX', 'Democratic', 'CONGRESS_GOV', NOW(), NOW())
ON CONFLICT (bioguide_id) DO UPDATE SET party = EXCLUDED.party, updated_at = NOW();
