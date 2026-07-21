package com.kakarote.ai_crm.entity.VO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class FinanceDashboardVO {

    private BigDecimal monthIncome = BigDecimal.ZERO;
    private BigDecimal monthExpense = BigDecimal.ZERO;
    private BigDecimal netCashFlow = BigDecimal.ZERO;
    private BigDecimal receivable30 = BigDecimal.ZERO;
    private BigDecimal receivable60 = BigDecimal.ZERO;
    private BigDecimal receivable90 = BigDecimal.ZERO;
    private BigDecimal overdueReceivable = BigDecimal.ZERO;
    private List<CashFlowPoint> cashFlow = new ArrayList<>();
    private List<FinanceRecordVO> recentRecords = new ArrayList<>();

    @Data
    public static class CashFlowPoint {
        private String month;
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
        private BigDecimal net = BigDecimal.ZERO;
    }
}
