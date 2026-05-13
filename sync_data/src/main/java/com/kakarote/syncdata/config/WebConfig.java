package com.kakarote.syncdata.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    /**
     * 参考 AICRM 主服务的 Long 处理方式，将后端 Long 类型统一序列化为字符串。
     * 雪花 ID 已经超过 JavaScript 安全整数范围，如果直接以 JSON number 返回，
     * 前端会出现精度丢失，导致 bindingId、jobId 等路径参数回传后查不到原始记录。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            builder.serializerByType(Long.class, ToStringSerializer.instance);
        };
    }
}
