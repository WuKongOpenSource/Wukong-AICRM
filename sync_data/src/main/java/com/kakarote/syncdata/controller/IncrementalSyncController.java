package com.kakarote.syncdata.controller;

import com.kakarote.syncdata.incremental.IncrementalSyncEvent;
import com.kakarote.syncdata.incremental.IncrementalSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/sync/incremental")
public class IncrementalSyncController {

    private final IncrementalSyncService incrementalSyncService;

    /**
     * 注入增量同步服务，用于接收 HTTP 测试事件。
     */
    public IncrementalSyncController(IncrementalSyncService incrementalSyncService) {
        this.incrementalSyncService = incrementalSyncService;
    }

    /**
     * 接收模拟或上游传入的增量事件，并转交给预留的增量处理流程。
     */
    @PostMapping("/events")
    public Map<String, Object> receive(@Valid @RequestBody IncrementalEventRequest request) {
        IncrementalSyncEvent event = new IncrementalSyncEvent(
                request.sourceSystem() == null ? "wk_crm" : request.sourceSystem(),
                request.sourceCompanyId(),
                request.sourceTable(),
                request.sourceId(),
                request.operation(),
                request.traceId(),
                request.offset(),
                request.eventTime() == null ? Instant.now() : request.eventTime(),
                request.payload(),
                request.rawPayload()
        );
        String status = incrementalSyncService.handleEvent(event);
        return Map.of(
                "status", status,
                "incrementalApplicationAvailable", false,
                "message", "reserved".equals(status)
                        ? "增量事件已记录，但尚未应用到目标业务表。"
                        : "增量事件未应用。"
        );
    }

    /**
     * 用于测试或桥接增量变更事件的 HTTP 请求体。
     */
    public record IncrementalEventRequest(
            String sourceSystem,
            @NotNull Long sourceCompanyId,
            @NotNull String sourceTable,
            String sourceId,
            @NotNull String operation,
            String traceId,
            String offset,
            Instant eventTime,
            Map<String, Object> payload,
            String rawPayload
    ) {
    }
}
