package com.kakarote.ai_crm.entity.BO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(name = "SessionPinBO", description = "Session pin parameters")
public class SessionPinBO {

    @NotNull(message = "Pinned status is required")
    @Schema(description = "Whether the session is pinned", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean pinned;
}
