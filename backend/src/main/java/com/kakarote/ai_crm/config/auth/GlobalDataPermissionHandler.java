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
            Map.entry("WecomEmployeeMapper", "wecomEmployeeSession"),
            Map.entry("WecomExternalCustomerMapper", "wecomCustomer"),
            Map.entry("WecomCustomerBindingMapper", "wecomCustomer"),
            Map.entry("TencentMeetingMapper", "tencentMeeting"),
            Map.entry("TencentMeetingParticipantMapper", "tencentMeeting"),
            Map.entry("TencentMeetingRecordingMapper", "tencentMeeting"),
            Map.entry("TencentMeetingTranscriptSegmentMapper", "tencentMeeting"),
            Map.entry("TencentMeetingCustomerBindingMapper", "tencentMeeting")
    );

    @Autowired
    private ObjectProvider<DataPermissionService> dataPermissionServiceProvider;

    /**
     * 获取SQLSegment。
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        String module = resolveModule(mappedStatementId);
        if (module == null) {
            return null;
        }

        DataPermissionService dataPermissionService = dataPermissionServiceProvider.getIfAvailable();
        if (dataPermissionService == null) {
            log.warn("DataPermissionService is not available yet, skip data permission for {}", mappedStatementId);
            return null;
        }

        Long currentUserId = resolveCurrentUserId();
        DataPermissionContext context = dataPermissionService.createContext(module);

        // MyBatis-Plus 会按 SQL 中出现的表逐个回调这里，因此需要先把 mapper 映射成模块，再按具体表名生成过滤片段。
        String sqlSegment = buildSqlSegment(module, table, context);
        String relationPrivacySegment = buildRelationPrivacySegment(module, table, currentUserId);
        if (sqlSegment != null && relationPrivacySegment != null) {
            sqlSegment = "(" + sqlSegment + ") AND (" + relationPrivacySegment + ")";
        } else if (relationPrivacySegment != null) {
            sqlSegment = relationPrivacySegment;
        }
        if (sqlSegment == null) {
            return null;
        }

        try {
            return CCJSqlParserUtil.parseCondExpression(sqlSegment);
        } catch (JSQLParserException e) {
            log.error("Failed to parse data permission SQL segment: {}", sqlSegment, e);
            throw new IllegalStateException("Failed to parse data permission SQL segment", e);
        }
    }

    /**
     * 解析模块。
     */
    private String resolveModule(String mappedStatementId) {
        // 用 mapper 名推断权限模块，避免在每个 XML/方法上手工重复声明模块信息。
        return MODULE_BY_MAPPER.entrySet().stream()
                .filter(entry -> mappedStatementId.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 构建SQLSegment。
     */
    private String buildSqlSegment(String module, Table table, DataPermissionContext context) {
        if ("relation".equals(module)) {
            return null;
        }
        if (context != null && context.isAllData()) {
            return null;
        }
        List<Long> userIds = context == null ? null : context.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            // 无可见用户时返回恒假表达式，显式拒绝访问，避免因为 null 片段而误放行整表数据。
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
            case "wecomEmployeeSession" -> "crm_wecom_employee".equals(tableName)
                    ? qualifiedColumn(table, "crm_user_id") + " IN (" + inClause + ")"
                    : null;
            case "wecomCustomer" -> switch (tableName) {
                case "crm_wecom_external_customer", "crm_wecom_customer_binding" ->
                        qualifiedColumn(table, "customer_id")
                                + " IN (SELECT customer_id FROM crm_customer WHERE owner_id IN (" + inClause + "))";
                default -> null;
            };
            case "tencentMeeting" -> switch (tableName) {
                case "crm_tencent_meeting" ->
                        "((" + qualifiedColumn(table, "customer_id") + " IS NOT NULL AND "
                                + qualifiedColumn(table, "customer_id")
                                + " IN (SELECT customer_id FROM crm_customer WHERE owner_id IN (" + inClause + ")))"
                                + " OR (" + qualifiedColumn(table, "customer_id") + " IS NULL AND "
                                + qualifiedColumn(table, "crm_creator_user_id") + " IN (" + inClause + ")))";
                case "crm_tencent_meeting_customer_binding" ->
                        qualifiedColumn(table, "customer_id")
                                + " IN (SELECT customer_id FROM crm_customer WHERE owner_id IN (" + inClause + "))";
                case "crm_tencent_meeting_participant", "crm_tencent_meeting_recording", "crm_tencent_meeting_transcript_segment" ->
                        qualifiedColumn(table, "meeting_db_id")
                                + " IN (SELECT id FROM crm_tencent_meeting WHERE "
                                + "((customer_id IS NOT NULL AND customer_id IN (SELECT customer_id FROM crm_customer WHERE owner_id IN (" + inClause + ")))"
                                + " OR (customer_id IS NULL AND crm_creator_user_id IN (" + inClause + "))))";
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * 关系模块及关系挂载数据始终按当前创建人私有，不受角色“全部数据”范围放大。
     */
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

    /**
     * 标准化Table名称。
     */
    private String normalizeTableName(String tableName) {
        return Optional.ofNullable(tableName)
                .map(name -> name.replace("\"", "").toLowerCase())
                .orElse("");
    }

    /**
     * 处理qualifiedColumn方法逻辑。
     */
    private String qualifiedColumn(Table table, String column) {
        Alias alias = table.getAlias();
        String prefix = alias != null ? alias.getName() : null;
        if (prefix == null || prefix.isBlank()) {
            return column;
        }
        // 统一带别名可避免 JOIN 场景下列名歧义，否则数据权限 SQL 很容易在复杂查询里失效。
        return prefix + "." + column;
    }
}
