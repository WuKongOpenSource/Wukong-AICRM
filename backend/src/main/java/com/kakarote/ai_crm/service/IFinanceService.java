package com.kakarote.ai_crm.service;

import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.entity.BO.FinanceContractBO;
import com.kakarote.ai_crm.entity.BO.FinanceExpenseBO;
import com.kakarote.ai_crm.entity.BO.FinanceInvoiceBO;
import com.kakarote.ai_crm.entity.BO.FinancePaymentBO;
import com.kakarote.ai_crm.entity.BO.FinanceQueryBO;
import com.kakarote.ai_crm.entity.BO.FinanceReceivableBO;
import com.kakarote.ai_crm.entity.VO.FinanceDashboardVO;
import com.kakarote.ai_crm.entity.VO.FinanceRecordVO;

public interface IFinanceService {

    FinanceDashboardVO dashboard(FinanceQueryBO query);

    Long addContract(FinanceContractBO bo);
    void updateContract(FinanceContractBO bo);
    void deleteContract(Long contractId);
    BasePage<FinanceRecordVO> queryContracts(FinanceQueryBO query);
    FinanceRecordVO getContractDetail(Long contractId);

    Long addReceivable(FinanceReceivableBO bo);
    void updateReceivable(FinanceReceivableBO bo);
    void deleteReceivable(Long receivableId);
    BasePage<FinanceRecordVO> queryReceivables(FinanceQueryBO query);
    FinanceRecordVO getReceivableDetail(Long receivableId);

    Long addPayment(FinancePaymentBO bo);
    void updatePayment(FinancePaymentBO bo);
    void deletePayment(Long paymentId);
    BasePage<FinanceRecordVO> queryPayments(FinanceQueryBO query);
    FinanceRecordVO getPaymentDetail(Long paymentId);

    Long addInvoice(FinanceInvoiceBO bo);
    void updateInvoice(FinanceInvoiceBO bo);
    void deleteInvoice(Long invoiceId);
    BasePage<FinanceRecordVO> queryInvoices(FinanceQueryBO query);
    FinanceRecordVO getInvoiceDetail(Long invoiceId);

    Long addExpense(FinanceExpenseBO bo);
    void updateExpense(FinanceExpenseBO bo);
    void deleteExpense(Long expenseId);
    BasePage<FinanceRecordVO> queryExpenses(FinanceQueryBO query);
    FinanceRecordVO getExpenseDetail(Long expenseId);
}
