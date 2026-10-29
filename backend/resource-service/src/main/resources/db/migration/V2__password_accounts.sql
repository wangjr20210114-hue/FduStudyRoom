ALTER TABLE iam.users ADD COLUMN password_hash varchar(255);
-- Preserve existing school/development metadata. Local accounts do not claim school verification.
ALTER TABLE iam.users DROP CONSTRAINT users_identity_source_check;
ALTER TABLE iam.users ADD CONSTRAINT users_identity_source_check
    CHECK (identity_source IN ('FUDAN_UIS','DEVELOPMENT','LOCAL'));
ALTER TABLE iam.users ALTER COLUMN identity_source SET DEFAULT 'LOCAL';
ALTER TABLE iam.users ALTER COLUMN identity_type DROP NOT NULL;
ALTER TABLE iam.users ADD CONSTRAINT users_password_required
    CHECK (identity_source <> 'LOCAL' OR password_hash IS NOT NULL);
ALTER TABLE iam.users ADD CONSTRAINT users_password_format
    CHECK (password_hash IS NULL OR password_hash ~ '^\{pbkdf2-sha256-v1\}[0-9a-f]{96}$');
CREATE UNIQUE INDEX users_local_login_name_ci_idx ON iam.users(lower(login_name)) WHERE identity_source='LOCAL';
