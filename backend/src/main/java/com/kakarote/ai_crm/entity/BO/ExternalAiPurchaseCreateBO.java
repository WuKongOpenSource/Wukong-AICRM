package com.kakarote.ai_crm.entity.BO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExternalAiPurchaseCreateBO {

    @NotBlank(message = "purchase plan is required")
    private String planId;

    @NotBlank(message = "payment channel is required")
    private String paymentChannel;
}
