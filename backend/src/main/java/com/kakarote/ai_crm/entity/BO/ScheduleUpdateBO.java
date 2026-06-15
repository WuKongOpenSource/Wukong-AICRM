package com.kakarote.ai_crm.entity.BO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Schema(name = "ScheduleUpdateBO", description = "Schedule update request")
public class ScheduleUpdateBO {

    @NotNull(message = "Schedule ID is required")
    @Schema(description = "Schedule ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long scheduleId;

    @NotBlank(message = "Title is required")
    @Schema(description = "Title", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "Description")
    private String description;

    @NotNull(message = "Start time is required")
    @Schema(description = "Start time", requiredMode = Schema.RequiredMode.REQUIRED)
    private Date startTime;

    @Schema(description = "End time")
    private Date endTime;

    @Schema(description = "Type: meeting, call, visit, other")
    private String type;

    @Schema(description = "Customer ID")
    private Long customerId;

    @Schema(description = "Relation ID")
    private Long relationId;

    @Schema(description = "Contact ID")
    private Long contactId;

    @Schema(description = "Location")
    private String location;

    @Schema(description = "Participant user IDs")
    private List<Long> participantUserIds;
}
