package com.kakarote.ai_crm.entity.BO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
public class FinanceReceivableBO {

    private Long receivableId;
    private Long contractId;
    private Long customerId;
    private Long projectId;
    private Long ownerId;
    @NotBlank(message = "应收标题不能为空")
    private String title;
    @NotNull(message = "应收金额不能为空")
    @DecimalMin(value = "0.01", message = "应收金额必须大于0")
    private BigDecimal amount;
    private Date dueDate;
    private String status;
    private String remark;
    private String sourceType;
    private String sourceText;
    private Boolean aiCreated;
    private Map<String, Object> customFields;
}
