package com.kakarote.syncdata.incremental;

import java.time.Instant;
import java.util.Map;

/**
 * HTTP 测试入口和预留 MQ 消费入口共用的标准化增量事件。
 */
public record IncrementalSyncEvent(
        String sourceSystem,
        Long sourceCompanyId,
        String sourceTable,
        String sourceId,
        String operation,
        String traceId,
        String offset,
        Instant eventTime,
        Map<String, Object> payload,
        String rawPayload
) {
}
