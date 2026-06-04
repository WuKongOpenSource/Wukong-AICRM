ALTER TABLE crm_wecom_corp_config
    ADD COLUMN IF NOT EXISTS suite_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS permanent_code_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS auth_info_json TEXT,
    ADD COLUMN IF NOT EXISTS auth_corp_info_json TEXT,
    ADD COLUMN IF NOT EXISTS auth_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS authorized_at TIMESTAMP(3),
    ADD COLUMN IF NOT EXISTS unauthorized_at TIMESTAMP(3),
    ADD COLUMN IF NOT EXISTS auth_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS auth_user_name VARCHAR(255);

ALTER TABLE crm_wecom_corp_config
    DROP COLUMN IF EXISTS auth_mode,
    DROP COLUMN IF EXISTS app_secret_encrypted,
    DROP COLUMN IF EXISTS contact_secret_encrypted;

CREATE INDEX IF NOT EXISTS idx_wecom_config_open_auth
    ON crm_wecom_corp_config (tenant_id, auth_status, update_time DESC);

CREATE TABLE IF NOT EXISTS crm_wecom_suite_ticket (
    id BIGINT NOT NULL,
    suite_id VARCHAR(128) NOT NULL,
    suite_ticket_encrypted TEXT NOT NULL,
    received_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
    raw_event_xml TEXT,
    create_time TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_wecom_suite_ticket_latest
    ON crm_wecom_suite_ticket (suite_id, received_at DESC, update_time DESC);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'trg_wecom_suite_ticket_update_time'
          AND tgrelid = 'crm_wecom_suite_ticket'::regclass
    ) THEN
        CREATE TRIGGER trg_wecom_suite_ticket_update_time
            BEFORE UPDATE ON crm_wecom_suite_ticket
            FOR EACH ROW EXECUTE FUNCTION update_timestamp();
    END IF;
END
$$;

ALTER TABLE crm_customer
    ADD COLUMN IF NOT EXISTS wecom_customer BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS wecom_corp_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS wecom_external_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS wecom_synced_at TIMESTAMP(3);

UPDATE crm_customer
SET wecom_customer = FALSE
WHERE wecom_customer IS NULL;

COMMENT ON COLUMN crm_customer.wecom_customer IS '是否企业微信客户';
COMMENT ON COLUMN crm_customer.wecom_corp_id IS '企业微信企业ID';
COMMENT ON COLUMN crm_customer.wecom_external_user_id IS '企业微信外部客户ID';
COMMENT ON COLUMN crm_customer.wecom_synced_at IS '企业微信同步时间';

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_wecom_external
    ON crm_customer (tenant_id, wecom_corp_id, wecom_external_user_id)
    WHERE wecom_customer = TRUE
      AND wecom_corp_id IS NOT NULL
      AND wecom_external_user_id IS NOT NULL
      AND status = 1;

CREATE INDEX IF NOT EXISTS idx_customer_wecom_customer
    ON crm_customer (tenant_id, wecom_customer, update_time DESC);

ALTER TABLE manager_dept
    ADD COLUMN IF NOT EXISTS wecom_corp_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS wecom_dept_id BIGINT,
    ADD COLUMN IF NOT EXISTS wecom_parent_dept_id BIGINT,
    ADD COLUMN IF NOT EXISTS wecom_synced_at TIMESTAMP(3);

ALTER TABLE manager_user
    ADD COLUMN IF NOT EXISTS wecom_corp_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS wecom_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS wecom_synced_at TIMESTAMP(3);

CREATE UNIQUE INDEX IF NOT EXISTS uk_manager_dept_wecom_dept
    ON manager_dept (tenant_id, wecom_corp_id, wecom_dept_id)
    WHERE wecom_corp_id IS NOT NULL AND wecom_dept_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_manager_user_wecom_user
    ON manager_user (tenant_id, wecom_corp_id, wecom_user_id)
    WHERE wecom_corp_id IS NOT NULL AND wecom_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_manager_user_mobile
    ON manager_user (mobile)
    WHERE mobile IS NOT NULL;

COMMENT ON COLUMN manager_dept.wecom_corp_id IS 'WeCom corp ID';
COMMENT ON COLUMN manager_dept.wecom_dept_id IS 'WeCom department ID';
COMMENT ON COLUMN manager_dept.wecom_parent_dept_id IS 'WeCom parent department ID';
COMMENT ON COLUMN manager_dept.wecom_synced_at IS 'WeCom department sync time';
COMMENT ON COLUMN manager_user.wecom_corp_id IS 'WeCom corp ID';
COMMENT ON COLUMN manager_user.wecom_user_id IS 'WeCom user ID';
COMMENT ON COLUMN manager_user.wecom_synced_at IS 'WeCom user sync time';
