ALTER TABLE iam.users ADD COLUMN email_verified_at timestamptz;
ALTER TABLE iam.users ADD COLUMN email_version integer NOT NULL DEFAULT 1 CHECK(email_version>0);
CREATE TABLE iam.system_settings (
 id smallint PRIMARY KEY CHECK(id=1),version integer NOT NULL CHECK(version>0),
 max_reservation_hours integer NOT NULL CHECK(max_reservation_hours>0),
 before_start_reminder_minutes integer NOT NULL CHECK(before_start_reminder_minutes>0),
 after_start_reminder_minutes integer NOT NULL CHECK(after_start_reminder_minutes>0),
 checkin_deadline_minutes integer NOT NULL CHECK(checkin_deadline_minutes>after_start_reminder_minutes),
 approval_ttl_minutes integer NOT NULL CHECK(approval_ttl_minutes>0),repair_sla_minutes integer NOT NULL CHECK(repair_sla_minutes>0)
);
INSERT INTO iam.system_settings VALUES(1,1,4,15,10,15,60,120);
CREATE TABLE iam.system_settings_history (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),actor_id uuid NOT NULL REFERENCES iam.users(id) ON DELETE RESTRICT,
 changed_at timestamptz NOT NULL,version integer NOT NULL UNIQUE,before_data jsonb NOT NULL,after_data jsonb NOT NULL
);
CREATE TABLE iam.email_verification_challenges (
 operation_id uuid PRIMARY KEY,user_id uuid NOT NULL REFERENCES iam.users(id) ON DELETE RESTRICT,
 email varchar(254) NOT NULL,email_version integer NOT NULL,nonce varchar(64) NOT NULL,
 token_hash char(64) NOT NULL UNIQUE,issued_at timestamptz NOT NULL,expires_at timestamptz NOT NULL,
 consumed_at timestamptz,delivery_status varchar(32) NOT NULL DEFAULT 'UNKNOWN',delivery_error varchar(64),
 next_retry_at timestamptz,retryable boolean NOT NULL DEFAULT false,CHECK(expires_at>issued_at)
);
CREATE INDEX email_challenges_user_idx ON iam.email_verification_challenges(user_id,issued_at);
CREATE TABLE iam.audit_outbox (
 event_key uuid PRIMARY KEY,actor_id uuid,action varchar(128) NOT NULL,source_id uuid NOT NULL,
 payload jsonb NOT NULL,created_at timestamptz NOT NULL,next_attempt_at timestamptz NOT NULL,
 attempts integer NOT NULL DEFAULT 0,sent_at timestamptz,last_error varchar(64)
);
CREATE INDEX audit_outbox_pending_idx ON iam.audit_outbox(next_attempt_at) WHERE sent_at IS NULL;
INSERT INTO iam.permissions(code,name,module) VALUES
 ('identity:settings:read','Read system settings','identity'),('identity:settings:write','Update system settings','identity'),
 ('reservation:create','Create own reservations','reservation'),('reservation:cancel','Cancel own reservations','reservation'),
 ('reservation:checkin','Check in own reservations','reservation'),('reservation:admin-cancel','Administratively cancel reservations','reservation'),
 ('approval:apply','Apply for a classroom','approval'),('repair:report','Report faults','repair'),('repair:assign','Assign maintenance','repair'),
 ('repair:maintain','Maintain assigned repairs','repair'),('repair:admin-cancel','Review repair cancellation','repair'),
 ('audit:read','Read audit events','audit'),('statistics:read','Read operational statistics','statistics')
ON CONFLICT(code) DO NOTHING;
INSERT INTO iam.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM iam.roles r CROSS JOIN iam.permissions p WHERE r.code='SYSTEM_ADMIN'
ON CONFLICT(role_id,permission_id) DO NOTHING;
INSERT INTO iam.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM iam.roles r JOIN iam.permissions p ON p.code IN
 ('resource:rooms:read','reservation:read','reservation:create','reservation:cancel','reservation:checkin','approval:read','approval:apply','repair:report','repair:history:read','notification:read')
WHERE r.code IN('USER','MAINTAINER') ON CONFLICT(role_id,permission_id) DO NOTHING;
INSERT INTO iam.role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM iam.roles r JOIN iam.permissions p ON p.code='repair:maintain' WHERE r.code='MAINTAINER'
ON CONFLICT(role_id,permission_id) DO NOTHING;
