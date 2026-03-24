package com.kakarote.ai_crm.ai;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakarote.ai_crm.ai.provider.AiModelCapabilities;
import com.kakarote.ai_crm.ai.provider.AiProviderDescriptor;
import com.kakarote.ai_crm.ai.provider.AiProviderRegistry;
import com.kakarote.ai_crm.ai.tools.ContactTools;
import com.kakarote.ai_crm.ai.tools.CustomerTools;
import com.kakarote.ai_crm.ai.tools.FollowupTools;
import com.kakarote.ai_crm.ai.tools.KnowledgeTools;
import com.kakarote.ai_crm.ai.tools.ScheduleTools;
import com.kakarote.ai_crm.ai.tools.TaskTools;
import com.kakarote.ai_crm.config.tenant.TenantContextHolder;
import com.kakarote.ai_crm.entity.PO.SystemConfig;
import com.kakarote.ai_crm.mapper.SystemConfigMapper;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 动态 ChatClient 提供器。
 */
@Slf4j
@Component
public class DynamicChatClientProvider {

    private static final String AI_MODE_KEY = "ai_mode";
    private static final String AI_PROVIDER_KEY = "ai_provider";
    private static final String AI_API_URL_KEY = "ai_api_url";
    private static final String AI_API_KEY_KEY = "ai_api_key";
    private static final String AI_MODEL_KEY = "ai_model";
    private static final String AI_TEMPERATURE_KEY = "ai_temperature";
    private static final String AI_MAX_TOKENS_KEY = "ai_max_tokens";
    private static final String AI_EXTRA_HEADERS_KEY = "ai_extra_headers";

    private final ConcurrentHashMap<Long, ChatClient> tenantChatClients = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private CustomerTools customerTools;

    @Autowired
    private TaskTools taskTools;

    @Autowired
    private KnowledgeTools knowledgeTools;

    @Autowired
    private ContactTools contactTools;

    @Autowired
    private FollowupTools followupTools;

    @Autowired
    private ScheduleTools scheduleTools;

    @Autowired
    private ToolCallingManager toolCallingManager;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode}")
    private String defaultBaseUrl;

    @Value("${spring.ai.openai.api-key:${DASHSCOPE_API_KEY:${OPENAI_API_KEY:}}}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen3.5-plus}")
    private String defaultModel;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double defaultTemperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:2048}")
    private Integer defaultMaxTokens;

    @Value("${weknora.init-models.chat.base-url:}")
    private String giftBaseUrl;

    @Value("${weknora.init-models.chat.api-key:}")
    private String giftApiKey;

    @Value("${weknora.init-models.chat.name:}")
    private String giftModel;

    public ChatClient getChatClient() {
        Long tenantId = TenantContextHolder.getTenantId();
        long key = tenantId != null ? tenantId : 0L;
        ChatClient client = tenantChatClients.get(key);
        if (client == null) {
            synchronized (lock) {
                client = tenantChatClients.get(key);
                if (client == null) {
                    refreshChatClient();
                    client = tenantChatClients.get(key);
                }
            }
        }
        return client;
    }

    public void refreshChatClient() {
        synchronized (lock) {
            Long tenantId = TenantContextHolder.getTenantId();
            long key = tenantId != null ? tenantId : 0L;
            AiRuntimeConfig runtimeConfig = resolveRuntimeConfig(loadAiConfigsFromDB());

            if (StrUtil.isBlank(runtimeConfig.apiKey())) {
                log.warn("AI 运行 Key 未配置，tenantId={}, mode={}", key, runtimeConfig.mode().getCode());
            }

            ChatClient client = createChatClient(runtimeConfig);
            tenantChatClients.put(key, client);

            log.info("ChatClient 刷新完成: tenantId={}, mode={}, provider={}, baseUrl={}, model={}",
                    key, runtimeConfig.mode().getCode(), runtimeConfig.providerCode(),
                    runtimeConfig.apiUrl(), runtimeConfig.model());
        }
    }

    public boolean isApiKeyConfigured() {
        return StrUtil.isNotBlank(resolveRuntimeConfig(loadAiConfigsFromDB()).apiKey());
    }

    public AiModelCapabilities getCurrentCapabilities() {
        return resolveRuntimeConfig(loadAiConfigsFromDB()).capabilities();
    }

    public AiMode getCurrentMode() {
        return resolveRuntimeConfig(loadAiConfigsFromDB()).mode();
    }

    public boolean isUsingGiftMode() {
        return getCurrentMode() == AiMode.GIFT;
    }

    public void evictTenantChatClient(Long tenantId) {
        if (tenantId != null) {
            tenantChatClients.remove(tenantId);
        }
    }

    public ChatClient createChatClient(String baseUrl, String apiKey, String model,
                                       Double temperature, Integer maxTokens) {
        return createChatClient(baseUrl, apiKey, model, temperature, maxTokens, null, defaultCapabilities(), true);
    }

    public ChatClient createChatClient(String baseUrl, String apiKey, String model,
                                       Double temperature, Integer maxTokens,
                                       String extraHeadersJson, AiModelCapabilities capabilities,
                                       boolean registerTools) {
        OpenAiApi openAiApi = buildOpenAiApi(baseUrl, apiKey, extraHeadersJson);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
        options.setStreamUsage(Boolean.TRUE);

        ObservationRegistry obsRegistry = observationRegistry != null
                ? observationRegistry : ObservationRegistry.NOOP;
        OpenAiChatModel chatModel = new OpenAiChatModel(
                openAiApi, options, toolCallingManager,
                RetryTemplate.builder().build(), obsRegistry);

        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (registerTools && capabilities != null && capabilities.isSupportsToolCall()) {
            builder.defaultTools(customerTools, taskTools, knowledgeTools, contactTools, followupTools, scheduleTools);
        }
        return builder.build();
    }

    public ChatClient createTestChatClient(String baseUrl, String apiKey, String model,
                                           Double temperature, Integer maxTokens,
                                           String extraHeadersJson, AiModelCapabilities capabilities) {
        return createChatClient(
                baseUrl,
                apiKey,
                model,
                temperature != null ? temperature : 0.7,
                maxTokens != null ? maxTokens : 100,
                extraHeadersJson,
                capabilities != null ? capabilities : defaultCapabilities(),
                false
        );
    }

    private ChatClient createChatClient(AiRuntimeConfig runtimeConfig) {
        return createChatClient(
                runtimeConfig.apiUrl(),
                runtimeConfig.apiKey(),
                runtimeConfig.model(),
                runtimeConfig.temperature(),
                runtimeConfig.maxTokens(),
                runtimeConfig.extraHeadersJson(),
                runtimeConfig.capabilities(),
                true
        );
    }

    private OpenAiApi buildOpenAiApi(String baseUrl, String apiKey, String extraHeadersJson) {
        String normalizedBaseUrl = normalizeCompatibleBaseUrl(baseUrl);
        AiProviderDescriptor descriptor = AiProviderRegistry.resolve(null, normalizedBaseUrl);
        OpenAiApi.Builder builder = OpenAiApi.builder()
                .baseUrl(normalizedBaseUrl)
                .apiKey(apiKey);

        if (StrUtil.isNotBlank(descriptor.getCompletionsPath())) {
            builder.completionsPath(descriptor.getCompletionsPath());
        }
        if (StrUtil.isNotBlank(descriptor.getEmbeddingsPath())) {
            builder.embeddingsPath(descriptor.getEmbeddingsPath());
        }

        MultiValueMap<String, String> headers = parseExtraHeaders(extraHeadersJson);
        if (!headers.isEmpty()) {
            builder.headers(headers);
        }
        return builder.build();
    }

    private MultiValueMap<String, String> parseExtraHeaders(String extraHeadersJson) {
        LinkedMultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (StrUtil.isBlank(extraHeadersJson)) {
            return headers;
        }

        try {
            Map<String, Object> values = objectMapper.readValue(
                    extraHeadersJson,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            values.forEach((key, value) -> {
                if (StrUtil.isNotBlank(key) && value != null) {
                    headers.add(key.trim(), String.valueOf(value));
                }
            });
        } catch (Exception e) {
            log.warn("解析额外请求头失败，将忽略该配置: {}", e.getMessage());
        }
        return headers;
    }

    private AiRuntimeConfig resolveRuntimeConfig(Map<String, String> configs) {
        boolean hasCustomConfig = hasSavedCustomConfig(configs);
        AiMode requestedMode = AiMode.resolve(configs.get(AI_MODE_KEY));
        if (requestedMode == AiMode.CUSTOM && hasCustomConfig) {
            String resolvedApiUrl = normalizeCompatibleBaseUrl(configs.get(AI_API_URL_KEY));
            AiProviderDescriptor descriptor = AiProviderRegistry.resolve(configs.get(AI_PROVIDER_KEY), resolvedApiUrl);
            String model = configs.get(AI_MODEL_KEY);
            return new AiRuntimeConfig(
                    descriptor.getCode(),
                    resolvedApiUrl,
                    configs.get(AI_API_KEY_KEY),
                    model,
                    parseDouble(configs.get(AI_TEMPERATURE_KEY), defaultTemperature),
                    parseInt(configs.get(AI_MAX_TOKENS_KEY), defaultMaxTokens),
                    StrUtil.blankToDefault(configs.get(AI_EXTRA_HEADERS_KEY), null),
                    descriptor.resolveCapabilities(model),
                    AiMode.CUSTOM
            );
        }

        String resolvedApiUrl = normalizeCompatibleBaseUrl(StrUtil.blankToDefault(giftBaseUrl, defaultBaseUrl));
        AiProviderDescriptor descriptor = AiProviderRegistry.resolve(null, resolvedApiUrl);
        String resolvedApiKey = StrUtil.blankToDefault(giftApiKey, defaultApiKey);
        String resolvedModel = StrUtil.blankToDefault(giftModel, defaultModel);
        return new AiRuntimeConfig(
                descriptor.getCode(),
                resolvedApiUrl,
                resolvedApiKey,
                resolvedModel,
                defaultTemperature,
                defaultMaxTokens,
                null,
                descriptor.resolveCapabilities(resolvedModel),
                AiMode.GIFT
        );
    }

    private boolean hasSavedCustomConfig(Map<String, String> configs) {
        return StrUtil.isNotBlank(configs.get(AI_API_KEY_KEY))
                && StrUtil.isNotBlank(configs.get(AI_API_URL_KEY))
                && StrUtil.isNotBlank(configs.get(AI_MODEL_KEY));
    }

    private Map<String, String> loadAiConfigsFromDB() {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigType, "ai");

        List<SystemConfig> configs = systemConfigMapper.selectList(wrapper);

        return configs.stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        c -> c.getConfigValue() != null ? c.getConfigValue() : "",
                        (v1, v2) -> v2
                ));
    }

    private double parseDouble(String value, double defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int parseInt(String value, int defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private AiModelCapabilities defaultCapabilities() {
        return AiModelCapabilities.builder()
                .supportsStream(true)
                .supportsToolCall(true)
                .supportsVision(false)
                .build();
    }

    public static String normalizeCompatibleBaseUrl(String baseUrl) {
        if (StrUtil.isBlank(baseUrl)) {
            return baseUrl;
        }

        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.matches("(?i).*/v1$")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @PostConstruct
    public void init() {
        try {
            refreshChatClient();
        } catch (Exception e) {
            log.warn("初始化 ChatClient 失败（可能配置尚未准备好）: {}", e.getMessage());
        }
    }

    private record AiRuntimeConfig(
            String providerCode,
            String apiUrl,
            String apiKey,
            String model,
            Double temperature,
            Integer maxTokens,
            String extraHeadersJson,
            AiModelCapabilities capabilities,
            AiMode mode
    ) {
    }
}
