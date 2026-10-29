CREATE TABLE iam.roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    code varchar(64) NOT NULL UNIQUE,
    name varchar(64) NOT NULL,
    enabled boolean NOT NULL DEFAULT true
);
CREATE TABLE iam.permissions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    code varchar(128) NOT NULL UNIQUE,
    name varchar(128) NOT NULL,
    module varchar(32) NOT NULL
);
CREATE TABLE iam.role_permissions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    role_id uuid NOT NULL REFERENCES iam.roles(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    permission_id uuid NOT NULL REFERENCES iam.permissions(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    UNIQUE (role_id,permission_id)
);
CREATE TABLE iam.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    login_name varchar(128) NOT NULL UNIQUE,
    display_name varchar(128) NOT NULL,
    email varchar(254),
    identity_type varchar(16) NOT NULL CHECK (identity_type IN ('STUDENT','STAFF')),
    identity_source varchar(16) NOT NULL DEFAULT 'FUDAN_UIS' CHECK (identity_source IN ('FUDAN_UIS','DEVELOPMENT')),
    identity_active boolean NOT NULL DEFAULT false,
    role_id uuid NOT NULL REFERENCES iam.roles(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    department_code varchar(64),
    can_apply_room boolean NOT NULL DEFAULT false,
    enabled boolean NOT NULL DEFAULT true,
    auth_version integer NOT NULL DEFAULT 1 CHECK (auth_version > 0),
    CHECK (identity_type = 'STUDENT' OR department_code IS NULL)
);
CREATE INDEX users_role_idx ON iam.users(role_id);
