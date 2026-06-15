package com.kakarote.ai_crm.entity.VO;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "通讯录员工详情")
public class AddressBookDetailVO extends AddressBookEmployeeVO {

    @Schema(description = "直属上级ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "直属上级")
    private String parentName;

    @Schema(description = "相关任务")
    private List<TaskVO> relatedTasks = new ArrayList<>();

    @Schema(description = "相关日程")
    private List<ScheduleVO> relatedSchedules = new ArrayList<>();

    @Schema(description = "相关附件")
    private List<KnowledgeVO> relatedAttachments = new ArrayList<>();

    @Schema(description = "最近记录")
    private List<AddressBookRecentRecordVO> recentRecords = new ArrayList<>();
}
