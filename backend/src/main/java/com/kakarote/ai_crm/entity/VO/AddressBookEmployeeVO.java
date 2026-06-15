package com.kakarote.ai_crm.entity.VO;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "通讯录员工列表项")
public class AddressBookEmployeeVO {

    @Schema(description = "员工ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "姓名")
    private String realname;

    @Schema(description = "头像objectKey")
    private String img;

    @Schema(description = "头像访问URL")
    private String imgUrl;

    @Schema(description = "部门ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deptId;

    @Schema(description = "部门")
    private String deptName;

    @Schema(description = "职位")
    private String post;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "员工状态")
    private String employeeStatus;

    @Schema(description = "员工状态名称")
    private String employeeStatusName;

    @Schema(description = "最近相关任务时间")
    private Date recentTaskTime;
}
