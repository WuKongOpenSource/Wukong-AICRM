package com.kakarote.ai_crm.entity.VO;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AiModelOptionVO implements Serializable {

    private String provider;

    private String providerLabel;

    private String modelName;

    private String displayName;

    private BigDecimal creditMultiplier;
}
