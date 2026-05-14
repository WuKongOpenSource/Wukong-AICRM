DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'gift_token_total'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'gift_credit_total'
    ) THEN
        ALTER TABLE crm_tenant RENAME COLUMN gift_token_total TO gift_credit_total;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'gift_credit_total'
    ) THEN
        ALTER TABLE crm_tenant ADD COLUMN gift_credit_total BIGINT DEFAULT 200000;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'gift_token_used'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'gift_credit_used'
    ) THEN
        ALTER TABLE crm_tenant RENAME COLUMN gift_token_used TO gift_credit_used;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'gift_credit_used'
    ) THEN
        ALTER TABLE crm_tenant ADD COLUMN gift_credit_used BIGINT DEFAULT 0;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'purchased_token_total'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'purchased_credit_total'
    ) THEN
        ALTER TABLE crm_tenant RENAME COLUMN purchased_token_total TO purchased_credit_total;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'purchased_credit_total'
    ) THEN
        ALTER TABLE crm_tenant ADD COLUMN purchased_credit_total BIGINT DEFAULT 0;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'purchased_token_used'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'purchased_credit_used'
    ) THEN
        ALTER TABLE crm_tenant RENAME COLUMN purchased_token_used TO purchased_credit_used;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_tenant' AND column_name = 'purchased_credit_used'
    ) THEN
        ALTER TABLE crm_tenant ADD COLUMN purchased_credit_used BIGINT DEFAULT 0;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_token_purchase_order' AND column_name = 'token_amount'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_token_purchase_order' AND column_name = 'credit_amount'
    ) THEN
        ALTER TABLE crm_token_purchase_order RENAME COLUMN token_amount TO credit_amount;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'crm_token_purchase_order' AND column_name = 'credit_amount'
    ) THEN
        ALTER TABLE crm_token_purchase_order ADD COLUMN credit_amount BIGINT DEFAULT 0;
    END IF;
END $$;

COMMENT ON COLUMN crm_tenant.gift_credit_total IS '注册赠送积分总量';
COMMENT ON COLUMN crm_tenant.gift_credit_used IS '赠送积分已使用量';
COMMENT ON COLUMN crm_tenant.purchased_credit_total IS '已购买积分总量';
COMMENT ON COLUMN crm_tenant.purchased_credit_used IS '已购买积分已使用量';
COMMENT ON COLUMN crm_token_purchase_order.credit_amount IS '购买积分数量';

ALTER TABLE crm_chat_message ADD COLUMN IF NOT EXISTS credits_used BIGINT DEFAULT 0;
ALTER TABLE crm_chat_message ADD COLUMN IF NOT EXISTS credit_multiplier NUMERIC(10, 4) DEFAULT 1.0000;
ALTER TABLE crm_chat_message ADD COLUMN IF NOT EXISTS billing_model_provider VARCHAR(50);
ALTER TABLE crm_chat_message ADD COLUMN IF NOT EXISTS billing_model_name VARCHAR(100);

COMMENT ON COLUMN crm_chat_message.credits_used IS '本次扣除积分数';
COMMENT ON COLUMN crm_chat_message.credit_multiplier IS '本次计费模型积分倍率';
COMMENT ON COLUMN crm_chat_message.billing_model_provider IS '计费模型服务商';
COMMENT ON COLUMN crm_chat_message.billing_model_name IS '计费模型名称';

CREATE TABLE IF NOT EXISTS crm_ai_model_pricing (
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(120),
    credit_multiplier NUMERIC(10, 4) NOT NULL DEFAULT 1.0000,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    remark VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (provider, model_name),
    CONSTRAINT ck_ai_model_pricing_multiplier_positive CHECK (credit_multiplier > 0)
);

COMMENT ON TABLE crm_ai_model_pricing IS 'AI 模型积分倍率配置（平台全局，仅数据库维护）';
COMMENT ON COLUMN crm_ai_model_pricing.provider IS 'AI 服务商编码';
COMMENT ON COLUMN crm_ai_model_pricing.model_name IS '模型名称';
COMMENT ON COLUMN crm_ai_model_pricing.display_name IS '模型展示名称';
COMMENT ON COLUMN crm_ai_model_pricing.credit_multiplier IS '总 token 转积分倍率';
COMMENT ON COLUMN crm_ai_model_pricing.enabled IS '是否允许在聊天中选择';

CREATE INDEX IF NOT EXISTS idx_ai_model_pricing_enabled_sort
    ON crm_ai_model_pricing (enabled, sort_order, provider, model_name);

INSERT INTO crm_ai_model_pricing (provider, model_name, display_name, credit_multiplier, enabled, sort_order, remark)
VALUES
    ('dashscope', 'qwen3.5-plus', 'qwen3.5-plus', 1.0000, TRUE, 10, '默认通义千问模型'),
    ('deepseek', 'deepseek-chat', 'deepseek-chat', 1.0000, TRUE, 20, 'DeepSeek Chat'),
    ('openai', 'gpt-5.4', 'gpt-5.4', 1.0000, TRUE, 30, 'OpenAI GPT-5.4')
ON CONFLICT (provider, model_name) DO NOTHING;
