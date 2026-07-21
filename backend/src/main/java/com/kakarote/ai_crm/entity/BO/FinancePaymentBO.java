package com.kakarote.ai_crm.entity.BO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
public class FinancePaymentBO {

    private Long paymentId;
    private Long receivableId;
    private Long contractId;
    private Long customerId;
    private Long projectId;
    private Long ownerId;
    @NotNull(message = "回款金额不能为空")
    @DecimalMin(value = "0.01", message = "回款金额必须大于0")
    private BigDecimal amount;
    @NotNull(message = "回款日期不能为空")
    private Date paymentDate;
    private String paymentMethod;
    private String remark;
    private String sourceType;
    private String sourceText;
    private Boolean aiCreated;
    private Map<String, Object> customFields;
}
