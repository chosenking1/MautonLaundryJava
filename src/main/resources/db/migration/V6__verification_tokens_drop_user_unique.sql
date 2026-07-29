-- verification_tokens.user_id was UNIQUE because VerificationToken mapped the
-- user as @OneToOne. That allowed only one token per user, so re-sending a
-- verification email (or issuing a password-reset token while a verification
-- token existed) failed with a duplicate-key violation.
--
-- The entity is now @ManyToOne. Hibernate's ddl-auto=update never drops
-- constraints, so we drop the auto-generated UNIQUE constraint here. The
-- constraint name is a generated hash, so discover and drop it by column
-- rather than relying on the exact name (robust across environments).

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname
    INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
    JOIN pg_attribute att
        ON att.attrelid = con.conrelid
       AND att.attnum = ANY (con.conkey)
    WHERE rel.relname = 'verification_tokens'
      AND con.contype = 'u'
      AND att.attname = 'user_id'
      AND array_length(con.conkey, 1) = 1
    LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE verification_tokens DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- Helpful for the per-user / per-type lookups and cleanups the service does.
CREATE INDEX IF NOT EXISTS idx_verification_tokens_user_type
    ON verification_tokens (user_id, token_type);
