ALTER TABLE crm_tencent_meeting_corp_config
    DROP COLUMN IF EXISTS secret_id_encrypted,
    DROP COLUMN IF EXISTS secret_key_encrypted,
    DROP COLUMN IF EXISTS operator_user_id,
    ADD COLUMN IF NOT EXISTS app_secret_encrypted TEXT;

ALTER TABLE crm_tencent_meeting_user_mapping
    ADD COLUMN IF NOT EXISTS open_corp_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS open_corp_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS avatar_url TEXT,
    ADD COLUMN IF NOT EXISTS access_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS refresh_token_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS token_expires_at TIMESTAMP(3),
    ADD COLUMN IF NOT EXISTS scopes TEXT,
    ADD COLUMN IF NOT EXISTS auth_status VARCHAR(32) DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS last_auth_time TIMESTAMP(3),
    ADD COLUMN IF NOT EXISTS last_refresh_time TIMESTAMP(3),
    ADD COLUMN IF NOT EXISTS last_sync_time TIMESTAMP(3),
    ADD COLUMN IF NOT EXISTS last_sync_error TEXT;

UPDATE crm_tencent_meeting_user_mapping
SET auth_status = 'ACTIVE'
WHERE auth_status IS NULL;
