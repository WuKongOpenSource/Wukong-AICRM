package com.kakarote.ai_crm.entity.PO;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 聊天消息表
 */
@Data
@TableName("crm_chat_message")
@Schema(name = "ChatMessage", description = "聊天消息表")
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "消息ID")
    private Long messageId;

    /**
     * 会话ID
     */
    @Schema(description = "会话ID")
    private Long sessionId;

    /**
     * 角色: user, assistant, system
     */
    @Schema(description = "角色: user, assistant, system")
    private String role;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容")
    private String content;

    /**
     * 使用token数
     */
    @Schema(description = "使用token数")
    private Integer tokensUsed;

    /**
     * 输入token数
     */
    @Schema(description = "输入token数")
    private Integer promptTokens;

    /**
     * 输出token数
     */
    @Schema(description = "输出token数")
    private Integer completionTokens;

    /**
     * 使用的模型
     */
    @Schema(description = "使用的模型")
    private String modelName;

    @Schema(description = "扣除积分数")
    private Long creditsUsed;

    @Schema(description = "积分倍率")
    private BigDecimal creditMultiplier;

    @Schema(description = "计费模型服务商")
    private String billingModelProvider;

    @Schema(description = "计费模型名称")
    private String billingModelName;

    /**
     * 函数调用(JSON)
     */
    @Schema(description = "函数调用(JSON)")
    private String functionCall;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private Date createTime;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "租户ID")
    private Long tenantId;
}
