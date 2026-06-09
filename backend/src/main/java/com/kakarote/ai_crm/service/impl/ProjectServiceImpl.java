package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakarote.ai_crm.ai.AiMode;
import com.kakarote.ai_crm.ai.AiModelSource;
import com.kakarote.ai_crm.ai.DynamicChatClientProvider;
import com.kakarote.ai_crm.ai.app.ChatApplicationCodes;
import com.kakarote.ai_crm.ai.context.AiContextHolder;
import com.kakarote.ai_crm.ai.provider.AiModelCapabilities;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.ChatSendBO;
import com.kakarote.ai_crm.entity.BO.ProjectBO;
import com.kakarote.ai_crm.entity.VO.KnowledgeVO;
import com.kakarote.ai_crm.entity.VO.ProjectVO;
import com.kakarote.ai_crm.service.FileStorageService;
import com.kakarote.ai_crm.service.IKnowledgeService;
import com.kakarote.ai_crm.service.IProjectService;
import com.kakarote.ai_crm.service.ISystemConfigService;
import com.kakarote.ai_crm.service.PermissionService;
import com.kakarote.ai_crm.utils.AiMediaUtil;
import com.kakarote.ai_crm.utils.DocumentTextExtractor;
import com.kakarote.ai_crm.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements IProjectService {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String PERMISSION_VIEW_PROJECT = "VIEW_PROJECT";
    private static final String PERMISSION_EDIT_PROJECT = "EDIT_PROJECT";
    private static final String PERMISSION_DELETE_PROJECT = "DELETE_PROJECT";
    private static final String PERMISSION_ARCHIVE_PROJECT = "ARCHIVE_PROJECT";
    private static final String PERMISSION_ADD_MEMBER = "ADD_MEMBER";
    private static final String PERMISSION_REMOVE_MEMBER = "REMOVE_MEMBER";
    private static final String PERMISSION_MODIFY_MEMBER_PERMISSION = "MODIFY_MEMBER_PERMISSION";
    private static final String PERMISSION_CREATE_TASK = "CREATE_TASK";
    private static final String PERMISSION_EDIT_TASK = "EDIT_TASK";
    private static final String PERMISSION_DELETE_TASK = "DELETE_TASK";
    private static final String PERMISSION_MOVE_TASK = "MOVE_TASK";
    private static final String PERMISSION_ADD_LANE = "ADD_LANE";
    private static final String PERMISSION_EDIT_LANE = "EDIT_LANE";
    private static final String PERMISSION_DELETE_LANE = "DELETE_LANE";
    private static final String PERMISSION_USE_AI_CHAT = "USE_AI_CHAT";
    private static final String PERMISSION_AI_CREATE_TASK = "AI_CREATE_TASK";
    private static final String PERMISSION_UPLOAD_ATTACHMENT = "UPLOAD_ATTACHMENT";
    private static final String PERMISSION_DELETE_ATTACHMENT = "DELETE_ATTACHMENT";
    private static final String PERMISSION_CREATE_SCHEDULE = "CREATE_SCHEDULE";
    private static final String PERMISSION_VIEW_STATISTICS = "VIEW_STATISTICS";
    private static final String PROJECT_CONFIG_TYPE = "project";
    private static final String PROJECT_ROLE_PERMISSION_CONFIG_KEY = "project.role.permissions";
    private static final List<String> PROJECT_ROLES = List.of("OWNER", "ADMIN", "MEMBER", "READONLY", "EXTERNAL");
    private static final int MAX_AI_CONTEXT_TEXT_LENGTH = 3000;
    private static final String PROJECT_AI_ERROR_MESSAGE = "抱歉，分析资料时发生错误，请稍后重试。";

    private static final List<String> ALL_PERMISSIONS = List.of(
            PERMISSION_VIEW_PROJECT,
            PERMISSION_EDIT_PROJECT,
            PERMISSION_DELETE_PROJECT,
            PERMISSION_ARCHIVE_PROJECT,
            PERMISSION_ADD_MEMBER,
            PERMISSION_REMOVE_MEMBER,
            PERMISSION_MODIFY_MEMBER_PERMISSION,
            PERMISSION_CREATE_TASK,
            PERMISSION_EDIT_TASK,
            PERMISSION_DELETE_TASK,
            PERMISSION_MOVE_TASK,
            PERMISSION_ADD_LANE,
            PERMISSION_EDIT_LANE,
            PERMISSION_DELETE_LANE,
            PERMISSION_USE_AI_CHAT,
            PERMISSION_AI_CREATE_TASK,
            PERMISSION_UPLOAD_ATTACHMENT,
            PERMISSION_DELETE_ATTACHMENT,
            PERMISSION_CREATE_SCHEDULE,
            PERMISSION_VIEW_STATISTICS
    );

    private static final Map<String, List<String>> DEFAULT_ROLE_PERMISSIONS = buildRolePermissions();

    private final JdbcTemplate jdbcTemplate;
    private final PermissionService permissionService;
    private final ObjectProvider<ISystemConfigService> systemConfigServiceProvider;
    private final ObjectProvider<DynamicChatClientProvider> chatClientProvider;
    private final FileStorageService fileStorageService;
    private final IKnowledgeService knowledgeService;
    private final AiQuotaService aiQuotaService;
    private final AiModelPricingService aiModelPricingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<ProjectVO> listProjects() {
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        boolean admin = isSystemAdmin();
        List<Long> projectIds = jdbcTemplate.query("""
                SELECT p.project_id
                FROM crm_project p
                WHERE (p.tenant_id = ? OR ? IS NULL)
                  AND (
                    ? = TRUE
                    OR p.owner_id = ?
                    OR EXISTS (
                        SELECT 1
                        FROM crm_project_member m
                        WHERE m.project_id = p.project_id
                          AND m.user_id = ?
                          AND m.status = 'ACTIVE'
                    )
                  )
                ORDER BY p.update_time DESC
                """, (rs, rowNum) -> rs.getLong("project_id"), tenantId, tenantId, admin, userId, userId);

        return projectIds.stream()
                .map(this::buildProjectVO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public BasePage<ProjectVO> queryPageList(ProjectBO.Query queryBO) {
        ProjectBO.Query query = queryBO == null ? new ProjectBO.Query() : queryBO;
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        boolean admin = isSystemAdmin();
        String status = normalizeProjectQueryStatus(query.getStatus());
        String keyword = StrUtil.trimToEmpty(query.getKeyword());

        StringBuilder baseSql = new StringBuilder("""
                FROM crm_project p
                WHERE (p.tenant_id = ? OR ? IS NULL)
                  AND (
                    ? = TRUE
                    OR p.owner_id = ?
                    OR EXISTS (
                        SELECT 1
                        FROM crm_project_member m
                        WHERE m.project_id = p.project_id
                          AND m.user_id = ?
                          AND m.status = 'ACTIVE'
                    )
                  )
                """);
        List<Object> baseParams = new ArrayList<>();
        baseParams.add(tenantId);
        baseParams.add(tenantId);
        baseParams.add(admin);
        baseParams.add(userId);
        baseParams.add(userId);

        if (StrUtil.isNotBlank(keyword)) {
            String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
            baseSql.append("""
                    AND (
                      LOWER(p.name) LIKE ?
                      OR LOWER(COALESCE(p.description, '')) LIKE ?
                      OR LOWER(COALESCE(p.customer_name, '')) LIKE ?
                    )
                    """);
            baseParams.add(like);
            baseParams.add(like);
            baseParams.add(like);
        }

        Map<String, Long> statusCounts = jdbcTemplate.query(
                "SELECT p.status, COUNT(*) AS count " + baseSql + " GROUP BY p.status",
                rs -> {
                    Map<String, Long> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("status"), rs.getLong("count"));
                    }
                    return result;
                },
                baseParams.toArray());
        long allCount = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long inProgressCount = statusCounts.getOrDefault("IN_PROGRESS", 0L);
        long completedCount = statusCounts.getOrDefault("COMPLETED", 0L);

        StringBuilder filteredSql = new StringBuilder(baseSql);
        List<Object> filteredParams = new ArrayList<>(baseParams);
        if (status != null) {
            filteredSql.append(" AND p.status = ? ");
            filteredParams.add(status);
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + filteredSql, Long.class, filteredParams.toArray());
        BasePage<ProjectVO> page = new BasePage<>(query.getPage(), query.getLimit());
        page.setTotal(total == null ? 0L : total);

        if (page.getTotal() > 0) {
            long offset = Math.max(0, ((long) query.getPage() - 1) * query.getLimit());
            List<Object> pageParams = new ArrayList<>(filteredParams);
            pageParams.add(query.getLimit());
            pageParams.add(offset);
            List<Long> projectIds = jdbcTemplate.query(
                    "SELECT p.project_id " + filteredSql + " ORDER BY p.update_time DESC LIMIT ? OFFSET ?",
                    (rs, rowNum) -> rs.getLong("project_id"),
                    pageParams.toArray());
            page.setList(projectIds.stream()
                    .map(this::buildProjectVO)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("all", allCount);
        stats.put("inProgress", inProgressCount);
        stats.put("completed", completedCount);
        page.setExtraData(stats);
        return page;
    }

    @Override
    public ProjectVO getProject(Long projectId) {
        return getProject(projectId, null);
    }

    @Override
    public ProjectVO getProject(Long projectId, String taskKeyword) {
        ensureProjectPermission(projectId, PERMISSION_VIEW_PROJECT);
        return buildProjectVO(projectId, taskKeyword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO createProject(ProjectBO.Create createBO) {
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        UserSnapshot currentUser = currentUserSnapshot();
        Long ownerId = createBO.getOwnerId() == null ? userId : createBO.getOwnerId();
        UserSnapshot owner = resolveUserSnapshot(ownerId, createBO.getOwnerName(), createBO.getOwnerAccount(), createBO.getOwnerDeptName());
        Long projectId = IdWorker.getId();
        Date now = new Date();
        String customerName = resolveCustomerName(createBO.getCustomerId(), createBO.getCustomerName());

        jdbcTemplate.update("""
                INSERT INTO crm_project(
                    project_id, tenant_id, name, description, status, owner_id, customer_id, customer_name,
                    start_date, due_date, create_user_id, update_user_id, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                projectId,
                tenantId,
                createBO.getName().trim(),
                StrUtil.blankToDefault(createBO.getDescription(), ""),
                normalizeProjectStatus(createBO.getStatus()),
                ownerId,
                createBO.getCustomerId(),
                customerName,
                toTimestamp(createBO.getStartDate()),
                toTimestamp(createBO.getDueDate()),
                userId,
                userId,
                toTimestamp(now),
                toTimestamp(now));

        insertLane(projectId, tenantId, "not-started", "未开始", 0, true);
        insertLane(projectId, tenantId, "in-progress", "进行中", 1, true);
        insertLane(projectId, tenantId, "completed", "已完成", 2, true);

        upsertMember(projectId, tenantId, ownerId, owner.displayName(), owner.account(), owner.deptName(),
                "OWNER", ALL_PERMISSIONS, "项目负责人", "ACTIVE", false);

        if (!Objects.equals(ownerId, userId)) {
            upsertMember(projectId, tenantId, userId, currentUser.displayName(), currentUser.account(), currentUser.deptName(),
                    "ADMIN", roleDefaultPermissions("ADMIN"), "项目创建人", "ACTIVE", false);
        }

        updateProjectCustomerNameHint(projectId, customerName);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateProject(ProjectBO.Update updateBO) {
        ensureProjectPermission(updateBO.getProjectId(), PERMISSION_EDIT_PROJECT);
        ProjectRow existing = findProject(updateBO.getProjectId());
        Long tenantId = currentTenantId();
        Long userId = currentUserId();
        Long ownerId = updateBO.getOwnerId() == null ? existing.ownerId() : updateBO.getOwnerId();

        jdbcTemplate.update("""
                UPDATE crm_project
                SET name = ?, description = ?, status = ?, owner_id = ?, customer_id = ?, customer_name = ?,
                    start_date = ?, due_date = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND (tenant_id = ? OR ? IS NULL)
                """,
                StrUtil.blankToDefault(updateBO.getName(), existing.name()).trim(),
                updateBO.getDescription() == null ? existing.description() : updateBO.getDescription(),
                normalizeProjectStatus(StrUtil.blankToDefault(updateBO.getStatus(), existing.status())),
                ownerId,
                updateBO.getCustomerId() == null ? existing.customerId() : updateBO.getCustomerId(),
                resolveCustomerName(updateBO.getCustomerId() == null ? existing.customerId() : updateBO.getCustomerId(),
                        updateBO.getCustomerName() == null ? existing.customerName() : updateBO.getCustomerName()),
                toTimestamp(updateBO.getStartDate() == null ? existing.startDate() : updateBO.getStartDate()),
                toTimestamp(updateBO.getDueDate() == null ? existing.dueDate() : updateBO.getDueDate()),
                userId,
                updateBO.getProjectId(),
                tenantId,
                tenantId);

        if (!Objects.equals(existing.ownerId(), ownerId)) {
            jdbcTemplate.update("""
                    UPDATE crm_project_member
                    SET role = 'ADMIN', permissions = ?, last_action_time = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                    WHERE project_id = ? AND role = 'OWNER' AND user_id <> ?
                    """, writeJson(roleDefaultPermissions("ADMIN")), updateBO.getProjectId(), ownerId);
            UserSnapshot owner = resolveUserSnapshot(ownerId, updateBO.getOwnerName(), updateBO.getOwnerAccount(), updateBO.getOwnerDeptName());
            upsertMember(updateBO.getProjectId(), tenantId, ownerId, owner.displayName(), owner.account(), owner.deptName(),
                    "OWNER", ALL_PERMISSIONS, "项目负责人", "ACTIVE", true);
        }

        return buildProjectVO(updateBO.getProjectId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO archiveProject(Long projectId) {
        ensureProjectPermission(projectId, PERMISSION_ARCHIVE_PROJECT);
        updateProjectStatus(projectId, "ARCHIVED");
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long projectId) {
        ensureProjectPermission(projectId, PERMISSION_DELETE_PROJECT);
        Long tenantId = currentTenantId();
        jdbcTemplate.update("DELETE FROM crm_project_task_chat_message WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_task_note WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_task_attachment WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_task_schedule WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_attachment WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_schedule WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_chat_message WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_member_log WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_member WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_project_lane WHERE project_id = ?", projectId);
        jdbcTemplate.update("DELETE FROM crm_task WHERE project_id = ? AND (tenant_id = ? OR ? IS NULL)", projectId, tenantId, tenantId);
        jdbcTemplate.update("DELETE FROM crm_project WHERE project_id = ? AND (tenant_id = ? OR ? IS NULL)", projectId, tenantId, tenantId);
    }

    @Override
    public ProjectVO.ProjectRolePermissionConfigVO getProjectRolePermissionConfig() {
        return buildRolePermissionConfigVO(loadProjectRolePermissions());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO.ProjectRolePermissionConfigVO updateProjectRolePermissionConfig(ProjectBO.RolePermissionConfig configBO) {
        if (!isSystemAdmin()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前账号没有配置项目角色权限的权限");
        }
        Map<String, List<String>> rolePermissions = normalizeRolePermissionConfig(
                configBO == null ? null : configBO.getRolePermissions());
        systemConfigServiceProvider.getObject().updateConfigsWithType(
                Map.of(PROJECT_ROLE_PERMISSION_CONFIG_KEY, writeJson(rolePermissions)),
                PROJECT_CONFIG_TYPE);
        return buildRolePermissionConfigVO(rolePermissions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addLane(Long projectId, ProjectBO.LaneSave laneBO) {
        ensureProjectPermission(projectId, PERMISSION_ADD_LANE);
        Long tenantId = currentTenantId();
        Integer nextOrder = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM crm_project_lane WHERE project_id = ?",
                Integer.class,
                projectId);
        insertLane(projectId, tenantId, "custom-" + IdWorker.getId(), laneBO.getName().trim(), nextOrder == null ? 0 : nextOrder, false);
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateLane(Long projectId, ProjectBO.LaneSave laneBO) {
        ensureProjectPermission(projectId, PERMISSION_EDIT_LANE);
        if (laneBO.getLaneId() == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "泳道ID不能为空");
        }
        jdbcTemplate.update("""
                UPDATE crm_project_lane
                SET name = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND lane_id = ?
                """, laneBO.getName().trim(), currentUserId(), projectId, laneBO.getLaneId());
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO deleteLane(Long projectId, Long laneId) {
        ensureProjectPermission(projectId, PERMISSION_DELETE_LANE);
        LaneRow lane = findLane(projectId, laneId);
        if (lane.system()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "系统默认泳道不能删除");
        }
        LaneRow defaultLane = defaultLane(projectId);
        jdbcTemplate.update("""
                UPDATE crm_task
                SET lane_id = ?, status = 'pending', update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND lane_id = ?
                """, defaultLane.laneId(), currentUserId(), projectId, laneId);
        jdbcTemplate.update("DELETE FROM crm_project_lane WHERE project_id = ? AND lane_id = ?", projectId, laneId);
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addTask(Long projectId, ProjectBO.TaskSave taskBO) {
        ensureProjectPermission(projectId, PERMISSION_CREATE_TASK);
        insertTask(projectId, taskBO);
        return buildProjectVO(projectId);
    }

    private Long insertTask(Long projectId, ProjectBO.TaskSave taskBO) {
        ProjectRow project = findProject(projectId);
        Long taskId = IdWorker.getId();
        Long laneId = taskBO.getLaneId() == null ? defaultLane(projectId).laneId() : taskBO.getLaneId();
        LaneRow lane = findLane(projectId, laneId);
        Long ownerId = taskBO.getOwnerId() == null ? currentUserId() : taskBO.getOwnerId();
        String participantNames = joinNames(taskBO.getParticipantNames());
        String customerName = resolveCustomerName(firstNonNull(taskBO.getCustomerId(), project.customerId()), taskBO.getCustomerName());

        jdbcTemplate.update("""
                INSERT INTO crm_task(
                    task_id, tenant_id, title, description, due_date, priority, status, assigned_to, customer_id,
                    generated_by_ai, ai_context, project_id, lane_id, participant_user_ids, participant_names,
                    source, ai_source_text, has_attachments, has_schedule, create_user_id, update_user_id,
                    create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                taskId,
                currentTenantId(),
                taskBO.getTitle().trim(),
                StrUtil.blankToDefault(taskBO.getDescription(), ""),
                toTimestamp(taskBO.getDueDate()),
                normalizeTaskPriority(taskBO.getPriority()),
                laneToTaskStatus(lane),
                ownerId,
                firstNonNull(taskBO.getCustomerId(), project.customerId()),
                Boolean.TRUE.equals(taskBO.getGeneratedByAi()) ? 1 : 0,
                taskBO.getAiSourceText(),
                projectId,
                laneId,
                writeJson(defaultList(taskBO.getParticipantIds())),
                participantNames,
                Boolean.TRUE.equals(taskBO.getGeneratedByAi()) ? "ai" : "manual",
                taskBO.getAiSourceText(),
                Boolean.TRUE.equals(taskBO.getHasAttachments()),
                Boolean.TRUE.equals(taskBO.getHasSchedule()),
                currentUserId(),
                currentUserId());

        List<ProjectBO.TaskAttachmentSave> attachments = normalizedTaskAttachments(taskBO.getAttachments());
        if (!attachments.isEmpty()) {
            appendTaskAttachments(projectId, taskId, attachments);
        } else if (Boolean.TRUE.equals(taskBO.getHasAttachments())) {
            appendTaskAttachment(projectId, taskId, "手动标记附件");
        }
        if (Boolean.TRUE.equals(taskBO.getHasSchedule())) {
            appendTaskSchedule(projectId, taskId, taskBO.getTitle().trim() + "相关日程", taskBO.getDueDate());
        }
        touchProject(projectId);
        updateProjectCustomerNameHint(projectId, customerName);
        return taskId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateTask(Long projectId, ProjectBO.TaskSave taskBO) {
        if (taskBO.getTaskId() == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "任务ID不能为空");
        }
        TaskRow task = findTask(projectId, taskBO.getTaskId());
        ensureCanEditTask(projectId, task);
        Long laneId = taskBO.getLaneId() == null ? task.laneId() : taskBO.getLaneId();
        LaneRow lane = findLane(projectId, laneId);

        jdbcTemplate.update("""
                UPDATE crm_task
                SET title = ?, description = ?, due_date = ?, priority = ?, status = ?, assigned_to = ?,
                    customer_id = ?, lane_id = ?, participant_user_ids = ?, participant_names = ?,
                    has_attachments = ?, has_schedule = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND task_id = ?
                """,
                taskBO.getTitle().trim(),
                StrUtil.blankToDefault(taskBO.getDescription(), ""),
                toTimestamp(taskBO.getDueDate()),
                normalizeTaskPriority(taskBO.getPriority()),
                laneToTaskStatus(lane),
                taskBO.getOwnerId(),
                taskBO.getCustomerId(),
                laneId,
                writeJson(defaultList(taskBO.getParticipantIds())),
                joinNames(taskBO.getParticipantNames()),
                Boolean.TRUE.equals(taskBO.getHasAttachments()),
                Boolean.TRUE.equals(taskBO.getHasSchedule()),
                currentUserId(),
                projectId,
                taskBO.getTaskId());
        appendTaskAttachments(projectId, taskBO.getTaskId(), normalizedTaskAttachments(taskBO.getAttachments()));
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addTaskAttachment(Long projectId, Long taskId, ProjectBO.TaskAttachmentSave attachmentBO) {
        TaskRow task = findTask(projectId, taskId);
        if (!hasProjectPermission(projectId, PERMISSION_UPLOAD_ATTACHMENT) || !canViewTask(projectId, task)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前账号没有上传该任务附件的权限");
        }
        appendTaskAttachments(projectId, taskId, normalizedTaskAttachments(
                attachmentBO == null ? Collections.emptyList() : List.of(attachmentBO)
        ));
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO deleteTask(Long projectId, Long taskId) {
        TaskRow task = findTask(projectId, taskId);
        ensureProjectPermission(projectId, PERMISSION_DELETE_TASK);
        ensureCanEditTask(projectId, task);
        jdbcTemplate.update("DELETE FROM crm_project_task_chat_message WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM crm_project_task_note WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM crm_project_task_attachment WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM crm_project_task_schedule WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM crm_task WHERE project_id = ? AND task_id = ?", projectId, taskId);
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO moveTask(Long projectId, ProjectBO.TaskMove moveBO) {
        TaskRow task = findTask(projectId, moveBO.getTaskId());
        ensureCanMoveTask(projectId, task);
        LaneRow lane = findLane(projectId, moveBO.getLaneId());
        jdbcTemplate.update("""
                UPDATE crm_task
                SET lane_id = ?, status = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND task_id = ?
                """, moveBO.getLaneId(), laneToTaskStatus(lane), currentUserId(), projectId, moveBO.getTaskId());
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addMember(Long projectId, ProjectBO.MemberSave memberBO) {
        ensureProjectPermission(projectId, PERMISSION_ADD_MEMBER);
        UserSnapshot user = resolveUserSnapshot(memberBO.getUserId(), memberBO.getMemberName(), memberBO.getAccount(), memberBO.getDeptName());
        List<String> permissions = memberBO.getPermissions() == null || memberBO.getPermissions().isEmpty()
                ? roleDefaultPermissions(memberBO.getRole())
                : normalizePermissions(memberBO.getPermissions());
        upsertMember(projectId, currentTenantId(), memberBO.getUserId(), user.displayName(), user.account(), user.deptName(),
                normalizeRole(memberBO.getRole()), permissions, memberBO.getRemark(), normalizeMemberStatus(memberBO.getStatus()), true);
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateMemberRole(Long projectId, ProjectBO.MemberRole roleBO) {
        ensureProjectPermission(projectId, PERMISSION_MODIFY_MEMBER_PERMISSION);
        MemberRow member = findMember(projectId, roleBO.getUserId());
        String before = memberSummary(member);
        String role = normalizeRole(roleBO.getRole());
        List<String> permissions = roleDefaultPermissions(role);
        jdbcTemplate.update("""
                UPDATE crm_project_member
                SET role = ?, permissions = ?, last_action_time = CURRENT_TIMESTAMP, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND user_id = ?
                """, role, writeJson(permissions), currentUserId(), projectId, roleBO.getUserId());
        MemberRow after = findMember(projectId, roleBO.getUserId());
        appendMemberLog(projectId, "UPDATE_ROLE", roleBO.getUserId(), after.memberName(), before, memberSummary(after));
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateMemberPermissions(Long projectId, ProjectBO.MemberPermissions permissionsBO) {
        ensureProjectPermission(projectId, PERMISSION_MODIFY_MEMBER_PERMISSION);
        MemberRow member = findMember(projectId, permissionsBO.getUserId());
        String before = memberSummary(member);
        List<String> permissions = normalizePermissions(permissionsBO.getPermissions());
        jdbcTemplate.update("""
                UPDATE crm_project_member
                SET permissions = ?, last_action_time = CURRENT_TIMESTAMP, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND user_id = ?
                """, writeJson(permissions), currentUserId(), projectId, permissionsBO.getUserId());
        MemberRow after = findMember(projectId, permissionsBO.getUserId());
        appendMemberLog(projectId, "UPDATE_PERMISSION", permissionsBO.getUserId(), after.memberName(), before, memberSummary(after));
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateMemberStatus(Long projectId, ProjectBO.MemberStatus statusBO) {
        String permission = "REMOVED".equalsIgnoreCase(statusBO.getStatus()) ? PERMISSION_REMOVE_MEMBER : PERMISSION_MODIFY_MEMBER_PERMISSION;
        ensureProjectPermission(projectId, permission);
        MemberRow member = findMember(projectId, statusBO.getUserId());
        String before = memberSummary(member);
        jdbcTemplate.update("""
                UPDATE crm_project_member
                SET status = ?, last_action_time = CURRENT_TIMESTAMP, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND user_id = ?
                """, normalizeMemberStatus(statusBO.getStatus()), currentUserId(), projectId, statusBO.getUserId());
        MemberRow after = findMember(projectId, statusBO.getUserId());
        appendMemberLog(projectId, "REMOVED".equals(after.status()) ? "REMOVE_MEMBER" : "UPDATE_STATUS",
                statusBO.getUserId(), after.memberName(), before, memberSummary(after));
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO handleProjectAiCommand(Long projectId, ProjectBO.AiCommand commandBO) {
        ensureProjectPermission(projectId, PERMISSION_USE_AI_CHAT);
        String content = commandBO.getContent().trim();
        appendProjectChat(projectId, "user", buildAiCommandChatContent(content, commandBO));
        if (hasAiReferenceMaterials(commandBO)) {
            String reply = buildProjectMaterialAnalysisReply(projectId, null, content, commandBO);
            appendProjectChat(projectId, "assistant", reply);
            touchProject(projectId);
            return buildProjectVO(projectId);
        }
        String reply = "我已经记录到当前项目上下文。你也可以继续让我创建任务、总结进展或查询项目任务。";
        ProjectAiCommandParser.ParsedCommand command = ProjectAiCommandParser.parse(content);

        switch (command.action()) {
            case CREATE_TASK -> {
                if (!hasProjectPermission(projectId, PERMISSION_CREATE_TASK) || !hasProjectPermission(projectId, PERMISSION_AI_CREATE_TASK)) {
                    reply = "当前账号没有通过 AI 创建任务的权限。";
                } else {
                    ProjectBO.TaskSave task = parseProjectAiTask(projectId, content, command.title());
                    Long taskId = insertTask(projectId, task);
                    String scheduleReply = "";
                    if (command.createTaskSchedule()) {
                        if (hasProjectPermission(projectId, PERMISSION_CREATE_SCHEDULE)) {
                            appendTaskSchedule(projectId, taskId, task.getTitle() + "相关日程", task.getDueDate());
                            scheduleReply = " 同时已为该任务创建相关日程。";
                        } else {
                            scheduleReply = " 但当前账号没有创建日程的权限，未同步创建日程。";
                        }
                    }
                    reply = "已为当前项目创建任务「" + task.getTitle() + "」，并默认放入「未开始」泳道。"
                            + (task.getDueDate() == null ? "" : " 截止时间：" + task.getDueDate())
                            + scheduleReply;
                }
            }
            case CREATE_LANE -> {
                if (!hasProjectPermission(projectId, PERMISSION_ADD_LANE)) {
                    reply = "当前账号没有新增泳道的权限。";
                } else {
                    String laneName = command.title();
                    LaneRow existing = findLaneByExactName(projectId, laneName);
                    if (existing != null) {
                        reply = "项目中已经存在「" + laneName + "」泳道。";
                    } else {
                        ProjectBO.LaneSave laneBO = new ProjectBO.LaneSave();
                        laneBO.setName(laneName);
                        addLane(projectId, laneBO);
                        reply = "已为当前项目创建泳道「" + laneName + "」。";
                    }
                }
            }
            case CREATE_PROJECT_SCHEDULE -> {
                if (!hasProjectPermission(projectId, PERMISSION_CREATE_SCHEDULE)) {
                    reply = "当前账号没有创建日程的权限。";
                } else {
                    String title = command.title();
                    appendProjectSchedule(projectId, title, parseRelativeDateTime(content));
                    reply = "已为当前项目创建日程「" + title + "」。";
                }
            }
            case CREATE_PROJECT_ATTACHMENT -> {
                if (!hasProjectPermission(projectId, PERMISSION_UPLOAD_ATTACHMENT)) {
                    reply = "当前账号没有上传或挂载附件的权限。";
                } else {
                    String name = command.title();
                    appendProjectAttachment(projectId, name);
                    reply = "已将「" + name + "」记录为项目附件。";
                }
            }
            case UPDATE_PROJECT_STATUS -> {
                if (!hasProjectPermission(projectId, PERMISSION_EDIT_PROJECT)) {
                    reply = "当前账号没有修改项目信息的权限。";
                } else {
                    updateProjectStatus(projectId, command.targetStatus());
                    reply = "已将项目状态更新为「" + projectStatusName(command.targetStatus()) + "」。";
                }
            }
            case ARCHIVE_PROJECT -> {
                if (!hasProjectPermission(projectId, PERMISSION_ARCHIVE_PROJECT)) {
                    reply = "当前账号没有归档项目的权限。";
                } else {
                    updateProjectStatus(projectId, "ARCHIVED");
                    reply = "已将项目状态更新为「已归档」。";
                }
            }
            case SUMMARIZE_PROJECT -> reply = summarizeProject(projectId);
            case QUERY_TASKS -> reply = summarizeProjectTasks(projectId);
            case UNSAFE_DELETE -> reply = "删除类操作风险较高，请通过项目、任务或泳道的手动删除入口确认执行。";
            case UNKNOWN -> {
                // Keep the default context reply.
            }
        }

        appendProjectChat(projectId, "assistant", reply);
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO handleTaskAiCommand(Long projectId, Long taskId, ProjectBO.AiCommand commandBO) {
        TaskRow task = findTask(projectId, taskId);
        ensureCanUseTaskAi(projectId, task);
        String content = commandBO.getContent().trim();
        appendTaskChat(projectId, taskId, "user", buildAiCommandChatContent(content, commandBO));
        if (hasAiReferenceMaterials(commandBO)) {
            String reply = buildProjectMaterialAnalysisReply(projectId, task, content, commandBO);
            appendTaskChat(projectId, taskId, "assistant", reply);
            touchProject(projectId);
            return buildProjectVO(projectId);
        }
        String reply = "当前对话对象是任务「" + task.title() + "」，我已经收到你的指令。";

        if (containsAny(content, "改到", "改成", "调整到", "延期到")
                && containsAny(content, "今天", "明天", "后天", "上午", "下午", "晚上", "点")) {
            ensureCanEditTask(projectId, task);
            Date dueDate = parseRelativeDateTime(content);
            if (dueDate != null) {
                jdbcTemplate.update("""
                        UPDATE crm_task
                        SET due_date = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                        WHERE project_id = ? AND task_id = ?
                        """, toTimestamp(dueDate), currentUserId(), projectId, taskId);
                reply = "已将任务「" + task.title() + "」的截止时间更新为 " + dueDate + "。";
            }
        } else if (containsAny(content, "优先级")) {
            ensureCanEditTask(projectId, task);
            String priority = parsePriority(content);
            jdbcTemplate.update("""
                    UPDATE crm_task
                    SET priority = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                    WHERE project_id = ? AND task_id = ?
                    """, priority, currentUserId(), projectId, taskId);
            reply = "已将任务优先级更新为「" + priority + "」。";
        } else if (containsAny(content, "状态", "移动到", "放到")) {
            ensureCanMoveTask(projectId, task);
            LaneRow lane = findLaneByKeyword(projectId, content);
            if (lane != null) {
                jdbcTemplate.update("""
                        UPDATE crm_task
                        SET lane_id = ?, status = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                        WHERE project_id = ? AND task_id = ?
                        """, lane.laneId(), laneToTaskStatus(lane), currentUserId(), projectId, taskId);
                reply = "已将任务移动到「" + lane.name() + "」泳道。";
            }
        } else if (containsAny(content, "附件", "设计稿", "文档", "追加到")) {
            ensureTaskSidePermission(projectId, task, PERMISSION_UPLOAD_ATTACHMENT, "当前账号没有上传附件的权限。");
            String name = extractAttachmentName(content);
            appendTaskAttachment(projectId, taskId, name);
            reply = "已将「" + name + "」记录为任务附件。";
        } else if (containsAny(content, "日程", "安排", "提醒")) {
            ensureTaskSidePermission(projectId, task, PERMISSION_CREATE_SCHEDULE, "当前账号没有创建日程的权限。");
            Date scheduleTime = parseRelativeDateTime(content);
            String title = extractScheduleTitle(content, task.title());
            appendTaskSchedule(projectId, taskId, title, scheduleTime);
            reply = "已为任务创建相关日程「" + title + "」。";
        } else if (containsAny(content, "备注", "补充说明", "追加说明")) {
            ensureTaskSidePermission(projectId, task, PERMISSION_VIEW_PROJECT, "当前账号没有追加备注的权限。");
            String note = extractNote(content);
            appendTaskNote(projectId, taskId, note);
            reply = "已为任务追加备注。";
        } else if (containsAny(content, "大纲", "执行方案", "方案")) {
            reply = buildTaskExecutionPlan(task);
            appendTaskNote(projectId, taskId, reply);
        }

        appendTaskChat(projectId, taskId, "assistant", reply);
        touchProject(projectId);
        return buildProjectVO(projectId);
    }

    private boolean hasAiReferenceMaterials(ProjectBO.AiCommand commandBO) {
        return commandBO != null
                && (hasItems(commandBO.getAttachments()) || hasItems(commandBO.getKnowledgeIds()));
    }

    private boolean hasItems(List<?> items) {
        return items != null && !items.isEmpty();
    }

    private String buildProjectMaterialAnalysisReply(Long projectId, TaskRow task, String content, ProjectBO.AiCommand commandBO) {
        ProjectRow project = findProject(projectId);
        try {
            ProjectAiBillingContext billingContext = resolveProjectAiBillingContext(commandBO);
            DynamicChatClientProvider provider = billingContext.provider();
            DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig = billingContext.runtimeConfig();

            if (StrUtil.isBlank(runtimeConfig.apiKey())) {
                return "请先在系统设置-系统参数设置-AI/API设置中配置 AI 大模型相关信息。";
            }

            List<KnowledgeVO> selectedKnowledge = loadSelectedKnowledge(commandBO.getKnowledgeIds());
            AiModelCapabilities capabilities = runtimeConfig.capabilities();
            String attachmentContext = buildAiAttachmentContext(commandBO.getAttachments());
            String knowledgeContext = buildAiKnowledgeContext(selectedKnowledge);
            String systemPrompt = buildProjectMaterialSystemPrompt(project, task);
            String enhancedContent = buildProjectMaterialUserContent(content, project, task, attachmentContext, knowledgeContext);

            if (containsImageReference(commandBO.getAttachments(), selectedKnowledge)
                    && (capabilities == null || !capabilities.isSupportsVision())) {
                enhancedContent = enhancedContent + "\n\n[系统提示] 当前选择的模型不支持图片理解，请仅基于可提取文本、文件名和已有上下文回答；如需图片内容分析，请切换到支持视觉的模型。";
            }

            List<Media> mediaList = buildAiMediaList(commandBO.getAttachments(), selectedKnowledge, capabilities);
            List<Message> history = Collections.emptyList();
            String quotaTip = aiQuotaService.resolveQuotaFailureMessage(
                    currentTenantId(),
                    "project_chat",
                    systemPrompt,
                    history,
                    enhancedContent,
                    billingContext.pricing().creditMultiplier(),
                    billingContext.modelSource()
            );
            if (quotaTip != null) {
                return quotaTip;
            }

            ChatClient chatClient = provider.getChatClient(
                    billingContext.modelSource(),
                    runtimeConfig.providerCode(),
                    runtimeConfig.model(),
                    ChatApplicationCodes.PROJECT
            );

            Long aiSessionId = IdWorker.getId();
            AiContextHolder.setContext(aiSessionId, currentUserId(), currentTenantId());
            ChatResponse chatResponse;
            try {
                ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt().system(systemPrompt);
                final String finalEnhancedContent = enhancedContent;
                chatResponse = hasItems(mediaList)
                        ? requestSpec.user(user -> user.text(finalEnhancedContent).media(mediaList.toArray(new Media[0]))).call().chatResponse()
                        : requestSpec.user(finalEnhancedContent).call().chatResponse();
            } finally {
                AiContextHolder.clear();
                AiContextHolder.clearSession(aiSessionId);
            }
            String response = extractChatResponseText(chatResponse);
            if (StrUtil.isBlank(response)) {
                return "我没有从当前资料中得到可用结论，请换一种问法或重新选择文件后再试。";
            }

            AiQuotaService.TokenUsageSnapshot usage = aiQuotaService.resolveTokenUsage(
                    chatResponse,
                    systemPrompt,
                    history,
                    enhancedContent,
                    response
            );
            aiQuotaService.consumeResolvedTokens(
                    currentTenantId(),
                    "project_chat",
                    usage,
                    billingContext.pricing().creditMultiplier(),
                    billingContext.modelSource(),
                    runtimeConfig.providerCode(),
                    runtimeConfig.model(),
                    task == null ? "project_chat_message" : "project_task_chat_message",
                    task == null ? projectId : task.taskId()
            );
            return response.trim();
        } catch (BusinessException e) {
            return e.getMsg();
        } catch (Exception e) {
            logProjectAiError(e);
            return resolveProjectAiErrorMessage(e);
        }
    }

    private ProjectAiBillingContext resolveProjectAiBillingContext(ProjectBO.AiCommand commandBO) {
        String requestedSource = AiModelSource.normalize(commandBO.getModelSource());
        boolean explicitModelSelection = StrUtil.isNotBlank(requestedSource)
                || StrUtil.isNotBlank(commandBO.getModelProvider())
                || StrUtil.isNotBlank(commandBO.getModelName());
        DynamicChatClientProvider provider = chatClientProvider.getObject();
        DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig = provider.getRuntimeConfigSnapshot(
                requestedSource,
                commandBO.getModelProvider(),
                commandBO.getModelName()
        );
        String resolvedSource = StrUtil.isNotBlank(requestedSource)
                ? requestedSource
                : (runtimeConfig.mode() == AiMode.CUSTOM ? AiModelSource.CUSTOM : AiModelSource.SYSTEM);
        AiModelPricingService.PricingSnapshot pricing = aiModelPricingService.resolvePricing(
                runtimeConfig.providerCode(),
                runtimeConfig.model(),
                explicitModelSelection && !AiModelSource.isCustom(resolvedSource)
        );
        return new ProjectAiBillingContext(provider, runtimeConfig, pricing, resolvedSource);
    }

    private String buildProjectMaterialSystemPrompt(ProjectRow project, TaskRow task) {
        String taskContext = task == null
                ? ""
                : "\n当前任务：%s；状态：%s；优先级：%s；截止时间：%s。".formatted(
                        task.title(),
                        task.status(),
                        task.priority(),
                        task.dueDate()
                );
        return """
                你是悟空 CRM 的项目资料分析助手。请基于当前项目/任务上下文、用户上传的图片或附件，以及用户选中的知识库文件回答。
                要求：
                1. 优先分析用户本次选择的图片、附件和知识库内容。
                2. 如果资料中没有答案，请明确说明缺少哪些信息，不要编造。
                3. 如果用户要求总结、提炼风险、生成执行建议，请给出可操作的条目。
                4. 如果用户明确要求根据资料创建、修改、移动或删除项目任务，请优先调用项目工具完成操作；创建当前项目任务时必须使用当前项目ID。
                5. 只有在项目工具结果确认成功后，才可以说数据已创建、更新、移动或删除成功。
                6. 使用简体中文，回答要清晰、具体。

                当前项目ID：%s；当前项目：%s；状态：%s；关联客户：%s；负责人ID：%s。%s
                """.formatted(
                project.projectId(),
                project.name(),
                project.status(),
                StrUtil.blankToDefault(project.customerName(), "未关联"),
                project.ownerId(),
                taskContext
        );
    }

    private String buildProjectMaterialUserContent(String content,
                                                   ProjectRow project,
                                                   TaskRow task,
                                                   String attachmentContext,
                                                   String knowledgeContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题：").append(content).append("\n\n");
        sb.append("[项目上下文]\n");
        sb.append("- 项目名称：").append(project.name()).append("\n");
        sb.append("- 项目描述：").append(StrUtil.blankToDefault(project.description(), "无")).append("\n");
        sb.append("- 项目状态：").append(project.status()).append("\n");
        sb.append("- 关联客户：").append(StrUtil.blankToDefault(project.customerName(), "未关联")).append("\n");
        if (task != null) {
            sb.append("\n[任务上下文]\n");
            sb.append("- 任务名称：").append(task.title()).append("\n");
            sb.append("- 任务描述：").append(StrUtil.blankToDefault(task.description(), "无")).append("\n");
            sb.append("- 任务状态：").append(task.status()).append("\n");
            sb.append("- 优先级：").append(task.priority()).append("\n");
            sb.append("- 截止时间：").append(task.dueDate()).append("\n");
        }
        if (StrUtil.isNotBlank(attachmentContext)) {
            sb.append("\n").append(attachmentContext);
        }
        if (StrUtil.isNotBlank(knowledgeContext)) {
            sb.append("\n").append(knowledgeContext);
        }
        return sb.toString();
    }

    private List<KnowledgeVO> loadSelectedKnowledge(List<Long> knowledgeIds) {
        if (!hasItems(knowledgeIds)) {
            return Collections.emptyList();
        }
        List<KnowledgeVO> result = new ArrayList<>();
        for (Long knowledgeId : knowledgeIds) {
            if (knowledgeId == null) {
                continue;
            }
            try {
                KnowledgeVO knowledge = knowledgeService.getKnowledgeDetail(knowledgeId);
                if (knowledge != null) {
                    result.add(knowledge);
                }
            } catch (Exception e) {
                log.warn("读取项目对话知识库文件失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
            }
        }
        return result;
    }

    private String buildAiKnowledgeContext(List<KnowledgeVO> selectedKnowledge) {
        if (!hasItems(selectedKnowledge)) {
            return "";
        }
        StringBuilder context = new StringBuilder("[用户从知识库选中的文件]\n");
        for (KnowledgeVO knowledge : selectedKnowledge) {
            context.append("- 知识库文件：")
                    .append(StrUtil.blankToDefault(knowledge.getName(), "未命名文件"))
                    .append("（knowledgeId=").append(knowledge.getKnowledgeId()).append("）\n");
            if (StrUtil.isNotBlank(knowledge.getSummary())) {
                context.append("  摘要：").append(abbreviateAiContext(knowledge.getSummary(), 800)).append("\n");
            }
            String contentText = StrUtil.blankToDefault(knowledge.getContentText(), "");
            if (StrUtil.isBlank(contentText) && StrUtil.isNotBlank(knowledge.getFilePath())) {
                contentText = extractAttachmentText(knowledge.getFilePath(), knowledge.getMimeType(), knowledge.getName());
            }
            if (StrUtil.isNotBlank(contentText)) {
                context.append("  内容摘录：\n```\n")
                        .append(abbreviateAiContext(contentText, MAX_AI_CONTEXT_TEXT_LENGTH))
                        .append("\n```\n");
            } else if (isImageFile(knowledge.getMimeType(), knowledge.getName())) {
                context.append("  说明：该知识库文件是图片，已在模型支持时作为图片传入。\n");
            } else {
                context.append("  说明：该文件暂无可读取文本，请结合文件名、摘要和用户问题谨慎回答。\n");
            }
        }
        return context.toString();
    }

    private String buildAiAttachmentContext(List<ChatSendBO.AttachmentDTO> attachments) {
        if (!hasItems(attachments)) {
            return "";
        }
        StringBuilder context = new StringBuilder("[用户上传的文件]\n");
        for (ChatSendBO.AttachmentDTO attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String fileName = StrUtil.blankToDefault(attachment.getFileName(), "未命名文件");
            String mimeType = attachment.getMimeType();
            if (isImageFile(mimeType, fileName)) {
                context.append("- 图片：").append(fileName).append("（已在模型支持时作为图片传入，请直接分析图片内容）\n");
                continue;
            }
            String text = extractAttachmentText(attachment.getFilePath(), mimeType, fileName);
            if (StrUtil.isNotBlank(text)) {
                context.append("- 文件：").append(fileName).append("，内容摘录如下：\n```\n")
                        .append(abbreviateAiContext(text, MAX_AI_CONTEXT_TEXT_LENGTH))
                        .append("\n```\n");
            } else {
                context.append("- 文件：").append(fileName)
                        .append("（类型：").append(StrUtil.blankToDefault(mimeType, "未知")).append("，暂无法提取文本）\n");
            }
        }
        return context.toString();
    }

    private List<Media> buildAiMediaList(List<ChatSendBO.AttachmentDTO> attachments,
                                         List<KnowledgeVO> selectedKnowledge,
                                         AiModelCapabilities capabilities) {
        if (capabilities == null || !capabilities.isSupportsVision()) {
            return Collections.emptyList();
        }
        List<Media> mediaList = new ArrayList<>();
        if (hasItems(attachments)) {
            for (ChatSendBO.AttachmentDTO attachment : attachments) {
                if (attachment != null && isImageFile(attachment.getMimeType(), attachment.getFileName())) {
                    addImageMedia(mediaList, attachment.getFileName(), attachment.getFilePath(), attachment.getMimeType());
                }
            }
        }
        if (hasItems(selectedKnowledge)) {
            for (KnowledgeVO knowledge : selectedKnowledge) {
                if (knowledge != null && isImageFile(knowledge.getMimeType(), knowledge.getName())) {
                    addImageMedia(mediaList, knowledge.getName(), knowledge.getFilePath(), knowledge.getMimeType());
                }
            }
        }
        return mediaList;
    }

    private void addImageMedia(List<Media> mediaList, String fileName, String filePath, String mimeType) {
        if (StrUtil.isBlank(filePath)) {
            return;
        }
        try {
            MimeType resolvedMimeType = MimeType.valueOf(StrUtil.blankToDefault(mimeType, "image/jpeg"));
            mediaList.add(AiMediaUtil.buildMedia(fileStorageService, filePath, resolvedMimeType));
        } catch (Exception e) {
            log.warn("构建项目对话图片媒体失败: fileName={}, filePath={}", fileName, filePath, e);
        }
    }

    private boolean containsImageReference(List<ChatSendBO.AttachmentDTO> attachments, List<KnowledgeVO> selectedKnowledge) {
        if (hasItems(attachments) && attachments.stream()
                .filter(Objects::nonNull)
                .anyMatch(attachment -> isImageFile(attachment.getMimeType(), attachment.getFileName()))) {
            return true;
        }
        return hasItems(selectedKnowledge) && selectedKnowledge.stream()
                .filter(Objects::nonNull)
                .anyMatch(knowledge -> isImageFile(knowledge.getMimeType(), knowledge.getName()));
    }

    private String extractAttachmentText(String filePath, String mimeType, String fileName) {
        if (StrUtil.isBlank(filePath)) {
            return "";
        }
        if (isTextFile(mimeType, fileName)) {
            return extractTextFile(filePath);
        }
        if (isDocumentFile(mimeType, fileName)) {
            return extractDocumentFile(filePath);
        }
        return "";
    }

    private String extractTextFile(String filePath) {
        try (InputStream inputStream = fileStorageService.getFileStream(filePath)) {
            if (inputStream == null) {
                return "";
            }
            byte[] bytes = inputStream.readNBytes(MAX_AI_CONTEXT_TEXT_LENGTH * 3);
            return abbreviateAiContext(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), MAX_AI_CONTEXT_TEXT_LENGTH);
        } catch (Exception e) {
            log.warn("读取项目对话文本附件失败: {}", filePath, e);
            return "";
        }
    }

    private String extractDocumentFile(String filePath) {
        try (InputStream inputStream = fileStorageService.getFileStream(filePath)) {
            if (inputStream == null) {
                return "";
            }
            String text = DocumentTextExtractor.parseToString(inputStream, null, filePath);
            return abbreviateAiContext(text, MAX_AI_CONTEXT_TEXT_LENGTH);
        } catch (Exception e) {
            log.warn("提取项目对话文档附件失败: {}", filePath, e);
            return "";
        }
    }

    private boolean isImageFile(String mimeType, String fileName) {
        if (mimeType != null && mimeType.startsWith("image/")) {
            return true;
        }
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private boolean isTextFile(String mimeType, String fileName) {
        if (mimeType != null && (mimeType.startsWith("text/") || "application/json".equals(mimeType))) {
            return true;
        }
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".yaml")
                || lower.endsWith(".yml") || lower.endsWith(".log");
    }

    private boolean isDocumentFile(String mimeType, String fileName) {
        if (mimeType != null) {
            if (mimeType.equals("application/pdf")
                    || mimeType.equals("application/msword")
                    || mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || mimeType.equals("application/vnd.ms-excel")
                    || mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    || mimeType.equals("application/vnd.ms-powerpoint")
                    || mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
                return true;
            }
        }
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".xls") || lower.endsWith(".xlsx")
                || lower.endsWith(".ppt") || lower.endsWith(".pptx");
    }

    private String abbreviateAiContext(String text, int limit) {
        String normalized = StrUtil.trim(text);
        if (StrUtil.isBlank(normalized)) {
            return "";
        }
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "\n...(内容过长已截断)";
    }

    private String extractChatResponseText(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        return StrUtil.blankToDefault(chatResponse.getResult().getOutput().getText(), "");
    }

    private void logProjectAiError(Throwable error) {
        WebClientResponseException exception = findWebClientResponseException(error);
        if (exception != null) {
            log.error("项目资料 AI 分析错误: {}, response body: {}",
                    error.getMessage(), exception.getResponseBodyAsString(), error);
            return;
        }
        log.error("项目资料 AI 分析错误: {}", error.getMessage(), error);
    }

    private String resolveProjectAiErrorMessage(Throwable error) {
        WebClientResponseException exception = findWebClientResponseException(error);
        if (exception == null) {
            return PROJECT_AI_ERROR_MESSAGE;
        }
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return "AI 服务认证失败：当前 API Key 无效或无权访问所选模型。请检查 API Key、服务商和模型权限后重试。";
        }
        if (status == 404) {
            return "当前 AI 模型不可用：所选模型不存在或当前 API Key 无权访问。请检查模型名称、服务商和 API Key 权限后重试。";
        }
        if (status == 429) {
            return "AI 服务暂时无法处理请求：额度不足或请求过于频繁。请稍后重试，或检查服务商额度。";
        }
        if (status >= 500) {
            return "AI 服务商暂时不可用，请稍后重试。";
        }
        return PROJECT_AI_ERROR_MESSAGE;
    }

    private WebClientResponseException findWebClientResponseException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebClientResponseException exception) {
                return exception;
            }
            current = current.getCause();
        }
        return null;
    }

    private String buildAiCommandChatContent(String content, ProjectBO.AiCommand commandBO) {
        if (commandBO == null) {
            return content;
        }
        List<String> contextLines = new ArrayList<>();
        if (commandBO.getAttachments() != null && !commandBO.getAttachments().isEmpty()) {
            String attachments = commandBO.getAttachments().stream()
                    .filter(Objects::nonNull)
                    .map(this::describeAiCommandAttachment)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.joining("、"));
            if (StrUtil.isNotBlank(attachments)) {
                contextLines.add("已选择资料：" + attachments);
            }
        }
        if (commandBO.getKnowledgeIds() != null && !commandBO.getKnowledgeIds().isEmpty()) {
            String knowledgeIds = commandBO.getKnowledgeIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining("、"));
            if (StrUtil.isNotBlank(knowledgeIds)) {
                contextLines.add("已选择知识库 ID：" + knowledgeIds);
            }
        }
        if (contextLines.isEmpty()) {
            return content;
        }
        return content + "\n\n" + String.join("\n", contextLines);
    }

    private String describeAiCommandAttachment(ChatSendBO.AttachmentDTO attachment) {
        String fileName = StrUtil.blankToDefault(attachment.getFileName(), "未命名资料");
        Long fileSize = attachment.getFileSize();
        if (fileSize == null || fileSize <= 0) {
            return fileName;
        }
        return fileName + "（" + fileSize + " B）";
    }

    private ProjectVO buildProjectVO(Long projectId) {
        return buildProjectVO(projectId, null);
    }

    private ProjectVO buildProjectVO(Long projectId, String taskKeyword) {
        ProjectRow project = findProject(projectId);
        ensureProjectPermission(projectId, PERMISSION_VIEW_PROJECT);
        boolean admin = isSystemAdmin();
        MemberRow currentMember = findActiveMember(projectId, currentUserId());

        ProjectVO vo = new ProjectVO();
        vo.setProjectId(project.projectId());
        vo.setName(project.name());
        vo.setDescription(project.description());
        vo.setCustomerId(project.customerId());
        vo.setCustomerName(resolveCustomerName(project.customerId(), project.customerName()));
        vo.setOwnerId(project.ownerId());
        vo.setOwnerName(resolveUserSnapshot(project.ownerId(), null, null, null).displayName());
        vo.setStartDate(project.startDate());
        vo.setDueDate(project.dueDate());
        vo.setStatus(project.status());
        vo.setCreateTime(project.createTime());
        vo.setUpdateTime(project.updateTime());
        vo.setSystemAdmin(admin);
        boolean ownerFallback = Objects.equals(project.ownerId(), currentUserId());
        vo.setCurrentUserRole(admin ? "ADMIN" : ownerFallback ? "OWNER" : currentMember == null ? null : currentMember.role());
        vo.setCurrentUserPermissions(admin || ownerFallback ? ALL_PERMISSIONS : currentMember == null ? Collections.emptyList() : currentMember.permissions());

        List<ProjectVO.ProjectLaneVO> lanes = loadLanes(projectId).stream().map(this::toLaneVO).collect(Collectors.toList());
        vo.setLanes(lanes);

        List<ProjectVO.ProjectTaskVO> tasks = loadTasks(projectId, taskKeyword).stream()
                .filter(task -> canViewTask(projectId, task))
                .map(this::toTaskVO)
                .sorted(Comparator.comparing(ProjectVO.ProjectTaskVO::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        vo.setTasks(tasks);
        vo.setTaskCount(tasks.size());
        vo.setIncompleteTaskCount((int) tasks.stream().filter(task -> !"已完成".equals(task.getStatus())).count());

        vo.setAttachments(loadProjectAttachments(projectId));
        vo.setSchedules(loadProjectSchedules(projectId));
        vo.setChatMessages(loadProjectChats(projectId).stream()
                .filter(message -> !isProjectWelcomeMessage(message.getContent()))
                .collect(Collectors.toList()));
        vo.setMembers(loadMembers(projectId).stream().map(this::toMemberVO).collect(Collectors.toList()));
        vo.setMemberLogs(loadMemberLogs(projectId));
        return vo;
    }

    private ProjectVO.ProjectLaneVO toLaneVO(LaneRow lane) {
        ProjectVO.ProjectLaneVO vo = new ProjectVO.ProjectLaneVO();
        vo.setLaneId(lane.laneId());
        vo.setName(lane.name());
        vo.setOrder(lane.sortOrder());
        vo.setSystem(lane.system());
        return vo;
    }

    private ProjectVO.ProjectTaskVO toTaskVO(TaskRow task) {
        ProjectVO.ProjectTaskVO vo = new ProjectVO.ProjectTaskVO();
        LaneRow lane = findLane(task.projectId(), task.laneId());
        vo.setTaskId(task.taskId());
        vo.setProjectId(task.projectId());
        vo.setTitle(task.title());
        vo.setDescription(task.description());
        vo.setLaneId(task.laneId());
        vo.setStatus(lane.name());
        vo.setDueDate(task.dueDate());
        vo.setOwnerId(task.ownerId());
        vo.setOwnerName(resolveUserSnapshot(task.ownerId(), null, null, null).displayName());
        vo.setParticipantIds(task.participantIds());
        vo.setParticipantNames(task.participantNames());
        vo.setPriority(normalizeTaskPriority(task.priority()));
        vo.setCustomerId(task.customerId());
        ProjectRow project = findProject(task.projectId());
        vo.setCustomerName(resolveCustomerName(task.customerId(), project.customerName()));
        vo.setHasAttachments(Boolean.TRUE.equals(task.hasAttachments()) || !loadTaskAttachments(task.taskId()).isEmpty());
        vo.setHasSchedule(Boolean.TRUE.equals(task.hasSchedule()) || !loadTaskSchedules(task.taskId()).isEmpty());
        vo.setGeneratedByAi(Boolean.TRUE.equals(task.generatedByAi()));
        vo.setSource(StrUtil.blankToDefault(task.source(), Boolean.TRUE.equals(task.generatedByAi()) ? "ai" : "manual"));
        vo.setAiSourceText(task.aiSourceText());
        vo.setAttachments(loadTaskAttachments(task.taskId()));
        vo.setSchedules(loadTaskSchedules(task.taskId()));
        vo.setNotes(loadTaskNotes(task.taskId()));
        vo.setChatMessages(loadTaskChats(task.taskId()).stream()
                .filter(message -> !isTaskWelcomeMessage(message.getContent()))
                .collect(Collectors.toList()));
        vo.setCreateTime(task.createTime());
        vo.setUpdateTime(task.updateTime());
        return vo;
    }

    private ProjectVO.ProjectMemberVO toMemberVO(MemberRow member) {
        ProjectVO.ProjectMemberVO vo = new ProjectVO.ProjectMemberVO();
        vo.setMemberId(member.memberId());
        vo.setUserId(member.userId());
        vo.setMemberName(member.memberName());
        vo.setAccount(member.account());
        vo.setRole(member.role());
        vo.setDeptName(member.deptName());
        vo.setJoinedAt(member.joinedAt());
        vo.setLastActionTime(member.lastActionTime());
        vo.setStatus(member.status());
        vo.setPermissions(member.permissions());
        vo.setRemark(member.remark());
        return vo;
    }

    private ProjectRow findProject(Long projectId) {
        List<ProjectRow> rows = jdbcTemplate.query("""
                SELECT project_id, tenant_id, name, description, status, owner_id, customer_id,
                       customer_name, start_date, due_date, create_time, update_time
                FROM crm_project
                WHERE project_id = ? AND (tenant_id = ? OR ? IS NULL)
                """, (rs, rowNum) -> new ProjectRow(
                rs.getLong("project_id"),
                getLong(rs, "tenant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                getLong(rs, "owner_id"),
                getLong(rs, "customer_id"),
                rs.getString("customer_name"),
                rs.getTimestamp("start_date"),
                rs.getTimestamp("due_date"),
                rs.getTimestamp("create_time"),
                rs.getTimestamp("update_time")
        ), projectId, currentTenantId(), currentTenantId());
        if (rows.isEmpty()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_DATA_DOES_NOT_EXIST, "项目不存在或无权访问");
        }
        return rows.get(0);
    }

    private LaneRow findLane(Long projectId, Long laneId) {
        List<LaneRow> rows = jdbcTemplate.query("""
                SELECT lane_id, project_id, name, code, sort_order, system_flag
                FROM crm_project_lane
                WHERE project_id = ? AND lane_id = ?
                """, (rs, rowNum) -> mapLane(rs), projectId, laneId);
        if (rows.isEmpty()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_DATA_DOES_NOT_EXIST, "泳道不存在");
        }
        return rows.get(0);
    }

    private TaskRow findTask(Long projectId, Long taskId) {
        List<TaskRow> rows = jdbcTemplate.query("""
                SELECT task_id, project_id, lane_id, title, description, due_date, priority, status,
                       assigned_to, customer_id, generated_by_ai, participant_user_ids, participant_names,
                       source, ai_source_text, has_attachments, has_schedule, create_time, update_time
                FROM crm_task
                WHERE project_id = ? AND task_id = ?
                """, (rs, rowNum) -> mapTask(rs), projectId, taskId);
        if (rows.isEmpty()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_DATA_DOES_NOT_EXIST, "任务不存在");
        }
        return rows.get(0);
    }

    private MemberRow findMember(Long projectId, Long userId) {
        List<MemberRow> rows = jdbcTemplate.query("""
                SELECT member_id, project_id, user_id, member_name, account, role, dept_name,
                       joined_at, last_action_time, status, permissions, remark
                FROM crm_project_member
                WHERE project_id = ? AND user_id = ?
                """, (rs, rowNum) -> mapMember(rs), projectId, userId);
        if (rows.isEmpty()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_DATA_DOES_NOT_EXIST, "项目成员不存在");
        }
        return rows.get(0);
    }

    private MemberRow findActiveMember(Long projectId, Long userId) {
        if (userId == null) {
            return null;
        }
        List<MemberRow> rows = jdbcTemplate.query("""
                SELECT member_id, project_id, user_id, member_name, account, role, dept_name,
                       joined_at, last_action_time, status, permissions, remark
                FROM crm_project_member
                WHERE project_id = ? AND user_id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> mapMember(rs), projectId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<LaneRow> loadLanes(Long projectId) {
        return jdbcTemplate.query("""
                SELECT lane_id, project_id, name, code, sort_order, system_flag
                FROM crm_project_lane
                WHERE project_id = ?
                ORDER BY sort_order ASC, create_time ASC
                """, (rs, rowNum) -> mapLane(rs), projectId);
    }

    private List<TaskRow> loadTasks(Long projectId) {
        return loadTasks(projectId, null);
    }

    private List<TaskRow> loadTasks(Long projectId, String taskKeyword) {
        String keyword = normalizeTaskSearchKeyword(taskKeyword);
        List<Object> params = new ArrayList<>();
        params.add(projectId);
        StringBuilder sql = new StringBuilder("""
                SELECT t.task_id, t.project_id, t.lane_id, t.title, t.description, t.due_date, t.priority, t.status,
                       t.assigned_to, t.customer_id, t.generated_by_ai, t.participant_user_ids, t.participant_names,
                       t.source, t.ai_source_text, t.has_attachments, t.has_schedule, t.create_time, t.update_time
                FROM crm_task t
                LEFT JOIN crm_project_lane l ON l.project_id = t.project_id AND l.lane_id = t.lane_id
                LEFT JOIN crm_customer c ON c.customer_id = t.customer_id
                LEFT JOIN crm_project p ON p.project_id = t.project_id
                LEFT JOIN manager_user assignee ON assignee.user_id = t.assigned_to
                WHERE t.project_id = ?
                """);

        if (keyword != null) {
            sql.append("""
                      AND LOWER(CONCAT_WS(' ',
                          t.title,
                          t.description,
                          t.priority,
                          t.status,
                          t.participant_names,
                          l.name,
                          c.company_name,
                          p.customer_name,
                          assignee.realname
                      )) LIKE ?
                    """);
            params.add("%" + keyword + "%");
        }

        sql.append(" ORDER BY t.update_time DESC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapTask(rs), params.toArray());
    }

    private String normalizeTaskSearchKeyword(String taskKeyword) {
        if (StrUtil.isBlank(taskKeyword)) {
            return null;
        }
        return taskKeyword.trim().toLowerCase(Locale.ROOT);
    }

    private List<MemberRow> loadMembers(Long projectId) {
        return jdbcTemplate.query("""
                SELECT member_id, project_id, user_id, member_name, account, role, dept_name,
                       joined_at, last_action_time, status, permissions, remark
                FROM crm_project_member
                WHERE project_id = ?
                ORDER BY last_action_time DESC
                """, (rs, rowNum) -> mapMember(rs), projectId);
    }

    private List<ProjectVO.ProjectChatMessageVO> loadProjectChats(Long projectId) {
        return jdbcTemplate.query("""
                SELECT message_id, role, content, create_time
                FROM crm_project_chat_message
                WHERE project_id = ?
                ORDER BY create_time ASC
                """, (rs, rowNum) -> {
            ProjectVO.ProjectChatMessageVO vo = new ProjectVO.ProjectChatMessageVO();
            vo.setMessageId(rs.getLong("message_id"));
            vo.setRole(rs.getString("role"));
            vo.setContent(rs.getString("content"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        }, projectId);
    }

    private List<ProjectVO.ProjectAttachmentVO> loadProjectAttachments(Long projectId) {
        return jdbcTemplate.query("""
                SELECT attachment_id, name, file_url, create_user_name, create_time
                FROM crm_project_attachment
                WHERE project_id = ?
                ORDER BY create_time DESC
                """, (rs, rowNum) -> {
            ProjectVO.ProjectAttachmentVO vo = new ProjectVO.ProjectAttachmentVO();
            vo.setAttachmentId(rs.getLong("attachment_id"));
            vo.setName(rs.getString("name"));
            vo.setFileUrl(rs.getString("file_url"));
            vo.setCreatedByName(rs.getString("create_user_name"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        }, projectId);
    }

    private List<ProjectVO.ProjectScheduleVO> loadProjectSchedules(Long projectId) {
        return jdbcTemplate.query("""
                SELECT schedule_id, title, schedule_time, create_user_name, create_time
                FROM crm_project_schedule
                WHERE project_id = ?
                ORDER BY COALESCE(schedule_time, create_time) DESC
                """, (rs, rowNum) -> {
            ProjectVO.ProjectScheduleVO vo = new ProjectVO.ProjectScheduleVO();
            vo.setScheduleId(rs.getLong("schedule_id"));
            vo.setTitle(rs.getString("title"));
            vo.setScheduleTime(rs.getTimestamp("schedule_time"));
            vo.setCreatedByName(rs.getString("create_user_name"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        }, projectId);
    }

    private List<ProjectVO.ProjectTaskChatMessageVO> loadTaskChats(Long taskId) {
        return jdbcTemplate.query("""
                SELECT message_id, role, content, create_time
                FROM crm_project_task_chat_message
                WHERE task_id = ?
                ORDER BY create_time ASC
                """, (rs, rowNum) -> {
            ProjectVO.ProjectTaskChatMessageVO vo = new ProjectVO.ProjectTaskChatMessageVO();
            vo.setMessageId(rs.getLong("message_id"));
            vo.setRole(rs.getString("role"));
            vo.setContent(rs.getString("content"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        }, taskId);
    }

    private List<ProjectVO.ProjectTaskAttachmentVO> loadTaskAttachments(Long taskId) {
        return jdbcTemplate.query("""
                SELECT attachment_id, name, file_url, file_path, file_size, mime_type, create_user_name, create_time
                FROM crm_project_task_attachment
                WHERE task_id = ?
                ORDER BY create_time DESC
                """, (rs, rowNum) -> {
            ProjectVO.ProjectTaskAttachmentVO vo = new ProjectVO.ProjectTaskAttachmentVO();
            vo.setAttachmentId(rs.getLong("attachment_id"));
            vo.setName(rs.getString("name"));
            vo.setFileUrl(rs.getString("file_url"));
            vo.setFilePath(rs.getString("file_path"));
            vo.setFileSize(getLong(rs, "file_size"));
            vo.setMimeType(rs.getString("mime_type"));
            vo.setCreatedByName(rs.getString("create_user_name"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        }, taskId);
    }

    private List<ProjectVO.ProjectTaskScheduleVO> loadTaskSchedules(Long taskId) {
        return jdbcTemplate.query("""
                SELECT schedule_id, title, schedule_time, create_user_name, create_time
                FROM crm_project_task_schedule
                WHERE task_id = ?
                ORDER BY create_time DESC
                """, (rs, rowNum) -> {
            ProjectVO.ProjectTaskScheduleVO vo = new ProjectVO.ProjectTaskScheduleVO();
            vo.setScheduleId(rs.getLong("schedule_id"));
            vo.setTitle(rs.getString("title"));
            vo.setScheduleTime(rs.getTimestamp("schedule_time"));
            vo.setCreatedByName(rs.getString("create_user_name"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        }, taskId);
    }

    private List<ProjectVO.ProjectTaskNoteVO> loadTaskNotes(Long taskId) {
        return jdbcTemplate.query("""
                SELECT note_id, content, create_user_name, create_time
                FROM crm_project_task_note
                WHERE task_id = ?
                ORDER BY create_time DESC
                """, (rs, rowNum) -> {
            ProjectVO.ProjectTaskNoteVO vo = new ProjectVO.ProjectTaskNoteVO();
            vo.setNoteId(rs.getLong("note_id"));
            vo.setContent(rs.getString("content"));
            vo.setCreatedByName(rs.getString("create_user_name"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        }, taskId);
    }

    private List<ProjectVO.ProjectMemberLogVO> loadMemberLogs(Long projectId) {
        return jdbcTemplate.query("""
                SELECT log_id, action_type, operator_id, operator_name, target_user_id, target_user_name,
                       before_summary, after_summary, create_time
                FROM crm_project_member_log
                WHERE project_id = ?
                ORDER BY create_time DESC
                LIMIT 100
                """, (rs, rowNum) -> {
            ProjectVO.ProjectMemberLogVO vo = new ProjectVO.ProjectMemberLogVO();
            vo.setLogId(rs.getLong("log_id"));
            vo.setActionType(rs.getString("action_type"));
            vo.setOperatorId(getLong(rs, "operator_id"));
            vo.setOperatorName(rs.getString("operator_name"));
            vo.setTargetUserId(getLong(rs, "target_user_id"));
            vo.setTargetUserName(rs.getString("target_user_name"));
            vo.setBeforeSummary(rs.getString("before_summary"));
            vo.setAfterSummary(rs.getString("after_summary"));
            vo.setCreateTime(rs.getTimestamp("create_time"));
            return vo;
        }, projectId);
    }

    private LaneRow mapLane(ResultSet rs) throws SQLException {
        return new LaneRow(
                rs.getLong("lane_id"),
                rs.getLong("project_id"),
                rs.getString("name"),
                rs.getString("code"),
                rs.getInt("sort_order"),
                rs.getBoolean("system_flag")
        );
    }

    private TaskRow mapTask(ResultSet rs) throws SQLException {
        return new TaskRow(
                rs.getLong("task_id"),
                getLong(rs, "project_id"),
                getLong(rs, "lane_id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getTimestamp("due_date"),
                rs.getString("priority"),
                rs.getString("status"),
                getLong(rs, "assigned_to"),
                getLong(rs, "customer_id"),
                rs.getInt("generated_by_ai") == 1,
                parseLongList(rs.getString("participant_user_ids")),
                splitNames(rs.getString("participant_names")),
                rs.getString("source"),
                rs.getString("ai_source_text"),
                rs.getBoolean("has_attachments"),
                rs.getBoolean("has_schedule"),
                rs.getTimestamp("create_time"),
                rs.getTimestamp("update_time")
        );
    }

    private MemberRow mapMember(ResultSet rs) throws SQLException {
        return new MemberRow(
                rs.getLong("member_id"),
                rs.getLong("project_id"),
                rs.getLong("user_id"),
                rs.getString("member_name"),
                rs.getString("account"),
                rs.getString("role"),
                rs.getString("dept_name"),
                rs.getTimestamp("joined_at"),
                rs.getTimestamp("last_action_time"),
                rs.getString("status"),
                parseStringList(rs.getString("permissions")),
                rs.getString("remark")
        );
    }

    private void insertLane(Long projectId, Long tenantId, String code, String name, Integer sortOrder, boolean systemFlag) {
        jdbcTemplate.update("""
                INSERT INTO crm_project_lane(
                    lane_id, tenant_id, project_id, name, code, sort_order, system_flag,
                    create_user_id, update_user_id, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), tenantId, projectId, name, code, sortOrder, systemFlag, currentUserId(), currentUserId());
    }

    private void upsertMember(Long projectId, Long tenantId, Long userId, String memberName, String account, String deptName,
                              String role, List<String> permissions, String remark, String status, boolean writeLog) {
        MemberRow before = null;
        try {
            before = findMember(projectId, userId);
        } catch (BusinessException ignored) {
            // create below
        }

        if (before == null) {
            jdbcTemplate.update("""
                    INSERT INTO crm_project_member(
                        member_id, tenant_id, project_id, user_id, member_name, account, role, dept_name,
                        joined_at, last_action_time, status, permissions, remark, create_user_id, update_user_id,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, IdWorker.getId(), tenantId, projectId, userId, memberName, account, role, deptName,
                    status, writeJson(normalizePermissions(permissions)), remark, currentUserId(), currentUserId());
            if (writeLog) {
                MemberRow after = findMember(projectId, userId);
                appendMemberLog(projectId, "ADD_MEMBER", userId, memberName, null, memberSummary(after));
            }
            return;
        }

        String beforeSummary = memberSummary(before);
        jdbcTemplate.update("""
                UPDATE crm_project_member
                SET member_name = ?, account = ?, role = ?, dept_name = ?, last_action_time = CURRENT_TIMESTAMP,
                    status = ?, permissions = ?, remark = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ? AND user_id = ?
                """, memberName, account, role, deptName, status, writeJson(normalizePermissions(permissions)),
                remark, currentUserId(), projectId, userId);
        if (writeLog) {
            MemberRow after = findMember(projectId, userId);
            appendMemberLog(projectId, "UPDATE_PERMISSION", userId, memberName, beforeSummary, memberSummary(after));
        }
    }

    private void appendMemberLog(Long projectId, String actionType, Long targetUserId, String targetUserName, String beforeSummary, String afterSummary) {
        UserSnapshot operator = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_member_log(
                    log_id, tenant_id, project_id, operator_id, operator_name, action_type,
                    target_user_id, target_user_name, before_summary, after_summary, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), currentTenantId(), projectId, currentUserId(), operator.displayName(),
                actionType, targetUserId, targetUserName, beforeSummary, afterSummary);
    }

    private void appendProjectChat(Long projectId, String role, String content) {
        UserSnapshot user = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_chat_message(
                    message_id, tenant_id, project_id, role, content, create_user_id, create_user_name, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), currentTenantId(), projectId, role, content, currentUserId(), user.displayName());
    }

    private void appendProjectAttachment(Long projectId, String name) {
        UserSnapshot user = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_attachment(
                    attachment_id, tenant_id, project_id, name, create_user_id, create_user_name, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), currentTenantId(), projectId, name, currentUserId(), user.displayName());
    }

    private void appendProjectSchedule(Long projectId, String title, Date scheduleTime) {
        UserSnapshot user = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_schedule(
                    schedule_id, tenant_id, project_id, title, schedule_time, create_user_id, create_user_name, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), currentTenantId(), projectId, title, toTimestamp(scheduleTime), currentUserId(), user.displayName());
    }

    private void appendTaskChat(Long projectId, Long taskId, String role, String content) {
        UserSnapshot user = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_task_chat_message(
                    message_id, tenant_id, project_id, task_id, role, content, create_user_id, create_user_name, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), currentTenantId(), projectId, taskId, role, content, currentUserId(), user.displayName());
    }

    private List<ProjectBO.TaskAttachmentSave> normalizedTaskAttachments(List<ProjectBO.TaskAttachmentSave> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return Collections.emptyList();
        }
        return attachments.stream()
                .filter(Objects::nonNull)
                .filter(attachment -> StrUtil.isNotBlank(attachment.getFileName()) || StrUtil.isNotBlank(attachment.getFilePath()))
                .toList();
    }

    private void appendTaskAttachments(Long projectId, Long taskId, List<ProjectBO.TaskAttachmentSave> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        attachments.forEach(attachment -> appendTaskAttachment(projectId, taskId, attachment));
    }

    private void appendTaskAttachment(Long projectId, Long taskId, ProjectBO.TaskAttachmentSave attachment) {
        String filePath = StrUtil.trimToNull(attachment.getFilePath());
        String name = StrUtil.blankToDefault(attachment.getFileName(), StrUtil.blankToDefault(filePath, "附件"));
        UserSnapshot user = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_task_attachment(
                    attachment_id, tenant_id, project_id, task_id, name, file_url, file_path, file_size, mime_type,
                    create_user_id, create_user_name, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                IdWorker.getId(),
                currentTenantId(),
                projectId,
                taskId,
                name,
                resolveAttachmentFileUrl(filePath),
                filePath,
                attachment.getFileSize(),
                StrUtil.blankToDefault(attachment.getMimeType(), "application/octet-stream"),
                currentUserId(),
                user.displayName());
        jdbcTemplate.update("UPDATE crm_task SET has_attachments = TRUE, update_time = CURRENT_TIMESTAMP WHERE task_id = ?", taskId);
    }

    private String resolveAttachmentFileUrl(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return null;
        }
        try {
            return fileStorageService.getUrl(filePath);
        } catch (Exception e) {
            log.warn("获取项目任务附件访问地址失败: {}", filePath, e);
            return null;
        }
    }

    private void appendTaskAttachment(Long projectId, Long taskId, String name) {
        UserSnapshot user = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_task_attachment(
                    attachment_id, tenant_id, project_id, task_id, name, create_user_id, create_user_name, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), currentTenantId(), projectId, taskId, name, currentUserId(), user.displayName());
        jdbcTemplate.update("UPDATE crm_task SET has_attachments = TRUE, update_time = CURRENT_TIMESTAMP WHERE task_id = ?", taskId);
    }

    private void appendTaskSchedule(Long projectId, Long taskId, String title, Date scheduleTime) {
        UserSnapshot user = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_task_schedule(
                    schedule_id, tenant_id, project_id, task_id, title, schedule_time,
                    create_user_id, create_user_name, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), currentTenantId(), projectId, taskId, title, toTimestamp(scheduleTime), currentUserId(), user.displayName());
        jdbcTemplate.update("UPDATE crm_task SET has_schedule = TRUE, update_time = CURRENT_TIMESTAMP WHERE task_id = ?", taskId);
    }

    private void appendTaskNote(Long projectId, Long taskId, String content) {
        UserSnapshot user = currentUserSnapshot();
        jdbcTemplate.update("""
                INSERT INTO crm_project_task_note(
                    note_id, tenant_id, project_id, task_id, content, create_user_id, create_user_name, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, IdWorker.getId(), currentTenantId(), projectId, taskId, content, currentUserId(), user.displayName());
    }

    private void ensureProjectPermission(Long projectId, String permission) {
        if (!hasProjectPermission(projectId, permission)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前账号没有该项目操作权限");
        }
    }

    private boolean hasProjectPermission(Long projectId, String permission) {
        if (isSystemAdmin()) {
            return true;
        }
        Long userId = currentUserId();
        ProjectRow project = findProject(projectId);
        if (Objects.equals(project.ownerId(), userId)) {
            return true;
        }
        MemberRow member = findActiveMember(projectId, userId);
        return member != null && member.permissions().contains(permission);
    }

    private void ensureCanEditTask(Long projectId, TaskRow task) {
        if (!canEditTask(projectId, task)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前账号没有编辑该任务的权限");
        }
    }

    private void ensureCanMoveTask(Long projectId, TaskRow task) {
        if (!canMoveTask(projectId, task)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前账号没有拖动该任务状态的权限");
        }
    }

    private void ensureCanUseTaskAi(Long projectId, TaskRow task) {
        if (!hasProjectPermission(projectId, PERMISSION_USE_AI_CHAT) || !canViewTask(projectId, task)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前账号没有使用该任务 AI 对话的权限");
        }
    }

    private void ensureTaskSidePermission(Long projectId, TaskRow task, String permission, String message) {
        if (isSystemAdmin()) {
            return;
        }
        MemberRow member = findActiveMember(projectId, currentUserId());
        if (member == null || !member.permissions().contains(permission)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_AUTH, message);
        }
        if ("EXTERNAL".equals(member.role()) && !isTaskAssigneeOrParticipant(task, currentUserId())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_AUTH, message);
        }
    }

    private boolean canViewTask(Long projectId, TaskRow task) {
        if (isSystemAdmin()) {
            return true;
        }
        MemberRow member = findActiveMember(projectId, currentUserId());
        if (member == null) {
            return false;
        }
        if (isTaskAssigneeOrParticipant(task, currentUserId())) {
            return true;
        }
        return member.permissions().contains(PERMISSION_VIEW_PROJECT) && !"EXTERNAL".equals(member.role());
    }

    private boolean canEditTask(Long projectId, TaskRow task) {
        if (isSystemAdmin()) {
            return true;
        }
        MemberRow member = findActiveMember(projectId, currentUserId());
        if (member == null || !member.permissions().contains(PERMISSION_EDIT_TASK)) {
            return false;
        }
        if (Set.of("OWNER", "ADMIN").contains(member.role())) {
            return true;
        }
        return isTaskAssigneeOrParticipant(task, currentUserId()) && !"READONLY".equals(member.role()) && !"EXTERNAL".equals(member.role());
    }

    private boolean canMoveTask(Long projectId, TaskRow task) {
        if (isSystemAdmin()) {
            return true;
        }
        MemberRow member = findActiveMember(projectId, currentUserId());
        if (member == null || !member.permissions().contains(PERMISSION_MOVE_TASK)) {
            return false;
        }
        if (Set.of("OWNER", "ADMIN").contains(member.role())) {
            return true;
        }
        return Objects.equals(task.ownerId(), currentUserId());
    }

    private boolean isTaskAssigneeOrParticipant(TaskRow task, Long userId) {
        return Objects.equals(task.ownerId(), userId) || task.participantIds().contains(userId);
    }

    private boolean isSystemAdmin() {
        try {
            Long userId = currentUserId();
            return Objects.equals(userId, UserUtil.getSuperUserId())
                    || permissionService.hasPermission("config")
                    || permissionService.hasPermission("user")
                    || permissionService.hasPermission("role");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateProjectStatus(Long projectId, String status) {
        jdbcTemplate.update("""
                UPDATE crm_project
                SET status = ?, update_user_id = ?, update_time = CURRENT_TIMESTAMP
                WHERE project_id = ?
                """, status, currentUserId(), projectId);
    }

    private void touchProject(Long projectId) {
        jdbcTemplate.update("UPDATE crm_project SET update_user_id = ?, update_time = CURRENT_TIMESTAMP WHERE project_id = ?",
                currentUserId(), projectId);
    }

    private void updateProjectCustomerNameHint(Long projectId, String customerName) {
        if (StrUtil.isNotBlank(customerName)) {
            jdbcTemplate.update("""
                    UPDATE crm_project
                    SET customer_name = COALESCE(NULLIF(customer_name, ''), ?), update_time = CURRENT_TIMESTAMP
                    WHERE project_id = ?
                    """, customerName, projectId);
        }
    }

    private LaneRow defaultLane(Long projectId) {
        return loadLanes(projectId).stream()
                .filter(lane -> "not-started".equals(lane.code()))
                .findFirst()
                .orElseGet(() -> loadLanes(projectId).stream().findFirst()
                        .orElseThrow(() -> new BusinessException(SystemCodeEnum.SYSTEM_DATA_DOES_NOT_EXIST, "项目泳道不存在")));
    }

    private LaneRow findLaneByKeyword(Long projectId, String content) {
        return loadLanes(projectId).stream()
                .filter(lane -> content.contains(lane.name()))
                .findFirst()
                .orElse(null);
    }

    private LaneRow findLaneByExactName(Long projectId, String laneName) {
        return loadLanes(projectId).stream()
                .filter(lane -> lane.name().equals(laneName))
                .findFirst()
                .orElse(null);
    }

    private String laneToTaskStatus(LaneRow lane) {
        if ("not-started".equals(lane.code())) {
            return "pending";
        }
        if ("in-progress".equals(lane.code())) {
            return "in_progress";
        }
        if ("completed".equals(lane.code())) {
            return "completed";
        }
        return lane.name();
    }

    private ProjectBO.TaskSave parseProjectAiTask(Long projectId, String content) {
        return parseProjectAiTask(projectId, content, "");
    }

    private ProjectBO.TaskSave parseProjectAiTask(Long projectId, String content, String parsedTitle) {
        ProjectRow project = findProject(projectId);
        ProjectBO.TaskSave task = new ProjectBO.TaskSave();
        String customerName = extractCustomerName(content);
        if (StrUtil.isNotBlank(customerName) && project.customerId() == null) {
            updateProjectCustomerNameHint(projectId, customerName);
        }
        task.setTitle(StrUtil.blankToDefault(parsedTitle, extractTaskTitle(content)));
        task.setDescription("由项目 AI 对话创建：" + content);
        task.setLaneId(defaultLane(projectId).laneId());
        task.setDueDate(parseRelativeDateTime(content));
        task.setCustomerId(project.customerId());
        task.setCustomerName(StrUtil.blankToDefault(customerName, project.customerName()));
        task.setPriority(parsePriority(content));
        task.setGeneratedByAi(true);
        task.setAiSourceText(content);
        return task;
    }

    private String summarizeProject(Long projectId) {
        ProjectVO project = buildProjectVO(projectId);
        long completed = project.getTasks().stream().filter(task -> "已完成".equals(task.getStatus())).count();
        return "项目「" + project.getName() + "」当前共有 " + project.getTasks().size() + " 个任务，已完成 "
                + completed + " 个，未完成 " + (project.getTasks().size() - completed) + " 个。";
    }

    private String summarizeProjectTasks(Long projectId) {
        ProjectVO project = buildProjectVO(projectId);
        if (project.getTasks().isEmpty()) {
            return "当前项目下还没有你可查看的任务。";
        }
        return "当前可查看任务：" + project.getTasks().stream()
                .limit(6)
                .map(task -> "【" + task.getStatus() + "】" + task.getTitle())
                .collect(Collectors.joining("；"));
    }

    private String buildTaskExecutionPlan(TaskRow task) {
        return """
                任务执行方案：
                1. 明确交付目标：%s。
                2. 梳理所需资料、附件和客户输入。
                3. 拆分准备、确认、交付和复盘四个步骤。
                4. 在截止时间前预留至少一次内部检查。
                """.formatted(task.title());
    }

    private String extractTaskTitle(String content) {
        String cleaned = content
                .replaceAll("这个项目是.*?项目，?", "")
                .replaceAll("帮我创建一个任务|创建一个任务|新增一个任务|创建任务|新增任务|安排任务", "")
                .replaceAll("[。！!]+$", "")
                .trim();
        Matcher matcher = Pattern.compile("(给客户.+)").matcher(cleaned);
        if (matcher.find()) {
            cleaned = matcher.group(1);
        }
        return StrUtil.blankToDefault(cleaned, "项目任务").length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private String extractCustomerName(String content) {
        Matcher partner = Pattern.compile("和(.+?公司).*?(?:合作|项目)").matcher(content);
        if (partner.find()) {
            return partner.group(1).trim();
        }
        Matcher customer = Pattern.compile("(?:客户|关联客户)[：:为是]?([^，。,.]+)").matcher(content);
        return customer.find() ? customer.group(1).trim() : "";
    }

    private Date parseRelativeDateTime(String content) {
        if (!containsAny(content, "今天", "明天", "后天", "本周", "这周", "下周", "本月", "月底", "月末", "上午", "下午", "晚上", "点", ":", "：")
                && !Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}/\\d{1,2}|\\d{1,2}月\\d{1,2}[日号]?|周[一二三四五六日天]|星期[一二三四五六日天]").matcher(content).find()) {
            return null;
        }
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        LocalDate date = today;
        LocalDate explicitDate = parseExplicitDate(content, today);
        DayOfWeek weekday = parseChineseWeekday(content);
        if (explicitDate != null) {
            date = explicitDate;
        } else if (weekday != null) {
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (content.contains("下周")) {
                weekStart = weekStart.plusWeeks(1);
            }
            date = weekStart.plusDays(weekday.getValue() - 1L);
            if (!content.contains("下周") && date.isBefore(today)) {
                date = date.plusWeeks(1);
            }
        } else if (content.contains("后天")) {
            date = date.plusDays(2);
        } else if (content.contains("明天")) {
            date = date.plusDays(1);
        } else if (content.contains("本周") || content.contains("这周")) {
            date = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        } else if (content.contains("下周")) {
            date = date.plusWeeks(1);
        } else if (content.contains("本月") || content.contains("月底") || content.contains("月末")) {
            date = date.with(TemporalAdjusters.lastDayOfMonth());
        }

        int hour = content.contains("下午") || content.contains("晚上") || containsAny(content, "本周", "这周", "本月", "月底", "月末", "完成", "截止") ? 18 : 9;
        int minute = 0;
        Matcher colon = Pattern.compile("(\\d{1,2})[:：](\\d{1,2})").matcher(content);
        if (colon.find()) {
            hour = Integer.parseInt(colon.group(1));
            minute = Integer.parseInt(colon.group(2));
        } else {
            Matcher chineseHour = Pattern.compile("(\\d{1,2})点").matcher(content);
            if (chineseHour.find()) {
                hour = Integer.parseInt(chineseHour.group(1));
            } else if (content.contains("三点")) {
                hour = 3;
            } else if (content.contains("两点") || content.contains("二点")) {
                hour = 2;
            }
        }
        if ((content.contains("下午") || content.contains("晚上")) && hour < 12) {
            hour += 12;
        }
        return Date.from(LocalDateTime.of(date, LocalTime.of(Math.min(hour, 23), Math.min(minute, 59))).atZone(DEFAULT_ZONE).toInstant());
    }

    private LocalDate parseExplicitDate(String content, LocalDate baseDate) {
        Matcher iso = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(content);
        if (iso.find()) {
            return localDateOf(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
        }
        Matcher slash = Pattern.compile("(\\d{1,2})/(\\d{1,2})").matcher(content);
        if (slash.find()) {
            return localDateOf(baseDate.getYear(), Integer.parseInt(slash.group(1)), Integer.parseInt(slash.group(2)));
        }
        Matcher chinese = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]?").matcher(content);
        if (chinese.find()) {
            return localDateOf(baseDate.getYear(), Integer.parseInt(chinese.group(1)), Integer.parseInt(chinese.group(2)));
        }
        return null;
    }

    private LocalDate localDateOf(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private DayOfWeek parseChineseWeekday(String content) {
        if (containsAny(content, "周一", "星期一")) {
            return DayOfWeek.MONDAY;
        }
        if (containsAny(content, "周二", "星期二")) {
            return DayOfWeek.TUESDAY;
        }
        if (containsAny(content, "周三", "星期三")) {
            return DayOfWeek.WEDNESDAY;
        }
        if (containsAny(content, "周四", "星期四")) {
            return DayOfWeek.THURSDAY;
        }
        if (containsAny(content, "周五", "星期五")) {
            return DayOfWeek.FRIDAY;
        }
        if (containsAny(content, "周六", "星期六")) {
            return DayOfWeek.SATURDAY;
        }
        if (containsAny(content, "周日", "周天", "星期日", "星期天")) {
            return DayOfWeek.SUNDAY;
        }
        return null;
    }

    private String parsePriority(String content) {
        if (containsAny(content, "紧急", "马上", "尽快", "最高")) {
            return "URGENT";
        }
        if (containsAny(content, "高优先级", "重要", "高")) {
            return "HIGH";
        }
        if (containsAny(content, "低优先级", "不急", "低")) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private String extractAttachmentName(String content) {
        Matcher matcher = Pattern.compile("(?:把|将)?(?:刚上传的)?(.+?)(?:追加到|添加到|挂到|上传到)").matcher(content);
        if (matcher.find() && StrUtil.isNotBlank(matcher.group(1))) {
            return matcher.group(1).trim();
        }
        return "任务附件";
    }

    private String extractScheduleTitle(String content, String fallbackTitle) {
        Matcher matcher = Pattern.compile("(?:创建日程|安排日程|加个日程|同步日程)(.+)$").matcher(content);
        return matcher.find() && StrUtil.isNotBlank(matcher.group(1)) ? matcher.group(1).trim() : fallbackTitle + "相关日程";
    }

    private String extractNote(String content) {
        Matcher matcher = Pattern.compile("(?:备注|补充说明|追加说明)[:：,， ]?(.+)$").matcher(content);
        return matcher.find() && StrUtil.isNotBlank(matcher.group(1)) ? matcher.group(1).trim() : content;
    }

    private UserSnapshot resolveUserSnapshot(Long userId, String fallbackName, String fallbackAccount, String fallbackDeptName) {
        if (userId == null) {
            return new UserSnapshot(null, StrUtil.blankToDefault(fallbackAccount, ""), StrUtil.blankToDefault(fallbackName, ""), StrUtil.blankToDefault(fallbackDeptName, ""));
        }
        List<UserSnapshot> users = jdbcTemplate.query("""
                SELECT u.user_id, u.username, u.realname, d.dept_name
                FROM manager_user u
                LEFT JOIN manager_dept d ON u.dept_id = d.dept_id
                WHERE u.user_id = ?
                LIMIT 1
                """, (rs, rowNum) -> new UserSnapshot(
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("realname"),
                rs.getString("dept_name")
        ), userId);
        if (users.isEmpty()) {
            return new UserSnapshot(userId, StrUtil.blankToDefault(fallbackAccount, String.valueOf(userId)),
                    StrUtil.blankToDefault(fallbackName, String.valueOf(userId)), StrUtil.blankToDefault(fallbackDeptName, ""));
        }
        UserSnapshot user = users.get(0);
        return new UserSnapshot(user.userId(),
                StrUtil.blankToDefault(user.account(), fallbackAccount),
                StrUtil.blankToDefault(user.realName(), fallbackName),
                StrUtil.blankToDefault(user.deptName(), fallbackDeptName));
    }

    private UserSnapshot currentUserSnapshot() {
        try {
            Long userId = currentUserId();
            return resolveUserSnapshot(userId, UserUtil.getLoginUser().getUser().getRealname(), UserUtil.getLoginUser().getUsername(), "");
        } catch (Exception ignored) {
            return new UserSnapshot(null, "system", "系统", "");
        }
    }

    private String resolveCustomerName(Long customerId, String fallback) {
        if (customerId == null) {
            return StrUtil.blankToDefault(fallback, "");
        }
        List<String> names = jdbcTemplate.query("""
                SELECT company_name
                FROM crm_customer
                WHERE customer_id = ?
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("company_name"), customerId);
        return names.isEmpty() ? StrUtil.blankToDefault(fallback, "") : StrUtil.blankToDefault(names.get(0), fallback);
    }

    private Long currentUserId() {
        return UserUtil.getUserId();
    }

    private Long currentTenantId() {
        return UserUtil.getTenantId();
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Timestamp toTimestamp(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private String normalizeProjectStatus(String status) {
        String normalized = StrUtil.blankToDefault(status, "NOT_STARTED").toUpperCase(Locale.ROOT);
        return Set.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "PAUSED", "ARCHIVED").contains(normalized)
                ? normalized
                : "NOT_STARTED";
    }

    private String normalizeProjectQueryStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("ALL".equals(normalized)) {
            return null;
        }
        return Set.of("NOT_STARTED", "IN_PROGRESS", "COMPLETED", "PAUSED", "ARCHIVED").contains(normalized)
                ? normalized
                : null;
    }

    private boolean isProjectWelcomeMessage(String content) {
        return StrUtil.isNotBlank(content)
                && content.startsWith("已进入项目「")
                && content.contains("上下文")
                && content.contains("创建任务");
    }

    private boolean isTaskWelcomeMessage(String content) {
        return StrUtil.isNotBlank(content)
                && content.startsWith("当前对话对象：任务 - ")
                && content.contains("修改截止时间")
                && content.contains("追加备注");
    }

    private String projectStatusName(String status) {
        return switch (normalizeProjectStatus(status)) {
            case "IN_PROGRESS" -> "进行中";
            case "COMPLETED" -> "已完成";
            case "PAUSED" -> "已暂停";
            case "ARCHIVED" -> "已归档";
            default -> "未开始";
        };
    }

    private String normalizeTaskPriority(String priority) {
        String normalized = StrUtil.blankToDefault(priority, "MEDIUM").toUpperCase(Locale.ROOT);
        return Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(normalized) ? normalized : "MEDIUM";
    }

    private String normalizeRole(String role) {
        String normalized = StrUtil.blankToDefault(role, "MEMBER").trim();
        String systemRole = normalized.toUpperCase(Locale.ROOT);
        if (PROJECT_ROLES.contains(systemRole)) {
            return systemRole;
        }
        return StrUtil.isBlank(normalized) ? "MEMBER" : normalized;
    }

    private String normalizeMemberStatus(String status) {
        String normalized = StrUtil.blankToDefault(status, "ACTIVE").toUpperCase(Locale.ROOT);
        return Set.of("ACTIVE", "REMOVED", "DISABLED").contains(normalized) ? normalized : "ACTIVE";
    }

    private List<String> roleDefaultPermissions(String role) {
        Map<String, List<String>> rolePermissions = loadProjectRolePermissions();
        return new ArrayList<>(rolePermissions.getOrDefault(normalizeRole(role), rolePermissions.get("MEMBER")));
    }

    private List<String> normalizePermissions(List<String> permissions) {
        if (permissions == null) {
            return new ArrayList<>();
        }
        Set<String> allowed = new LinkedHashSet<>(ALL_PERMISSIONS);
        return permissions.stream()
                .filter(allowed::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, List<String>> loadProjectRolePermissions() {
        String raw = systemConfigServiceProvider.getObject().getConfigValue(PROJECT_ROLE_PERMISSION_CONFIG_KEY, "");
        if (StrUtil.isBlank(raw)) {
            return defaultRolePermissionsCopy();
        }
        try {
            Map<String, List<String>> parsed = objectMapper.readValue(raw, new TypeReference<Map<String, List<String>>>() {
            });
            return normalizeRolePermissionConfig(parsed);
        } catch (Exception e) {
            log.warn("Failed to parse project role permissions config, fallback to defaults", e);
            return defaultRolePermissionsCopy();
        }
    }

    private ProjectVO.ProjectRolePermissionConfigVO buildRolePermissionConfigVO(Map<String, List<String>> rolePermissions) {
        ProjectVO.ProjectRolePermissionConfigVO vo = new ProjectVO.ProjectRolePermissionConfigVO();
        vo.setRolePermissions(normalizeRolePermissionConfig(rolePermissions));
        return vo;
    }

    private Map<String, List<String>> normalizeRolePermissionConfig(Map<String, List<String>> rolePermissions) {
        Map<String, List<String>> normalized = defaultRolePermissionsCopy();
        if (rolePermissions == null || rolePermissions.isEmpty()) {
            return normalized;
        }
        for (String role : PROJECT_ROLES) {
            List<String> submitted = rolePermissions.get(role);
            if (submitted == null) {
                submitted = rolePermissions.get(role.toLowerCase(Locale.ROOT));
            }
            if (submitted == null) {
                continue;
            }
            List<String> permissions = normalizePermissions(submitted);
            if (!permissions.contains(PERMISSION_VIEW_PROJECT)) {
                permissions.add(0, PERMISSION_VIEW_PROJECT);
            }
            if ("OWNER".equals(role)) {
                permissions = new ArrayList<>(ALL_PERMISSIONS);
            }
            normalized.put(role, permissions);
        }
        rolePermissions.forEach((submittedRole, submittedPermissions) -> {
            String role = normalizeRole(submittedRole);
            if (PROJECT_ROLES.contains(role) || submittedPermissions == null) {
                return;
            }
            List<String> permissions = normalizePermissions(submittedPermissions);
            if (!permissions.contains(PERMISSION_VIEW_PROJECT)) {
                permissions.add(0, PERMISSION_VIEW_PROJECT);
            }
            normalized.put(role, permissions);
        });
        return normalized;
    }

    private Map<String, List<String>> defaultRolePermissionsCopy() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        PROJECT_ROLES.forEach(role -> defaults.put(role, new ArrayList<>(DEFAULT_ROLE_PERMISSIONS.get(role))));
        return defaults;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseStringList(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return Arrays.stream(raw.split(",")).map(String::trim).filter(StrUtil::isNotBlank).collect(Collectors.toList());
        }
    }

    private List<Long> parseLongList(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Long>>() {
            });
        } catch (Exception ignored) {
            return Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .map(value -> {
                        try {
                            return Long.parseLong(value);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    private List<String> splitNames(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split(",|，"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private String joinNames(List<String> values) {
        return values == null ? "" : values.stream().filter(StrUtil::isNotBlank).collect(Collectors.joining(","));
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private <T> List<T> defaultList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private boolean containsAny(String content, String... keywords) {
        if (StrUtil.isBlank(content)) {
            return false;
        }
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String memberSummary(MemberRow member) {
        return "角色：" + member.role() + "，状态：" + member.status() + "，权限数：" + member.permissions().size();
    }

    private static Map<String, List<String>> buildRolePermissions() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("OWNER", ALL_PERMISSIONS);
        map.put("ADMIN", ALL_PERMISSIONS.stream().filter(permission -> !PERMISSION_DELETE_PROJECT.equals(permission)).collect(Collectors.toList()));
        map.put("MEMBER", List.of(
                PERMISSION_VIEW_PROJECT,
                PERMISSION_CREATE_TASK,
                PERMISSION_EDIT_TASK,
                PERMISSION_MOVE_TASK,
                PERMISSION_USE_AI_CHAT,
                PERMISSION_AI_CREATE_TASK,
                PERMISSION_UPLOAD_ATTACHMENT,
                PERMISSION_CREATE_SCHEDULE,
                PERMISSION_VIEW_STATISTICS
        ));
        map.put("READONLY", List.of(PERMISSION_VIEW_PROJECT, PERMISSION_VIEW_STATISTICS));
        map.put("EXTERNAL", List.of(PERMISSION_VIEW_PROJECT, PERMISSION_UPLOAD_ATTACHMENT));
        return map;
    }

    private record ProjectRow(
            Long projectId,
            Long tenantId,
            String name,
            String description,
            String status,
            Long ownerId,
            Long customerId,
            String customerName,
            Date startDate,
            Date dueDate,
            Date createTime,
            Date updateTime
    ) {
    }

    private record LaneRow(
            Long laneId,
            Long projectId,
            String name,
            String code,
            Integer sortOrder,
            Boolean system
    ) {
    }

    private record TaskRow(
            Long taskId,
            Long projectId,
            Long laneId,
            String title,
            String description,
            Date dueDate,
            String priority,
            String status,
            Long ownerId,
            Long customerId,
            Boolean generatedByAi,
            List<Long> participantIds,
            List<String> participantNames,
            String source,
            String aiSourceText,
            Boolean hasAttachments,
            Boolean hasSchedule,
            Date createTime,
            Date updateTime
    ) {
    }

    private record MemberRow(
            Long memberId,
            Long projectId,
            Long userId,
            String memberName,
            String account,
            String role,
            String deptName,
            Date joinedAt,
            Date lastActionTime,
            String status,
            List<String> permissions,
            String remark
    ) {
    }

    private record UserSnapshot(
            Long userId,
            String account,
            String realName,
            String deptName
    ) {
        String displayName() {
            return StrUtil.blankToDefault(realName, StrUtil.blankToDefault(account, userId == null ? "系统" : String.valueOf(userId)));
        }
    }

    private record ProjectAiBillingContext(
            DynamicChatClientProvider provider,
            DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig,
            AiModelPricingService.PricingSnapshot pricing,
            String modelSource
    ) {
    }
}
