ALTER TABLE crm_project_task
    ADD COLUMN IF NOT EXISTS participant_user_ids TEXT,
    ADD COLUMN IF NOT EXISTS participant_names TEXT,
    ADD COLUMN IF NOT EXISTS source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS has_schedule BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE crm_project_task
SET source = CASE WHEN generated_by_ai THEN 'ai' ELSE 'manual' END
WHERE source IS NULL;

CREATE TABLE IF NOT EXISTS crm_project_member (
    member_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_name VARCHAR(100) NOT NULL,
    account VARCHAR(100),
    role VARCHAR(32) NOT NULL,
    dept_name VARCHAR(100),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_action_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    permissions TEXT,
    remark TEXT,
    create_user_id BIGINT,
    update_user_id BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_crm_project_member_user
    ON crm_project_member(project_id, user_id);
CREATE INDEX IF NOT EXISTS idx_crm_project_member_user_status
    ON crm_project_member(user_id, status);

DROP TRIGGER IF EXISTS trg_project_member_update_time ON crm_project_member;
CREATE TRIGGER trg_project_member_update_time
    BEFORE UPDATE ON crm_project_member
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TABLE IF NOT EXISTS crm_project_member_log (
    log_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    operator_id BIGINT,
    operator_name VARCHAR(100),
    action_type VARCHAR(32) NOT NULL,
    target_user_id BIGINT,
    target_user_name VARCHAR(100),
    before_summary TEXT,
    after_summary TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id)
);

CREATE INDEX IF NOT EXISTS idx_crm_project_member_log_project_time
    ON crm_project_member_log(project_id, create_time DESC);

CREATE TABLE IF NOT EXISTS crm_project_chat_message (
    message_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    create_user_id BIGINT,
    create_user_name VARCHAR(100),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id)
);

CREATE INDEX IF NOT EXISTS idx_crm_project_chat_project_time
    ON crm_project_chat_message(project_id, create_time);

CREATE TABLE IF NOT EXISTS crm_project_task_chat_message (
    message_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    create_user_id BIGINT,
    create_user_name VARCHAR(100),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id)
);

CREATE INDEX IF NOT EXISTS idx_crm_project_task_chat_task_time
    ON crm_project_task_chat_message(task_id, create_time);

CREATE TABLE IF NOT EXISTS crm_project_task_note (
    note_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    create_user_id BIGINT,
    create_user_name VARCHAR(100),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (note_id)
);

CREATE INDEX IF NOT EXISTS idx_crm_project_task_note_task_time
    ON crm_project_task_note(task_id, create_time DESC);

CREATE TABLE IF NOT EXISTS crm_project_task_schedule (
    schedule_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    schedule_time TIMESTAMP,
    create_user_id BIGINT,
    create_user_name VARCHAR(100),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id)
);

CREATE INDEX IF NOT EXISTS idx_crm_project_task_schedule_task_time
    ON crm_project_task_schedule(task_id, schedule_time);
