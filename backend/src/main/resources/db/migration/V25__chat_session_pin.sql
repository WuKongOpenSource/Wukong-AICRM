ALTER TABLE crm_chat_session
    ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE crm_chat_session
    ADD COLUMN IF NOT EXISTS pinned_time TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_chat_session_user_pin
    ON crm_chat_session (user_id, pinned DESC, pinned_time DESC, update_time DESC);

CREATE OR REPLACE FUNCTION update_chat_session_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    IF to_jsonb(NEW) - 'pinned' - 'pinned_time' - 'update_time'
       IS NOT DISTINCT FROM to_jsonb(OLD) - 'pinned' - 'pinned_time' - 'update_time' THEN
        NEW.update_time = OLD.update_time;
    ELSE
        NEW.update_time = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_chat_session_update_time ON crm_chat_session;

CREATE TRIGGER trg_chat_session_update_time
    BEFORE UPDATE ON crm_chat_session
    FOR EACH ROW EXECUTE FUNCTION update_chat_session_timestamp();
