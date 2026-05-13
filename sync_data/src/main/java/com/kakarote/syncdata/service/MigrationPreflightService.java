package com.kakarote.syncdata.service;

import com.kakarote.syncdata.SyncProperties;
import com.kakarote.syncdata.db.CompanyBindingRepository;
import com.kakarote.syncdata.db.MappingRepository;
import com.kakarote.syncdata.db.TargetSchema;
import com.kakarote.syncdata.model.CompanyBinding;
import com.kakarote.syncdata.model.MigrationModuleCoverage;
import com.kakarote.syncdata.model.MigrationPreflightResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MigrationPreflightService {

    private static final List<ModuleDefinition> SUPPORTED_MODULES = List.of(
            new ModuleDefinition("tenants", "租户绑定", "sync_company_binding", "crm_tenant", null, List.of(), false),
            new ModuleDefinition("departments", "部门", "wk_admin_dept", "manager_dept", null, List.of("company_id", "dept_id"), false),
            new ModuleDefinition("roles", "角色", "wk_admin_role", "manager_role", null, List.of("company_id", "role_id"), false),
            new ModuleDefinition("users", "用户", "wk_admin_user", "manager_user", null, List.of("company_id", "user_id"), false),
            new ModuleDefinition("user_roles", "用户角色", "wk_admin_user_role", "manager_user_role", null, List.of("company_id", "id", "user_id", "role_id"), false),
            new ModuleDefinition("custom_fields", "自定义字段", "wk_crm_field", "crm_custom_field", "label IN (2, 3)", List.of("company_id", "field_id", "label"), false),
            new ModuleDefinition("customers", "客户", "wk_crm_customer", "crm_customer", null, List.of("company_id", "customer_id"), true),
            new ModuleDefinition("contacts", "联系人", "wk_crm_contacts", "crm_contact", null, List.of("company_id", "contacts_id", "customer_id"), false),
            new ModuleDefinition("customer_custom_values", "客户自定义字段值", "wk_crm_customer_data", "crm_customer", null, List.of("company_id", "id", "field_id"), false),
            new ModuleDefinition("contact_custom_values", "联系人自定义字段值", "wk_crm_contacts_data", "crm_contact", null, List.of("company_id", "id", "field_id"), false),
            new ModuleDefinition("follow_ups", "跟进记录", "wk_crm_activity", "crm_follow_up", "type = 1 AND COALESCE(status, 1) = 1 AND activity_type IN (2, 3)", List.of("company_id", "id"), false),
            new ModuleDefinition("schedules", "日程", "wk_oa_event", "crm_schedule", null, List.of("company_id", "event_id", "start_time"), false),
            new ModuleDefinition("project_tasks", "项目任务", "wk_project_task", "crm_task", "COALESCE(ishidden, 0) = 0", List.of("company_id", "task_id"), false),
            new ModuleDefinition("work_tasks", "工作台任务", "wk_work_task", "crm_task", "COALESCE(ishidden, 0) = 0", List.of("company_id", "task_id"), false)
    );

    private static final List<UnsupportedModule> UNSUPPORTED_MODULES = List.of(
            new UnsupportedModule("business", "商机", "wk_crm_business"),
            new UnsupportedModule("contracts", "合同", "wk_crm_contract"),
            new UnsupportedModule("receivables", "回款", "wk_crm_receivables"),
            new UnsupportedModule("products", "产品", "wk_crm_product"),
            new UnsupportedModule("invoices", "发票", "wk_crm_invoice"),
            new UnsupportedModule("approvals", "审批", "wk_crm_check")
    );

    private static final Set<String> AUTO_PREPARED_TARGETS = Set.of(
            "crm_tenant", "manager_dept", "crm_schedule"
    );

    private final JdbcTemplate oldCrm;
    private final TargetSchema targetSchema;
    private final CompanyBindingRepository bindingRepository;
    private final MappingRepository mappingRepository;
    private final SyncProperties properties;

    /**
     * Inject source/target helpers used by migration preflight checks.
     */
    public MigrationPreflightService(@Qualifier("oldCrmJdbcTemplate") JdbcTemplate oldCrmJdbcTemplate,
                                     TargetSchema targetSchema,
                                     CompanyBindingRepository bindingRepository,
                                     MappingRepository mappingRepository,
                                     SyncProperties properties) {
        this.oldCrm = oldCrmJdbcTemplate;
        this.targetSchema = targetSchema;
        this.bindingRepository = bindingRepository;
        this.mappingRepository = mappingRepository;
        this.properties = properties;
    }

    /**
     * Inspect source, target, binding, coverage, and feature state before a migration starts.
     */
    public MigrationPreflightResult preflight(Long tenantId, Long companyId, Boolean incrementalRequested) {
        targetSchema.initialize();
        List<MigrationPreflightResult.PreflightIssue> errors = new ArrayList<>();
        List<MigrationPreflightResult.PreflightIssue> warnings = new ArrayList<>();
        List<MigrationModuleCoverage> modules = new ArrayList<>();
        Map<String, Long> rowCounts = new LinkedHashMap<>();

        if (tenantId == null) {
            errors.add(issue("tenant_required", "无法获取当前登录企业，请重新登录后再同步。", null));
        }
        if (companyId == null) {
            errors.add(issue("company_required", "请选择企业名称。", null));
        }

        CompanyBinding tenantBinding = tenantId == null ? null : bindingRepository.findByTenantId(tenantId);
        CompanyBinding companyBinding = companyId == null ? null : bindingRepository.findActiveByCompanyId(companyId);
        if (tenantBinding != null && companyId != null && !tenantBinding.sourceCompanyId().equals(companyId)) {
            warnings.add(issue("tenant_rebind", "当前登录企业已绑定其他源企业，继续执行会更新绑定关系。", null));
        }
        if (companyBinding != null && tenantId != null && !companyBinding.tenantId().equals(tenantId)) {
            errors.add(issue("company_bound_to_other_tenant", "该企业已绑定到其他登录企业，不能重复绑定。", null));
        }

        for (ModuleDefinition module : SUPPORTED_MODULES) {
            MigrationModuleCoverage coverage = inspectSupportedModule(module, companyId, errors, warnings);
            modules.add(coverage);
            rowCounts.put(module.key(), coverage.rowCount());
        }
        for (UnsupportedModule module : UNSUPPORTED_MODULES) {
            MigrationModuleCoverage coverage = inspectUnsupportedModule(module, companyId);
            modules.add(coverage);
            rowCounts.put(module.key(), coverage.rowCount());
            if ("unavailable".equals(coverage.status())) {
                warnings.add(issue("module_unavailable", coverage.label() + "源数据暂不会迁移。", coverage.key()));
            }
        }

        if (Boolean.TRUE.equals(incrementalRequested)) {
            warnings.add(issue("incremental_reserved", "增量同步目前仅记录事件，不会应用到目标业务表。", null));
        }

        long mappingCount = companyId == null ? 0L : mappingRepository.countMappingsForCompany(companyId);
        MigrationPreflightResult.RerunInfo rerun = new MigrationPreflightResult.RerunInfo(
                tenantBinding != null || companyBinding != null,
                mappingCount > 0,
                mappingCount,
                mappingCount > 0
                        ? "检测到已有映射，重复执行会复用目标 ID 并执行更新。"
                        : "未检测到该 company 的历史映射，本次将创建新的迁移映射。"
        );
        MigrationPreflightResult.CleanupInfo cleanup = new MigrationPreflightResult.CleanupInfo(
                properties.isTruncateBeforeSync(),
                properties.isTruncateBeforeSync()
                        ? "已启用同步前清理，只会删除当前 company 在 sync_mapping 中记录过的目标数据。"
                        : "未启用同步前清理，重复执行将按 sync_mapping 幂等更新目标数据。"
        );
        if (properties.isTruncateBeforeSync()) {
            warnings.add(issue("cleanup_enabled", cleanup.message(), null));
        }

        MigrationPreflightResult.IncrementalCapability incremental =
                new MigrationPreflightResult.IncrementalCapability(
                        false,
                        "reserved",
                        "增量事件目前仅审计记录，尚未实现对目标业务表的增删改应用。"
                );

        return new MigrationPreflightResult(
                tenantId,
                companyId,
                errors.isEmpty(),
                List.copyOf(errors),
                List.copyOf(warnings),
                List.copyOf(modules),
                Map.copyOf(rowCounts),
                rerun,
                cleanup,
                incremental
        );
    }

    private MigrationModuleCoverage inspectSupportedModule(ModuleDefinition module,
                                                           Long companyId,
                                                           List<MigrationPreflightResult.PreflightIssue> errors,
                                                           List<MigrationPreflightResult.PreflightIssue> warnings) {
        if ("sync_company_binding".equals(module.sourceTable())) {
            boolean targetReady = targetSchema.tableExists(module.targetTable());
            if (!targetReady) {
                errors.add(issue("target_table_missing", "目标表缺失：" + module.targetTable(), module.key()));
            }
            return new MigrationModuleCoverage(module.key(), module.label(), module.sourceTable(),
                    module.targetTable(), targetReady ? "supported" : "blocked", 1L,
                    targetReady ? "使用已绑定租户。" : "目标租户表不可用。");
        }

        if (!oldTableExists(module.sourceTable())) {
            if (module.required()) {
                errors.add(issue("source_table_missing", "源表缺失：" + module.sourceTable(), module.key()));
                return coverage(module, "blocked", 0L, "必需源表不存在。");
            }
            warnings.add(issue("source_table_skipped", "源表不存在，模块将跳过：" + module.sourceTable(), module.key()));
            return coverage(module, "skipped", 0L, "源表不存在，已跳过。");
        }

        List<String> missingColumns = module.requiredColumns().stream()
                .filter(column -> !oldColumnExists(module.sourceTable(), column))
                .toList();
        if (!missingColumns.isEmpty()) {
            errors.add(issue("source_columns_missing",
                    "源表 " + module.sourceTable() + " 缺少字段：" + String.join(", ", missingColumns),
                    module.key()));
            return coverage(module, "blocked", 0L, "源表字段不兼容。");
        }

        if (!AUTO_PREPARED_TARGETS.contains(module.targetTable()) && !targetSchema.tableExists(module.targetTable())) {
            errors.add(issue("target_table_missing", "目标表缺失：" + module.targetTable(), module.key()));
            return coverage(module, "blocked", 0L, "目标表不存在。");
        }

        Long count = countRows(module.sourceTable(), module.whereClause(), companyId);
        return coverage(module, count == 0 ? "skipped" : "supported", count,
                count == 0 ? "当前 company 无需迁移的数据。" : "将参与全量迁移。");
    }

    private MigrationModuleCoverage inspectUnsupportedModule(UnsupportedModule module, Long companyId) {
        if (!oldTableExists(module.sourceTable())) {
            return new MigrationModuleCoverage(module.key(), module.label(), module.sourceTable(), null,
                    "skipped", 0L, "源表不存在。");
        }
        Long count = countRows(module.sourceTable(), null, companyId);
        return new MigrationModuleCoverage(module.key(), module.label(), module.sourceTable(), null,
                count > 0 ? "unavailable" : "skipped", count,
                count > 0 ? "目标业务模型暂未接入，当前不会迁移。" : "当前 company 无源数据。");
    }

    private MigrationModuleCoverage coverage(ModuleDefinition module, String status, Long count, String message) {
        return new MigrationModuleCoverage(module.key(), module.label(), module.sourceTable(),
                module.targetTable(), status, count, message);
    }

    private Long countRows(String tableName, String whereClause, Long companyId) {
        String where = buildWhere(tableName, whereClause, companyId);
        Long count = oldCrm.queryForObject("SELECT COUNT(*) FROM " + tableName + where, Long.class);
        return count == null ? 0L : count;
    }

    private String buildWhere(String tableName, String whereClause, Long companyId) {
        List<String> conditions = new ArrayList<>();
        if (whereClause != null && !whereClause.isBlank()) {
            conditions.add("(" + whereClause + ")");
        }
        if (companyId != null && oldColumnExists(tableName, "company_id")) {
            conditions.add("company_id = " + companyId);
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private boolean oldTableExists(String tableName) {
        Integer count = oldCrm.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean oldColumnExists(String tableName, String columnName) {
        Integer count = oldCrm.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private MigrationPreflightResult.PreflightIssue issue(String code, String message, String module) {
        return new MigrationPreflightResult.PreflightIssue(code, message, module);
    }

    private record ModuleDefinition(String key, String label, String sourceTable, String targetTable,
                                    String whereClause, List<String> requiredColumns, boolean required) {
    }

    private record UnsupportedModule(String key, String label, String sourceTable) {
    }
}
