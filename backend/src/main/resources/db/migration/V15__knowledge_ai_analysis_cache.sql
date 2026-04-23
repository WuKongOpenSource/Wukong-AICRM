ALTER TABLE crm_knowledge
    ADD COLUMN IF NOT EXISTS ai_analysis_snapshot TEXT;

ALTER TABLE crm_knowledge
    ADD COLUMN IF NOT EXISTS ai_analysis_time TIMESTAMP(3);
