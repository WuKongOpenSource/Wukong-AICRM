package com.kakarote.ai_crm.entity.BO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "SessionPinBO", description = "会话置顶参数")
public class SessionPinBO {

    @NotNull(message = "置顶状态不能为空")
    @Schema(description = "是否置顶会话", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean pinned;
}
