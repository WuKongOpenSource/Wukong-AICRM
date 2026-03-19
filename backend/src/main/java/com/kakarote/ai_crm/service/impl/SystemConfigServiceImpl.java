package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kakarote.ai_crm.ai.DynamicChatClientProvider;
import com.kakarote.ai_crm.config.tenant.TenantContextHolder;
import com.kakarote.ai_crm.entity.BO.AiConfigUpdateBO;
import com.kakarote.ai_crm.entity.BO.EnterpriseConfigUpdateBO;
import com.kakarote.ai_crm.entity.PO.SystemConfig;
import com.kakarote.ai_crm.entity.VO.AiConfigVO;
import com.kakarote.ai_crm.entity.VO.AiConnectionTestVO;
import com.kakarote.ai_crm.entity.VO.EnterpriseConfigVO;
import com.kakarote.ai_crm.mapper.SystemConfigMapper;
import com.kakarote.ai_crm.service.FileStorageService;
import com.kakarote.ai_crm.service.ISystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现
 */
@Slf4j
@Service
public class SystemConfigServiceImpl extends ServiceImpl<SystemConfigMapper, SystemConfig>
        implements ISystemConfigService {

    private static final String CACHE_KEY_PREFIX = "system:config:";
    private static final String AI_CONFIG_TYPE = "ai";
    private static final String ENTERPRISE_CONFIG_TYPE = "enterprise";
    private static final long CACHE_EXPIRE_MINUTES = 30;

    /**
     * 构建租户级缓存 key: system:config:{tenantId}:{configKey}
     */
    private String buildCacheKey(String configKey) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            return CACHE_KEY_PREFIX + tenantId + ":" + configKey;
        }
        return CACHE_KEY_PREFIX + configKey;
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DynamicChatClientProvider chatClientProvider;

    @Autowired
    private FileStorageService fileStorageService;

    // 从 application.yml 读取默认值，确保与 DynamicChatClientProvider 一致
    @Value("${spring.ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode/}")
    private String defaultApiUrl;

    @Value("${spring.ai.openai.chat.options.model:qwen-max}")
    private String defaultModel;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double defaultTemperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:2048}")
    private Integer defaultMaxTokens;

    @Override
    public String getConfigValue(String configKey) {
        return getConfigValue(configKey, null);
    }

    @Override
    public String getConfigValue(String configKey, String defaultValue) {
        // 1. 先从 Redis 缓存查询（租户隔离）
        String cacheKey = buildCacheKey(configKey);
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            return cachedValue;
        }

        // 2. 从数据库查询
        SystemConfig config = lambdaQuery()
                .eq(SystemConfig::getConfigKey, configKey)
                .one();

        if (config != null && StrUtil.isNotBlank(config.getConfigValue())) {
            // 缓存到 Redis
            redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(),
                    CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            return config.getConfigValue();
        }

        return defaultValue;
    }

    @Override
    public Map<String, String> getConfigsByType(String configType) {
        List<SystemConfig> configs = lambdaQuery()
                .eq(SystemConfig::getConfigType, configType)
                .list();

        return configs.stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        c -> c.getConfigValue() != null ? c.getConfigValue() : "",
                        (v1, v2) -> v2
                ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String configKey, String configValue) {
        SystemConfig config = lambdaQuery()
                .eq(SystemConfig::getConfigKey, configKey)
                .one();

        if (config == null) {
            // 新增配置
            config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
            config.setConfigType(AI_CONFIG_TYPE);
            save(config);
        } else {
            // 更新配置
            config.setConfigValue(configValue);
            updateById(config);
        }

        // 清除缓存（租户隔离）
        redisTemplate.delete(buildCacheKey(configKey));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfigs(Map<String, String> configs) {
        configs.forEach(this::updateConfig);
    }

    @Override
    public AiConfigVO getAiConfig() {
        Map<String, String> configs = getConfigsByType(AI_CONFIG_TYPE);

        AiConfigVO vo = new AiConfigVO();

        // 如果 ai_provider 存在，说明用户已通过设置页面保存过配置，使用 DB 值
        // 如果不存在，说明从未通过设置页面配置，全部使用 application.yml 默认值
        boolean hasExplicitConfig = configs.containsKey("ai_provider");

        if (hasExplicitConfig) {
            vo.setProvider(configs.get("ai_provider"));
            vo.setApiUrl(configs.getOrDefault("ai_api_url", defaultApiUrl));
            vo.setApiKey(maskApiKey(configs.get("ai_api_key")));
            vo.setModel(configs.getOrDefault("ai_model", defaultModel));
            vo.setTemperature(parseDouble(configs.get("ai_temperature"), defaultTemperature));
            vo.setMaxTokens(parseInt(configs.get("ai_max_tokens"), defaultMaxTokens));
        } else {
            // 未通过设置页面配置过，返回 application.yml 默认值（通义千问）
            vo.setProvider("dashscope");
            vo.setApiUrl(defaultApiUrl);
            vo.setApiKey(maskApiKey(configs.get("ai_api_key")));
            vo.setModel(defaultModel);
            vo.setTemperature(defaultTemperature);
            vo.setMaxTokens(defaultMaxTokens);
        }

        // 获取最后更新时间
        SystemConfig anyConfig = lambdaQuery()
                .eq(SystemConfig::getConfigType, AI_CONFIG_TYPE)
                .orderByDesc(SystemConfig::getUpdateTime)
                .last("LIMIT 1")
                .one();
        if (anyConfig != null) {
            vo.setUpdateTime(anyConfig.getUpdateTime());
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAiConfig(AiConfigUpdateBO updateBO) {
        Map<String, String> configs = new HashMap<>();

        if (StrUtil.isNotBlank(updateBO.getProvider())) {
            configs.put("ai_provider", updateBO.getProvider());
        }
        if (StrUtil.isNotBlank(updateBO.getApiUrl())) {
            configs.put("ai_api_url", updateBO.getApiUrl());
        }
        if (StrUtil.isNotBlank(updateBO.getApiKey())) {
            configs.put("ai_api_key", updateBO.getApiKey());
        }
        if (StrUtil.isNotBlank(updateBO.getModel())) {
            configs.put("ai_model", updateBO.getModel());
        }
        if (updateBO.getTemperature() != null) {
            configs.put("ai_temperature", String.valueOf(updateBO.getTemperature()));
        }
        if (updateBO.getMaxTokens() != null) {
            configs.put("ai_max_tokens", String.valueOf(updateBO.getMaxTokens()));
        }

        updateConfigs(configs);

        // 重要：通知 DynamicChatClientProvider 刷新 ChatClient
        chatClientProvider.refreshChatClient();

        log.info("AI 配置已更新，ChatClient 已刷新");
    }

    @Override
    public AiConnectionTestVO testAiConnection(AiConfigUpdateBO configBO) {
        AiConnectionTestVO result = new AiConnectionTestVO();
        long startTime = System.currentTimeMillis();

        try {
            // 创建临时 ChatClient 进行测试
            ChatClient testClient = chatClientProvider.createTestChatClient(
                    configBO.getApiUrl(),
                    configBO.getApiKey(),
                    configBO.getModel(),
                    configBO.getTemperature(),
                    configBO.getMaxTokens()
            );

            // 发送简单测试消息
            String response = testClient.prompt()
                    .user("请回复 OK")
                    .call()
                    .content();

            result.setSuccess(true);
            result.setMessage(response);
            result.setModel(configBO.getModel());

        } catch (Exception e) {
            log.error("AI 连接测试失败: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setMessage("连接失败: " + extractErrorMessage(e));
        }

        result.setResponseTime(System.currentTimeMillis() - startTime);
        return result;
    }

    @Override
    public void clearConfigCache() {
        Set<String> keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("已清除 {} 个配置缓存", keys.size());
        }
    }

    /**
     * 更新配置并指定配置类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfigsWithType(Map<String, String> configs, String configType) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String configKey = entry.getKey();
            String configValue = entry.getValue();

            SystemConfig config = lambdaQuery()
                    .eq(SystemConfig::getConfigKey, configKey)
                    .one();

            if (config == null) {
                config = new SystemConfig();
                config.setConfigKey(configKey);
                config.setConfigValue(configValue);
                config.setConfigType(configType);
                save(config);
            } else {
                config.setConfigValue(configValue);
                updateById(config);
            }

            // 清除缓存（租户隔离）
            redisTemplate.delete(buildCacheKey(configKey));
        }
    }

    /**
     * API Key 脱敏处理
     */
    private String maskApiKey(String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
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

    /**
     * 根据 API URL 推断服务提供商
     */
    private String detectProvider(String apiUrl) {
        if (StrUtil.isBlank(apiUrl)) {
            return "dashscope";
        }
        if (apiUrl.contains("dashscope.aliyuncs.com")) {
            return "dashscope";
        }
        if (apiUrl.contains("api.openai.com")) {
            return "openai";
        }
        return "custom";
    }

    /**
     * 提取异常中的关键错误信息
     */
    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "未知错误";
        }
        // 处理常见错误
        if (message.contains("401")) {
            return "API Key 无效或已过期";
        }
        if (message.contains("403")) {
            return "API Key 权限不足";
        }
        if (message.contains("429")) {
            return "请求频率超限，请稍后重试";
        }
        if (message.contains("Connection refused") || message.contains("UnknownHost")) {
            return "无法连接到 API 服务器，请检查地址";
        }
        if (message.contains("timeout")) {
            return "连接超时";
        }
        // 截取关键信息
        if (message.length() > 100) {
            return message.substring(0, 100) + "...";
        }
        return message;
    }

    @Override
    public EnterpriseConfigVO getEnterpriseConfig() {
        Map<String, String> configs = getConfigsByType(ENTERPRISE_CONFIG_TYPE);

        EnterpriseConfigVO vo = new EnterpriseConfigVO();
        vo.setName(configs.getOrDefault("enterprise_name", null));
        vo.setDescription(configs.getOrDefault("enterprise_description", null));

        String logo = configs.getOrDefault("enterprise_logo", null);
        vo.setLogo(logo);
        if (StrUtil.isNotBlank(logo)) {
            vo.setLogoUrl(fileStorageService.getUrl(logo));
        }

        // 获取最近更新时间
        SystemConfig latestConfig = lambdaQuery()
                .eq(SystemConfig::getConfigType, ENTERPRISE_CONFIG_TYPE)
                .orderByDesc(SystemConfig::getUpdateTime)
                .last("LIMIT 1")
                .one();
        if (latestConfig != null) {
            vo.setUpdateTime(latestConfig.getUpdateTime());
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnterpriseConfig(EnterpriseConfigUpdateBO updateBO) {
        Map<String, String> configs = new HashMap<>();

        if (updateBO.getName() != null) {
            configs.put("enterprise_name", updateBO.getName());
        }
        if (updateBO.getLogo() != null) {
            configs.put("enterprise_logo", updateBO.getLogo());
        }
        if (updateBO.getDescription() != null) {
            configs.put("enterprise_description", updateBO.getDescription());
        }

        if (!configs.isEmpty()) {
            updateConfigsWithType(configs, ENTERPRISE_CONFIG_TYPE);
            log.info("企业信息配置已更新");
        }
    }
}
