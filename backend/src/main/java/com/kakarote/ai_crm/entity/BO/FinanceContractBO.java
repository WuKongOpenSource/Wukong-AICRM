package com.kakarote.ai_crm.entity.BO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class FinanceContractBO {

    private Long contractId;
    private String contractNo;
    @NotBlank(message = "合同名称不能为空")
    private String contractName;
    private Long customerId;
    private Long projectId;
    private Long ownerId;
    @NotNull(message = "合同金额不能为空")
    @DecimalMin(value = "0.01", message = "合同金额必须大于0")
    private BigDecimal amount;
    private Date signDate;
    private Date startDate;
    private Date endDate;
    private String status;
    private String remark;
    private String sourceType;
    private String sourceText;
    private Boolean aiCreated;
    private List<ReceivablePlan> receivablePlans;
    private Map<String, Object> customFields;

    @Data
    public static class ReceivablePlan {
        private String title;
        @NotNull(message = "应收金额不能为空")
        @DecimalMin(value = "0.01", message = "应收金额必须大于0")
        private BigDecimal amount;
        private Date dueDate;
        private String remark;
    }
}
