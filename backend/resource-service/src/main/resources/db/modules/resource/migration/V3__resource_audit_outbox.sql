ALTER TABLE resource.resource_operations ADD COLUMN audit_state varchar(16) NOT NULL DEFAULT 'SKIP' CHECK(audit_state IN('SKIP','PENDING','SENT','FAILED'));
ALTER TABLE resource.resource_operations ADD COLUMN audit_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE resource.resource_operations ADD COLUMN audit_next timestamptz NOT NULL DEFAULT now();
ALTER TABLE resource.resource_operations ADD COLUMN audit_payload jsonb;
CREATE INDEX resource_audit_pending ON resource.resource_operations(audit_state,audit_next);
