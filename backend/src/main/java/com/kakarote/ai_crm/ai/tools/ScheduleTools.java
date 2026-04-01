package com.kakarote.ai_crm.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kakarote.ai_crm.entity.BO.CustomerQueryBO;
import com.kakarote.ai_crm.entity.BO.ScheduleAddBO;
import com.kakarote.ai_crm.entity.PO.Contact;
import com.kakarote.ai_crm.entity.VO.CustomerListVO;
import com.kakarote.ai_crm.entity.VO.ScheduleVO;
import com.kakarote.ai_crm.service.IContactService;
import com.kakarote.ai_crm.service.ICustomerService;
import com.kakarote.ai_crm.service.IScheduleService;
import com.kakarote.ai_crm.common.BasePage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 日程相关 AI Tool - 用于 Spring AI Function Calling
 */
@Slf4j
@Component
public class ScheduleTools {

    @Autowired
    private IScheduleService scheduleService;

    @Autowired
    private ICustomerService customerService;

    @Autowired
    private IContactService contactService;

    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Tool(description = "创建日程安排。仅当用户提到具体时间点（如'10:00''下午3点''上午9点半''明天14:00'）时调用。只有截止日期没有具体执行时间的不用此工具，应使用createTask。直接传入客户名称和联系人姓名即可，无需先查询ID。")
    public String createSchedule(
            @ToolParam(description = "日程标题，必填") String title,
            @ToolParam(description = "开始时间，格式：yyyy-MM-dd HH:mm，必填") String startTime,
            @ToolParam(description = "结束时间，格式：yyyy-MM-dd HH:mm", required = false) String endTime,
            @ToolParam(description = "日程类型：meeting(会议)/call(电话)/visit(拜访)，默认meeting", required = false) String type,
            @ToolParam(description = "关联客户名称（公司名）", required = false) String customerName,
            @ToolParam(description = "关联联系人姓名", required = false) String contactName,
            @ToolParam(description = "地点", required = false) String location,
            @ToolParam(description = "描述", required = false) String description) {

        log.info("【Tool调用】createSchedule 被调用: title={}, startTime={}, endTime={}, type={}, customerName={}, contactName={}",
            title, startTime, endTime, type, customerName, contactName);

        try {
            // 解析开始时间
            if (startTime == null || startTime.isEmpty() || "null".equalsIgnoreCase(startTime)) {
                return "创建日程失败: 缺少开始时间参数";
            }

            ScheduleAddBO bo = new ScheduleAddBO();
            bo.setTitle(title);
            bo.setType(type != null && !type.isEmpty() && !"null".equalsIgnoreCase(type) ? type : "meeting");
            bo.setDescription(description);
            bo.setLocation(location);

            // 解析开始时间
            try {
                bo.setStartTime(dateTimeFormat.parse(startTime));
            } catch (Exception e) {
                return "创建日程失败: 开始时间格式无效，请使用 yyyy-MM-dd HH:mm 格式";
            }

            // 解析结束时间
            if (endTime != null && !endTime.isEmpty() && !"null".equalsIgnoreCase(endTime)) {
                try {
                    bo.setEndTime(dateTimeFormat.parse(endTime));
                } catch (Exception e) {
                    log.warn("结束时间格式无效: {}", endTime);
                }
            }

            // 根据客户名称查找客户ID
            Long customerId = null;
            if (customerName != null && !customerName.isEmpty() && !"null".equalsIgnoreCase(customerName)) {
                customerId = findCustomerIdByName(customerName);
                if (customerId == null) {
                    log.info("未找到客户「{}」，将不关联客户", customerName);
                }
                bo.setCustomerId(customerId);
            }

            // 根据联系人姓名查找联系人ID
            if (contactName != null && !contactName.isEmpty() && !"null".equalsIgnoreCase(contactName)) {
                Long contactId = findContactIdByName(contactName, customerId);
                if (contactId == null) {
                    log.info("未找到联系人「{}」，将不关联联系人", contactName);
                }
                bo.setContactId(contactId);
            }

            Long scheduleId = scheduleService.addSchedule(bo);

            log.info("【Tool调用】createSchedule 成功: scheduleId={}", scheduleId);

            String typeName = getTypeName(bo.getType());
            StringBuilder result = new StringBuilder();
            result.append(String.format("日程「%s」创建成功！日程ID: %d。", title, scheduleId));
            result.append(String.format("\n- 类型: %s", typeName));
            result.append(String.format("\n- 开始时间: %s", startTime));
            if (endTime != null && !endTime.isEmpty() && !"null".equalsIgnoreCase(endTime)) {
                result.append(String.format("\n- 结束时间: %s", endTime));
            }
            if (customerName != null && !customerName.isEmpty() && !"null".equalsIgnoreCase(customerName)) {
                result.append(String.format("\n- 客户: %s", customerName));
            }
            if (location != null && !location.isEmpty() && !"null".equalsIgnoreCase(location)) {
                result.append(String.format("\n- 地点: %s", location));
            }

            return result.toString();
        } catch (Exception e) {
            log.error("【Tool调用】createSchedule 失败: {}", e.getMessage(), e);
            return "创建日程失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询我的日程安排。当用户要查看日程、今天的安排、本周日程时调用。")
    public String querySchedules(
            @ToolParam(description = "筛选条件：all(全部)/today(今天)/thisWeek(本周)，默认today", required = false) String filter) {

        try {
            String actualFilter = filter != null && !filter.isEmpty() && !"null".equalsIgnoreCase(filter) ? filter : "today";
            List<ScheduleVO> schedules = scheduleService.getMySchedules(actualFilter);

            if (schedules.isEmpty()) {
                return switch (actualFilter) {
                    case "today" -> "今天没有日程安排。";
                    case "thisWeek" -> "本周没有日程安排。";
                    default -> "当前没有日程安排。";
                };
            }

            StringBuilder sb = new StringBuilder();
            String title = switch (actualFilter) {
                case "today" -> "今日日程";
                case "thisWeek" -> "本周日程";
                default -> "日程安排";
            };
            sb.append(String.format("## %s（共%d项）\n\n", title, schedules.size()));

            for (ScheduleVO vo : schedules) {
                String typeName = vo.getTypeName() != null ? vo.getTypeName() : getTypeName(vo.getType());
                String time = vo.getStartTime() != null ? dateTimeFormat.format(vo.getStartTime()) : "未知";
                sb.append(String.format("- **%s** [%s] %s", vo.getTitle(), typeName, time));
                if (vo.getEndTime() != null) {
                    sb.append(String.format(" ~ %s", dateTimeFormat.format(vo.getEndTime())));
                }
                if (vo.getCustomerName() != null) {
                    sb.append(String.format("，客户: %s", vo.getCustomerName()));
                }
                if (vo.getLocation() != null && !vo.getLocation().isEmpty()) {
                    sb.append(String.format("，地点: %s", vo.getLocation()));
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "查询日程失败: " + e.getMessage();
        }
    }

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

        for (CustomerListVO customer : page.getList()) {
            if (companyName.equals(customer.getCompanyName())) {
                return customer.getCustomerId();
            }
        }

        return page.getList().get(0).getCustomerId();
    }

    private Long findContactIdByName(String name, Long customerId) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // 如果有客户ID，先在该客户下查找
        if (customerId != null) {
            LambdaQueryWrapper<Contact> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Contact::getName, name);
            wrapper.eq(Contact::getCustomerId, customerId);
            wrapper.eq(Contact::getStatus, 1);
            wrapper.last("LIMIT 1");
            Contact contact = contactService.getOne(wrapper);
            if (contact != null) {
                return contact.getContactId();
            }

            // 模糊查找
            wrapper = new LambdaQueryWrapper<>();
            wrapper.like(Contact::getName, name);
            wrapper.eq(Contact::getCustomerId, customerId);
            wrapper.eq(Contact::getStatus, 1);
            wrapper.last("LIMIT 1");
            contact = contactService.getOne(wrapper);
            if (contact != null) {
                return contact.getContactId();
            }
        }

        return null;
    }

    private String getTypeName(String type) {
        if (type == null) return "会议";
        return switch (type.toLowerCase()) {
            case "meeting" -> "会议";
            case "call" -> "电话";
            case "visit" -> "拜访";
            default -> type;
        };
    }
}
