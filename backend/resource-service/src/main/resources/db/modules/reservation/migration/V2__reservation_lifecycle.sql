ALTER TABLE m1.seat_reservations DROP CONSTRAINT seat_reservations_status_check;
ALTER TABLE m1.seat_reservations ADD CONSTRAINT seat_reservations_status_check CHECK (status IN ('PENDING','RESERVED','CHECKED_IN','COMPLETED','CANCEL_PENDING','CANCELLED','NO_SHOW','REJECTED'));
ALTER TABLE m1.seat_reservations DROP CONSTRAINT seat_reservations_cancel_reason_check;
ALTER TABLE m1.seat_reservations ADD CONSTRAINT seat_reservations_cancel_reason_check CHECK(cancel_reason IN ('USER','NO_SHOW','REPAIR','ADMIN','EXTERNAL_CONFLICT','SYSTEM_TIMEOUT','SYSTEM_MAINTENANCE'));
ALTER TABLE m1.seat_reservations
 ADD COLUMN room_id uuid,
 ADD COLUMN room_snapshot jsonb,
 ADD COLUMN seat_snapshot jsonb,
 ADD COLUMN confirmed_at timestamptz,
 ADD COLUMN terminal_status varchar(24),
 ADD COLUMN resource_version integer NOT NULL DEFAULT 1 CHECK(resource_version>0),
 ADD COLUMN resource_state varchar(24) NOT NULL DEFAULT 'CONFIRMED' CHECK(resource_state IN ('PENDING','CONFIRMED','RELEASE_PENDING','RELEASED','REJECTED')),
 ADD COLUMN resource_receipt jsonb,
 ADD COLUMN failure_code varchar(64),
 ADD COLUMN record_seq bigint GENERATED ALWAYS AS IDENTITY;
CREATE UNIQUE INDEX reservation_record_seq ON m1.seat_reservations(record_seq);
CREATE INDEX reservation_room_active ON m1.seat_reservations(room_id,status,ends_at);
CREATE TABLE m1.reservation_commands (
 operation_id uuid PRIMARY KEY, reservation_id uuid NOT NULL REFERENCES m1.seat_reservations(id) ON DELETE CASCADE,
 actor_id uuid NOT NULL, action varchar(32) NOT NULL, fingerprint varchar(64) NOT NULL,
 created_at timestamptz NOT NULL
);
CREATE TABLE m1.reservation_outbox (
 id uuid PRIMARY KEY, reservation_id uuid REFERENCES m1.seat_reservations(id) ON DELETE CASCADE,
 kind varchar(24) NOT NULL CHECK(kind IN ('ALLOCATE','RELEASE','NOTIFY','NOTIFY_CANCEL','AUDIT')),
 payload jsonb NOT NULL, frozen boolean NOT NULL DEFAULT true,
 reminder_kind varchar(16),
 status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','CLAIMED','DONE','CANCELLED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0), next_retry_at timestamptz NOT NULL,
 lease_until timestamptz, claim_id uuid, response jsonb, last_error varchar(64), created_at timestamptz NOT NULL
);
CREATE INDEX reservation_outbox_due ON m1.reservation_outbox(next_retry_at) WHERE status IN ('PENDING','CLAIMED');
CREATE TABLE m1.reservation_reminders (
 reservation_id uuid NOT NULL REFERENCES m1.seat_reservations(id) ON DELETE CASCADE,
 kind varchar(16) NOT NULL CHECK(kind IN ('BEFORE','AFTER')),
 event_id uuid NOT NULL UNIQUE,
 due_at timestamptz NOT NULL,
 PRIMARY KEY(reservation_id,kind)
);
CREATE TABLE m1.reservation_violations (
 reservation_id uuid PRIMARY KEY REFERENCES m1.seat_reservations(id) ON DELETE CASCADE,
 user_id uuid NOT NULL, occurred_at timestamptz NOT NULL, reason varchar(16) NOT NULL CHECK(reason='NO_SHOW')
);
CREATE TABLE m1.maintenance_sources (
 source_id uuid PRIMARY KEY, room_id uuid NOT NULL, source_version integer NOT NULL CHECK(source_version>0),
 active boolean NOT NULL, receipt jsonb NOT NULL, fingerprint varchar(64) NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE m1.maintenance_operations (
 operation_id uuid PRIMARY KEY, fingerprint varchar(64) NOT NULL, response jsonb NOT NULL, created_at timestamptz NOT NULL
);
