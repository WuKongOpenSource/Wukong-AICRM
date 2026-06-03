-- Local development bootstrap for legacy manager/RBAC tables.
-- Flyway V1 is only a baseline marker, so a fresh local database needs these
-- pre-existing tables before V2+ migrations can run.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS crm_tenant (
    tenant_id BIGINT PRIMARY KEY,
    tenant_name VARCHAR(100) NOT NULL,
    contact_name VARCHAR(50),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(100),
    status INTEGER NOT NULL DEFAULT 1,
    expire_time TIMESTAMP,
    max_users INTEGER DEFAULT 50,
    remark VARCHAR(500),
    gift_credit_total BIGINT DEFAULT 300,
    gift_credit_used BIGINT DEFAULT 0,
    purchased_credit_total BIGINT DEFAULT 0,
    purchased_credit_used BIGINT DEFAULT 0,
    weknora_api_key VARCHAR(255),
    weknora_knowledge_base_id VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS manager_user (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255),
    salt VARCHAR(64),
    img VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    realname VARCHAR(100),
    num VARCHAR(100),
    mobile VARCHAR(50),
    email VARCHAR(100),
    sex INTEGER DEFAULT 0,
    dept_id BIGINT,
    post VARCHAR(100),
    status INTEGER NOT NULL DEFAULT 1,
    parent_id BIGINT DEFAULT 0,
    tenant_id BIGINT
);

CREATE TABLE IF NOT EXISTS manager_dept (
    dept_id BIGINT PRIMARY KEY,
    dept_name VARCHAR(100) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id BIGINT DEFAULT 2036380627891470338
);

CREATE TABLE IF NOT EXISTS manager_role (
    role_id BIGINT PRIMARY KEY,
    role_name VARCHAR(100) NOT NULL,
    realm VARCHAR(100),
    description VARCHAR(500),
    data_type INTEGER DEFAULT 5,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id BIGINT
);

CREATE TABLE IF NOT EXISTS manager_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id BIGINT
);

CREATE TABLE IF NOT EXISTS manager_menu (
    menu_id BIGINT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    realm VARCHAR(100),
    realm_name VARCHAR(100),
    type INTEGER
);

CREATE TABLE IF NOT EXISTS manager_role_menu (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    data_scope INTEGER
);

CREATE INDEX IF NOT EXISTS idx_manager_user_username ON manager_user(username);
CREATE INDEX IF NOT EXISTS idx_manager_user_tenant ON manager_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_manager_role_tenant ON manager_role(tenant_id);
CREATE INDEX IF NOT EXISTS idx_manager_user_role_user ON manager_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_manager_user_role_tenant ON manager_user_role(tenant_id);
CREATE INDEX IF NOT EXISTS idx_manager_dept_tenant ON manager_dept(tenant_id);

INSERT INTO crm_tenant (
    tenant_id, tenant_name, contact_name, contact_email, status, max_users,
    gift_credit_total, gift_credit_used, purchased_credit_total, purchased_credit_used,
    create_time, update_time
) VALUES (
    2036380627891470338, '小猴科技', '本地管理员', 'admin@local.dev', 1, 50,
    300, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (tenant_id) DO UPDATE SET
    tenant_name = EXCLUDED.tenant_name,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO manager_role (
    role_id, role_name, realm, description, data_type,
    create_user_id, update_user_id, create_time, update_time, tenant_id
) VALUES (
    2036380628088602625, '超级管理员', 'super_admin', '本地开发超级管理员', 5,
    2036380628482867202, 2036380628482867202, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    2036380627891470338
) ON CONFLICT (role_id) DO UPDATE SET
    role_name = EXCLUDED.role_name,
    realm = EXCLUDED.realm,
    data_type = EXCLUDED.data_type,
    update_time = CURRENT_TIMESTAMP,
    tenant_id = EXCLUDED.tenant_id;

INSERT INTO manager_user (
    user_id, username, password, realname, mobile, email, sex, dept_id, post,
    status, parent_id, tenant_id, create_time
) VALUES
    (
        1, 'admin',
        '$2a$10$EdQ37s/q.pADWeDAZEPDXeKFRVjLstY3mM3G.ceYlCFbyxpGzPJ6S',
        '本地管理员', '13800000000', 'admin@local.dev', 0, NULL, '管理员',
        1, 0, 2036380627891470338, CURRENT_TIMESTAMP
    ),
    (
        2036380628482867202, '1074362868@qq.com',
        '$2a$10$EdQ37s/q.pADWeDAZEPDXeKFRVjLstY3mM3G.ceYlCFbyxpGzPJ6S',
        '晏妙', '13800000001', '1074362868@qq.com', 0, NULL, '管理员',
        1, 0, 2036380627891470338, CURRENT_TIMESTAMP
    )
ON CONFLICT (user_id) DO UPDATE SET
    username = EXCLUDED.username,
    password = EXCLUDED.password,
    realname = EXCLUDED.realname,
    email = EXCLUDED.email,
    status = EXCLUDED.status,
    tenant_id = EXCLUDED.tenant_id;

INSERT INTO manager_user_role (
    id, user_id, role_id, create_user_id, update_user_id, create_time, update_time, tenant_id
) VALUES
    (
        2036380628482867203, 2036380628482867202, 2036380628088602625,
        2036380628482867202, 2036380628482867202, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        2036380627891470338
    ),
    (
        2036380628482867204, 1, 2036380628088602625,
        2036380628482867202, 2036380628482867202, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
        2036380627891470338
    )
ON CONFLICT (id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    role_id = EXCLUDED.role_id,
    update_time = CURRENT_TIMESTAMP,
    tenant_id = EXCLUDED.tenant_id;

-- Keep existing demo CRM rows visible for the local development tenant.
ALTER TABLE IF EXISTS crm_customer ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_contact ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_follow_up ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_task ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_knowledge ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_customer_tag ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_contact_tag ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_knowledge_tag ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_customer_team ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_chat_session ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_chat_message ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_chat_attachment ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_ai_agent ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_system_config ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_operation_log ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE IF EXISTS crm_custom_field ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

UPDATE crm_customer SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_contact SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_follow_up SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_task SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_knowledge SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_customer_tag SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_contact_tag SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_knowledge_tag SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_customer_team SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_chat_session SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_chat_message SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_chat_attachment SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_ai_agent SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_system_config SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_operation_log SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;
UPDATE crm_custom_field SET tenant_id = 2036380627891470338 WHERE tenant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_customer_tenant ON crm_customer(tenant_id);
CREATE INDEX IF NOT EXISTS idx_contact_tenant ON crm_contact(tenant_id);
CREATE INDEX IF NOT EXISTS idx_follow_up_tenant ON crm_follow_up(tenant_id);
CREATE INDEX IF NOT EXISTS idx_task_tenant ON crm_task(tenant_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_tenant ON crm_knowledge(tenant_id);
CREATE INDEX IF NOT EXISTS idx_customer_tag_tenant ON crm_customer_tag(tenant_id);
CREATE INDEX IF NOT EXISTS idx_contact_tag_tenant ON crm_contact_tag(tenant_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_tag_tenant ON crm_knowledge_tag(tenant_id);
CREATE INDEX IF NOT EXISTS idx_customer_team_tenant ON crm_customer_team(tenant_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_tenant ON crm_chat_session(tenant_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_tenant ON crm_chat_message(tenant_id);
CREATE INDEX IF NOT EXISTS idx_chat_attachment_tenant ON crm_chat_attachment(tenant_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_tenant ON crm_ai_agent(tenant_id);
CREATE INDEX IF NOT EXISTS idx_system_config_tenant ON crm_system_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_operation_log_tenant ON crm_operation_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_custom_field_tenant ON crm_custom_field(tenant_id);
