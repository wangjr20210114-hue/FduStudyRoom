ALTER TABLE m3.repair_orders ALTER COLUMN due_at DROP NOT NULL;
DO $$ DECLARE c record; BEGIN
 FOR c IN SELECT conname,pg_get_constraintdef(oid) d FROM pg_constraint WHERE conrelid='m3.repair_orders'::regclass AND contype='c' LOOP
  IF c.d LIKE '%status%' OR c.d LIKE '%evaluation%' THEN EXECUTE format('ALTER TABLE m3.repair_orders DROP CONSTRAINT %I',c.conname); END IF;
 END LOOP;
END $$;
ALTER TABLE m3.repair_orders ADD COLUMN managed boolean NOT NULL DEFAULT false,
 ADD COLUMN assigned_at timestamptz, ADD COLUMN sla_minutes integer CHECK(sla_minutes>0),
 ADD COLUMN desired_version integer NOT NULL DEFAULT 0 CHECK(desired_version>=0),
 ADD COLUMN applied_version integer NOT NULL DEFAULT 0 CHECK(applied_version>=0),
 ADD COLUMN desired_active boolean NOT NULL DEFAULT false, ADD COLUMN applied_active boolean NOT NULL DEFAULT false,
 ADD COLUMN restore_terminal varchar(24), ADD COLUMN resource_error varchar(64),
 ADD COLUMN cancel_reason text, ADD COLUMN cancel_reviewed_by uuid, ADD COLUMN cancel_reviewed_at timestamptz,
 ADD COLUMN safety_inspected_by uuid, ADD COLUMN safety_inspected_at timestamptz;
ALTER TABLE m3.repair_orders ADD CONSTRAINT repair_status_v2 CHECK(status IN ('SUBMITTED','ASSIGNED','RESTRICTION_PENDING','IN_PROGRESS','PENDING_ACCEPTANCE','RESTORATION_PENDING','CANCEL_REVIEW_PENDING','CANCEL_SAFETY_PENDING','COMPLETED','CANCELLED')),
 ADD CONSTRAINT repair_technician_v2 CHECK(status NOT IN ('IN_PROGRESS','PENDING_ACCEPTANCE','COMPLETED') OR technician_id IS NOT NULL),
 ADD CONSTRAINT repair_completed_v2 CHECK(status<>'COMPLETED' OR (accepted_by IS NOT NULL AND accepted_at IS NOT NULL)),
 ADD CONSTRAINT repair_evaluation_v2 CHECK((rating IS NULL AND evaluation IS NULL AND evaluated_at IS NULL) OR (status='COMPLETED' AND rating IS NOT NULL AND evaluation IS NOT NULL AND length(evaluation)<=500 AND evaluated_at IS NOT NULL));
ALTER TABLE m3.repair_events DROP CONSTRAINT repair_events_event_type_check;
ALTER TABLE m3.repair_events ADD CONSTRAINT repair_events_type_v2 CHECK(event_type IN ('SUBMIT','ASSIGN','REASSIGN','START','FINISH','ACCEPT','REJECT','ESCALATE','CANCEL','EVALUATE','CANCEL_REQUEST','CANCEL_REVIEW','CANCEL_INSPECT','RESOURCE_ACK'));
CREATE TABLE m3.repair_commands(operation_id uuid PRIMARY KEY,actor_id uuid NOT NULL,order_id uuid NOT NULL REFERENCES m3.repair_orders(id),action varchar(40) NOT NULL,fingerprint text NOT NULL,response jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE m3.repair_tasks(id uuid PRIMARY KEY,order_id uuid NOT NULL REFERENCES m3.repair_orders(id),kind varchar(32) NOT NULL,payload jsonb NOT NULL,state varchar(12) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','LEASED','DONE')),next_at timestamptz NOT NULL,lease_until timestamptz,last_error varchar(64),attempts integer NOT NULL DEFAULT 0,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX repair_tasks_due ON m3.repair_tasks(state,next_at,lease_until);
CREATE TABLE m3.repair_escalations(order_id uuid NOT NULL REFERENCES m3.repair_orders(id),level smallint NOT NULL CHECK(level=1),occurred_at timestamptz NOT NULL,PRIMARY KEY(order_id,level));
