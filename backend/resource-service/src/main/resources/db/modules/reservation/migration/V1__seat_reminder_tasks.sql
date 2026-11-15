-- M03-owned pre-state and durable overdue reminders. No cross-module foreign keys.
CREATE TABLE m1.seat_reservations (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 user_id uuid NOT NULL,
 seat_id uuid NOT NULL,
 starts_at timestamptz NOT NULL,
 ends_at timestamptz NOT NULL CHECK (ends_at > starts_at),
 status varchar(24) NOT NULL DEFAULT 'RESERVED' CHECK (status IN ('RESERVED','CHECKED_IN','COMPLETED','CANCELLED','NO_SHOW')),
 checked_in_at timestamptz,
 checkin_deadline_at timestamptz NOT NULL,
 cancel_reason varchar(32) CHECK (cancel_reason IN ('USER','NO_SHOW','REPAIR','ADMIN','EXTERNAL_CONFLICT')),
 cancelled_at timestamptz,
 policy_snapshot jsonb NOT NULL,
 version integer NOT NULL DEFAULT 1 CHECK (version > 0),
 CHECK (status <> 'CHECKED_IN' OR checked_in_at IS NOT NULL),
 CHECK (status NOT IN ('CANCELLED','NO_SHOW') OR (cancel_reason IS NOT NULL AND cancelled_at IS NOT NULL)),
 CHECK (checkin_deadline_at > starts_at)
);
CREATE INDEX seat_reservations_user_start ON m1.seat_reservations(user_id,starts_at DESC);
CREATE INDEX seat_reservations_status_deadline ON m1.seat_reservations(status,checkin_deadline_at);
CREATE TABLE m1.reminder_tasks (
 event_id uuid PRIMARY KEY,
 reservation_id uuid NOT NULL UNIQUE REFERENCES m1.seat_reservations(id),
 scheduled_at timestamptz NOT NULL,
 payload jsonb NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','CANCEL_PENDING','CANCELLED')),
 attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
 next_retry_at timestamptz NOT NULL,
 last_error_code varchar(64),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX reminder_tasks_due ON m1.reminder_tasks(next_retry_at,scheduled_at) WHERE status IN ('PENDING','CANCEL_PENDING');
