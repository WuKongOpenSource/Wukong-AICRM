package com.kakarote.ai_crm.entity.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "通讯录员工最近记录")
public class AddressBookRecentRecordVO {

    @Schema(description = "记录类型: task/schedule/attachment")
    private String type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "记录时间")
    private Date recordTime;
}
