ALTER TABLE crm_chat_session
    ADD COLUMN IF NOT EXISTS project_id BIGINT,
    ADD COLUMN IF NOT EXISTS project_task_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_chat_session_project_id
    ON crm_chat_session (project_id);

CREATE INDEX IF NOT EXISTS idx_chat_session_project_task_id
    ON crm_chat_session (project_task_id);

COMMENT ON COLUMN crm_chat_session.project_id IS '项目对象会话关联的项目ID';
COMMENT ON COLUMN crm_chat_session.project_task_id IS '项目任务对象会话关联的任务ID';
