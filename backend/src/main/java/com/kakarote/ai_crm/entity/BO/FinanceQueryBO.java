package com.kakarote.ai_crm.entity.BO;

import com.kakarote.ai_crm.common.PageEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceQueryBO extends PageEntity {

    private String keyword;
    private Long customerId;
    private Long projectId;
    private Long ownerId;
    private String status;
    private Boolean overdue;
    private Date startDate;
    private Date endDate;
}
