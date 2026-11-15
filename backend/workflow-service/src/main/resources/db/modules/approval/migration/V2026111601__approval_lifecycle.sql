
ALTER TABLE m2.classroom_requests ADD COLUMN resource_state varchar(24) NOT NULL DEFAULT 'UNCONFIRMED',ADD COLUMN resource_version integer NOT NULL DEFAULT 0 CHECK(resource_version>=0),ADD COLUMN resource_abnormal boolean NOT NULL DEFAULT false,ADD COLUMN cancel_reason varchar(32),ADD COLUMN cancelled_at timestamptz;
CREATE TABLE m2.configuration(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),version integer NOT NULL CHECK(version>0),approver_ids uuid[] NOT NULL CHECK(cardinality(approver_ids)>=2));
CREATE TABLE m2.request_operations(operation_id uuid PRIMARY KEY,actor_id uuid NOT NULL,fingerprint varchar(64) NOT NULL,request_id uuid NOT NULL REFERENCES m2.classroom_requests(id),kind varchar(16) NOT NULL);
CREATE TABLE m2.request_history(id uuid PRIMARY KEY,request_id uuid NOT NULL REFERENCES m2.classroom_requests(id),actor_id uuid,status varchar(24) NOT NULL,reason text,occurred_at timestamptz NOT NULL,request_version integer NOT NULL);
CREATE INDEX approval_history_order ON m2.request_history(request_id,occurred_at,id);
CREATE TABLE m2.outbox(id uuid PRIMARY KEY,request_id uuid REFERENCES m2.classroom_requests(id),kind varchar(16) NOT NULL,payload jsonb NOT NULL,prepared boolean NOT NULL DEFAULT false,state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN('PENDING','DONE','ERROR')),attempts integer NOT NULL DEFAULT 0,next_at timestamptz NOT NULL,last_error varchar(64),created_at timestamptz NOT NULL);
CREATE INDEX approval_outbox_due ON m2.outbox(state,next_at);
CREATE TABLE m2.maintenance_sources(source_id uuid PRIMARY KEY,room_id uuid NOT NULL,source_version integer NOT NULL CHECK(source_version>0),active boolean NOT NULL,effective_raw text NOT NULL,fingerprint varchar(64) NOT NULL);
CREATE TABLE m2.maintenance_operations(operation_id uuid PRIMARY KEY,fingerprint varchar(64) NOT NULL,receipt jsonb NOT NULL);
