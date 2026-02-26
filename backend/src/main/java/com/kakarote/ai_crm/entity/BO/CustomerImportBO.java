package com.kakarote.ai_crm.entity.BO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 客户导入行数据
 */
@Data
@Schema(name = "CustomerImportBO", description = "客户导入行数据")
public class CustomerImportBO {

    @Schema(description = "Excel行号")
    private int rowNum;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "行业")
    private String industry;

    @Schema(description = "商机阶段")
    private String stage;

    @Schema(description = "客户级别")
    private String level;

    @Schema(description = "客户来源")
    private String source;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "网站")
    private String website;

    @Schema(description = "报价金额")
    private BigDecimal quotation;

    @Schema(description = "备注")
    private String remark;

    // 联系人信息
    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系人职位")
    private String contactPosition;

    @Schema(description = "联系人电话")
    private String contactPhone;

    @Schema(description = "联系人邮箱")
    private String contactEmail;

    @Schema(description = "联系人微信")
    private String contactWechat;

    // 自定义字段
    @Schema(description = "自定义字段值")
    private Map<String, Object> customFields;

    // 重复检测
    @Schema(description = "是否检测到重复")
    private boolean duplicate;

    @Schema(description = "重复时的现有客户ID")
    private Long existingCustomerId;

    @Schema(description = "重复处理方式: skip / overwrite")
    private String handleMode;

    // 验证错误
    @Schema(description = "该行的验证错误信息")
    private List<String> errors;
}
