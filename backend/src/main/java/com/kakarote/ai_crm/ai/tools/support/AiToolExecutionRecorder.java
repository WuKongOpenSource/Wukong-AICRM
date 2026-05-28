package com.kakarote.ai_crm.ai.tools.support;

import cn.hutool.core.util.StrUtil;
import com.kakarote.ai_crm.ai.context.AiContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Records AI tool executions for one chat turn so the final assistant reply can
 * be grounded in the actual tool outcome instead of model wording alone.
 */
@Component
public class AiToolExecutionRecorder {

    private static final ConcurrentHashMap<Long, CopyOnWriteArrayList<ToolExecution>> EXECUTIONS =
            new ConcurrentHashMap<>();

    public void begin(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        EXECUTIONS.put(sessionId, new CopyOnWriteArrayList<>());
    }

    public void record(String toolName, String methodName, Object result, Throwable throwable) {
        Long sessionId = AiContextHolder.getCurrentSessionId();
        if (sessionId == null) {
            return;
        }

        String resultText = result instanceof String text ? StrUtil.trim(text) : null;
        String errorReason = resolveErrorReason(resultText, throwable);
        ToolExecution execution = new ToolExecution(
                toolName,
                methodName,
                resultText,
                errorReason,
                errorReason == null && isWriteMethod(methodName) && looksLikeWriteSuccess(resultText)
        );
        EXECUTIONS.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>()).add(execution);
    }

    public ToolExecution getLatestFailure(Long sessionId) {
        List<ToolExecution> executions = getExecutions(sessionId);
        for (int i = executions.size() - 1; i >= 0; i--) {
            ToolExecution execution = executions.get(i);
            if (execution.failed()) {
                return execution;
            }
        }
        return null;
    }

    public boolean hasSuccessfulWrite(Long sessionId) {
        return getExecutions(sessionId).stream().anyMatch(ToolExecution::successfulWrite);
    }

    public Set<String> getSuccessfulWriteMethodNames(Long sessionId) {
        return getExecutions(sessionId).stream()
                .filter(ToolExecution::successfulWrite)
                .map(ToolExecution::methodName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }

    public void finish(Long sessionId) {
        if (sessionId != null) {
            EXECUTIONS.remove(sessionId);
        }
    }

    private List<ToolExecution> getExecutions(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        return EXECUTIONS.getOrDefault(sessionId, new CopyOnWriteArrayList<>());
    }

    private String resolveErrorReason(String resultText, Throwable throwable) {
        if (throwable != null) {
            return StrUtil.blankToDefault(throwable.getMessage(), throwable.getClass().getSimpleName());
        }
        if (!looksLikeFailure(resultText)) {
            return null;
        }
        return resultText;
    }

    private boolean looksLikeFailure(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        String normalized = StrUtil.trim(text);
        return normalized.contains("失败")
                || normalized.startsWith("操作未执行")
                || normalized.contains("无法继续执行")
                || normalized.contains("无权限")
                || normalized.contains("没有这个客户的权限")
                || normalized.contains("所需的权限")
                || normalized.contains("格式无效")
                || normalized.contains("不能为空")
                || normalized.contains("必须是数字");
    }

    private boolean isWriteMethod(String methodName) {
        if (StrUtil.isBlank(methodName)) {
            return false;
        }
        String normalized = methodName.toLowerCase(Locale.ROOT);
        return normalized.startsWith("create")
                || normalized.startsWith("confirm")
                || normalized.startsWith("update")
                || normalized.startsWith("delete")
                || normalized.startsWith("cancel");
    }

    private boolean looksLikeWriteSuccess(String text) {
        return StrUtil.isNotBlank(text)
                && (text.contains("成功")
                || text.contains("已创建")
                || text.contains("已修改")
                || text.contains("已更新")
                || text.contains("已删除")
                || text.contains("已取消"));
    }

    public record ToolExecution(
            String toolName,
            String methodName,
            String resultText,
            String errorReason,
            boolean successfulWrite
    ) {
        public boolean failed() {
            return StrUtil.isNotBlank(errorReason);
        }
    }
}
