CREATE TABLE support.audit_logs (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 event_key varchar(255) NOT NULL UNIQUE, payload_hash char(64) NOT NULL,
 source_service varchar(32) NOT NULL CHECK(source_service IN ('resource-service','workflow-service')),
 actor_id uuid, action varchar(128) NOT NULL,
 source_module varchar(16) NOT NULL CHECK(source_module IN ('M1','M2','M3','D','EXTERNAL')),
 source_id uuid, before_data jsonb, after_data jsonb, success boolean NOT NULL,
 occurred_at timestamptz NOT NULL, trace_id varchar(64) NOT NULL
);
CREATE INDEX audit_trace ON support.audit_logs(trace_id,occurred_at DESC,id DESC);
CREATE INDEX audit_source ON support.audit_logs(source_module,source_id);
CREATE INDEX audit_actor ON support.audit_logs(actor_id,occurred_at DESC);
CREATE FUNCTION support.reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Audit records are append-only' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER audit_append_only BEFORE UPDATE OR DELETE ON support.audit_logs FOR EACH ROW EXECUTE FUNCTION support.reject_audit_mutation();
-- Only a digest and safe delivery metadata. Never persist recipient, token, URL or body.
CREATE TABLE support.verification_email_receipts (
 operation_id uuid PRIMARY KEY, payload_hash char(64) NOT NULL,
 status varchar(24) NOT NULL CHECK(status IN ('ACCEPTED','FAILED','NOT_CONFIGURED','UNKNOWN')),
 attempts integer NOT NULL CHECK(attempts>=0), retryable boolean NOT NULL,
 next_retry_at timestamptz, error_code varchar(64),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(status NOT IN ('ACCEPTED','UNKNOWN') OR (NOT retryable AND next_retry_at IS NULL))
);
