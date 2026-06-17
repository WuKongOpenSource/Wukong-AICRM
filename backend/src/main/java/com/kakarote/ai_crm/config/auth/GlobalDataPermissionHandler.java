package com.kakarote.ai_crm.config.auth;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.kakarote.ai_crm.common.auth.DataPermissionContext;
import com.kakarote.ai_crm.service.DataPermissionService;
import com.kakarote.ai_crm.utils.UserUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlobalDataPermissionHandler implements MultiDataPermissionHandler {

    private static final Map<String, String> MODULE_BY_MAPPER = Map.ofEntries(
            Map.entry("CustomerMapper", "customer"),
            Map.entry("ContactMapper", "contact"),
            Map.entry("TaskMapper", "task"),
            Map.entry("FollowUpMapper", "followup"),
            Map.entry("KnowledgeMapper", "knowledge"),
            Map.entry("RelationMapper", "relation"),
            Map.entry("ProductMapper", "product")
    );

    @Autowired
    private ObjectProvider<DataPermissionService> dataPermissionServiceProvider;

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        String module = resolveModule(mappedStatementId);
        if (module == null) {
            return null;
        }

        Long currentUserId = resolveCurrentUserId();
        if ("relation".equals(module)) {
            return parseSqlSegment(buildRelationPrivacySegment(module, table, currentUserId));
        }

        DataPermissionService dataPermissionService = dataPermissionServiceProvider.getIfAvailable();
        if (dataPermissionService == null) {
            log.warn("DataPermissionService is not available yet, skip data permission for {}", mappedStatementId);
            return null;
        }

        DataPermissionContext context = dataPermissionService.createContext(module);
        String sqlSegment = buildSqlSegment(module, table, context);
        String relationPrivacySegment = buildRelationPrivacySegment(module, table, currentUserId);
        if (sqlSegment != null && relationPrivacySegment != null) {
            sqlSegment = "(" + sqlSegment + ") AND (" + relationPrivacySegment + ")";
        } else if (relationPrivacySegment != null) {
            sqlSegment = relationPrivacySegment;
        }
        return parseSqlSegment(sqlSegment);
    }

    private Expression parseSqlSegment(String sqlSegment) {
        if (sqlSegment == null) {
            return null;
        }
        try {
            return CCJSqlParserUtil.parseCondExpression(sqlSegment);
        } catch (JSQLParserException e) {
            log.error("Failed to parse data permission SQL segment: {}", sqlSegment, e);
            throw new IllegalStateException("数据权限 SQL 片段解析失败", e);
        }
    }

    private String resolveModule(String mappedStatementId) {
        return MODULE_BY_MAPPER.entrySet().stream()
                .filter(entry -> mappedStatementId.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String buildSqlSegment(String module, Table table, DataPermissionContext context) {
        if ("relation".equals(module)) {
            return null;
        }
        if (context != null && context.isAllData()) {
            return null;
        }
        List<Long> userIds = context == null ? null : context.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            return "1 = 0";
        }

        String tableName = normalizeTableName(table.getName());
        String inClause = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        return switch (module) {
            case "customer" -> "crm_customer".equals(tableName)
                    ? qualifiedColumn(table, "owner_id") + " IN (" + inClause + ")"
                    : null;
            case "contact" -> "crm_contact".equals(tableName)
                    ? qualifiedColumn(table, "customer_id")
                    + " IN (SELECT customer_id FROM crm_customer WHERE owner_id IN (" + inClause + "))"
                    : null;
            case "task" -> "crm_task".equals(tableName)
                    ? "((" + qualifiedColumn(table, "relation_id") + " IS NULL AND "
                    + qualifiedColumn(table, "assigned_to") + " IN (" + inClause + "))"
                    + " OR (" + qualifiedColumn(table, "relation_id") + " IS NOT NULL AND "
                    + qualifiedColumn(table, "create_user_id") + " IN (" + inClause + "))"
                    + " OR (" + qualifiedColumn(table, "project_id") + " IS NOT NULL AND "
                    + qualifiedColumn(table, "project_id") + " IN ("
                    + "SELECT p.project_id FROM crm_project p WHERE p.owner_id IN (" + inClause + ") "
                    + "OR EXISTS (SELECT 1 FROM crm_project_member m WHERE m.project_id = p.project_id "
                    + "AND m.user_id IN (" + inClause + ") AND m.status = 'ACTIVE')"
                    + ")))"
                    : null;
            case "followup" -> "crm_follow_up".equals(tableName)
                    ? "((" + qualifiedColumn(table, "customer_id")
                    + " IN (SELECT customer_id FROM crm_customer WHERE owner_id IN (" + inClause + ")))"
                    + " OR (" + qualifiedColumn(table, "relation_id") + " IS NOT NULL AND "
                    + qualifiedColumn(table, "create_user_id") + " IN (" + inClause + ")))"
                    : null;
            case "knowledge" -> "crm_knowledge".equals(tableName)
                    ? "((" + qualifiedColumn(table, "customer_id") + " IS NOT NULL AND "
                    + qualifiedColumn(table, "customer_id")
                    + " IN (SELECT customer_id FROM crm_customer WHERE owner_id IN (" + inClause + ")))"
                    + " OR (" + qualifiedColumn(table, "employee_id") + " IS NOT NULL AND "
                    + qualifiedColumn(table, "employee_id") + " IN (" + inClause + "))"
                    + " OR (" + qualifiedColumn(table, "customer_id") + " IS NULL AND "
                    + qualifiedColumn(table, "employee_id") + " IS NULL AND "
                    + qualifiedColumn(table, "upload_user_id") + " IN (" + inClause + ")))"
                    : null;
            case "product" -> "crm_product".equals(tableName)
                    ? qualifiedColumn(table, "owner_id") + " IN (" + inClause + ")"
                    : null;
            default -> null;
        };
    }

    private String buildRelationPrivacySegment(String module, Table table, Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        String tableName = normalizeTableName(table.getName());
        return switch (module) {
            case "relation" -> "crm_relation".equals(tableName)
                    ? qualifiedColumn(table, "create_user_id") + " = " + currentUserId
                    : null;
            case "task" -> "crm_task".equals(tableName)
                    ? "(" + qualifiedColumn(table, "relation_id") + " IS NULL OR "
                    + qualifiedColumn(table, "project_id") + " IS NOT NULL OR "
                    + qualifiedColumn(table, "create_user_id") + " = " + currentUserId + ")"
                    : null;
            case "followup" -> "crm_follow_up".equals(tableName)
                    ? "(" + qualifiedColumn(table, "relation_id") + " IS NULL OR "
                    + qualifiedColumn(table, "create_user_id") + " = " + currentUserId + ")"
                    : null;
            case "knowledge" -> "crm_knowledge".equals(tableName)
                    ? "(" + qualifiedColumn(table, "relation_id") + " IS NULL OR "
                    + qualifiedColumn(table, "upload_user_id") + " = " + currentUserId + ")"
                    : null;
            default -> null;
        };
    }

    private Long resolveCurrentUserId() {
        try {
            return UserUtil.getUserId();
        } catch (Exception exception) {
            return null;
        }
    }

    private String normalizeTableName(String tableName) {
        return Optional.ofNullable(tableName)
                .map(name -> name.replace("\"", "").toLowerCase())
                .orElse("");
    }

    private String qualifiedColumn(Table table, String column) {
        Alias alias = table.getAlias();
        String prefix = alias != null ? alias.getName() : null;
        if (prefix == null || prefix.isBlank()) {
            return column;
        }
        return prefix + "." + column;
    }
}
