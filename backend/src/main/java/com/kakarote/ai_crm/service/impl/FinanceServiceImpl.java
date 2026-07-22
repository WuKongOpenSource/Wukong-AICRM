package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.FinanceContractBO;
import com.kakarote.ai_crm.entity.BO.FinanceExpenseBO;
import com.kakarote.ai_crm.entity.BO.FinanceInvoiceBO;
import com.kakarote.ai_crm.entity.BO.FinancePaymentBO;
import com.kakarote.ai_crm.entity.BO.FinanceQueryBO;
import com.kakarote.ai_crm.entity.BO.FinanceReceivableBO;
import com.kakarote.ai_crm.entity.PO.FinanceContract;
import com.kakarote.ai_crm.entity.PO.FinanceExpense;
import com.kakarote.ai_crm.entity.PO.FinanceInvoice;
import com.kakarote.ai_crm.entity.PO.FinancePayment;
import com.kakarote.ai_crm.entity.PO.FinanceReceivable;
import com.kakarote.ai_crm.entity.VO.FinanceDashboardVO;
import com.kakarote.ai_crm.entity.VO.FinanceRecordVO;
import com.kakarote.ai_crm.mapper.FinanceMapper;
import com.kakarote.ai_crm.service.ICustomFieldService;
import com.kakarote.ai_crm.service.IFinanceService;
import com.kakarote.ai_crm.utils.UserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class FinanceServiceImpl implements IFinanceService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PARTIAL = "partial";
    private static final String STATUS_PAID = "paid";
    private static final String STATUS_ISSUED = "issued";
    private static final String STATUS_RECORDED = "recorded";
    private static final String SOURCE_MANUAL = "manual";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Autowired
    private FinanceMapper financeMapper;

    @Autowired
    private ICustomFieldService customFieldService;

    @Override
    public FinanceDashboardVO dashboard(FinanceQueryBO query) {
        LocalDate today = LocalDate.now();
        Date monthStart = toDate(today.with(TemporalAdjusters.firstDayOfMonth()));
        Date monthEnd = toDate(today.with(TemporalAdjusters.lastDayOfMonth()));
        Date chartStart = toDate(today.minusMonths(5).with(TemporalAdjusters.firstDayOfMonth()));
        Date chartEnd = monthEnd;

        FinanceDashboardVO vo = new FinanceDashboardVO();
        vo.setMonthIncome(nvl(financeMapper.sumPayments(monthStart, monthEnd)));
        vo.setMonthExpense(nvl(financeMapper.sumExpenses(monthStart, monthEnd)));
        vo.setNetCashFlow(vo.getMonthIncome().subtract(vo.getMonthExpense()));
        vo.setReceivable30(nvl(financeMapper.sumReceivablesDueBetween(toDate(today), toDate(today.plusDays(30)))));
        vo.setReceivable60(nvl(financeMapper.sumReceivablesDueBetween(toDate(today.plusDays(31)), toDate(today.plusDays(60)))));
        vo.setReceivable90(nvl(financeMapper.sumReceivablesDueBetween(toDate(today.plusDays(61)), toDate(today.plusDays(90)))));
        vo.setOverdueReceivable(nvl(financeMapper.sumOverdueReceivables(toDate(today))));
        vo.setCashFlow(financeMapper.queryCashFlow(chartStart, chartEnd));
        vo.setRecentRecords(financeMapper.queryRecentRecords(8));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addContract(FinanceContractBO bo) {
        FinanceContract contract = new FinanceContract();
        contract.setContractId(IdWorker.getId());
        applyContract(contract, bo);
        contract.setCreateUserId(currentUserId());
        contract.setUpdateUserId(currentUserId());
        contract.setDelFlag(0);
        financeMapper.insertContract(contract);
        saveCustomFields("finance_contract", contract.getContractId(), bo.getCustomFields());

        if (bo.getReceivablePlans() == null || bo.getReceivablePlans().isEmpty()) {
            FinanceReceivableBO receivableBO = new FinanceReceivableBO();
            receivableBO.setContractId(contract.getContractId());
            receivableBO.setCustomerId(contract.getCustomerId());
            receivableBO.setProjectId(contract.getProjectId());
            receivableBO.setOwnerId(contract.getOwnerId());
            receivableBO.setTitle(contract.getContractName() + " 应收");
            receivableBO.setAmount(contract.getAmount());
            receivableBO.setDueDate(contract.getEndDate() != null ? contract.getEndDate() : contract.getSignDate());
            receivableBO.setRemark("合同创建自动生成");
            receivableBO.setSourceType(contract.getSourceType());
            receivableBO.setSourceText(contract.getSourceText());
            receivableBO.setAiCreated(contract.getAiCreated());
            addReceivable(receivableBO);
        } else {
            for (FinanceContractBO.ReceivablePlan plan : bo.getReceivablePlans()) {
                FinanceReceivableBO receivableBO = new FinanceReceivableBO();
                receivableBO.setContractId(contract.getContractId());
                receivableBO.setCustomerId(contract.getCustomerId());
                receivableBO.setProjectId(contract.getProjectId());
                receivableBO.setOwnerId(contract.getOwnerId());
                receivableBO.setTitle(StrUtil.blankToDefault(plan.getTitle(), contract.getContractName() + " 应收"));
                receivableBO.setAmount(plan.getAmount());
                receivableBO.setDueDate(plan.getDueDate());
                receivableBO.setRemark(plan.getRemark());
                receivableBO.setSourceType(contract.getSourceType());
                receivableBO.setSourceText(contract.getSourceText());
                receivableBO.setAiCreated(contract.getAiCreated());
                addReceivable(receivableBO);
            }
        }
        return contract.getContractId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContract(FinanceContractBO bo) {
        FinanceContract contract = financeMapper.selectContractById(requireId(bo.getContractId(), "合同ID不能为空"));
        ensureFound(contract, "合同不存在");
        applyContract(contract, bo);
        contract.setUpdateUserId(currentUserId());
        financeMapper.updateContract(contract);
        saveCustomFields("finance_contract", contract.getContractId(), bo.getCustomFields());
    }

    @Override
    public void deleteContract(Long contractId) {
        financeMapper.softDelete("crm_finance_contract", "contract_id", contractId, currentUserId());
    }

    @Override
    public BasePage<FinanceRecordVO> queryContracts(FinanceQueryBO query) {
        FinanceQueryBO normalizedQuery = normalizeQuery(query);
        return attachCustomFields(financeMapper.queryContracts(normalizedQuery.parse(), normalizedQuery), "finance_contract");
    }

    @Override
    public FinanceRecordVO getContractDetail(Long contractId) {
        return attachCustomFields(financeMapper.getContractDetail(contractId), "finance_contract");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addReceivable(FinanceReceivableBO bo) {
        FinanceReceivable receivable = new FinanceReceivable();
        receivable.setReceivableId(IdWorker.getId());
        applyReceivable(receivable, bo);
        receivable.setReceivedAmount(ZERO);
        receivable.setCreateUserId(currentUserId());
        receivable.setUpdateUserId(currentUserId());
        receivable.setDelFlag(0);
        financeMapper.insertReceivable(receivable);
        saveCustomFields("finance_receivable", receivable.getReceivableId(), bo.getCustomFields());
        return receivable.getReceivableId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReceivable(FinanceReceivableBO bo) {
        FinanceReceivable receivable = financeMapper.selectReceivableById(requireId(bo.getReceivableId(), "应收ID不能为空"));
        ensureFound(receivable, "应收不存在");
        BigDecimal receivedAmount = receivable.getReceivedAmount();
        applyReceivable(receivable, bo);
        receivable.setReceivedAmount(receivedAmount == null ? ZERO : receivedAmount);
        refreshReceivableStatus(receivable);
        receivable.setUpdateUserId(currentUserId());
        financeMapper.updateReceivable(receivable);
        saveCustomFields("finance_receivable", receivable.getReceivableId(), bo.getCustomFields());
    }

    @Override
    public void deleteReceivable(Long receivableId) {
        financeMapper.softDelete("crm_finance_receivable", "receivable_id", receivableId, currentUserId());
    }

    @Override
    public BasePage<FinanceRecordVO> queryReceivables(FinanceQueryBO query) {
        FinanceQueryBO normalizedQuery = normalizeQuery(query);
        return attachCustomFields(financeMapper.queryReceivables(normalizedQuery.parse(), normalizedQuery), "finance_receivable");
    }

    @Override
    public FinanceRecordVO getReceivableDetail(Long receivableId) {
        return attachCustomFields(financeMapper.getReceivableDetail(receivableId), "finance_receivable");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addPayment(FinancePaymentBO bo) {
        FinancePayment payment = new FinancePayment();
        payment.setPaymentId(IdWorker.getId());
        FinanceReceivable receivable = resolveReceivableForPayment(bo);
        applyPayment(payment, bo, receivable);
        payment.setCreateUserId(currentUserId());
        payment.setUpdateUserId(currentUserId());
        payment.setDelFlag(0);
        financeMapper.insertPayment(payment);
        saveCustomFields("finance_payment", payment.getPaymentId(), bo.getCustomFields());

        if (receivable != null) {
            recalculateReceivablePayment(receivable);
        }
        return payment.getPaymentId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePayment(FinancePaymentBO bo) {
        FinancePayment payment = financeMapper.selectPaymentById(requireId(bo.getPaymentId(), "回款ID不能为空"));
        ensureFound(payment, "回款不存在");
        Long oldReceivableId = payment.getReceivableId();
        FinanceReceivable oldReceivable = oldReceivableId == null ? null : financeMapper.selectReceivableByIdForUpdate(oldReceivableId);
        FinanceReceivable receivable = bo.getReceivableId() == null ? null : resolveReceivableForPayment(bo);
        applyPayment(payment, bo, receivable);
        payment.setUpdateUserId(currentUserId());
        financeMapper.updatePayment(payment);
        saveCustomFields("finance_payment", payment.getPaymentId(), bo.getCustomFields());
        if (oldReceivable != null) {
            recalculateReceivablePayment(oldReceivable);
        }
        if (receivable != null && !Objects.equals(oldReceivableId, receivable.getReceivableId())) {
            recalculateReceivablePayment(receivable);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePayment(Long paymentId) {
        FinancePayment payment = financeMapper.selectPaymentById(requireId(paymentId, "回款ID不能为空"));
        ensureFound(payment, "回款不存在");
        FinanceReceivable receivable = payment.getReceivableId() == null ? null : financeMapper.selectReceivableByIdForUpdate(payment.getReceivableId());
        financeMapper.softDelete("crm_finance_payment", "payment_id", paymentId, currentUserId());
        if (receivable != null) {
            recalculateReceivablePayment(receivable);
        }
    }

    @Override
    public BasePage<FinanceRecordVO> queryPayments(FinanceQueryBO query) {
        FinanceQueryBO normalizedQuery = normalizeQuery(query);
        return attachCustomFields(financeMapper.queryPayments(normalizedQuery.parse(), normalizedQuery), "finance_payment");
    }

    @Override
    public FinanceRecordVO getPaymentDetail(Long paymentId) {
        return attachCustomFields(financeMapper.getPaymentDetail(paymentId), "finance_payment");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addInvoice(FinanceInvoiceBO bo) {
        FinanceInvoice invoice = new FinanceInvoice();
        invoice.setInvoiceId(IdWorker.getId());
        applyInvoice(invoice, bo);
        invoice.setCreateUserId(currentUserId());
        invoice.setUpdateUserId(currentUserId());
        invoice.setDelFlag(0);
        financeMapper.insertInvoice(invoice);
        saveCustomFields("finance_invoice", invoice.getInvoiceId(), bo.getCustomFields());
        return invoice.getInvoiceId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInvoice(FinanceInvoiceBO bo) {
        FinanceInvoice invoice = financeMapper.selectInvoiceById(requireId(bo.getInvoiceId(), "发票ID不能为空"));
        ensureFound(invoice, "发票不存在");
        applyInvoice(invoice, bo);
        invoice.setUpdateUserId(currentUserId());
        financeMapper.updateInvoice(invoice);
        saveCustomFields("finance_invoice", invoice.getInvoiceId(), bo.getCustomFields());
    }

    @Override
    public void deleteInvoice(Long invoiceId) {
        financeMapper.softDelete("crm_finance_invoice", "invoice_id", invoiceId, currentUserId());
    }

    @Override
    public BasePage<FinanceRecordVO> queryInvoices(FinanceQueryBO query) {
        FinanceQueryBO normalizedQuery = normalizeQuery(query);
        return attachCustomFields(financeMapper.queryInvoices(normalizedQuery.parse(), normalizedQuery), "finance_invoice");
    }

    @Override
    public FinanceRecordVO getInvoiceDetail(Long invoiceId) {
        return attachCustomFields(financeMapper.getInvoiceDetail(invoiceId), "finance_invoice");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addExpense(FinanceExpenseBO bo) {
        FinanceExpense expense = new FinanceExpense();
        expense.setExpenseId(IdWorker.getId());
        applyExpense(expense, bo);
        expense.setCreateUserId(currentUserId());
        expense.setUpdateUserId(currentUserId());
        expense.setDelFlag(0);
        financeMapper.insertExpense(expense);
        saveCustomFields("finance_expense", expense.getExpenseId(), bo.getCustomFields());
        return expense.getExpenseId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExpense(FinanceExpenseBO bo) {
        FinanceExpense expense = financeMapper.selectExpenseById(requireId(bo.getExpenseId(), "费用ID不能为空"));
        ensureFound(expense, "费用不存在");
        applyExpense(expense, bo);
        expense.setUpdateUserId(currentUserId());
        financeMapper.updateExpense(expense);
        saveCustomFields("finance_expense", expense.getExpenseId(), bo.getCustomFields());
    }

    @Override
    public void deleteExpense(Long expenseId) {
        financeMapper.softDelete("crm_finance_expense", "expense_id", expenseId, currentUserId());
    }

    @Override
    public BasePage<FinanceRecordVO> queryExpenses(FinanceQueryBO query) {
        FinanceQueryBO normalizedQuery = normalizeQuery(query);
        return attachCustomFields(financeMapper.queryExpenses(normalizedQuery.parse(), normalizedQuery), "finance_expense");
    }

    @Override
    public FinanceRecordVO getExpenseDetail(Long expenseId) {
        return attachCustomFields(financeMapper.getExpenseDetail(expenseId), "finance_expense");
    }

    private BasePage<FinanceRecordVO> attachCustomFields(BasePage<FinanceRecordVO> page, String entityType) {
        List<FinanceRecordVO> records = page.getList();
        if (records == null || records.isEmpty()) {
            return page;
        }
        List<Long> ids = records.stream()
                .map(record -> recordId(record, entityType))
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Map<String, Object>> values = ids.isEmpty()
                ? Collections.emptyMap()
                : customFieldService.getBatchCustomFieldValues(entityType, ids);
        for (FinanceRecordVO record : records) {
            Long id = recordId(record, entityType);
            record.setCustomFields(id == null ? Collections.emptyMap() : values.getOrDefault(id, Collections.emptyMap()));
        }
        return page;
    }

    private FinanceRecordVO attachCustomFields(FinanceRecordVO record, String entityType) {
        ensureFound(record, "财务记录不存在");
        Long id = recordId(record, entityType);
        record.setCustomFields(id == null
                ? Collections.emptyMap()
                : customFieldService.getBatchCustomFieldValues(entityType, List.of(id)).getOrDefault(id, Collections.emptyMap()));
        return record;
    }

    private void saveCustomFields(String entityType, Long entityId, Map<String, Object> customFields) {
        if (entityId == null || customFields == null) {
            return;
        }
        customFieldService.updateCustomFieldValues(entityType, entityId, customFields);
    }

    private Long recordId(FinanceRecordVO record, String entityType) {
        return switch (entityType) {
            case "finance_contract" -> record.getContractId();
            case "finance_receivable" -> record.getReceivableId();
            case "finance_payment" -> record.getPaymentId();
            case "finance_invoice" -> record.getInvoiceId();
            case "finance_expense" -> record.getExpenseId();
            default -> record.getId();
        };
    }

    private void applyContract(FinanceContract record, FinanceContractBO bo) {
        record.setContractNo(trim(bo.getContractNo()));
        record.setContractName(requireText(bo.getContractName(), "合同名称不能为空"));
        record.setCustomerId(bo.getCustomerId());
        record.setProjectId(bo.getProjectId());
        record.setOwnerId(resolveOwnerId(bo.getOwnerId(), record.getOwnerId()));
        record.setAmount(requireAmount(bo.getAmount(), "合同金额不能为空"));
        record.setSignDate(bo.getSignDate());
        record.setStartDate(bo.getStartDate());
        record.setEndDate(bo.getEndDate());
        record.setStatus(StrUtil.blankToDefault(trim(bo.getStatus()), STATUS_ACTIVE));
        record.setRemark(trim(bo.getRemark()));
        applySource(record, bo.getSourceType(), bo.getSourceText(), bo.getAiCreated());
    }

    private void applyReceivable(FinanceReceivable record, FinanceReceivableBO bo) {
        record.setContractId(bo.getContractId());
        record.setCustomerId(bo.getCustomerId());
        record.setProjectId(bo.getProjectId());
        record.setOwnerId(resolveOwnerId(bo.getOwnerId(), record.getOwnerId()));
        record.setTitle(requireText(bo.getTitle(), "应收标题不能为空"));
        record.setAmount(requireAmount(bo.getAmount(), "应收金额不能为空"));
        record.setDueDate(bo.getDueDate());
        record.setStatus(StrUtil.blankToDefault(trim(bo.getStatus()), STATUS_PENDING));
        record.setRemark(trim(bo.getRemark()));
        record.setSourceType(resolveSourceType(bo.getSourceType(), bo.getAiCreated(), record.getSourceType()));
        record.setSourceText(resolveSourceText(bo.getSourceText(), record.getSourceText()));
        record.setAiCreated(resolveAiCreated(bo.getAiCreated(), record.getAiCreated()));
    }

    private void applyPayment(FinancePayment record, FinancePaymentBO bo, FinanceReceivable receivable) {
        record.setReceivableId(receivable != null ? receivable.getReceivableId() : bo.getReceivableId());
        record.setContractId(receivable == null ? bo.getContractId() : receivable.getContractId());
        record.setCustomerId(receivable == null ? bo.getCustomerId() : receivable.getCustomerId());
        record.setProjectId(receivable == null ? bo.getProjectId() : receivable.getProjectId());
        record.setOwnerId(resolveOwnerId(receivable == null ? bo.getOwnerId() : receivable.getOwnerId(), record.getOwnerId()));
        record.setAmount(requireAmount(bo.getAmount(), "回款金额不能为空"));
        record.setPaymentDate(bo.getPaymentDate() == null ? new Date() : bo.getPaymentDate());
        record.setPaymentMethod(trim(bo.getPaymentMethod()));
        record.setRemark(trim(bo.getRemark()));
        record.setSourceType(resolveSourceType(bo.getSourceType(), bo.getAiCreated(), record.getSourceType()));
        record.setSourceText(resolveSourceText(bo.getSourceText(), record.getSourceText()));
        record.setAiCreated(resolveAiCreated(bo.getAiCreated(), record.getAiCreated()));
    }

    private void applyInvoice(FinanceInvoice record, FinanceInvoiceBO bo) {
        record.setContractId(bo.getContractId());
        record.setReceivableId(bo.getReceivableId());
        record.setCustomerId(bo.getCustomerId());
        record.setProjectId(bo.getProjectId());
        record.setOwnerId(resolveOwnerId(bo.getOwnerId(), record.getOwnerId()));
        record.setInvoiceNo(trim(bo.getInvoiceNo()));
        record.setTitle(requireText(bo.getTitle(), "发票抬头不能为空"));
        record.setTaxNo(trim(bo.getTaxNo()));
        record.setAmount(requireAmount(bo.getAmount(), "开票金额不能为空"));
        record.setInvoiceDate(bo.getInvoiceDate() == null ? new Date() : bo.getInvoiceDate());
        record.setStatus(StrUtil.blankToDefault(trim(bo.getStatus()), STATUS_ISSUED));
        record.setRemark(trim(bo.getRemark()));
        record.setSourceType(resolveSourceType(bo.getSourceType(), bo.getAiCreated(), record.getSourceType()));
        record.setSourceText(resolveSourceText(bo.getSourceText(), record.getSourceText()));
        record.setAiCreated(resolveAiCreated(bo.getAiCreated(), record.getAiCreated()));
    }

    private void applyExpense(FinanceExpense record, FinanceExpenseBO bo) {
        record.setCustomerId(bo.getCustomerId());
        record.setProjectId(bo.getProjectId());
        record.setOwnerId(resolveOwnerId(bo.getOwnerId(), record.getOwnerId()));
        record.setExpenseType(requireText(bo.getExpenseType(), "费用类型不能为空"));
        record.setAmount(requireAmount(bo.getAmount(), "费用金额不能为空"));
        record.setExpenseDate(bo.getExpenseDate() == null ? new Date() : bo.getExpenseDate());
        record.setStatus(StrUtil.blankToDefault(trim(bo.getStatus()), STATUS_RECORDED));
        record.setRemark(trim(bo.getRemark()));
        record.setSourceType(resolveSourceType(bo.getSourceType(), bo.getAiCreated(), record.getSourceType()));
        record.setSourceText(resolveSourceText(bo.getSourceText(), record.getSourceText()));
        record.setAiCreated(resolveAiCreated(bo.getAiCreated(), record.getAiCreated()));
    }

    private void applySource(FinanceContract record, String sourceType, String sourceText, Boolean aiCreated) {
        record.setSourceType(resolveSourceType(sourceType, aiCreated, record.getSourceType()));
        record.setSourceText(resolveSourceText(sourceText, record.getSourceText()));
        record.setAiCreated(resolveAiCreated(aiCreated, record.getAiCreated()));
    }

    private FinanceReceivable resolveReceivableForPayment(FinancePaymentBO bo) {
        if (bo.getReceivableId() != null) {
            FinanceReceivable receivable = financeMapper.selectReceivableByIdForUpdate(bo.getReceivableId());
            ensureFound(receivable, "关联应收不存在");
            return receivable;
        }
        if (bo.getCustomerId() == null && bo.getContractId() == null) {
            return null;
        }
        return financeMapper.selectEarliestOpenReceivableForUpdate(bo.getCustomerId(), bo.getContractId());
    }

    private void recalculateReceivablePayment(FinanceReceivable receivable) {
        if (receivable == null || receivable.getReceivableId() == null) {
            return;
        }
        BigDecimal receivedAmount = nvl(financeMapper.sumPaymentsByReceivableId(receivable.getReceivableId()));
        receivable.setReceivedAmount(receivedAmount.min(nvl(receivable.getAmount())));
        refreshReceivableStatus(receivable);
        receivable.setUpdateUserId(currentUserId());
        financeMapper.updateReceivable(receivable);
    }

    private void refreshReceivableStatus(FinanceReceivable receivable) {
        BigDecimal amount = nvl(receivable.getAmount());
        BigDecimal received = nvl(receivable.getReceivedAmount());
        if (received.compareTo(amount) >= 0) {
            receivable.setStatus(STATUS_PAID);
        } else if (received.compareTo(ZERO) > 0) {
            receivable.setStatus(STATUS_PARTIAL);
        } else if (StrUtil.isBlank(receivable.getStatus()) || STATUS_PAID.equals(receivable.getStatus())) {
            receivable.setStatus(STATUS_PENDING);
        }
    }

    private FinanceQueryBO normalizeQuery(FinanceQueryBO query) {
        return query == null ? new FinanceQueryBO() : query;
    }

    private Long currentUserId() {
        return UserUtil.getUserIdOrNull();
    }

    private Long resolveOwnerId(Long ownerId) {
        return resolveOwnerId(ownerId, null);
    }

    private Long resolveOwnerId(Long ownerId, Long currentOwnerId) {
        if (ownerId != null) {
            return ownerId;
        }
        return currentOwnerId == null ? currentUserId() : currentOwnerId;
    }

    private String resolveSourceType(String sourceType, Boolean aiCreated, String currentSourceType) {
        if (StrUtil.isNotBlank(sourceType)) {
            return sourceType.trim();
        }
        if (currentSourceType != null && aiCreated == null) {
            return currentSourceType;
        }
        return Boolean.TRUE.equals(aiCreated) ? "ai" : SOURCE_MANUAL;
    }

    private String resolveSourceText(String sourceText, String currentSourceText) {
        String text = trim(sourceText);
        return text == null ? currentSourceText : text;
    }

    private Boolean resolveAiCreated(Boolean aiCreated, Boolean currentAiCreated) {
        return aiCreated == null ? Boolean.TRUE.equals(currentAiCreated) : Boolean.TRUE.equals(aiCreated);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal requireAmount(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, message);
        }
        return amount;
    }

    private String requireText(String value, String message) {
        String text = trim(value);
        if (text == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, message);
        }
        return text;
    }

    private Long requireId(Long id, String message) {
        if (id == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, message);
        }
        return id;
    }

    private void ensureFound(Object value, String message) {
        if (value == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, message);
        }
    }

    private String trim(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
