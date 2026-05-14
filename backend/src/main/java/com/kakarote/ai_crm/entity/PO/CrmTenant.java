package com.kakarote.ai_crm.entity.PO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("crm_tenant")
@Schema(name = "CrmTenant", description = "租户表")
public class CrmTenant implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "tenant_id", type = IdType.ASSIGN_ID)
    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "租户名称")
    private String tenantName;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    private String contactEmail;

    @Schema(description = "状态：0=禁用, 1=正常, 2=试用")
    private Integer status;

    @Schema(description = "到期时间")
    private Date expireTime;

    @Schema(description = "最大用户数")
    private Integer maxUsers;

    @Schema(description = "备注")
    private String remark;

    @TableField("gift_credit_total")
    @Schema(description = "注册赠送积分总量")
    private Long giftCreditTotal;

    @TableField("gift_credit_used")
    @Schema(description = "赠送积分已使用量")
    private Long giftCreditUsed;

    @TableField("purchased_credit_total")
    private Long purchasedCreditTotal;

    @TableField("purchased_credit_used")
    private Long purchasedCreditUsed;

    @TableField("weknora_api_key")
    @Schema(description = "WeKnora 租户 API Key")
    private String weKnoraApiKey;

    @TableField("weknora_knowledge_base_id")
    @Schema(description = "WeKnora 知识库ID")
    private String weKnoraKnowledgeBaseId;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
