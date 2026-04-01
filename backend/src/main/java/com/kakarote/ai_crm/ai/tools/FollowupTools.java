package com.kakarote.ai_crm.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kakarote.ai_crm.entity.BO.CustomerQueryBO;
import com.kakarote.ai_crm.entity.BO.FollowUpAddBO;
import com.kakarote.ai_crm.entity.PO.Contact;
import com.kakarote.ai_crm.entity.VO.CustomerListVO;
import com.kakarote.ai_crm.entity.VO.FollowUpVO;
import com.kakarote.ai_crm.service.IContactService;
import com.kakarote.ai_crm.service.ICustomerService;
import com.kakarote.ai_crm.service.IFollowUpService;
import com.kakarote.ai_crm.common.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 跟进记录相关 AI Tool - 用于 Spring AI Function Calling
 */
@Slf4j
@Component
public class FollowupTools {

    @Autowired
    private IFollowUpService followUpService;

    @Autowired
    private ICustomerService customerService;

    @Autowired
    private IContactService contactService;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Tool(description = "创建跟进记录。当用户描述已经发生的事情时调用（过去式：'已''已经''做了''拜访了''沟通了''谈了''聊了''见了''打了电话''发了邮件'等）。直接传入客户名称和联系人姓名即可，无需先查询ID。如果联系人找不到，仍然继续创建跟进记录。如果涉及签约/回款/成交等关键业务节点，在content内容前加【关键节点】标记。根据跟进内容推断类型：拜访/见面=visit，电话/打电话=call，邮件=email，会议/开会=meeting，其他=visit。")
    public String createFollowUp(
            @ToolParam(description = "客户名称（公司名），必填") String customerName,
            @ToolParam(description = "跟进类型：call(电话)/meeting(会议)/email(邮件)/visit(拜访)，根据内容推断，默认visit") String type,
            @ToolParam(description = "跟进内容，必填") String content,
            @ToolParam(description = "跟进时间，格式：yyyy-MM-dd，默认今天") String followTime,
            @ToolParam(description = "联系人姓名", required = false) String contactName,
            @ToolParam(description = "下次跟进时间，格式：yyyy-MM-dd", required = false) String nextFollowTime) {

        log.info("【Tool调用】createFollowUp 被调用: customerName={}, type={}, content={}, followTime={}, contactName={}, nextFollowTime={}",
            customerName, type, content, followTime, contactName, nextFollowTime);

        try {
            // 根据客户名称查找客户ID
            Long customerId = findCustomerIdByName(customerName);
            if (customerId == null) {
                return "创建跟进失败: 未找到名为「" + customerName + "」的客户，请确认客户名称是否正确";
            }

            // 根据联系人姓名查找联系人ID（可选）
            Long contactId = null;
            if (contactName != null && !contactName.isEmpty() && !"null".equalsIgnoreCase(contactName)) {
                contactId = findContactIdByName(contactName, customerId);
                if (contactId == null) {
                    log.info("未找到联系人「{}」，将不关联联系人", contactName);
                }
            }

            FollowUpAddBO bo = new FollowUpAddBO();
            bo.setCustomerId(customerId);
            bo.setContactId(contactId);
            bo.setType(type != null && !type.isEmpty() && !"null".equalsIgnoreCase(type) ? type : "visit");
            bo.setContent(content);

            // 解析跟进时间
            if (followTime != null && !followTime.isEmpty() && !"null".equalsIgnoreCase(followTime)) {
                try {
                    bo.setFollowTime(dateFormat.parse(followTime));
                } catch (Exception e) {
                    bo.setFollowTime(new Date());
                }
            } else {
                bo.setFollowTime(new Date());
            }

            // 解析下次跟进时间
            if (nextFollowTime != null && !nextFollowTime.isEmpty() && !"null".equalsIgnoreCase(nextFollowTime)) {
                try {
                    bo.setNextFollowTime(dateFormat.parse(nextFollowTime));
                } catch (Exception e) {
                    log.warn("下次跟进时间格式无效: {}", nextFollowTime);
                }
            }

            Long followUpId = followUpService.addFollowUp(bo);

            log.info("【Tool调用】createFollowUp 成功: followUpId={}", followUpId);

            String typeName = getTypeName(bo.getType());
            StringBuilder result = new StringBuilder();
            result.append(String.format("跟进记录创建成功！跟进ID: %d。", followUpId));
            result.append(String.format("\n- 客户: %s", customerName));
            result.append(String.format("\n- 类型: %s", typeName));
            result.append(String.format("\n- 内容: %s", content));
            result.append(String.format("\n- 跟进时间: %s", followTime != null && !followTime.isEmpty() && !"null".equalsIgnoreCase(followTime) ? followTime : dateFormat.format(new Date())));
            if (contactId != null) {
                result.append(String.format("\n- 联系人: %s", contactName));
            } else if (contactName != null && !contactName.isEmpty() && !"null".equalsIgnoreCase(contactName)) {
                result.append(String.format("\n- 联系人: %s（未在系统中找到，未关联）", contactName));
            }
            if (nextFollowTime != null && !nextFollowTime.isEmpty() && !"null".equalsIgnoreCase(nextFollowTime)) {
                result.append(String.format("\n- 下次跟进时间: %s", nextFollowTime));
            }

            return result.toString();
        } catch (Exception e) {
            log.error("【Tool调用】createFollowUp 失败: {}", e.getMessage(), e);
            return "创建跟进记录失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询客户的跟进记录。当用户要查看某个客户的跟进历史、跟进记录时调用。直接传入客户名称即可，无需先查询ID。")
    public String queryFollowUps(
            @ToolParam(description = "客户名称（公司名），必填") String customerName) {

        try {
            if (customerName == null || customerName.isEmpty() || "null".equalsIgnoreCase(customerName)) {
                return "查询跟进记录失败: 缺少客户名称";
            }

            Long customerId = findCustomerIdByName(customerName);
            if (customerId == null) {
                return "查询跟进记录失败: 未找到名为「" + customerName + "」的客户";
            }

            List<FollowUpVO> followUps = followUpService.queryByCustomer(customerId);

            if (followUps.isEmpty()) {
                return "该客户暂无跟进记录。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("## %s 的跟进记录（共%d条）\n\n", customerName, followUps.size()));

            for (FollowUpVO vo : followUps) {
                String typeName = getTypeName(vo.getType());
                String time = vo.getFollowTime() != null ? dateFormat.format(vo.getFollowTime()) : "未知";
                sb.append(String.format("- **[%s]** %s - %s", typeName, time, vo.getContent()));
                if (vo.getNextFollowTime() != null) {
                    sb.append(String.format("（下次跟进: %s）", dateFormat.format(vo.getNextFollowTime())));
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "查询跟进记录失败: " + e.getMessage();
        }
    }

    /**
     * 根据公司名称查找客户ID
     */
    private Long findCustomerIdByName(String companyName) {
        if (companyName == null || companyName.isEmpty()) {
            return null;
        }
        CustomerQueryBO queryBO = new CustomerQueryBO();
        queryBO.setKeyword(companyName);
        queryBO.setPage(1);
        queryBO.setLimit(5);

        BasePage<CustomerListVO> page = customerService.queryPageList(queryBO);
        if (page.getList().isEmpty()) {
            return null;
        }

        // 优先精确匹配
        for (CustomerListVO customer : page.getList()) {
            if (companyName.equals(customer.getCompanyName())) {
                return customer.getCustomerId();
            }
        }

        // 如果没有精确匹配，返回第一个结果
        return page.getList().get(0).getCustomerId();
    }

    /**
     * 根据联系人姓名查找联系人ID，优先在指定客户下查找
     */
    private Long findContactIdByName(String name, Long customerId) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // 先在该客户下精确查找
        LambdaQueryWrapper<Contact> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Contact::getName, name);
        wrapper.eq(Contact::getCustomerId, customerId);
        wrapper.eq(Contact::getStatus, 1);
        wrapper.last("LIMIT 1");
        Contact contact = contactService.getOne(wrapper);
        if (contact != null) {
            return contact.getContactId();
        }

        // 再在该客户下模糊查找
        wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Contact::getName, name);
        wrapper.eq(Contact::getCustomerId, customerId);
        wrapper.eq(Contact::getStatus, 1);
        wrapper.last("LIMIT 1");
        contact = contactService.getOne(wrapper);
        if (contact != null) {
            return contact.getContactId();
        }

        return null;
    }

    private String getTypeName(String type) {
        if (type == null) return "拜访";
        return switch (type.toLowerCase()) {
            case "call" -> "电话";
            case "meeting" -> "会议";
            case "email" -> "邮件";
            case "visit" -> "拜访";
            default -> type;
        };
    }
}
