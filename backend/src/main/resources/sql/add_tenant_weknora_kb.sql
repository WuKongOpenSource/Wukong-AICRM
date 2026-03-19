-- 为每个租户添加 WeKnora 租户 API Key 和知识库 ID 字段
-- 手动执行此 SQL 进行数据库升级

ALTER TABLE crm_tenant ADD COLUMN IF NOT EXISTS weknora_api_key VARCHAR(512);
ALTER TABLE crm_tenant ADD COLUMN IF NOT EXISTS weknora_knowledge_base_id VARCHAR(255);
COMMENT ON COLUMN crm_tenant.weknora_api_key IS 'WeKnora 租户 API Key';
COMMENT ON COLUMN crm_tenant.weknora_knowledge_base_id IS 'WeKnora 知识库ID';
