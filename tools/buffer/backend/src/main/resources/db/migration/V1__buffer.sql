CREATE TABLE buffer.submissions (
 id uuid PRIMARY KEY,
 member_id varchar(64) NOT NULL,
 module_id varchar(16) NOT NULL,
 layer varchar(16) NOT NULL CHECK(layer IN ('frontend','backend')),
 fingerprint char(64) NOT NULL,
 metadata jsonb NOT NULL,
 patch bytea NOT NULL,
 state varchar(32) NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','REVIEWED','VERIFYING','VERIFIED','VERIFY_FAILED','CONFLICT','PUBLISHED','PUSH_FAILED')),
 reviewer varchar(64), review_note text, review_at timestamptz,
 result jsonb NOT NULL DEFAULT '{}',
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(member_id,fingerprint)
);
CREATE TABLE buffer.sync_state (
 id int PRIMARY KEY CHECK(id=1),
 next_due timestamptz NOT NULL DEFAULT now()+interval '72 hours',
 retry_at timestamptz,
 last_run timestamptz,
 last_status varchar(32) NOT NULL DEFAULT 'NOT_RUN'
);
INSERT INTO buffer.sync_state(id) VALUES(1) ON CONFLICT DO NOTHING;
CREATE TABLE buffer.events (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 submission_id uuid REFERENCES buffer.submissions(id) ON DELETE RESTRICT,
 actor varchar(64) NOT NULL, action varchar(64) NOT NULL,
 detail jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON buffer.submissions(state,created_at);
