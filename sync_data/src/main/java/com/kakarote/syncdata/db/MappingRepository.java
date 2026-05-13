package com.kakarote.syncdata.db;

import com.kakarote.syncdata.util.SnowflakeIdGenerator;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class MappingRepository {

    private static final String SOURCE_SYSTEM = "wk_crm";

    private final JdbcTemplate target;
    private final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(11);
    private final ConcurrentHashMap<MappingKey, Long> targetIdCache = new ConcurrentHashMap<>();

    /**
     * 使用目标 ai_crm 数据库保存任务、错误和新旧 ID 映射。
     */
    public MappingRepository(JdbcTemplate targetJdbcTemplate) {
        this.target = targetJdbcTemplate;
    }

    /**
     * 创建全量同步任务主记录，并返回生成的任务编号。
     */
    public long startJob(String syncMode) {
        long jobId = idGenerator.nextId();
        target.update("""
                INSERT INTO sync_full_job(job_id, sync_mode, status, message)
                VALUES (?, ?, 'running', 'started')
                """, jobId, syncMode);
        return jobId;
    }

    /**
     * 将全量同步任务标记为完成，并保存汇总行数。
     */
    public void finishJob(long jobId, long total, long success, long fail) {
        target.update("""
                UPDATE sync_full_job
                SET status = ?, finished_at = CURRENT_TIMESTAMP,
                    total_count = ?, success_count = ?, fail_count = ?, message = ?
                WHERE job_id = ?
                """, fail > 0 ? "completed_with_errors" : "completed", total, success, fail, "finished", jobId);
    }

    /**
     * 将全量同步任务标记为失败，并保存最终错误信息。
     */
    public void failJob(long jobId, String message) {
        target.update("""
                UPDATE sync_full_job
                SET status = 'failed', finished_at = CURRENT_TIMESTAMP, message = ?
                WHERE job_id = ?
                """, message, jobId);
    }

    /**
     * 将历史遗留的运行中任务标记为中断，避免服务重启后状态永久停留在 running。
     */
    public int markRunningJobsInterrupted() {
        return target.update("""
                UPDATE sync_full_job
                SET status = 'interrupted',
                    finished_at = CURRENT_TIMESTAMP,
                    message = 'Interrupted by service restart or stale job recovery.'
                WHERE status = 'running'
                """);
    }

    /**
     * 将不在当前进程活跃集合中的运行中任务标记为中断。
     */
    public int markRunningJobsInterruptedExcept(Collection<Long> activeJobIds) {
        if (activeJobIds == null || activeJobIds.isEmpty()) {
            return markRunningJobsInterrupted();
        }
        String placeholders = String.join(", ", activeJobIds.stream().map(ignored -> "?").toList());
        return target.update("""
                UPDATE sync_full_job
                SET status = 'interrupted',
                    finished_at = CURRENT_TIMESTAMP,
                    message = 'Interrupted by service restart or stale job recovery.'
                WHERE status = 'running'
                  AND job_id NOT IN (%s)
                """.formatted(placeholders), activeJobIds.toArray());
    }

    /**
     * 写入或重置某个模块的运行中状态。
     */
    public void startModule(long jobId, String moduleName, String sourceTable, String targetTable, long total) {
        target.update("""
                INSERT INTO sync_job_module(
                    id, job_id, module_name, status, source_table, target_table,
                    total_count, success_count, fail_count, started_at, message
                )
                VALUES (?, ?, ?, 'running', ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, 'running')
                ON CONFLICT (job_id, module_name)
                DO UPDATE SET status = 'running',
                              source_table = EXCLUDED.source_table,
                              target_table = EXCLUDED.target_table,
                              total_count = EXCLUDED.total_count,
                              success_count = 0,
                              fail_count = 0,
                              started_at = CURRENT_TIMESTAMP,
                              finished_at = NULL,
                              message = EXCLUDED.message
                """, idGenerator.nextId(), jobId, moduleName, sourceTable, targetTable, total);
    }

    /**
     * 更新模块批处理进度，供前端轮询展示真实进度。
     */
    public void updateModuleProgress(long jobId, String moduleName, long total, long success, long fail) {
        target.update("""
                UPDATE sync_job_module
                SET total_count = ?,
                    success_count = ?,
                    fail_count = ?,
                    status = 'running',
                    message = 'running'
                WHERE job_id = ?
                  AND module_name = ?
                """, total, success, fail, jobId, moduleName);
    }

    /**
     * 写入或更新全量同步任务下某个模块的同步统计。
     */
    public void finishModule(long jobId, String moduleName, String sourceTable, String targetTable,
                             long total, long success, long fail, String message) {
        String status = "source table skipped".equals(message)
                ? "skipped"
                : fail > 0 ? "completed_with_errors" : "completed";
        target.update("""
                INSERT INTO sync_job_module(
                    id, job_id, module_name, status, source_table, target_table,
                    total_count, success_count, fail_count, finished_at, message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                ON CONFLICT (job_id, module_name)
                DO UPDATE SET status = EXCLUDED.status,
                              source_table = EXCLUDED.source_table,
                              target_table = EXCLUDED.target_table,
                              total_count = EXCLUDED.total_count,
                              success_count = EXCLUDED.success_count,
                              fail_count = EXCLUDED.fail_count,
                              finished_at = CURRENT_TIMESTAMP,
                              message = EXCLUDED.message
                """, idGenerator.nextId(), jobId, moduleName, status, sourceTable, targetTable, total, success, fail, message);
    }

    /**
     * 将模块标记为失败，避免致命异常后前端仍看到 running 状态。
     */
    public void failModule(long jobId, String moduleName, String sourceTable, String targetTable,
                           long total, long success, long fail, String message) {
        target.update("""
                INSERT INTO sync_job_module(
                    id, job_id, module_name, status, source_table, target_table,
                    total_count, success_count, fail_count, finished_at, message
                )
                VALUES (?, ?, ?, 'failed', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                ON CONFLICT (job_id, module_name)
                DO UPDATE SET status = 'failed',
                              source_table = EXCLUDED.source_table,
                              target_table = EXCLUDED.target_table,
                              total_count = EXCLUDED.total_count,
                              success_count = EXCLUDED.success_count,
                              fail_count = EXCLUDED.fail_count,
                              finished_at = CURRENT_TIMESTAMP,
                              message = EXCLUDED.message
                """, idGenerator.nextId(), jobId, moduleName, sourceTable, targetTable,
                total, success, fail, message);
    }

    /**
     * 记录单行数据同步错误，便于任务结束后诊断。
     */
    public void recordError(long jobId, String moduleName, String sourceTable, Long sourceCompanyId,
                            String sourceId, String errorMessage) {
        target.update("""
                INSERT INTO sync_job_error(
                    id, job_id, module_name, source_table, source_company_id, source_id, error_message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, idGenerator.nextId(), jobId, moduleName, sourceTable, sourceCompanyId, sourceId, errorMessage);
    }

    /**
     * 查询一条 wk_crm 源数据对应的 ai_crm 目标 ID。
     */
    public Long findTargetId(String sourceTable, Long sourceCompanyId, Object sourceId, String targetTable) {
        MappingKey key = mappingKey(sourceTable, sourceCompanyId, sourceId, targetTable);
        Long cached = targetIdCache.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Long targetId = target.queryForObject("""
                    SELECT target_id
                    FROM sync_mapping
                    WHERE source_system = ?
                      AND source_table = ?
                      AND source_company_id = ?
                      AND source_id = ?
                      AND target_table = ?
                    """, Long.class, SOURCE_SYSTEM, key.sourceTable(), key.sourceCompanyId(),
                    key.sourceId(), key.targetTable());
            if (targetId != null) {
                targetIdCache.put(key, targetId);
            }
            return targetId;
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    /**
     * 返回已有目标 ID；不存在时生成新的 Snowflake ID 并写入映射。
     */
    public long getOrCreateTargetId(String sourceTable, Long sourceCompanyId, Object sourceId,
                                    String targetTable, Long tenantId) {
        MappingKey key = mappingKey(sourceTable, sourceCompanyId, sourceId, targetTable);
        Long cached = targetIdCache.get(key);
        if (cached != null) {
            return cached;
        }
        long targetId = idGenerator.nextId();
        Long resolvedTargetId = target.queryForObject("""
                INSERT INTO sync_mapping(
                    id, source_system, source_table, source_company_id, source_id,
                    target_table, target_id, tenant_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_system, source_table, source_company_id, source_id, target_table)
                DO UPDATE SET target_id = sync_mapping.target_id,
                              tenant_id = COALESCE(sync_mapping.tenant_id, EXCLUDED.tenant_id)
                RETURNING target_id
                """, Long.class, idGenerator.nextId(), SOURCE_SYSTEM, key.sourceTable(), key.sourceCompanyId(),
                key.sourceId(), key.targetTable(), targetId, tenantId);
        long actualTargetId = resolvedTargetId == null ? targetId : resolvedTargetId;
        targetIdCache.put(key, actualTargetId);
        return actualTargetId;
    }

    /**
     * 批量获取或创建映射 ID，避免同步大表时每行一次远程数据库往返。
     */
    public Map<String, Long> getOrCreateTargetIds(String sourceTable, Long sourceCompanyId, Collection<?> sourceIds,
                                                  String targetTable, Long tenantId) {
        Map<String, Long> resolved = findTargetIds(sourceTable, sourceCompanyId, sourceIds, targetTable);
        List<String> missingSourceIds = normalizedSourceIds(sourceIds).stream()
                .filter(sourceId -> !resolved.containsKey(sourceId))
                .toList();
        if (missingSourceIds.isEmpty()) {
            return resolved;
        }

        long safeCompanyId = safeCompanyId(sourceCompanyId);
        List<Object[]> batchArgs = new ArrayList<>();
        for (String sourceId : missingSourceIds) {
            batchArgs.add(new Object[]{
                    idGenerator.nextId(),
                    SOURCE_SYSTEM,
                    sourceTable,
                    safeCompanyId,
                    sourceId,
                    targetTable,
                    idGenerator.nextId(),
                    tenantId
            });
        }
        target.batchUpdate("""
                INSERT INTO sync_mapping(
                    id, source_system, source_table, source_company_id, source_id,
                    target_table, target_id, tenant_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_system, source_table, source_company_id, source_id, target_table)
                DO UPDATE SET target_id = sync_mapping.target_id,
                              tenant_id = COALESCE(sync_mapping.tenant_id, EXCLUDED.tenant_id)
                """, batchArgs);
        resolved.putAll(findTargetIds(sourceTable, sourceCompanyId, missingSourceIds, targetTable));
        return resolved;
    }

    /**
     * 批量查询已有映射并写入本地缓存。
     */
    public Map<String, Long> findTargetIds(String sourceTable, Long sourceCompanyId, Collection<?> sourceIds,
                                           String targetTable) {
        Map<String, Long> result = new LinkedHashMap<>();
        List<String> uncachedSourceIds = new ArrayList<>();
        long safeCompanyId = safeCompanyId(sourceCompanyId);
        for (String sourceId : normalizedSourceIds(sourceIds)) {
            MappingKey key = new MappingKey(sourceTable, safeCompanyId, sourceId, targetTable);
            Long cached = targetIdCache.get(key);
            if (cached != null) {
                result.put(sourceId, cached);
            } else {
                uncachedSourceIds.add(sourceId);
            }
        }
        if (uncachedSourceIds.isEmpty()) {
            return result;
        }

        for (List<String> batch : chunks(uncachedSourceIds, 500)) {
            String placeholders = String.join(", ", batch.stream().map(ignored -> "?").toList());
            List<Object> args = new ArrayList<>();
            args.add(SOURCE_SYSTEM);
            args.add(sourceTable);
            args.add(safeCompanyId);
            args.add(targetTable);
            args.addAll(batch);
            List<Map<String, Object>> rows = target.queryForList("""
                    SELECT source_id, target_id
                    FROM sync_mapping
                    WHERE source_system = ?
                      AND source_table = ?
                      AND source_company_id = ?
                      AND target_table = ?
                      AND source_id IN (%s)
                    """.formatted(placeholders), args.toArray());
            for (Map<String, Object> row : rows) {
                String sourceId = String.valueOf(row.get("source_id"));
                Long targetId = ((Number) row.get("target_id")).longValue();
                result.put(sourceId, targetId);
                targetIdCache.put(new MappingKey(sourceTable, safeCompanyId, sourceId, targetTable), targetId);
            }
        }
        return result;
    }

    /**
     * 新增或刷新一条源数据到目标数据的 ID 映射。
     */
    public void upsertMapping(String sourceTable, Long sourceCompanyId, Object sourceId,
                              String targetTable, Long targetId, Long tenantId) {
        MappingKey key = mappingKey(sourceTable, sourceCompanyId, sourceId, targetTable);
        target.update("""
                INSERT INTO sync_mapping(
                    id, source_system, source_table, source_company_id, source_id,
                    target_table, target_id, tenant_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_system, source_table, source_company_id, source_id, target_table)
                DO UPDATE SET target_id = EXCLUDED.target_id,
                              tenant_id = EXCLUDED.tenant_id
                """, idGenerator.nextId(), SOURCE_SYSTEM, key.sourceTable(), key.sourceCompanyId(), key.sourceId(),
                key.targetTable(), targetId, tenantId);
        if (targetId != null) {
            targetIdCache.put(key, targetId);
        }
    }

    /**
     * 刷新已有映射记录上的 tenant_id。
     */
    public void refreshTenant(String sourceTable, Long sourceCompanyId, Object sourceId, String targetTable, Long tenantId) {
        target.update("""
                UPDATE sync_mapping
                SET tenant_id = ?
                WHERE source_system = ?
                  AND source_table = ?
                  AND source_company_id = ?
                  AND source_id = ?
                  AND target_table = ?
                """, tenantId, SOURCE_SYSTEM, sourceTable, safeCompanyId(sourceCompanyId),
                String.valueOf(sourceId), targetTable);
    }

    /**
     * 删除所有曾由同步映射创建的目标数据。
     */
    public void deleteMappedTargetRows() {
        String[][] targets = {
                {"crm_follow_up", "follow_up_id"},
                {"crm_contact", "contact_id"},
                {"crm_customer", "customer_id"},
                {"crm_schedule", "schedule_id"},
                {"crm_task", "task_id"},
                {"crm_custom_field", "field_id"},
                {"manager_user_role", "id"},
                {"manager_role_menu", "id"},
                {"manager_user", "user_id"},
                {"manager_role", "role_id"},
                {"manager_dept", "dept_id"},
                {"crm_tenant", "tenant_id"}
        };
        for (String[] targetTable : targets) {
            target.update("DELETE FROM " + targetTable[0] + " WHERE " + targetTable[1] +
                    " IN (SELECT target_id FROM sync_mapping WHERE target_table = ?)", targetTable[0]);
        }
        target.update("DELETE FROM sync_mapping WHERE source_system = ?", SOURCE_SYSTEM);
        targetIdCache.clear();
    }

    /**
     * 删除指定源 company_id 曾由同步映射创建的目标数据。
     */
    public void deleteMappedTargetRows(Long sourceCompanyId) {
        String[][] targets = {
                {"crm_follow_up", "follow_up_id"},
                {"crm_contact", "contact_id"},
                {"crm_customer", "customer_id"},
                {"crm_schedule", "schedule_id"},
                {"crm_task", "task_id"},
                {"crm_custom_field", "field_id"},
                {"manager_user_role", "id"},
                {"manager_role_menu", "id"},
                {"manager_user", "user_id"},
                {"manager_role", "role_id"},
                {"manager_dept", "dept_id"}
        };
        for (String[] targetTable : targets) {
            target.update("DELETE FROM " + targetTable[0] + " WHERE " + targetTable[1] +
                            " IN (SELECT target_id FROM sync_mapping WHERE target_table = ? AND source_company_id = ?)",
                    targetTable[0], safeCompanyId(sourceCompanyId));
        }
        target.update("DELETE FROM sync_mapping WHERE source_system = ? AND source_company_id = ?",
                SOURCE_SYSTEM, safeCompanyId(sourceCompanyId));
        targetIdCache.keySet().removeIf(key -> key.sourceCompanyId() == safeCompanyId(sourceCompanyId));
    }

    /**
     * 统计指定 company_id 已有的映射数量，用于预检提示重跑语义。
     */
    public long countMappingsForCompany(Long sourceCompanyId) {
        Long count = target.queryForObject("""
                SELECT COUNT(*)
                FROM sync_mapping
                WHERE source_system = ?
                  AND source_company_id = ?
                """, Long.class, SOURCE_SYSTEM, safeCompanyId(sourceCompanyId));
        return count == null ? 0L : count;
    }

    /**
     * 分配一个新的 ai_crm ID，但不立即写入映射表。
     */
    public long nextId() {
        return idGenerator.nextId();
    }

    /**
     * 将空 company_id 归一化到全局数据共用的映射分组。
     */
    private long safeCompanyId(Long sourceCompanyId) {
        return sourceCompanyId == null ? 0L : sourceCompanyId;
    }

    private MappingKey mappingKey(String sourceTable, Long sourceCompanyId, Object sourceId, String targetTable) {
        return new MappingKey(sourceTable, safeCompanyId(sourceCompanyId), String.valueOf(sourceId), targetTable);
    }

    private List<String> normalizedSourceIds(Collection<?> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        return sourceIds.stream()
                .map(this::normalizeSourceId)
                .filter(sourceId -> sourceId != null && !sourceId.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeSourceId(Object sourceId) {
        if (sourceId == null) {
            return null;
        }
        String text = String.valueOf(sourceId).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private <T> List<List<T>> chunks(List<T> values, int size) {
        if (values.isEmpty()) {
            return List.of();
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            chunks.add(values.subList(start, Math.min(start + size, values.size())));
        }
        return chunks;
    }

    private record MappingKey(String sourceTable, long sourceCompanyId, String sourceId, String targetTable) {
    }
}
