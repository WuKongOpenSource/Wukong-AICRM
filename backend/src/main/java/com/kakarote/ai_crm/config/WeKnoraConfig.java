package com.kakarote.ai_crm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WeKnora 配置类 - 从 application.yml 读取配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "weknora")
public class WeKnoraConfig {

    private String baseUrl = "http://localhost:8080/api/v1";
    /** 全局 API Key（仅作 fallback / admin 用途） */
    private String apiKey;
    private boolean enabled = false;
    private SearchConfig search = new SearchConfig();
    private InitModels initModels = new InitModels();

    @Data
    public static class SearchConfig {
        private int matchCount = 5;
        private double vectorThreshold = 0.5;
        private boolean autoRagEnabled = true;
    }

    @Data
    public static class InitModels {
        private EmbeddingModel embedding = new EmbeddingModel();

        @Data
        public static class EmbeddingModel {
            private String name = "text-embedding-v3";
            private String source = "aliyun";
            private String provider = "openai";
            private String apiKey;
            private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/";
            private int dimension = 1024;
        }
    }
}
