package com.kakarote.syncdata.util;

import java.time.Instant;

public class SnowflakeIdGenerator {

    private static final long EPOCH = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * 创建指定 workerId 的 Snowflake ID 生成器。
     */
    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个全局唯一 ID，处理同毫秒序列和时钟回拨。
     */
    public synchronized long nextId() {
        long timestamp = now();
        if (timestamp < lastTimestamp) {
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    /**
     * 获取当前系统毫秒时间。
     */
    private long now() {
        return System.currentTimeMillis();
    }

    /**
     * 自旋等待直到系统时间达到目标毫秒。
     */
    private long waitUntil(long targetTimestamp) {
        long timestamp = now();
        while (timestamp < targetTimestamp) {
            Thread.onSpinWait();
            timestamp = now();
        }
        return timestamp;
    }
}
