ALTER TABLE buffer.submissions DROP CONSTRAINT submissions_state_check;
ALTER TABLE buffer.submissions ADD CONSTRAINT submissions_state_check CHECK(state IN ('DRAFT','REVIEWED','REJECTED','VERIFYING','VERIFIED','VERIFY_FAILED','CONFLICT','PUBLISHED','PUSH_FAILED'));
