package com.kakarote.ai_crm.ai.tools;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kakarote.ai_crm.ai.tools.support.AiToolPermission;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.entity.BO.FinanceContractBO;
import com.kakarote.ai_crm.entity.BO.FinanceExpenseBO;
import com.kakarote.ai_crm.entity.BO.FinanceInvoiceBO;
import com.kakarote.ai_crm.entity.BO.FinancePaymentBO;
import com.kakarote.ai_crm.entity.BO.FinanceQueryBO;
import com.kakarote.ai_crm.entity.PO.Customer;
import com.kakarote.ai_crm.entity.PO.Project;
import com.kakarote.ai_crm.entity.VO.FinanceDashboardVO;
import com.kakarote.ai_crm.entity.VO.FinanceRecordVO;
import com.kakarote.ai_crm.mapper.CustomerMapper;
import com.kakarote.ai_crm.mapper.ProjectMapper;
import com.kakarote.ai_crm.service.DataPermissionService;
import com.kakarote.ai_crm.service.IFinanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class FinanceTools {

    @Autowired
    private IFinanceService financeService;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private DataPermissionService dataPermissionService;

    @Tool(description = "查询财务现金流和应收概览。当用户询问本月收入、支出、现金流、未来应收或逾期应收时调用。")
    @AiToolPermission(value = "finance:view", action = "查询财务")
    public String queryFinanceDashboard() {
        FinanceDashboardVO dashboard = financeService.dashboard(new FinanceQueryBO());
        return "财务概览：本月收入 " + money(dashboard.getMonthIncome())
                + "，本月支出 " + money(dashboard.getMonthExpense())
                + "，净现金流 " + money(dashboard.getNetCashFlow())
                + "，未来30天应收 " + money(dashboard.getReceivable30())
                + "，逾期应收 " + money(dashboard.getOverdueReceivable()) + "。";
    }

    @Tool(description = "创建正式财务合同，并可按期数自动生成应收计划。金额和客户必填；缺少时返回需要补充的信息，不要猜。")
    @AiToolPermission(value = "finance:ai_write", action = "AI写入财务")
    public String createFinanceContract(
            @ToolParam(description = "客户ID，数字类型；客户ID和客户名称至少提供一个", required = false) String customerIdStr,
            @ToolParam(description = "客户名称；客户ID和客户名称至少提供一个", required = false) String customerName,
            @ToolParam(description = "项目ID，数字类型，可选", required = false) String projectIdStr,
            @ToolParam(description = "项目名称，可选", required = false) String projectName,
            @ToolParam(description = "合同名称，缺省时可用客户名+合同", required = false) String contractName,
            @ToolParam(description = "合同编号", required = false) String contractNo,
            @ToolParam(description = "合同金额，数字", required = false) String amountStr,
            @ToolParam(description = "签约日期 yyyy-MM-dd，留空默认今天", required = false) String signDate,
            @ToolParam(description = "应收期数，例如 1 或 2，留空默认1", required = false) String planCountStr,
            @ToolParam(description = "原始用户输入，必须传入以便追溯", required = false) String sourceText) {
        try {
            Customer customer = resolveCustomer(customerIdStr, customerName);
            if (customer == null) {
                return "创建合同前请补充客户名称或客户ID。";
            }
            BigDecimal amount = parseAmount(amountStr);
            if (amount == null) {
                return "创建合同前请补充合同金额。";
            }
            Project project = resolveProject(projectIdStr, projectName);
            FinanceContractBO bo = new FinanceContractBO();
            bo.setCustomerId(customer.getCustomerId());
            bo.setProjectId(project == null ? null : project.getProjectId());
            bo.setContractName(StrUtil.blankToDefault(trim(contractName), customer.getCompanyName() + " 合同"));
            bo.setContractNo(trim(contractNo));
            bo.setAmount(amount);
            bo.setSignDate(parseDate(signDate));
            bo.setSourceType("ai");
            bo.setSourceText(sourceText);
            bo.setAiCreated(true);
            bo.setReceivablePlans(buildPlans(bo.getContractName(), amount, parseInt(planCountStr, 1), bo.getSignDate()));
            Long id = financeService.addContract(bo);
            return "合同已创建，contractId=" + id + "，客户=" + customer.getCompanyName() + "，金额=" + money(amount) + "。";
        } catch (Exception exception) {
            log.error("Finance tool createFinanceContract failed", exception);
            return "创建合同失败: " + exception.getMessage();
        }
    }

    @Tool(description = "创建正式回款记录。金额和客户必填；优先自动匹配同客户最早未结清应收。")
    @AiToolPermission(value = "finance:ai_write", action = "AI写入财务")
    public String createFinancePayment(
            @ToolParam(description = "客户ID，数字类型；客户ID和客户名称至少提供一个", required = false) String customerIdStr,
            @ToolParam(description = "客户名称；客户ID和客户名称至少提供一个", required = false) String customerName,
            @ToolParam(description = "合同ID，数字类型，可选", required = false) String contractIdStr,
            @ToolParam(description = "回款金额，数字", required = false) String amountStr,
            @ToolParam(description = "回款日期 yyyy-MM-dd，留空默认今天", required = false) String paymentDate,
            @ToolParam(description = "回款方式，如银行转账、现金、支付宝等", required = false) String paymentMethod,
            @ToolParam(description = "备注", required = false) String remark,
            @ToolParam(description = "原始用户输入，必须传入以便追溯", required = false) String sourceText) {
        try {
            Customer customer = resolveCustomer(customerIdStr, customerName);
            if (customer == null) {
                return "创建回款前请补充客户名称或客户ID。";
            }
            BigDecimal amount = parseAmount(amountStr);
            if (amount == null) {
                return "创建回款前请补充回款金额。";
            }
            FinancePaymentBO bo = new FinancePaymentBO();
            bo.setCustomerId(customer.getCustomerId());
            bo.setContractId(parseLong(contractIdStr));
            bo.setAmount(amount);
            bo.setPaymentDate(parseDate(paymentDate));
            bo.setPaymentMethod(trim(paymentMethod));
            bo.setRemark(trim(remark));
            bo.setSourceType("ai");
            bo.setSourceText(sourceText);
            bo.setAiCreated(true);
            Long id = financeService.addPayment(bo);
            return "回款已入账，paymentId=" + id + "，客户=" + customer.getCompanyName() + "，金额=" + money(amount) + "。";
        } catch (Exception exception) {
            log.error("Finance tool createFinancePayment failed", exception);
            return "创建回款失败: " + exception.getMessage();
        }
    }

    @Tool(description = "创建正式发票记录。金额和客户必填；发票抬头缺省时使用客户名称。")
    @AiToolPermission(value = "finance:ai_write", action = "AI写入财务")
    public String createFinanceInvoice(
            @ToolParam(description = "客户ID，数字类型；客户ID和客户名称至少提供一个", required = false) String customerIdStr,
            @ToolParam(description = "客户名称；客户ID和客户名称至少提供一个", required = false) String customerName,
            @ToolParam(description = "合同ID，数字类型，可选", required = false) String contractIdStr,
            @ToolParam(description = "发票金额，数字", required = false) String amountStr,
            @ToolParam(description = "发票抬头，留空默认客户名称", required = false) String title,
            @ToolParam(description = "税号", required = false) String taxNo,
            @ToolParam(description = "开票日期 yyyy-MM-dd，留空默认今天", required = false) String invoiceDate,
            @ToolParam(description = "原始用户输入，必须传入以便追溯", required = false) String sourceText) {
        try {
            Customer customer = resolveCustomer(customerIdStr, customerName);
            if (customer == null) {
                return "创建发票前请补充客户名称或客户ID。";
            }
            BigDecimal amount = parseAmount(amountStr);
            if (amount == null) {
                return "创建发票前请补充开票金额。";
            }
            FinanceInvoiceBO bo = new FinanceInvoiceBO();
            bo.setCustomerId(customer.getCustomerId());
            bo.setContractId(parseLong(contractIdStr));
            bo.setTitle(StrUtil.blankToDefault(trim(title), customer.getCompanyName()));
            bo.setTaxNo(trim(taxNo));
            bo.setAmount(amount);
            bo.setInvoiceDate(parseDate(invoiceDate));
            bo.setSourceType("ai");
            bo.setSourceText(sourceText);
            bo.setAiCreated(true);
            Long id = financeService.addInvoice(bo);
            return "发票记录已创建，invoiceId=" + id + "，客户=" + customer.getCompanyName() + "，金额=" + money(amount) + "。";
        } catch (Exception exception) {
            log.error("Finance tool createFinanceInvoice failed", exception);
            return "创建发票失败: " + exception.getMessage();
        }
    }

    @Tool(description = "创建正式费用记录。金额和费用类型必填；客户或项目至少提供一个。")
    @AiToolPermission(value = "finance:ai_write", action = "AI写入财务")
    public String createFinanceExpense(
            @ToolParam(description = "客户ID，数字类型，可选", required = false) String customerIdStr,
            @ToolParam(description = "客户名称，可选", required = false) String customerName,
            @ToolParam(description = "项目ID，数字类型，可选", required = false) String projectIdStr,
            @ToolParam(description = "项目名称，可选", required = false) String projectName,
            @ToolParam(description = "费用类型，例如差旅费、招待费、采购费", required = false) String expenseType,
            @ToolParam(description = "费用金额，数字", required = false) String amountStr,
            @ToolParam(description = "费用日期 yyyy-MM-dd，留空默认今天", required = false) String expenseDate,
            @ToolParam(description = "备注", required = false) String remark,
            @ToolParam(description = "原始用户输入，必须传入以便追溯", required = false) String sourceText) {
        try {
            Customer customer = resolveCustomer(customerIdStr, customerName);
            Project project = resolveProject(projectIdStr, projectName);
            if (customer == null && project == null) {
                return "创建费用前请补充客户或项目。";
            }
            BigDecimal amount = parseAmount(amountStr);
            if (amount == null) {
                return "创建费用前请补充费用金额。";
            }
            if (StrUtil.isBlank(expenseType)) {
                return "创建费用前请补充费用类型。";
            }
            FinanceExpenseBO bo = new FinanceExpenseBO();
            bo.setCustomerId(customer == null ? null : customer.getCustomerId());
            bo.setProjectId(project == null ? null : project.getProjectId());
            bo.setExpenseType(expenseType.trim());
            bo.setAmount(amount);
            bo.setExpenseDate(parseDate(expenseDate));
            bo.setRemark(trim(remark));
            bo.setSourceType("ai");
            bo.setSourceText(sourceText);
            bo.setAiCreated(true);
            Long id = financeService.addExpense(bo);
            return "费用已入账，expenseId=" + id + "，金额=" + money(amount) + "。";
        } catch (Exception exception) {
            log.error("Finance tool createFinanceExpense failed", exception);
            return "创建费用失败: " + exception.getMessage();
        }
    }

    @Tool(description = "查询财务记录列表。type 可为 contract/receivable/payment/invoice/expense。")
    @AiToolPermission(value = "finance:view", action = "查询财务")
    public String queryFinanceRecords(
            @ToolParam(description = "记录类型：contract/receivable/payment/invoice/expense", required = false) String type,
            @ToolParam(description = "关键词，可搜索客户、项目、合同、备注", required = false) String keyword) {
        FinanceQueryBO query = new FinanceQueryBO();
        query.setKeyword(trim(keyword));
        query.setPage(1);
        query.setLimit(8);
        BasePage<FinanceRecordVO> page = switch (StrUtil.blankToDefault(type, "receivable")) {
            case "contract" -> financeService.queryContracts(query);
            case "payment" -> financeService.queryPayments(query);
            case "invoice" -> financeService.queryInvoices(query);
            case "expense" -> financeService.queryExpenses(query);
            default -> financeService.queryReceivables(query);
        };
        if (page.getRecords().isEmpty()) {
            return "未找到匹配的财务记录。";
        }
        StringBuilder builder = new StringBuilder("找到 ").append(page.getTotal()).append(" 条记录：\n");
        for (FinanceRecordVO record : page.getRecords()) {
            builder.append("- id=").append(record.getId())
                    .append(", 标题=").append(StrUtil.blankToDefault(record.getTitle(), record.getContractName()))
                    .append(", 客户=").append(StrUtil.blankToDefault(record.getCustomerName(), "-"))
                    .append(", 金额=").append(money(record.getAmount()))
                    .append(", 状态=").append(StrUtil.blankToDefault(record.getStatus(), "-"))
                    .append("\n");
        }
        return builder.toString();
    }

    private Customer resolveCustomer(String customerIdStr, String customerName) {
        Long customerId = parseLong(customerIdStr);
        if (customerId != null) {
            return visibleCustomer(customerMapper.selectByIdIgnoreDataPermission(customerId));
        }
        String name = trim(customerName);
        if (name == null) {
            return null;
        }
        List<Customer> exact = visibleCustomers(customerMapper.selectByExactCompanyNameIgnoreDataPermission(name));
        if (exact.size() == 1) {
            return exact.get(0);
        }
        List<Customer> fuzzy = visibleCustomers(customerMapper.selectByCompanyNameLikeIgnoreDataPermission(name, 5));
        return fuzzy.size() == 1 ? fuzzy.get(0) : null;
    }

    private List<Customer> visibleCustomers(List<Customer> customers) {
        if (customers == null || customers.isEmpty()) {
            return List.of();
        }
        return customers.stream()
                .map(this::visibleCustomer)
                .filter(customer -> customer != null)
                .toList();
    }

    private Customer visibleCustomer(Customer customer) {
        if (customer == null || customer.getOwnerId() == null) {
            return null;
        }
        return dataPermissionService.hasUserDataAccessByPermission("customer:view", customer.getOwnerId())
                ? customer
                : null;
    }

    private Project resolveProject(String projectIdStr, String projectName) {
        Long projectId = parseLong(projectIdStr);
        if (projectId != null) {
            return projectMapper.selectById(projectId);
        }
        String name = trim(projectName);
        if (name == null) {
            return null;
        }
        return projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getName, name)
                .last("LIMIT 1"));
    }

    private List<FinanceContractBO.ReceivablePlan> buildPlans(String contractName, BigDecimal amount, int planCount, Date startDate) {
        int count = Math.max(1, Math.min(planCount, 12));
        List<FinanceContractBO.ReceivablePlan> plans = new ArrayList<>();
        BigDecimal each = amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        BigDecimal assigned = BigDecimal.ZERO;
        LocalDate base = toLocalDate(startDate == null ? new Date() : startDate);
        for (int index = 1; index <= count; index++) {
            FinanceContractBO.ReceivablePlan plan = new FinanceContractBO.ReceivablePlan();
            plan.setTitle(contractName + " 第" + index + "期应收");
            BigDecimal planAmount = index == count ? amount.subtract(assigned) : each;
            assigned = assigned.add(planAmount);
            plan.setAmount(planAmount);
            plan.setDueDate(Date.from(base.plusMonths(index - 1).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            plans.add(plan);
        }
        return plans;
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date parseDate(String value) {
        String text = trim(value);
        LocalDate date = text == null ? LocalDate.now() : LocalDate.parse(text);
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private BigDecimal parseAmount(String value) {
        String text = trim(value);
        if (text == null) {
            return null;
        }
        return new BigDecimal(text.replace(",", ""));
    }

    private Long parseLong(String value) {
        String text = trim(value);
        if (text == null) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int parseInt(String value, int fallback) {
        String text = trim(value);
        if (text == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String trim(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
