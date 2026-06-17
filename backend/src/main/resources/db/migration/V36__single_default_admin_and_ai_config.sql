-- Ensure single-node installs have a durable built-in super administrator role.
WITH admin_role AS (
    SELECT mur.role_id
    FROM manager_user_role mur
    JOIN manager_role r ON r.role_id = mur.role_id
    WHERE mur.user_id = 1
    ORDER BY
        CASE WHEN r.realm = 'super_admin' THEN 0 ELSE 1 END,
        mur.create_time NULLS LAST,
        mur.id
    LIMIT 1
),
promoted_role AS (
    UPDATE manager_role r
    SET role_name = '超级管理员',
        realm = 'super_admin',
        description = COALESCE(NULLIF(r.description, ''), '系统内置超级管理员'),
        data_type = 5,
        update_time = CURRENT_TIMESTAMP
    WHERE r.role_id = (SELECT role_id FROM admin_role)
      AND NOT EXISTS (
          SELECT 1
          FROM manager_role existing
          WHERE existing.realm = 'super_admin'
            AND existing.role_id <> r.role_id
      )
    RETURNING r.role_id
),
existing_role AS (
    SELECT role_id
    FROM manager_role
    WHERE realm = 'super_admin'
    LIMIT 1
),
created_role AS (
    INSERT INTO manager_role (role_id, role_name, realm, description, data_type, create_user_id, create_time)
    SELECT 1000000000000000001,
           '超级管理员',
           'super_admin',
           '系统内置超级管理员',
           5,
           1,
           CURRENT_TIMESTAMP
    WHERE NOT EXISTS (SELECT 1 FROM existing_role)
      AND NOT EXISTS (SELECT 1 FROM promoted_role)
      AND NOT EXISTS (SELECT 1 FROM manager_role WHERE role_id = 1000000000000000001)
    RETURNING role_id
),
super_role AS (
    SELECT role_id FROM promoted_role
    UNION
    SELECT role_id FROM existing_role
    UNION
    SELECT role_id FROM created_role
    LIMIT 1
),
admin_binding AS (
    INSERT INTO manager_user_role (id, user_id, role_id, create_user_id, create_time)
    SELECT 1000000000000000002,
           1,
           sr.role_id,
           1,
           CURRENT_TIMESTAMP
    FROM super_role sr
    WHERE EXISTS (SELECT 1 FROM manager_user WHERE user_id = 1)
      AND NOT EXISTS (
          SELECT 1
          FROM manager_user_role mur
          WHERE mur.user_id = 1
            AND mur.role_id = sr.role_id
      )
    ON CONFLICT (id) DO NOTHING
    RETURNING id
)
INSERT INTO manager_role_menu (id, role_id, menu_id, data_scope, create_user_id, create_time)
SELECT sr.role_id + m.menu_id,
       sr.role_id,
       m.menu_id,
       5,
       1,
       CURRENT_TIMESTAMP
FROM super_role sr
JOIN manager_menu m ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM manager_role_menu rm
    WHERE rm.role_id = sr.role_id
      AND rm.menu_id = m.menu_id
)
ON CONFLICT (id) DO NOTHING;

-- Keep the bundled seed data aligned with self-managed AI key semantics.
UPDATE crm_system_config
SET config_value = '',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'ai_api_key'
  AND COALESCE(config_value, '') IN ('sk-sss', 'sk-test', 'test');

UPDATE crm_system_config
SET config_value = 'https://dashscope.aliyuncs.com/compatible-mode',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'ai_api_url'
  AND COALESCE(config_value, '') IN ('', 'https://dashscope.aliyuncs.com/compatible-mode/');

UPDATE crm_system_config
SET config_value = 'qwen3.6-plus',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'ai_model'
  AND COALESCE(config_value, '') IN ('', 'qwen-max');

WITH seed_config(config_id, config_key, config_value, config_type, description) AS (
    VALUES
        (2017413716023709698::BIGINT, 'ai_provider', 'dashscope', 'ai', 'AI provider'),
        (101::BIGINT, 'weknora_enabled', 'false', 'weknora', 'WeKnora enabled'),
        (102::BIGINT, 'weknora_base_url', '', 'weknora', 'WeKnora API base URL'),
        (103::BIGINT, 'weknora_api_key', '', 'weknora', 'WeKnora API key'),
        (104::BIGINT, 'weknora_knowledge_base_id', '', 'weknora', 'Default WeKnora knowledge base ID'),
        (105::BIGINT, 'weknora_match_count', '5', 'weknora', 'WeKnora max match count'),
        (106::BIGINT, 'weknora_vector_threshold', '0.5', 'weknora', 'WeKnora vector threshold'),
        (107::BIGINT, 'weknora_auto_rag_enabled', 'true', 'weknora', 'WeKnora auto RAG enabled')
)
INSERT INTO crm_system_config (config_id, config_key, config_value, config_type, description, create_time)
SELECT sc.config_id,
       sc.config_key,
       sc.config_value,
       sc.config_type,
       sc.description,
       CURRENT_TIMESTAMP
FROM seed_config sc
WHERE NOT EXISTS (
    SELECT 1
    FROM crm_system_config existing
    WHERE existing.config_key = sc.config_key
)
  AND NOT EXISTS (
      SELECT 1
      FROM crm_system_config existing
      WHERE existing.config_id = sc.config_id
  );
