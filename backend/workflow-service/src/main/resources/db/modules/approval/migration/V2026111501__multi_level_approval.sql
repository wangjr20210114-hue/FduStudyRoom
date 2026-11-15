-- M2-02 only. Upstream request submission and resource allocation remain separate.
CREATE TABLE m2.classroom_requests (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 applicant_id uuid NOT NULL,
 room_id uuid NOT NULL,
 starts_at timestamptz NOT NULL,
 ends_at timestamptz NOT NULL CHECK (ends_at > starts_at),
 purpose text NOT NULL CHECK (length(btrim(purpose)) > 0),
 attendee_count integer NOT NULL CHECK (attendee_count > 0),
 status varchar(24) NOT NULL DEFAULT 'PENDING'
   CHECK (status IN ('PENDING','RESOURCE_PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED','CONFLICT')),
 expires_at timestamptz NOT NULL,
 version integer NOT NULL DEFAULT 1 CHECK (version > 0),
 flow_version varchar(120) NOT NULL CHECK (length(btrim(flow_version)) > 0)
);
CREATE INDEX classroom_requests_applicant_created ON m2.classroom_requests (applicant_id, created_at DESC);
CREATE INDEX classroom_requests_status_expiry ON m2.classroom_requests (status, expires_at);
CREATE TABLE m2.approval_tasks (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 request_id uuid NOT NULL REFERENCES m2.classroom_requests(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 step_no smallint NOT NULL CHECK (step_no > 0),
 approver_id uuid NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'WAITING'
   CHECK (status IN ('WAITING','PENDING','APPROVED','REJECTED','EXPIRED','CANCELLED')),
 due_at timestamptz,
 acted_at timestamptz,
 decided_by uuid,
 comment text,
 UNIQUE (request_id, step_no),
 UNIQUE (request_id, approver_id),
 UNIQUE (request_id, decided_by),
 CHECK (status <> 'PENDING' OR due_at IS NOT NULL),
 CHECK (status NOT IN ('APPROVED','REJECTED') OR (acted_at IS NOT NULL AND decided_by IS NOT NULL)),
 CHECK (status <> 'REJECTED' OR (comment IS NOT NULL AND length(btrim(comment)) > 0))
);
CREATE INDEX approval_tasks_actor_status_due ON m2.approval_tasks (approver_id, status, due_at);
COMMENT ON COLUMN m2.classroom_requests.flow_version IS 'Immutable submission-time workflow snapshot version; M2-01 constructs the request and assigned tasks.';
COMMENT ON COLUMN m2.approval_tasks.decided_by IS 'Actual trusted session actor, retained independently of assignment for imported history checks.';
COMMENT ON TABLE m2.classroom_requests IS 'RESOURCE_PENDING means approvals complete, resource allocation unconfirmed. Only M2-03 may confirm APPROVED.';
