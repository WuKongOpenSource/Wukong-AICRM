package com.kakarote.syncdata.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class TargetSchema {

    private static final Logger log = LoggerFactory.getLogger(TargetSchema.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final JdbcTemplate target;
    private final ConcurrentHashMap<String, Set<String>> columnCache = new ConcurrentHashMap<>();

    /**
     * 使用目标 ai_crm 数据库检查并准备同步所需结构。
     */
    public TargetSchema(JdbcTemplate targetJdbcTemplate) {
        this.target = targetJdbcTemplate;
    }

    /**
     * 在数据同步前确保同步元数据表和兼容字段已存在。
     */
    public void initialize() {
        ensureSyncTables();
        ensureCompatibilityColumns();
    }

    /**
     * 检查目标库当前 schema 中是否存在指定表。
     */
    public boolean tableExists(String tableName) {
        Integer count = target.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    /**
     * 检查目标表是否包含指定字段。
     */
    public boolean columnExists(String tableName, String columnName) {
        return columns(tableName).contains(columnName.toLowerCase(Locale.ROOT));
    }

    /**
     * 返回目标表的小写字段名缓存。
     */
    public Set<String> columns(String tableName) {
        return columnCache.computeIfAbsent(tableName, table -> {
            if (!tableExists(table)) {
                return Set.of();
            }
            return new LinkedHashSet<>(target.queryForList("""
                    SELECT lower(column_name)
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = ?
                    ORDER BY ordinal_position
                    """, String.class, table));
        });
    }

    /**
     * 校验动态标识符后，在字段不存在时补充目标表字段。
     */
    public void addColumnIfMissing(String tableName, String columnName, String columnType) {
        validateIdentifier(tableName);
        validateIdentifier(columnName);
        if (columnExists(tableName, columnName)) {
            return;
        }
        target.execute("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + columnName + " " + columnType);
        columnCache.remove(tableName);
        log.info("Added target column {}.{} {}", tableName, columnName, columnType);
    }

    /**
     * 创建同步元数据表、索引以及映射更新时间触发器。
     */
    private void ensureSyncTables() {
        target.execute("""
                CREATE TABLE IF NOT EXISTS sync_full_job (
                    job_id BIGINT PRIMARY KEY,
                    sync_mode VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    finished_at TIMESTAMP,
                    total_count BIGINT DEFAULT 0,
                    success_count BIGINT DEFAULT 0,
                    fail_count BIGINT DEFAULT 0,
                    message TEXT
                )
                """);
        target.execute("""
                CREATE TABLE IF NOT EXISTS sync_job_module (
                    id BIGINT PRIMARY KEY,
                    job_id BIGINT NOT NULL,
                    module_name VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'pending',
                    source_table VARCHAR(64),
                    target_table VARCHAR(64),
                    total_count BIGINT DEFAULT 0,
                    success_count BIGINT DEFAULT 0,
                    fail_count BIGINT DEFAULT 0,
                    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    finished_at TIMESTAMP,
                    message TEXT,
                    CONSTRAINT uk_sync_job_module UNIQUE (job_id, module_name)
                )
                """);
        target.execute("""
                CREATE TABLE IF NOT EXISTS sync_job_error (
                    id BIGINT PRIMARY KEY,
                    job_id BIGINT NOT NULL,
                    module_name VARCHAR(64) NOT NULL,
                    source_table VARCHAR(64),
                    source_company_id BIGINT,
                    source_id VARCHAR(128),
                    error_message TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        target.execute("""
                CREATE TABLE IF NOT EXISTS sync_company_binding (
                    binding_id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    source_system VARCHAR(32) NOT NULL DEFAULT 'wk_crm',
                    source_db VARCHAR(64) NOT NULL DEFAULT 'wk_crm',
                    source_company_id BIGINT NOT NULL,
                    source_company_name VARCHAR(255),
                    sync_direction VARCHAR(32) NOT NULL DEFAULT 'old_to_new',
                    full_sync_status VARCHAR(32) NOT NULL DEFAULT 'not_started',
                    full_sync_job_id BIGINT,
                    last_full_sync_at TIMESTAMP,
                    incremental_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    mq_topic VARCHAR(128),
                    mq_group VARCHAR(128),
                    last_incremental_event_time TIMESTAMP,
                    last_incremental_offset VARCHAR(255),
                    status SMALLINT NOT NULL DEFAULT 1,
                    remark TEXT,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_sync_company_binding_tenant UNIQUE (tenant_id),
                    CONSTRAINT uk_sync_company_binding_source UNIQUE (source_system, source_company_id)
                )
                """);
        target.execute("""
                CREATE TABLE IF NOT EXISTS sync_incremental_event_log (
                    event_log_id BIGINT PRIMARY KEY,
                    binding_id BIGINT,
                    source_system VARCHAR(32) NOT NULL DEFAULT 'wk_crm',
                    source_company_id BIGINT NOT NULL,
                    source_table VARCHAR(64) NOT NULL,
                    source_id VARCHAR(128),
                    operation VARCHAR(32) NOT NULL,
                    trace_id VARCHAR(128),
                    event_time TIMESTAMP,
                    consume_status VARCHAR(32) NOT NULL DEFAULT 'received',
                    error_message TEXT,
                    raw_payload TEXT,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        target.execute("""
                CREATE TABLE IF NOT EXISTS sync_mapping (
                    id BIGINT PRIMARY KEY,
                    source_system VARCHAR(32) NOT NULL DEFAULT 'wk_crm',
                    source_table VARCHAR(64) NOT NULL,
                    source_company_id BIGINT NOT NULL DEFAULT 0,
                    source_id VARCHAR(128) NOT NULL,
                    target_table VARCHAR(64) NOT NULL,
                    target_id BIGINT NOT NULL,
                    tenant_id BIGINT,
                    source_hash VARCHAR(128),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_sync_mapping UNIQUE
                        (source_system, source_table, source_company_id, source_id, target_table)
                )
                """);
        target.execute("CREATE INDEX IF NOT EXISTS idx_sync_company_binding_company ON sync_company_binding(source_system, source_company_id)");
        target.execute("CREATE INDEX IF NOT EXISTS idx_sync_company_binding_tenant ON sync_company_binding(tenant_id)");
        target.execute("CREATE INDEX IF NOT EXISTS idx_sync_incremental_event_binding ON sync_incremental_event_log(binding_id, create_time)");
        target.execute("CREATE INDEX IF NOT EXISTS idx_sync_incremental_event_source ON sync_incremental_event_log(source_company_id, source_table, source_id)");
        target.execute("CREATE INDEX IF NOT EXISTS idx_sync_mapping_target ON sync_mapping(target_table, target_id)");
        target.execute("CREATE INDEX IF NOT EXISTS idx_sync_mapping_tenant ON sync_mapping(tenant_id)");
        addIfTableExists("sync_job_module", "status", "VARCHAR(32) DEFAULT 'pending'");
        dropMetadataSerialDefaults();
        target.execute("""
                CREATE OR REPLACE FUNCTION sync_mapping_update_timestamp()
                RETURNS TRIGGER AS $$
                BEGIN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        target.execute("""
                DO $$
                BEGIN
                    PERFORM pg_advisory_xact_lock(hashtext('sync_mapping_update_time_trigger')::bigint);
                    IF NOT EXISTS (
                        SELECT 1
                        FROM pg_trigger
                        WHERE tgname = 'trg_sync_mapping_update_time'
                          AND tgrelid = 'sync_mapping'::regclass
                          AND NOT tgisinternal
                    ) THEN
                        EXECUTE 'CREATE TRIGGER trg_sync_mapping_update_time
                            BEFORE UPDATE ON sync_mapping
                            FOR EACH ROW EXECUTE FUNCTION sync_mapping_update_timestamp()';
                    END IF;
                END;
                $$
                """);
        columnCache.clear();
    }

    /**
     * 移除历史 BIGSERIAL 默认值，确保同步元数据主键统一由雪花算法生成。
     */
    private void dropMetadataSerialDefaults() {
        dropColumnDefault("sync_full_job", "job_id");
        dropColumnDefault("sync_job_module", "id");
        dropColumnDefault("sync_job_error", "id");
        dropColumnDefault("sync_company_binding", "binding_id");
        dropColumnDefault("sync_incremental_event_log", "event_log_id");
        dropColumnDefault("sync_mapping", "id");
    }

    /**
     * 移除指定主键字段的数据库自增默认值，避免后续插入意外依赖 sequence。
     */
    private void dropColumnDefault(String tableName, String idColumn) {
        validateIdentifier(tableName);
        validateIdentifier(idColumn);
        target.execute("ALTER TABLE IF EXISTS " + tableName + " ALTER COLUMN " + idColumn + " DROP DEFAULT");
    }

    /**
     * 创建同步模块需要但目标库可能缺失的轻量表和兼容字段。
     */
    private void ensureCompatibilityColumns() {
        ensureCustomFieldPoolTable();

        if (!tableExists("crm_tenant")) {
            target.execute("""
                    CREATE TABLE IF NOT EXISTS crm_tenant (
                        tenant_id BIGINT PRIMARY KEY,
                        tenant_name VARCHAR(100) NOT NULL,
                        contact_name VARCHAR(50),
                        contact_phone VARCHAR(20),
                        contact_email VARCHAR(100),
                        status INTEGER NOT NULL DEFAULT 1,
                        expire_time TIMESTAMP,
                        max_users INTEGER DEFAULT 50,
                        gift_token_total BIGINT DEFAULT 200000,
                        gift_token_used BIGINT DEFAULT 0,
                        remark VARCHAR(500),
                        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        if (!tableExists("manager_dept")) {
            target.execute("""
                    CREATE TABLE IF NOT EXISTS manager_dept (
                        dept_id BIGINT PRIMARY KEY,
                        dept_name VARCHAR(100) NOT NULL,
                        parent_id BIGINT DEFAULT 0,
                        sort_order INTEGER DEFAULT 0,
                        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        tenant_id BIGINT
                    )
                    """);
        }
        if (!tableExists("crm_schedule")) {
            target.execute("""
                    CREATE TABLE IF NOT EXISTS crm_schedule (
                        schedule_id BIGINT PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        description TEXT,
                        start_time TIMESTAMP NOT NULL,
                        end_time TIMESTAMP,
                        type VARCHAR(20) DEFAULT 'meeting',
                        customer_id BIGINT,
                        contact_id BIGINT,
                        location VARCHAR(255),
                        create_user_id BIGINT,
                        create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        tenant_id BIGINT NOT NULL
                    )
                    """);
        }

        addIfTableExists("crm_customer", "tenant_id", "BIGINT");
        addIfTableExists("crm_customer", "primary_contact_name", "VARCHAR(100)");
        addIfTableExists("crm_customer", "primary_contact_phone", "VARCHAR(50)");
        addIfTableExists("crm_customer", "primary_contact_position", "VARCHAR(100)");
        addIfTableExists("crm_customer", "contact_count", "INTEGER DEFAULT 0");
        addIfTableExists("crm_customer", "tag_names", "TEXT DEFAULT ''");
        addIfTableExists("crm_customer", "search_text", "TEXT");
        addIfTableExists("crm_customer", "logo", "VARCHAR(500)");

        addIfTableExists("crm_contact", "tenant_id", "BIGINT");
        addIfTableExists("crm_follow_up", "tenant_id", "BIGINT");
        addIfTableExists("crm_follow_up", "summary", "VARCHAR(500)");
        addIfTableExists("crm_follow_up", "scene_type", "VARCHAR(100)");
        addIfTableExists("crm_follow_up", "ai_generated", "SMALLINT DEFAULT 0");

        addIfTableExists("crm_task", "tenant_id", "BIGINT");
        addIfTableExists("crm_task", "source_follow_up_id", "BIGINT");
        addIfTableExists("crm_task", "task_type", "VARCHAR(50)");
        addIfTableExists("crm_task", "participant_names", "TEXT");

        addIfTableExists("crm_schedule", "participant_user_ids", "TEXT");
        addIfTableExists("manager_user", "tenant_id", "BIGINT");
        addIfTableExists("manager_dept", "tenant_id", "BIGINT");
        addIfTableExists("manager_role", "tenant_id", "BIGINT");
        addIfTableExists("manager_user_role", "tenant_id", "BIGINT");
        addIfTableExists("manager_role_menu", "data_scope", "INTEGER");
        addIfTableExists("crm_custom_field", "tenant_id", "BIGINT");
        addIfTableExists("crm_custom_field", "field_source", "VARCHAR(32)");
    }

    /**
     * 仅在目标表存在时补充兼容字段。
     */
    /**
     * Prepare the shared custom-field pool used by AI CRM for reusable physical field columns.
     */
    private void ensureCustomFieldPoolTable() {
        target.execute("""
                CREATE TABLE IF NOT EXISTS crm_custom_field_pool (
                    pool_id BIGINT PRIMARY KEY,
                    entity_type VARCHAR(50) NOT NULL,
                    column_name VARCHAR(100) NOT NULL,
                    column_type VARCHAR(100) NOT NULL,
                    field_type VARCHAR(50) NOT NULL,
                    column_created BOOLEAN DEFAULT TRUE,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        target.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_custom_field_pool_entity_column
                ON crm_custom_field_pool(entity_type, column_name)
                """);
        target.execute("""
                CREATE INDEX IF NOT EXISTS idx_custom_field_pool_entity_type
                ON crm_custom_field_pool(entity_type, field_type)
                """);
        columnCache.remove("crm_custom_field_pool");
    }

    private void addIfTableExists(String tableName, String columnName, String columnType) {
        if (tableExists(tableName)) {
            addColumnIfMissing(tableName, columnName, columnType);
        }
    }

    /**
     * 校验生成 DDL 时使用的 SQL 标识符。
     */
    private void validateIdentifier(String value) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + value);
        }
    }
}
