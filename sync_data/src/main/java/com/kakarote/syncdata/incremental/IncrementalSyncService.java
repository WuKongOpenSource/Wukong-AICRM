package com.kakarote.syncdata.incremental;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakarote.syncdata.db.CompanyBindingRepository;
import com.kakarote.syncdata.db.TargetSchema;
import com.kakarote.syncdata.model.CompanyBinding;
import com.kakarote.syncdata.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Service
public class IncrementalSyncService {

    private static final Logger log = LoggerFactory.getLogger(IncrementalSyncService.class);

    private final TargetSchema targetSchema;
    private final CompanyBindingRepository bindingRepository;
    private final JdbcTemplate target;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(13);

    /**
     * 注入目标库结构初始化、绑定查询、事件日志写入和 JSON 序列化组件。
     */
    public IncrementalSyncService(TargetSchema targetSchema,
                                  CompanyBindingRepository bindingRepository,
                                  @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate,
                                  ObjectMapper objectMapper) {
        this.targetSchema = targetSchema;
        this.bindingRepository = bindingRepository;
        this.target = targetJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一条增量变更事件，并根据绑定关系记录当前预留处理结果。
     */
    public String handleEvent(IncrementalSyncEvent event) {
        targetSchema.initialize();
        CompanyBinding binding = bindingRepository.findActiveByCompanyId(event.sourceCompanyId());
        Long bindingId = binding == null ? null : binding.bindingId();
        if (binding == null || !Boolean.TRUE.equals(binding.incrementalEnabled())) {
            recordEvent(event, bindingId, "ignored", "No active incremental binding.");
            log.info("Incremental event ignored. sourceCompanyId={}, table={}, id={}",
                    event.sourceCompanyId(), event.sourceTable(), event.sourceId());
            return "ignored";
        }

        // 增量同步预留扩展点：
        // 1. 将 binlog/MQ 消息反序列化为老库行数据结构。
        // 2. 复用全量同步中的表字段转换逻辑。
        // 3. 结合 sync_mapping 幂等执行 INSERT/UPDATE/DELETE。
        // 4. 忽略携带本服务 trace_id 的事件，避免双向同步循环。
        recordEvent(event, bindingId, "reserved", "Incremental data application is not implemented. Event recorded only.");
        bindingRepository.updateIncrementalCheckpoint(bindingId, event.offset());
        return "reserved";
    }

    /**
     * 保存增量事件消费记录，便于审计和后续排查重放。
     */
    private void recordEvent(IncrementalSyncEvent event, Long bindingId, String status, String message) {
        target.update("""
                INSERT INTO sync_incremental_event_log(
                    event_log_id, binding_id, source_system, source_company_id, source_table, source_id,
                    operation, trace_id, event_time, consume_status, error_message, raw_payload
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                idGenerator.nextId(),
                bindingId,
                event.sourceSystem() == null ? "wk_crm" : event.sourceSystem(),
                event.sourceCompanyId(),
                event.sourceTable(),
                event.sourceId(),
                event.operation(),
                event.traceId(),
                toTimestamp(event.eventTime()),
                status,
                message,
                event.rawPayload() == null ? toJson(event.payload()) : event.rawPayload()
        );
    }

    /**
     * 将事件中的 Instant 时间转换为 JDBC Timestamp。
     */
    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /**
     * 当上游原始消息不存在时，将结构化 payload 序列化为文本。
     */
    private String toJson(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return payload.toString();
        }
    }
}
