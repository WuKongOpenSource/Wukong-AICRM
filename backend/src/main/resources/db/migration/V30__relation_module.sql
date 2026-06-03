-- 关系模块、关系人挂载字段与权限菜单

CREATE TABLE IF NOT EXISTS crm_relation (
    relation_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    avatar VARCHAR(500),
    phone VARCHAR(50),
    wechat VARCHAR(100),
    email VARCHAR(100),
    relation_type VARCHAR(50) DEFAULT 'other',
    company VARCHAR(255),
    remark TEXT,
    source VARCHAR(50) NOT NULL DEFAULT 'manual',
    source_customer_id BIGINT,
    source_contact_id BIGINT,
    status SMALLINT DEFAULT 1,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id BIGINT,
    PRIMARY KEY (relation_id)
);

CREATE INDEX IF NOT EXISTS idx_relation_owner
    ON crm_relation (tenant_id, create_user_id, status);
CREATE INDEX IF NOT EXISTS idx_relation_source_contact
    ON crm_relation (tenant_id, source_contact_id, create_user_id);
CREATE INDEX IF NOT EXISTS idx_relation_name
    ON crm_relation (tenant_id, name);

DROP TRIGGER IF EXISTS trg_relation_update_time ON crm_relation;
CREATE TRIGGER trg_relation_update_time
    BEFORE UPDATE ON crm_relation
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

ALTER TABLE crm_task
    ADD COLUMN IF NOT EXISTS relation_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_task_relation_id
    ON crm_task (relation_id);

ALTER TABLE crm_schedule
    ADD COLUMN IF NOT EXISTS relation_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_schedule_relation_id
    ON crm_schedule (relation_id);

ALTER TABLE crm_follow_up
    ALTER COLUMN customer_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS relation_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_follow_up_relation_id
    ON crm_follow_up (relation_id);

ALTER TABLE crm_knowledge
    ADD COLUMN IF NOT EXISTS relation_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_knowledge_relation_id
    ON crm_knowledge (relation_id);

ALTER TABLE crm_chat_session
    ADD COLUMN IF NOT EXISTS relation_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_chat_session_relation_id
    ON crm_chat_session (relation_id);

-- ========== 关系 (2500) ==========
INSERT INTO manager_menu (menu_id, parent_id, realm, realm_name, type)
VALUES (2500, 0, 'relation', '关系', 3)
ON CONFLICT (menu_id) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    realm = EXCLUDED.realm,
    realm_name = EXCLUDED.realm_name,
    type = EXCLUDED.type;

INSERT INTO manager_menu (menu_id, parent_id, realm, realm_name, type)
VALUES (2501, 2500, 'relation:view', '查看', 5)
ON CONFLICT (menu_id) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    realm = EXCLUDED.realm,
    realm_name = EXCLUDED.realm_name,
    type = EXCLUDED.type;

INSERT INTO manager_menu (menu_id, parent_id, realm, realm_name, type)
VALUES (2502, 2500, 'relation:create', '新建', 5)
ON CONFLICT (menu_id) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    realm = EXCLUDED.realm,
    realm_name = EXCLUDED.realm_name,
    type = EXCLUDED.type;

INSERT INTO manager_menu (menu_id, parent_id, realm, realm_name, type)
VALUES (2503, 2500, 'relation:edit', '编辑', 5)
ON CONFLICT (menu_id) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    realm = EXCLUDED.realm,
    realm_name = EXCLUDED.realm_name,
    type = EXCLUDED.type;

INSERT INTO manager_menu (menu_id, parent_id, realm, realm_name, type)
VALUES (2504, 2500, 'relation:delete', '删除', 5)
ON CONFLICT (menu_id) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    realm = EXCLUDED.realm,
    realm_name = EXCLUDED.realm_name,
    type = EXCLUDED.type;
