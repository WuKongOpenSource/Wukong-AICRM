ALTER TABLE crm_tencent_meeting_corp_config
    ADD COLUMN IF NOT EXISTS webhook_token_encrypted TEXT;
