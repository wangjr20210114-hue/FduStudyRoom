ALTER TABLE buffer.sync_state ADD COLUMN pressure_next_at timestamptz;
ALTER TABLE buffer.sync_state ADD COLUMN last_trigger varchar(24) NOT NULL DEFAULT 'NONE';
