package com.kakarote.ai_crm.entity.BO;

import com.kakarote.ai_crm.common.PageEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ProjectBO {

    @Data
    public static class Query extends PageEntity {
        private String keyword;
        private String status;
        private Boolean includeArchived;
    }

    @Data
    public static class Create {
        @NotBlank(message = "Project name is required")
        private String name;
        private String description;
        private Long customerId;
        private String customerName;
        private Long ownerId;
        private String ownerName;
        private String ownerAccount;
        private String ownerDeptName;
        private Date startDate;
        private Date dueDate;
        private String status;
    }

    @Data
    public static class Update extends Create {
        @NotNull(message = "Project ID is required")
        private Long projectId;
    }

    @Data
    public static class LaneSave {
        private Long laneId;

        @NotBlank(message = "Lane name is required")
        private String name;
    }

    @Data
    public static class TaskSave {
        private Long taskId;

        @NotBlank(message = "Task title is required")
        private String title;

        private String description;
        private Long laneId;
        private Date dueDate;
        private Long ownerId;
        private String ownerName;
        private List<Long> participantIds;
        private List<String> participantNames;
        private String priority;
        private Long customerId;
        private String customerName;
        private Boolean hasAttachments;
        private Boolean hasSchedule;
        private Boolean generatedByAi;
        private String aiSourceText;
        private List<TaskAttachmentSave> attachments;
    }

    @Data
    public static class TaskAttachmentSave {
        private String fileName;
        private String filePath;
        private Long fileSize;
        private String mimeType;
    }

    @Data
    public static class ProjectAttachmentSave {
        private String name;
        private String fileUrl;
    }

    @Data
    public static class ProjectScheduleSave {
        @NotBlank(message = "Schedule title is required")
        private String title;
        private Date scheduleTime;
    }

    @Data
    public static class TaskMove {
        @NotNull(message = "Task ID is required")
        private Long taskId;

        @NotNull(message = "Lane ID is required")
        private Long laneId;
    }

    @Data
    public static class MemberSave {
        @NotNull(message = "Member user is required")
        private Long userId;

        @NotBlank(message = "Member name is required")
        private String memberName;

        @NotBlank(message = "Member account is required")
        private String account;

        @NotBlank(message = "Project role is required")
        private String role;

        private String deptName;
        private List<String> permissions;
        private String remark;
        private String status;
    }

    @Data
    public static class MemberRole {
        @NotNull(message = "Member user is required")
        private Long userId;

        @NotBlank(message = "Project role is required")
        private String role;
    }

    @Data
    public static class MemberPermissions {
        @NotNull(message = "Member user is required")
        private Long userId;

        private List<String> permissions;
    }

    @Data
    public static class MemberStatus {
        @NotNull(message = "Member user is required")
        private Long userId;

        @NotBlank(message = "Member status is required")
        private String status;
    }

    @Data
    public static class RolePermissionConfig {
        private Map<String, List<String>> rolePermissions;
    }

    @Data
    public static class AiCommand {
        @NotBlank(message = "Command content is required")
        private String content;
        private List<TaskAttachmentSave> attachments;
        private List<String> knowledgeIds;
        private String modelProvider;
        private String modelName;
        private String modelSource;
    }
}
