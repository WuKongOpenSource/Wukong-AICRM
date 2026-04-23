ALTER TABLE crm_customer
    ADD COLUMN IF NOT EXISTS ai_parse_snapshot TEXT;
