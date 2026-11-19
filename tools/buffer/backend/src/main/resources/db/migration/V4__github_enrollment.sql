CREATE TABLE buffer.github_enrollments (
    member_id varchar(1) PRIMARY KEY CHECK (member_id IN ('a','b','c','d','e','f')),
    github_id bigint NOT NULL UNIQUE CHECK (github_id > 0),
    github_login varchar(39) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
