INSERT INTO crm_system_config (config_id, config_key, config_value, config_type, description, create_time)
SELECT 108, 'product.code.required', 'true', 'product', 'Whether product code is required', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM crm_system_config WHERE config_key = 'product.code.required'
)
AND NOT EXISTS (
    SELECT 1 FROM crm_system_config WHERE config_id = 108
);
