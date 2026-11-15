CREATE TABLE m3.repair_orders (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 reporter_id uuid NOT NULL, room_id uuid NOT NULL, seat_id uuid, description text NOT NULL CHECK(btrim(description)<>''), technician_id uuid,
 status varchar(24) NOT NULL DEFAULT 'SUBMITTED' CHECK(status IN ('SUBMITTED','ASSIGNED','IN_PROGRESS','PENDING_ACCEPTANCE','COMPLETED','CANCELLED')),
 due_at timestamptz NOT NULL, escalation_level smallint NOT NULL DEFAULT 0 CHECK(escalation_level>=0), repair_report text,
 accepted_by uuid, accepted_at timestamptz, acceptance_comment text, rating smallint CHECK(rating BETWEEN 1 AND 5), evaluation text, evaluated_at timestamptz,
 version integer NOT NULL DEFAULT 1 CHECK(version>0),
 CHECK(status NOT IN ('IN_PROGRESS','PENDING_ACCEPTANCE','COMPLETED') OR technician_id IS NOT NULL),
 CHECK(status<>'COMPLETED' OR (accepted_by IS NOT NULL AND accepted_at IS NOT NULL)),
 CHECK(accepted_by IS NULL OR accepted_by IS DISTINCT FROM technician_id),
 CHECK((rating IS NULL AND evaluation IS NULL AND evaluated_at IS NULL) OR (status='COMPLETED' AND rating IS NOT NULL AND evaluation IS NOT NULL AND btrim(evaluation)<>'' AND evaluated_at IS NOT NULL))
);
CREATE INDEX repair_technician_status_due ON m3.repair_orders(technician_id,status,due_at);
CREATE INDEX repair_room_status ON m3.repair_orders(room_id,status);
CREATE INDEX repair_status_due ON m3.repair_orders(status,due_at);
CREATE TABLE m3.repair_events (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 order_id uuid NOT NULL REFERENCES m3.repair_orders(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 actor_id uuid, event_type varchar(32) NOT NULL CHECK(event_type IN ('SUBMIT','ASSIGN','REASSIGN','START','FINISH','ACCEPT','REJECT','ESCALATE','CANCEL','EVALUATE')),
 from_status varchar(24), to_status varchar(24) NOT NULL, assigned_to uuid, note text,
 order_version integer NOT NULL CHECK(order_version>0), rating smallint CHECK(rating BETWEEN 1 AND 5)
);
CREATE INDEX repair_event_timeline ON m3.repair_events(order_id,created_at,id);
CREATE FUNCTION m3.repair_preserve_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Repair history is append only' USING ERRCODE='23514'; END;
$$;
CREATE TRIGGER repair_event_append_only BEFORE UPDATE OR DELETE ON m3.repair_events FOR EACH ROW EXECUTE FUNCTION m3.repair_preserve_event();
CREATE FUNCTION m3.repair_preserve_evaluation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.evaluated_at IS NOT NULL AND ROW(OLD.rating,OLD.evaluation,OLD.evaluated_at) IS DISTINCT FROM ROW(NEW.rating,NEW.evaluation,NEW.evaluated_at) THEN
  RAISE EXCEPTION 'Repair evaluation is immutable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER repair_evaluation_immutable BEFORE UPDATE ON m3.repair_orders FOR EACH ROW EXECUTE FUNCTION m3.repair_preserve_evaluation();
