ALTER TABLE crm_schedule
    ADD COLUMN IF NOT EXISTS relation_id BIGINT,
    ADD COLUMN IF NOT EXISTS participant_user_ids TEXT;

CREATE INDEX IF NOT EXISTS idx_schedule_relation
    ON crm_schedule(relation_id);

COMMENT ON COLUMN crm_schedule.relation_id IS 'Related relation ID';
COMMENT ON COLUMN crm_schedule.participant_user_ids IS 'Comma-separated participant user IDs';
