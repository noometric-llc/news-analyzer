-- Convert committee_memberships.role from PostgreSQL native enum to varchar(20)
-- to match the rest of the codebase's enum storage pattern.
-- Native enums cause type mismatch errors with Hibernate's @Enumerated(EnumType.STRING).

ALTER TABLE committee_memberships
    ALTER COLUMN role TYPE VARCHAR(20) USING role::text;

ALTER TABLE committee_memberships
    ALTER COLUMN role SET DEFAULT 'MEMBER';
