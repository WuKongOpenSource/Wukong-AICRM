package com.kakarote.ai_crm.entity.PO;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("crm_ai_model_pricing")
public class AiModelPricing implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String provider;

    private String modelName;

    private String displayName;

    private BigDecimal creditMultiplier;

    private Boolean enabled;

    private Integer sortOrder;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
