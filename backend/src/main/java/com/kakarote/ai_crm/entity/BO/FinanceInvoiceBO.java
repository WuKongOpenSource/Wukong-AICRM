package com.kakarote.ai_crm.entity.BO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
public class FinanceInvoiceBO {

    private Long invoiceId;
    private Long contractId;
    private Long receivableId;
    private Long customerId;
    private Long projectId;
    private Long ownerId;
    private String invoiceNo;
    @NotBlank(message = "发票抬头不能为空")
    private String title;
    private String taxNo;
    @NotNull(message = "开票金额不能为空")
    @DecimalMin(value = "0.01", message = "开票金额必须大于0")
    private BigDecimal amount;
    private Date invoiceDate;
    private String status;
    private String remark;
    private String sourceType;
    private String sourceText;
    private Boolean aiCreated;
    private Map<String, Object> customFields;
}
