package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.ProjectBO;
import com.kakarote.ai_crm.entity.PO.Customer;
import com.kakarote.ai_crm.entity.PO.ManagerUser;
import com.kakarote.ai_crm.entity.PO.Project;
import com.kakarote.ai_crm.entity.PO.ProjectAttachment;
import com.kakarote.ai_crm.entity.PO.ProjectLane;
import com.kakarote.ai_crm.entity.PO.ProjectSchedule;
import com.kakarote.ai_crm.entity.PO.ProjectTask;
import com.kakarote.ai_crm.entity.PO.ProjectTaskAttachment;
import com.kakarote.ai_crm.entity.VO.ProjectVO;
import com.kakarote.ai_crm.mapper.CustomerMapper;
import com.kakarote.ai_crm.mapper.ManageUserMapper;
import com.kakarote.ai_crm.mapper.ProjectAttachmentMapper;
import com.kakarote.ai_crm.mapper.ProjectLaneMapper;
import com.kakarote.ai_crm.mapper.ProjectMapper;
import com.kakarote.ai_crm.mapper.ProjectScheduleMapper;
import com.kakarote.ai_crm.mapper.ProjectTaskAttachmentMapper;
import com.kakarote.ai_crm.mapper.ProjectTaskMapper;
import com.kakarote.ai_crm.service.IKnowledgeService;
import com.kakarote.ai_crm.service.IProjectService;
import com.kakarote.ai_crm.utils.UserUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class ProjectServiceImpl implements IProjectService {

    private static final String STATUS_NOT_STARTED = "NOT_STARTED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String TASK_STATUS_TODO = "TODO";
    private static final String TASK_STATUS_COMPLETED = "COMPLETED";
    private static final String PRIORITY_MEDIUM = "MEDIUM";

    private final ProjectMapper projectMapper;
    private final ProjectLaneMapper projectLaneMapper;
    private final ProjectTaskMapper projectTaskMapper;
    private final ProjectTaskAttachmentMapper projectTaskAttachmentMapper;
    private final ProjectAttachmentMapper projectAttachmentMapper;
    private final ProjectScheduleMapper projectScheduleMapper;
    private final ManageUserMapper manageUserMapper;
    private final CustomerMapper customerMapper;
    private final IKnowledgeService knowledgeService;

    public ProjectServiceImpl(ProjectMapper projectMapper,
                              ProjectLaneMapper projectLaneMapper,
                              ProjectTaskMapper projectTaskMapper,
                              ProjectTaskAttachmentMapper projectTaskAttachmentMapper,
                              ProjectAttachmentMapper projectAttachmentMapper,
                              ProjectScheduleMapper projectScheduleMapper,
                              ManageUserMapper manageUserMapper,
                              CustomerMapper customerMapper,
                              IKnowledgeService knowledgeService) {
        this.projectMapper = projectMapper;
        this.projectLaneMapper = projectLaneMapper;
        this.projectTaskMapper = projectTaskMapper;
        this.projectTaskAttachmentMapper = projectTaskAttachmentMapper;
        this.projectAttachmentMapper = projectAttachmentMapper;
        this.projectScheduleMapper = projectScheduleMapper;
        this.manageUserMapper = manageUserMapper;
        this.customerMapper = customerMapper;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public List<ProjectVO> listProjects() {
        ProjectBO.Query query = new ProjectBO.Query();
        query.setPage(1);
        query.setLimit(1000);
        return queryPageList(query).getRecords();
    }

    @Override
    public BasePage<ProjectVO> queryPageList(ProjectBO.Query queryBO) {
        ProjectBO.Query query = queryBO == null ? new ProjectBO.Query() : queryBO;
        query.setStatus(normalizeProjectQueryStatus(query.getStatus()));
        query.setIncludeArchived(STATUS_ARCHIVED.equals(query.getStatus()));
        return projectMapper.queryPageList(query.parse(), query);
    }

    @Override
    public ProjectVO getProject(Long projectId) {
        return getProject(projectId, null);
    }

    @Override
    public ProjectVO getProject(Long projectId, String taskKeyword) {
        ProjectVO project = projectMapper.getProjectById(projectId);
        if (project == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Project does not exist");
        }
        project.setLanes(projectLaneMapper.selectList(Wrappers.<ProjectLane>lambdaQuery()
                        .eq(ProjectLane::getProjectId, projectId)
                        .orderByAsc(ProjectLane::getSortOrder)
                        .orderByAsc(ProjectLane::getCreateTime))
                .stream()
                .map(this::toLaneVO)
                .toList());
        List<ProjectVO.ProjectTaskVO> tasks = projectTaskMapper.selectProjectTasks(projectId, StrUtil.trimToNull(taskKeyword));
        tasks.forEach(this::fillTaskAttachments);
        project.setTasks(tasks);
        project.setAttachments(loadProjectAttachments(projectId));
        project.setSchedules(loadProjectSchedules(projectId));
        return project;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO createProject(ProjectBO.Create createBO) {
        Long currentUserId = UserUtil.getUserId();
        Long ownerId = createBO.getOwnerId() == null ? currentUserId : createBO.getOwnerId();
        ManagerUser owner = resolveUser(ownerId);

        Project project = new Project();
        project.setName(requireName(createBO.getName()));
        project.setDescription(normalizeOptional(createBO.getDescription()));
        project.setStatus(normalizeProjectStatus(createBO.getStatus()));
        project.setOwnerId(ownerId);
        project.setCustomerId(createBO.getCustomerId());
        project.setCustomerName(resolveCustomerName(createBO.getCustomerId(), createBO.getCustomerName()));
        project.setStartDate(createBO.getStartDate());
        project.setDueDate(createBO.getDueDate());
        project.setCreateUserId(currentUserId);
        project.setUpdateUserId(currentUserId);
        projectMapper.insert(project);

        insertLane(project.getProjectId(), "not-started", "未开始", 0, true, currentUserId);
        insertLane(project.getProjectId(), "in-progress", "进行中", 1, true, currentUserId);
        insertLane(project.getProjectId(), "completed", "已完成", 2, true, currentUserId);

        ProjectVO vo = BeanUtil.copyProperties(project, ProjectVO.class);
        vo.setOwnerName(StrUtil.blankToDefault(owner.getRealname(), owner.getUsername()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateProject(ProjectBO.Update updateBO) {
        Project project = getProjectEntity(updateBO.getProjectId());
        if (updateBO.getName() != null) {
            project.setName(requireName(updateBO.getName()));
        }
        if (updateBO.getDescription() != null) {
            project.setDescription(normalizeOptional(updateBO.getDescription()));
        }
        if (updateBO.getStatus() != null) {
            project.setStatus(normalizeProjectStatus(updateBO.getStatus()));
        }
        if (updateBO.getOwnerId() != null) {
            project.setOwnerId(resolveUser(updateBO.getOwnerId()).getUserId());
        }
        if (updateBO.getCustomerId() != null || updateBO.getCustomerName() != null) {
            project.setCustomerId(updateBO.getCustomerId());
            project.setCustomerName(resolveCustomerName(updateBO.getCustomerId(), updateBO.getCustomerName()));
        }
        if (updateBO.getStartDate() != null) {
            project.setStartDate(updateBO.getStartDate());
        }
        if (updateBO.getDueDate() != null) {
            project.setDueDate(updateBO.getDueDate());
        }
        project.setUpdateUserId(UserUtil.getUserId());
        projectMapper.updateById(project);
        return getProject(project.getProjectId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO archiveProject(Long projectId) {
        Project project = getProjectEntity(projectId);
        project.setStatus(STATUS_ARCHIVED);
        project.setUpdateUserId(UserUtil.getUserId());
        projectMapper.updateById(project);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO restoreProject(Long projectId) {
        Project project = getProjectEntity(projectId);
        project.setStatus(STATUS_NOT_STARTED);
        project.setUpdateUserId(UserUtil.getUserId());
        projectMapper.updateById(project);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addProjectAttachment(Long projectId, ProjectBO.ProjectAttachmentSave attachmentBO) {
        getProjectEntity(projectId);
        if (attachmentBO == null || StrUtil.isAllBlank(attachmentBO.getName(), attachmentBO.getFileUrl())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Attachment is required");
        }
        ProjectAttachment attachment = new ProjectAttachment();
        attachment.setProjectId(projectId);
        attachment.setName(StrUtil.blankToDefault(normalizeOptional(attachmentBO.getName()), normalizeOptional(attachmentBO.getFileUrl())));
        attachment.setFileUrl(normalizeOptional(attachmentBO.getFileUrl()));
        attachment.setCreateUserId(UserUtil.getUserId());
        attachment.setCreateUserName(currentUserDisplayName());
        projectAttachmentMapper.insert(attachment);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO deleteProjectAttachment(Long projectId, Long attachmentId) {
        ProjectAttachment attachment = projectAttachmentMapper.selectById(attachmentId);
        if (attachment == null || !Objects.equals(attachment.getProjectId(), projectId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Project attachment does not exist");
        }
        projectAttachmentMapper.deleteById(attachmentId);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addProjectSchedule(Long projectId, ProjectBO.ProjectScheduleSave scheduleBO) {
        getProjectEntity(projectId);
        if (scheduleBO == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Schedule is required");
        }
        ProjectSchedule schedule = new ProjectSchedule();
        schedule.setProjectId(projectId);
        schedule.setTitle(requireName(scheduleBO.getTitle()));
        schedule.setScheduleTime(scheduleBO.getScheduleTime());
        schedule.setCreateUserId(UserUtil.getUserId());
        schedule.setCreateUserName(currentUserDisplayName());
        projectScheduleMapper.insert(schedule);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO deleteProjectSchedule(Long projectId, Long scheduleId) {
        ProjectSchedule schedule = projectScheduleMapper.selectById(scheduleId);
        if (schedule == null || !Objects.equals(schedule.getProjectId(), projectId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Project schedule does not exist");
        }
        projectScheduleMapper.deleteById(scheduleId);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long projectId) {
        getProjectEntity(projectId);
        projectAttachmentMapper.delete(Wrappers.<ProjectAttachment>lambdaQuery()
                .eq(ProjectAttachment::getProjectId, projectId));
        projectScheduleMapper.delete(Wrappers.<ProjectSchedule>lambdaQuery()
                .eq(ProjectSchedule::getProjectId, projectId));
        projectTaskAttachmentMapper.delete(Wrappers.<ProjectTaskAttachment>lambdaQuery()
                .eq(ProjectTaskAttachment::getProjectId, projectId));
        projectTaskMapper.delete(Wrappers.<ProjectTask>lambdaQuery().eq(ProjectTask::getProjectId, projectId));
        projectLaneMapper.delete(Wrappers.<ProjectLane>lambdaQuery().eq(ProjectLane::getProjectId, projectId));
        projectMapper.deleteById(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addLane(Long projectId, ProjectBO.LaneSave laneBO) {
        getProjectEntity(projectId);
        Long currentUserId = UserUtil.getUserId();
        int sortOrder = nextLaneSortOrder(projectId);
        insertLane(projectId, null, laneBO.getName(), sortOrder, false, currentUserId);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateLane(Long projectId, ProjectBO.LaneSave laneBO) {
        getProjectEntity(projectId);
        ProjectLane lane = getProjectLane(projectId, laneBO.getLaneId());
        lane.setName(requireName(laneBO.getName()));
        lane.setUpdateUserId(UserUtil.getUserId());
        projectLaneMapper.updateById(lane);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO deleteLane(Long projectId, Long laneId) {
        getProjectEntity(projectId);
        ProjectLane lane = getProjectLane(projectId, laneId);
        if (Boolean.TRUE.equals(lane.getSystemFlag())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "System lane cannot be deleted");
        }
        Long taskCount = projectTaskMapper.selectCount(Wrappers.<ProjectTask>lambdaQuery()
                .eq(ProjectTask::getProjectId, projectId)
                .eq(ProjectTask::getLaneId, laneId));
        if (taskCount != null && taskCount > 0) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Lane has tasks");
        }
        projectLaneMapper.deleteById(laneId);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addTask(Long projectId, ProjectBO.TaskSave taskBO) {
        getProjectEntity(projectId);
        ProjectLane lane = taskBO.getLaneId() == null ? firstLane(projectId) : getProjectLane(projectId, taskBO.getLaneId());
        ProjectTask task = new ProjectTask();
        copyTaskFields(task, taskBO);
        task.setProjectId(projectId);
        task.setLaneId(lane.getLaneId());
        task.setStatus(TASK_STATUS_TODO);
        task.setPriority(normalizePriority(taskBO.getPriority()));
        task.setGeneratedByAi(Boolean.TRUE.equals(taskBO.getGeneratedByAi()));
        Long currentUserId = UserUtil.getUserId();
        task.setCreateUserId(currentUserId);
        task.setUpdateUserId(currentUserId);
        projectTaskMapper.insert(task);
        saveTaskAttachments(projectId, task.getTaskId(), taskBO.getAttachments());
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO updateTask(Long projectId, ProjectBO.TaskSave taskBO) {
        getProjectEntity(projectId);
        ProjectTask task = getProjectTask(projectId, taskBO.getTaskId());
        copyTaskFields(task, taskBO);
        if (taskBO.getLaneId() != null) {
            task.setLaneId(getProjectLane(projectId, taskBO.getLaneId()).getLaneId());
        }
        if (taskBO.getPriority() != null) {
            task.setPriority(normalizePriority(taskBO.getPriority()));
        }
        task.setUpdateUserId(UserUtil.getUserId());
        projectTaskMapper.updateById(task);
        saveTaskAttachments(projectId, task.getTaskId(), taskBO.getAttachments());
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO addTaskAttachment(Long projectId, Long taskId, ProjectBO.TaskAttachmentSave attachmentBO) {
        getProjectTask(projectId, taskId);
        saveTaskAttachment(projectId, taskId, attachmentBO);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO deleteTaskAttachment(Long projectId, Long taskId, Long attachmentId) {
        ProjectTaskAttachment attachment = getProjectTaskAttachment(projectId, taskId, attachmentId);
        projectTaskAttachmentMapper.deleteById(attachment.getAttachmentId());
        return getProject(projectId);
    }

    @Override
    public ProjectVO.ProjectTaskAttachmentVO getTaskAttachment(Long projectId, Long taskId, Long attachmentId) {
        return toTaskAttachmentVO(getProjectTaskAttachment(projectId, taskId, attachmentId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO deleteTask(Long projectId, Long taskId) {
        getProjectTask(projectId, taskId);
        projectTaskAttachmentMapper.delete(Wrappers.<ProjectTaskAttachment>lambdaQuery()
                .eq(ProjectTaskAttachment::getProjectId, projectId)
                .eq(ProjectTaskAttachment::getTaskId, taskId));
        projectTaskMapper.deleteById(taskId);
        return getProject(projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectVO moveTask(Long projectId, ProjectBO.TaskMove moveBO) {
        ProjectTask task = getProjectTask(projectId, moveBO.getTaskId());
        task.setLaneId(getProjectLane(projectId, moveBO.getLaneId()).getLaneId());
        if (isCompletedLane(task.getLaneId())) {
            task.setStatus(TASK_STATUS_COMPLETED);
        }
        task.setUpdateUserId(UserUtil.getUserId());
        projectTaskMapper.updateById(task);
        return getProject(projectId);
    }

    private void copyTaskFields(ProjectTask task, ProjectBO.TaskSave taskBO) {
        task.setTitle(requireName(taskBO.getTitle()));
        task.setDescription(normalizeOptional(taskBO.getDescription()));
        task.setDueDate(taskBO.getDueDate());
        task.setOwnerId(taskBO.getOwnerId());
        task.setOwnerName(normalizeOptional(taskBO.getOwnerName()));
        task.setCustomerId(taskBO.getCustomerId());
        task.setCustomerName(resolveCustomerName(taskBO.getCustomerId(), taskBO.getCustomerName()));
        task.setAiSourceText(normalizeOptional(taskBO.getAiSourceText()));
    }

    private List<ProjectVO.ProjectAttachmentVO> loadProjectAttachments(Long projectId) {
        return projectAttachmentMapper.selectList(Wrappers.<ProjectAttachment>lambdaQuery()
                        .eq(ProjectAttachment::getProjectId, projectId)
                        .orderByDesc(ProjectAttachment::getCreateTime)
                        .orderByDesc(ProjectAttachment::getAttachmentId))
                .stream()
                .map(this::toProjectAttachmentVO)
                .toList();
    }

    private List<ProjectVO.ProjectScheduleVO> loadProjectSchedules(Long projectId) {
        return projectScheduleMapper.selectList(Wrappers.<ProjectSchedule>lambdaQuery()
                        .eq(ProjectSchedule::getProjectId, projectId)
                        .orderByDesc(ProjectSchedule::getScheduleTime)
                        .orderByDesc(ProjectSchedule::getCreateTime)
                        .orderByDesc(ProjectSchedule::getScheduleId))
                .stream()
                .map(this::toProjectScheduleVO)
                .toList();
    }

    private ProjectVO.ProjectAttachmentVO toProjectAttachmentVO(ProjectAttachment attachment) {
        ProjectVO.ProjectAttachmentVO vo = new ProjectVO.ProjectAttachmentVO();
        vo.setAttachmentId(attachment.getAttachmentId());
        vo.setName(attachment.getName());
        vo.setFileUrl(attachment.getFileUrl());
        vo.setCreatedByName(attachment.getCreateUserName());
        vo.setCreateTime(attachment.getCreateTime());
        return vo;
    }

    private ProjectVO.ProjectScheduleVO toProjectScheduleVO(ProjectSchedule schedule) {
        ProjectVO.ProjectScheduleVO vo = new ProjectVO.ProjectScheduleVO();
        vo.setScheduleId(schedule.getScheduleId());
        vo.setTitle(schedule.getTitle());
        vo.setScheduleTime(schedule.getScheduleTime());
        vo.setCreatedByName(schedule.getCreateUserName());
        vo.setCreateTime(schedule.getCreateTime());
        return vo;
    }

    private void saveTaskAttachments(Long projectId, Long taskId, List<ProjectBO.TaskAttachmentSave> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        attachments.stream()
                .filter(Objects::nonNull)
                .forEach(attachment -> saveTaskAttachment(projectId, taskId, attachment));
    }

    private void saveTaskAttachment(Long projectId, Long taskId, ProjectBO.TaskAttachmentSave attachmentBO) {
        if (attachmentBO == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Attachment is required");
        }
        String filePath = normalizeOptional(attachmentBO.getFilePath());
        String name = normalizeOptional(attachmentBO.getFileName());
        if (StrUtil.isBlank(name)) {
            name = StrUtil.blankToDefault(filePath, "Attachment");
        }
        ProjectTaskAttachment attachment = new ProjectTaskAttachment();
        attachment.setProjectId(projectId);
        attachment.setTaskId(taskId);
        attachment.setName(name);
        attachment.setFilePath(filePath);
        attachment.setFileUrl(filePath);
        attachment.setFileSize(attachmentBO.getFileSize());
        attachment.setMimeType(StrUtil.blankToDefault(normalizeOptional(attachmentBO.getMimeType()), "application/octet-stream"));
        attachment.setCreateUserId(UserUtil.getUserId());
        attachment.setCreateUserName(currentUserDisplayName());
        projectTaskAttachmentMapper.insert(attachment);
        scheduleTaskAttachmentKnowledgeSync(projectId, taskId, name, filePath, attachment.getFileSize(), attachment.getMimeType());
    }

    private void scheduleTaskAttachmentKnowledgeSync(Long projectId, Long taskId, String fileName,
                                                     String filePath, Long fileSize, String mimeType) {
        if (StrUtil.isBlank(filePath)) {
            return;
        }
        Runnable sync = () -> syncTaskAttachmentToKnowledge(projectId, taskId, fileName, filePath, fileSize, mimeType);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sync.run();
                }
            });
            return;
        }
        sync.run();
    }

    private void syncTaskAttachmentToKnowledge(Long projectId, Long taskId, String fileName,
                                               String filePath, Long fileSize, String mimeType) {
        try {
            Project project = getProjectEntity(projectId);
            ProjectTask task = getProjectTask(projectId, taskId);
            Long customerId = firstNonNull(task.getCustomerId(), project.getCustomerId());
            String summary = "项目任务附件自动同步。项目：" + project.getName() + "；任务：" + task.getTitle();
            knowledgeService.archiveExistingStandaloneFile(fileName, filePath, fileSize, mimeType, customerId, summary);
        } catch (Exception e) {
            log.warn("项目任务附件同步知识库失败: projectId={}, taskId={}, fileName={}, error={}",
                    projectId, taskId, fileName, e.getMessage(), e);
        }
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    private void fillTaskAttachments(ProjectVO.ProjectTaskVO task) {
        List<ProjectVO.ProjectTaskAttachmentVO> attachments = projectTaskAttachmentMapper.selectList(
                        Wrappers.<ProjectTaskAttachment>lambdaQuery()
                                .eq(ProjectTaskAttachment::getTaskId, task.getTaskId())
                                .orderByDesc(ProjectTaskAttachment::getCreateTime)
                                .orderByDesc(ProjectTaskAttachment::getAttachmentId))
                .stream()
                .map(this::toTaskAttachmentVO)
                .toList();
        task.setAttachments(attachments);
        task.setHasAttachments(!attachments.isEmpty());
    }

    private ProjectVO.ProjectTaskAttachmentVO toTaskAttachmentVO(ProjectTaskAttachment attachment) {
        ProjectVO.ProjectTaskAttachmentVO vo = new ProjectVO.ProjectTaskAttachmentVO();
        vo.setAttachmentId(attachment.getAttachmentId());
        vo.setName(attachment.getName());
        vo.setFileUrl(attachment.getFileUrl());
        vo.setFilePath(attachment.getFilePath());
        vo.setFileSize(attachment.getFileSize());
        vo.setMimeType(attachment.getMimeType());
        vo.setCreatedByName(attachment.getCreateUserName());
        vo.setCreateTime(attachment.getCreateTime());
        return vo;
    }

    private void insertLane(Long projectId, String code, String name, int sortOrder, boolean systemFlag, Long userId) {
        ProjectLane lane = new ProjectLane();
        lane.setProjectId(projectId);
        lane.setCode(code);
        lane.setName(requireName(name));
        lane.setSortOrder(sortOrder);
        lane.setSystemFlag(systemFlag);
        lane.setCreateUserId(userId);
        lane.setUpdateUserId(userId);
        projectLaneMapper.insert(lane);
    }

    private Project getProjectEntity(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Project does not exist");
        }
        return project;
    }

    private ProjectLane getProjectLane(Long projectId, Long laneId) {
        ProjectLane lane = projectLaneMapper.selectById(laneId);
        if (lane == null || !Objects.equals(lane.getProjectId(), projectId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Project lane does not exist");
        }
        return lane;
    }

    private ProjectTask getProjectTask(Long projectId, Long taskId) {
        ProjectTask task = projectTaskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getProjectId(), projectId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Project task does not exist");
        }
        return task;
    }

    private ProjectTaskAttachment getProjectTaskAttachment(Long projectId, Long taskId, Long attachmentId) {
        ProjectTaskAttachment attachment = projectTaskAttachmentMapper.selectById(attachmentId);
        if (attachment == null
                || !Objects.equals(attachment.getProjectId(), projectId)
                || !Objects.equals(attachment.getTaskId(), taskId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Project task attachment does not exist");
        }
        return attachment;
    }

    private ProjectLane firstLane(Long projectId) {
        ProjectLane lane = projectLaneMapper.selectOne(Wrappers.<ProjectLane>lambdaQuery()
                .eq(ProjectLane::getProjectId, projectId)
                .orderByAsc(ProjectLane::getSortOrder)
                .last("LIMIT 1"));
        if (lane == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Project lane does not exist");
        }
        return lane;
    }

    private int nextLaneSortOrder(Long projectId) {
        ProjectLane latest = projectLaneMapper.selectOne(Wrappers.<ProjectLane>lambdaQuery()
                .eq(ProjectLane::getProjectId, projectId)
                .orderByDesc(ProjectLane::getSortOrder)
                .last("LIMIT 1"));
        return latest == null || latest.getSortOrder() == null ? 10 : latest.getSortOrder() + 10;
    }

    private boolean isCompletedLane(Long laneId) {
        ProjectLane lane = projectLaneMapper.selectById(laneId);
        return lane != null && ("completed".equals(lane.getCode()) || "已完成".equals(lane.getName()));
    }

    private ManagerUser resolveUser(Long userId) {
        ManagerUser user = manageUserMapper.getUserId(userId);
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Owner does not exist");
        }
        return user;
    }

    private String resolveCustomerName(Long customerId, String fallback) {
        if (customerId == null) {
            return normalizeOptional(fallback);
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null || Integer.valueOf(0).equals(customer.getStatus())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Customer does not exist");
        }
        return StrUtil.blankToDefault(customer.getCompanyName(), fallback);
    }

    private ProjectVO.ProjectLaneVO toLaneVO(ProjectLane lane) {
        ProjectVO.ProjectLaneVO vo = new ProjectVO.ProjectLaneVO();
        vo.setLaneId(lane.getLaneId());
        vo.setName(lane.getName());
        vo.setCode(lane.getCode());
        vo.setSortOrder(lane.getSortOrder());
        vo.setSystem(Boolean.TRUE.equals(lane.getSystemFlag()));
        return vo;
    }

    private String requireName(String value) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Name is required");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (StrUtil.isBlank(value) || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private String currentUserDisplayName() {
        try {
            ManagerUser user = UserUtil.getLoginUser().getUser();
            return StrUtil.blankToDefault(user.getRealname(), user.getUsername());
        } catch (Exception ignored) {
            return String.valueOf(UserUtil.getUserId());
        }
    }

    private String normalizeProjectStatus(String status) {
        String normalized = StrUtil.blankToDefault(status, STATUS_NOT_STARTED).trim().toUpperCase(Locale.ROOT).replace("-", "_");
        return Set.of(STATUS_NOT_STARTED, STATUS_IN_PROGRESS, STATUS_COMPLETED, "PAUSED", STATUS_ARCHIVED).contains(normalized)
                ? normalized
                : STATUS_NOT_STARTED;
    }

    private String normalizeProjectQueryStatus(String status) {
        if (StrUtil.isBlank(status) || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return normalizeProjectStatus(status);
    }

    private String normalizePriority(String priority) {
        String normalized = StrUtil.blankToDefault(priority, PRIORITY_MEDIUM).trim().toUpperCase(Locale.ROOT);
        return Set.of("LOW", PRIORITY_MEDIUM, "HIGH", "URGENT").contains(normalized) ? normalized : PRIORITY_MEDIUM;
    }
}
