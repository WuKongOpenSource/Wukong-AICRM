INSERT INTO crm_ai_model_pricing (provider, model_name, display_name, credit_multiplier, enabled, sort_order, remark)
VALUES
    ('openai', 'gpt-5.4', 'OpenAI GPT-5.4', 1.0000, TRUE, 30, 'System default OpenAI model'),
    ('openai', 'gpt-5.2', 'OpenAI GPT-5.2', 1.0000, TRUE, 31, 'System default OpenAI model'),
    ('deepseek', 'deepseek-chat', 'DeepSeek Chat', 1.0000, TRUE, 40, 'System default DeepSeek model'),
    ('deepseek', 'deepseek-reasoner', 'DeepSeek Reasoner', 1.0000, TRUE, 41, 'System default DeepSeek model'),
    ('moonshot', 'kimi-k2-thinking-turbo', 'Kimi K2 Thinking Turbo', 1.0000, TRUE, 50, 'System default Kimi model'),
    ('moonshot', 'kimi-k2-0905-preview', 'Kimi K2 0905 Preview', 1.0000, TRUE, 51, 'System default Kimi model'),
    ('moonshot', 'kimi-latest', 'Kimi Latest', 1.0000, TRUE, 52, 'System default Kimi model')
ON CONFLICT (provider, model_name) DO NOTHING;
