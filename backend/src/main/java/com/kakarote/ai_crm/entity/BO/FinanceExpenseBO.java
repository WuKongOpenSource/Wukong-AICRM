package com.kakarote.ai_crm.entity.BO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
public class FinanceExpenseBO {

    private Long expenseId;
    private Long customerId;
    private Long projectId;
    private Long ownerId;
    @NotBlank(message = "费用类型不能为空")
    private String expenseType;
    @NotNull(message = "费用金额不能为空")
    @DecimalMin(value = "0.01", message = "费用金额必须大于0")
    private BigDecimal amount;
    @NotNull(message = "费用日期不能为空")
    private Date expenseDate;
    private String status;
    private String remark;
    private String sourceType;
    private String sourceText;
    private Boolean aiCreated;
    private Map<String, Object> customFields;
}
