package com.kakarote.ai_crm.entity.VO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
public class FinanceRecordVO {

    private Long id;
    private Long contractId;
    private Long receivableId;
    private Long paymentId;
    private Long invoiceId;
    private Long expenseId;
    private String contractNo;
    private String contractName;
    private String title;
    private String invoiceNo;
    private String taxNo;
    private String expenseType;
    private Long customerId;
    private String customerName;
    private Long projectId;
    private String projectName;
    private Long ownerId;
    private String ownerName;
    private BigDecimal amount;
    private BigDecimal receivedAmount;
    private BigDecimal unpaidAmount;
    private Date signDate;
    private Date startDate;
    private Date endDate;
    private Date dueDate;
    private Integer overdueDays;
    private Date paymentDate;
    private String paymentMethod;
    private Date invoiceDate;
    private Date expenseDate;
    private String status;
    private String remark;
    private String sourceType;
    private String sourceText;
    private Boolean aiCreated;
    private Long createUserId;
    private String createUserName;
    private Date createTime;
    private Date updateTime;
    private Map<String, Object> customFields;
}
