package com.kakarote.ai_crm.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.entity.BO.FinanceQueryBO;
import com.kakarote.ai_crm.entity.PO.FinanceContract;
import com.kakarote.ai_crm.entity.PO.FinanceExpense;
import com.kakarote.ai_crm.entity.PO.FinanceInvoice;
import com.kakarote.ai_crm.entity.PO.FinancePayment;
import com.kakarote.ai_crm.entity.PO.FinanceReceivable;
import com.kakarote.ai_crm.entity.VO.FinanceDashboardVO;
import com.kakarote.ai_crm.entity.VO.FinanceRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Mapper
public interface FinanceMapper {

    void insertContract(FinanceContract record);
    void updateContract(FinanceContract record);
    FinanceContract selectContractById(@Param("contractId") Long contractId);
    BasePage<FinanceRecordVO> queryContracts(IPage<FinanceRecordVO> page, @Param("query") FinanceQueryBO query);
    FinanceRecordVO getContractDetail(@Param("contractId") Long contractId);

    void insertReceivable(FinanceReceivable record);
    void updateReceivable(FinanceReceivable record);
    FinanceReceivable selectReceivableById(@Param("receivableId") Long receivableId);
    FinanceReceivable selectEarliestOpenReceivable(@Param("customerId") Long customerId,
                                                   @Param("contractId") Long contractId);
    BasePage<FinanceRecordVO> queryReceivables(IPage<FinanceRecordVO> page, @Param("query") FinanceQueryBO query);
    FinanceRecordVO getReceivableDetail(@Param("receivableId") Long receivableId);

    void insertPayment(FinancePayment record);
    void updatePayment(FinancePayment record);
    FinancePayment selectPaymentById(@Param("paymentId") Long paymentId);
    BasePage<FinanceRecordVO> queryPayments(IPage<FinanceRecordVO> page, @Param("query") FinanceQueryBO query);
    FinanceRecordVO getPaymentDetail(@Param("paymentId") Long paymentId);

    void insertInvoice(FinanceInvoice record);
    void updateInvoice(FinanceInvoice record);
    FinanceInvoice selectInvoiceById(@Param("invoiceId") Long invoiceId);
    BasePage<FinanceRecordVO> queryInvoices(IPage<FinanceRecordVO> page, @Param("query") FinanceQueryBO query);
    FinanceRecordVO getInvoiceDetail(@Param("invoiceId") Long invoiceId);

    void insertExpense(FinanceExpense record);
    void updateExpense(FinanceExpense record);
    FinanceExpense selectExpenseById(@Param("expenseId") Long expenseId);
    BasePage<FinanceRecordVO> queryExpenses(IPage<FinanceRecordVO> page, @Param("query") FinanceQueryBO query);
    FinanceRecordVO getExpenseDetail(@Param("expenseId") Long expenseId);

    void softDelete(@Param("table") String table, @Param("idColumn") String idColumn, @Param("id") Long id,
                    @Param("userId") Long userId);

    BigDecimal sumPayments(@Param("startDate") Date startDate, @Param("endDate") Date endDate);
    BigDecimal sumExpenses(@Param("startDate") Date startDate, @Param("endDate") Date endDate);
    BigDecimal sumReceivablesDueBetween(@Param("startDate") Date startDate, @Param("endDate") Date endDate);
    BigDecimal sumOverdueReceivables(@Param("today") Date today);
    List<FinanceRecordVO> queryRecentRecords(@Param("limit") Integer limit);
    List<FinanceDashboardVO.CashFlowPoint> queryCashFlow(@Param("startDate") Date startDate,
                                                         @Param("endDate") Date endDate);
}
