package com.kakarote.ai_crm.controller;

import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.auth.RequirePermission;
import com.kakarote.ai_crm.common.result.Result;
import com.kakarote.ai_crm.entity.BO.FinanceContractBO;
import com.kakarote.ai_crm.entity.BO.FinanceExpenseBO;
import com.kakarote.ai_crm.entity.BO.FinanceInvoiceBO;
import com.kakarote.ai_crm.entity.BO.FinancePaymentBO;
import com.kakarote.ai_crm.entity.BO.FinanceQueryBO;
import com.kakarote.ai_crm.entity.BO.FinanceReceivableBO;
import com.kakarote.ai_crm.entity.VO.FinanceDashboardVO;
import com.kakarote.ai_crm.entity.VO.FinanceRecordVO;
import com.kakarote.ai_crm.service.IFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/finance")
@Tag(name = "财务管理")
public class FinanceController {

    @Autowired
    private IFinanceService financeService;

    @PostMapping("/dashboard")
    @Operation(summary = "财务看板")
    @RequirePermission("finance:view")
    public Result<FinanceDashboardVO> dashboard(@RequestBody(required = false) FinanceQueryBO query) {
        return Result.ok(financeService.dashboard(query));
    }

    @PostMapping("/contract/queryPageList")
    @Operation(summary = "查询合同")
    @RequirePermission("finance:view")
    public Result<BasePage<FinanceRecordVO>> queryContracts(@RequestBody(required = false) FinanceQueryBO query) {
        return Result.ok(financeService.queryContracts(query));
    }

    @GetMapping("/contract/detail/{id}")
    @Operation(summary = "合同详情")
    @RequirePermission("finance:view")
    public Result<FinanceRecordVO> getContractDetail(@PathVariable Long id) {
        return Result.ok(financeService.getContractDetail(id));
    }

    @PostMapping("/contract/add")
    @Operation(summary = "新增合同")
    @RequirePermission("finance:create")
    public Result<Long> addContract(@Valid @RequestBody FinanceContractBO bo) {
        return Result.ok(financeService.addContract(bo));
    }

    @PostMapping("/contract/update")
    @Operation(summary = "更新合同")
    @RequirePermission("finance:edit")
    public Result<Void> updateContract(@Valid @RequestBody FinanceContractBO bo) {
        financeService.updateContract(bo);
        return Result.ok();
    }

    @PostMapping("/contract/delete/{id}")
    @Operation(summary = "删除合同")
    @RequirePermission("finance:delete")
    public Result<Void> deleteContract(@PathVariable Long id) {
        financeService.deleteContract(id);
        return Result.ok();
    }

    @PostMapping("/receivable/queryPageList")
    @Operation(summary = "查询应收")
    @RequirePermission("finance:view")
    public Result<BasePage<FinanceRecordVO>> queryReceivables(@RequestBody(required = false) FinanceQueryBO query) {
        return Result.ok(financeService.queryReceivables(query));
    }

    @GetMapping("/receivable/detail/{id}")
    @Operation(summary = "应收详情")
    @RequirePermission("finance:view")
    public Result<FinanceRecordVO> getReceivableDetail(@PathVariable Long id) {
        return Result.ok(financeService.getReceivableDetail(id));
    }

    @PostMapping("/receivable/add")
    @Operation(summary = "新增应收")
    @RequirePermission("finance:create")
    public Result<Long> addReceivable(@Valid @RequestBody FinanceReceivableBO bo) {
        return Result.ok(financeService.addReceivable(bo));
    }

    @PostMapping("/receivable/update")
    @Operation(summary = "更新应收")
    @RequirePermission("finance:edit")
    public Result<Void> updateReceivable(@Valid @RequestBody FinanceReceivableBO bo) {
        financeService.updateReceivable(bo);
        return Result.ok();
    }

    @PostMapping("/receivable/delete/{id}")
    @Operation(summary = "删除应收")
    @RequirePermission("finance:delete")
    public Result<Void> deleteReceivable(@PathVariable Long id) {
        financeService.deleteReceivable(id);
        return Result.ok();
    }

    @PostMapping("/payment/queryPageList")
    @Operation(summary = "查询回款")
    @RequirePermission("finance:view")
    public Result<BasePage<FinanceRecordVO>> queryPayments(@RequestBody(required = false) FinanceQueryBO query) {
        return Result.ok(financeService.queryPayments(query));
    }

    @GetMapping("/payment/detail/{id}")
    @Operation(summary = "回款详情")
    @RequirePermission("finance:view")
    public Result<FinanceRecordVO> getPaymentDetail(@PathVariable Long id) {
        return Result.ok(financeService.getPaymentDetail(id));
    }

    @PostMapping("/payment/add")
    @Operation(summary = "新增回款")
    @RequirePermission("finance:create")
    public Result<Long> addPayment(@Valid @RequestBody FinancePaymentBO bo) {
        return Result.ok(financeService.addPayment(bo));
    }

    @PostMapping("/payment/update")
    @Operation(summary = "更新回款")
    @RequirePermission("finance:edit")
    public Result<Void> updatePayment(@Valid @RequestBody FinancePaymentBO bo) {
        financeService.updatePayment(bo);
        return Result.ok();
    }

    @PostMapping("/payment/delete/{id}")
    @Operation(summary = "删除回款")
    @RequirePermission("finance:delete")
    public Result<Void> deletePayment(@PathVariable Long id) {
        financeService.deletePayment(id);
        return Result.ok();
    }

    @PostMapping("/invoice/queryPageList")
    @Operation(summary = "查询发票")
    @RequirePermission("finance:view")
    public Result<BasePage<FinanceRecordVO>> queryInvoices(@RequestBody(required = false) FinanceQueryBO query) {
        return Result.ok(financeService.queryInvoices(query));
    }

    @GetMapping("/invoice/detail/{id}")
    @Operation(summary = "发票详情")
    @RequirePermission("finance:view")
    public Result<FinanceRecordVO> getInvoiceDetail(@PathVariable Long id) {
        return Result.ok(financeService.getInvoiceDetail(id));
    }

    @PostMapping("/invoice/add")
    @Operation(summary = "新增发票")
    @RequirePermission("finance:create")
    public Result<Long> addInvoice(@Valid @RequestBody FinanceInvoiceBO bo) {
        return Result.ok(financeService.addInvoice(bo));
    }

    @PostMapping("/invoice/update")
    @Operation(summary = "更新发票")
    @RequirePermission("finance:edit")
    public Result<Void> updateInvoice(@Valid @RequestBody FinanceInvoiceBO bo) {
        financeService.updateInvoice(bo);
        return Result.ok();
    }

    @PostMapping("/invoice/delete/{id}")
    @Operation(summary = "删除发票")
    @RequirePermission("finance:delete")
    public Result<Void> deleteInvoice(@PathVariable Long id) {
        financeService.deleteInvoice(id);
        return Result.ok();
    }

    @PostMapping("/expense/queryPageList")
    @Operation(summary = "查询费用")
    @RequirePermission("finance:view")
    public Result<BasePage<FinanceRecordVO>> queryExpenses(@RequestBody(required = false) FinanceQueryBO query) {
        return Result.ok(financeService.queryExpenses(query));
    }

    @GetMapping("/expense/detail/{id}")
    @Operation(summary = "费用详情")
    @RequirePermission("finance:view")
    public Result<FinanceRecordVO> getExpenseDetail(@PathVariable Long id) {
        return Result.ok(financeService.getExpenseDetail(id));
    }

    @PostMapping("/expense/add")
    @Operation(summary = "新增费用")
    @RequirePermission("finance:create")
    public Result<Long> addExpense(@Valid @RequestBody FinanceExpenseBO bo) {
        return Result.ok(financeService.addExpense(bo));
    }

    @PostMapping("/expense/update")
    @Operation(summary = "更新费用")
    @RequirePermission("finance:edit")
    public Result<Void> updateExpense(@Valid @RequestBody FinanceExpenseBO bo) {
        financeService.updateExpense(bo);
        return Result.ok();
    }

    @PostMapping("/expense/delete/{id}")
    @Operation(summary = "删除费用")
    @RequirePermission("finance:delete")
    public Result<Void> deleteExpense(@PathVariable Long id) {
        financeService.deleteExpense(id);
        return Result.ok();
    }
}
