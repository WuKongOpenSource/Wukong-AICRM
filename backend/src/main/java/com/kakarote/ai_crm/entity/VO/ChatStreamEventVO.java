package com.kakarote.ai_crm.entity.VO;

/**
 * Structured SSE event for chat streaming.
 */
public record ChatStreamEventVO(String event, String data, String persistedMessage) {

    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_QUOTA_EXHAUSTED = "quota_exhausted";

    public static ChatStreamEventVO message(String data) {
        return new ChatStreamEventVO(EVENT_MESSAGE, data, data);
    }

    public static ChatStreamEventVO quotaExhausted(String data, String persistedMessage) {
        return new ChatStreamEventVO(EVENT_QUOTA_EXHAUSTED, data, persistedMessage);
    }
}
