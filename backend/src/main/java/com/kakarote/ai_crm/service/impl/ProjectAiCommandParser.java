package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProjectAiCommandParser {

    enum Action {
        CREATE_TASK,
        CREATE_LANE,
        CREATE_PROJECT_SCHEDULE,
        CREATE_PROJECT_ATTACHMENT,
        UPDATE_PROJECT_STATUS,
        ARCHIVE_PROJECT,
        QUERY_TASKS,
        SUMMARIZE_PROJECT,
        UNSAFE_DELETE,
        UNKNOWN
    }

    record ParsedCommand(Action action, String title, String targetStatus, boolean createTaskSchedule) {
    }

    private ProjectAiCommandParser() {
    }

    static ParsedCommand parse(String content) {
        String normalized = normalize(content);
        if (StrUtil.isBlank(normalized)) {
            return new ParsedCommand(Action.UNKNOWN, "", null, false);
        }
        if (hasAny(normalized, "删除", "移除", "清空")
                && hasAny(normalized, "项目", "任务", "泳道", "日程", "附件")) {
            return new ParsedCommand(Action.UNSAFE_DELETE, "", null, false);
        }
        if (hasAny(normalized, "归档")) {
            return new ParsedCommand(Action.ARCHIVE_PROJECT, "", "ARCHIVED", false);
        }
        String targetStatus = parseProjectStatus(normalized);
        if (targetStatus != null) {
            return new ParsedCommand(Action.UPDATE_PROJECT_STATUS, "", targetStatus, false);
        }
        if (hasAny(normalized, "总结", "进展", "汇总")) {
            return new ParsedCommand(Action.SUMMARIZE_PROJECT, "", null, false);
        }
        if (hasAny(normalized, "查询任务", "任务列表", "有哪些任务", "看看任务")) {
            return new ParsedCommand(Action.QUERY_TASKS, "", null, false);
        }
        if (isCreateLane(normalized)) {
            return new ParsedCommand(Action.CREATE_LANE, extractLaneName(normalized), null, false);
        }
        if (isCreateTask(normalized)) {
            return new ParsedCommand(Action.CREATE_TASK, extractTaskTitle(normalized), null, hasScheduleIntent(normalized));
        }
        if (isCreateProjectSchedule(normalized)) {
            return new ParsedCommand(Action.CREATE_PROJECT_SCHEDULE, extractScheduleTitle(normalized), null, false);
        }
        if (isCreateProjectAttachment(normalized)) {
            return new ParsedCommand(Action.CREATE_PROJECT_ATTACHMENT, extractAttachmentName(normalized), null, false);
        }
        return new ParsedCommand(Action.UNKNOWN, "", null, false);
    }

    private static boolean isCreateLane(String content) {
        return hasCreateVerb(content) && hasAny(content, "泳道", "看板列", "状态列");
    }

    private static boolean isCreateTask(String content) {
        return (hasCreateVerb(content) && hasAny(content, "任务", "待办", "事项"))
                || Pattern.compile("(创建|新增|添加|安排|建立).+的任务").matcher(content).find();
    }

    private static boolean isCreateProjectSchedule(String content) {
        return hasAny(content, "日程", "会议", "提醒") && hasAny(content, "创建", "新增", "添加", "安排", "建立");
    }

    private static boolean isCreateProjectAttachment(String content) {
        return hasAny(content, "挂到项目", "追加到项目", "添加到项目", "上传到项目")
                || (hasAny(content, "附件", "文件", "文档", "设计稿", "材料") && hasAny(content, "挂载", "上传", "追加", "添加", "记录"));
    }

    private static boolean hasCreateVerb(String content) {
        return hasAny(content, "创建", "新增", "添加", "安排", "建立");
    }

    private static boolean hasScheduleIntent(String content) {
        return hasAny(content, "日程", "会议", "提醒") && hasAny(content, "安排", "创建", "新增", "添加", "建立");
    }

    private static String parseProjectStatus(String content) {
        if (hasAny(content, "改成已完成", "项目已完成", "完成这个项目")) {
            return "COMPLETED";
        }
        if (hasAny(content, "改成进行中", "启动这个项目", "开始这个项目")) {
            return "IN_PROGRESS";
        }
        if (hasAny(content, "改成未开始", "项目未开始")) {
            return "NOT_STARTED";
        }
        if (hasAny(content, "改成已暂停", "暂停这个项目", "项目暂停")) {
            return "PAUSED";
        }
        return null;
    }

    private static String extractTaskTitle(String content) {
        Matcher taskMatcher = Pattern.compile("(给.+?)(?:的)?任务").matcher(content);
        if (taskMatcher.find()) {
            return cleanTitle(taskMatcher.group(1));
        }
        Matcher naturalMatcher = Pattern.compile("(?:创建|新增|添加|安排|建立)(?:一个|一条|1个)?(.+?)(?:的)?(?:任务|待办|事项)").matcher(content);
        if (naturalMatcher.find()) {
            return cleanTitle(naturalMatcher.group(1));
        }
        return cleanTitle(content
                .replaceAll("帮我|请|麻烦", "")
                .replaceAll("创建任务|新增任务|添加任务|安排任务|建立任务", ""));
    }

    private static String extractLaneName(String content) {
        Matcher matcher = Pattern.compile("(?:创建|新增|添加|建立)(?:一个|一条|1个)?(.+?)(?:泳道|看板列|状态列)").matcher(content);
        if (matcher.find()) {
            return cleanTitle(matcher.group(1));
        }
        return cleanTitle(content.replaceAll("泳道|看板列|状态列", ""));
    }

    private static String extractScheduleTitle(String content) {
        Matcher matcher = Pattern.compile("(?:创建|新增|添加|安排|建立)(.+?)(?:日程|会议|提醒)").matcher(content);
        if (matcher.find()) {
            return cleanTitle(matcher.group(1));
        }
        return cleanTitle(content.replaceAll("日程|会议|提醒", ""));
    }

    private static String extractAttachmentName(String content) {
        Matcher matcher = Pattern.compile("(?:把|将)?(.+?)(?:挂到|追加到|添加到|上传到|挂载到)项目").matcher(content);
        if (matcher.find()) {
            return cleanTitle(matcher.group(1));
        }
        return cleanTitle(content.replaceAll("附件|文件|挂载|上传|追加|添加|记录|到项目下?|到项目", ""));
    }

    private static String cleanTitle(String text) {
        String cleaned = StrUtil.blankToDefault(text, "")
                .replaceAll("这个项目是.*?项目，?", "")
                .replaceAll("今天|明天|后天|下周|上午|下午|晚上|早上|中午|\\d{1,2}[:：]\\d{1,2}|\\d{1,2}点|一点|两点|二点|三点|四点|五点|六点|七点|八点|九点|十点|十一点|十二点", "")
                .replaceAll("帮我|请|麻烦|一个|一条|1个", "")
                .replaceAll("[，。,.、；;：:！!]+$", "")
                .trim();
        return StrUtil.blankToDefault(cleaned, "项目任务");
    }

    private static boolean hasAny(String content, String... keywords) {
        if (StrUtil.isBlank(content)) {
            return false;
        }
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String content) {
        return StrUtil.blankToDefault(content, "").replaceAll("\\s+", " ").trim();
    }
}
