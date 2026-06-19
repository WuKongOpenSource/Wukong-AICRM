package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakarote.ai_crm.ai.provider.AiProviderRegistry;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.AiConfigUpdateBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiCaptchaProxyBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiCompleteMobileBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiPurchaseCreateBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiRegisterAndSaveBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiSmsCodeBO;
import com.kakarote.ai_crm.entity.VO.AiConfigVO;
import com.kakarote.ai_crm.entity.VO.ExternalAiRegisterAndSaveVO;
import com.kakarote.ai_crm.service.ISystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class ExternalAiProviderRegistrationService {

    public static final String PROVIDER_CODE = "wukong_external";
    public static final String DEFAULT_MODEL = "qwen3.6-plus";

    private static final String AI_CONFIG_TYPE = "ai";
    private static final String AI_PROVIDER_KEY = "ai_provider";
    private static final String AI_API_URL_KEY = "ai_api_url";
    private static final String AI_API_KEY_KEY = "ai_api_key";
    private static final String AI_MODEL_KEY = "ai_model";
    private static final String AI_TEMPERATURE_KEY = "ai_temperature";
    private static final String AI_MAX_TOKENS_KEY = "ai_max_tokens";
    private static final String AI_EXTRA_HEADERS_KEY = "ai_extra_headers";
    private static final String AI_PROVIDER_CONFIGS_KEY = "ai_provider_configs";
    private static final String WUKONG_EXTERNAL_MOBILE_COMPLETED_KEY = "ai_wukong_external_mobile_completed";

    private final RestOperations restOperations;
    private final ISystemConfigService systemConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ExternalAiProviderRegistrationService(ISystemConfigService systemConfigService) {
        this(createDefaultRestTemplate(), systemConfigService);
    }

    ExternalAiProviderRegistrationService(RestOperations restOperations, ISystemConfigService systemConfigService) {
        this.restOperations = restOperations;
        this.systemConfigService = systemConfigService;
    }

    public Object getCaptcha(ExternalAiCaptchaProxyBO request) {
        return postRemote(request.getApiUrl(), "/getCaptcha", safePayload(request.getPayload()));
    }

    public Object checkCaptcha(ExternalAiCaptchaProxyBO request) {
        return postRemote(request.getApiUrl(), "/checkCaptcha", safePayload(request.getPayload()));
    }

    public Object sendSmsCode(ExternalAiSmsCodeBO request) {
        assertWukongExternalMobileNotCompleted();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mobile", StrUtil.trim(request.getMobile()));
        payload.put("captchaVerification", StrUtil.trim(request.getCaptchaVerification()));
        return postRemote(request.getApiUrl(), "/sms-code", payload);
    }

    public synchronized void ensureWukongExternalProvider() {
        if (hasConfiguredWukongProvider()) {
            return;
        }
        registerAnonymousAndSave();
    }

    public ExternalAiRegisterAndSaveVO registerAndSave(ExternalAiRegisterAndSaveBO request) {
        String externalApiRoot = normalizeExternalApiRoot(request.getApiUrl());
        Map<String, Object> payload = new LinkedHashMap<>();
        putMobileRegistrationPayload(payload, request.getMobile(), request.getVerificationCode());
        if (StrUtil.isNotBlank(request.getAccountName())) {
            payload.put("accountName", StrUtil.trim(request.getAccountName()));
        }

        Object data = postRemote(externalApiRoot, "/register", payload);
        return saveRemoteRegistrationResult(externalApiRoot, data, request);
    }

    public ExternalAiRegisterAndSaveVO completeMobile(ExternalAiCompleteMobileBO request) {
        assertWukongExternalMobileNotCompleted();
        SavedExternalAiConfig savedConfig = resolveSavedWukongConfig();
        if (savedConfig == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "当前系统还没有可用的悟空云 AI 配置");
        }

        String externalApiRoot = normalizeExternalApiRoot(StrUtil.blankToDefault(request.getApiUrl(), savedConfig.apiUrl()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mobile", StrUtil.trim(request.getMobile()));
        payload.put("verificationCode", StrUtil.trim(request.getVerificationCode()));

        Object data = postRemoteWithBearer(externalApiRoot, "/complete-mobile", payload, savedConfig.apiKey());
        Map<String, Object> result = asMap(data);
        String apiKey = StrUtil.blankToDefault(valueAsString(result.get("apiKey")), savedConfig.apiKey());
        String model = StrUtil.blankToDefault(savedConfig.model(), DEFAULT_MODEL);
        saveWukongConfig(
                externalApiRoot,
                apiKey,
                model,
                savedConfig.temperature(),
                savedConfig.maxTokens(),
                savedConfig.extraHeadersJson()
        );
        systemConfigService.updateConfig(WUKONG_EXTERNAL_MOBILE_COMPLETED_KEY, "true");

        ExternalAiRegisterAndSaveVO vo = new ExternalAiRegisterAndSaveVO();
        vo.setProvider(PROVIDER_CODE);
        vo.setApiUrl(externalApiRoot);
        vo.setModel(model);
        vo.setKeyPrefix(valueAsString(result.get("keyPrefix")));
        vo.setApiKeyConfigured(Boolean.TRUE);
        return vo;
    }

    public Object getExternalApiPurchaseOptions() {
        SavedExternalAiConfig savedConfig = resolveSavedWukongConfigOrRegister();
        return getRemoteWithBearer(savedConfig.apiUrl(), "/purchase/options", savedConfig.apiKey());
    }

    public Object createExternalApiPurchaseOrder(ExternalAiPurchaseCreateBO request) {
        SavedExternalAiConfig savedConfig = resolveSavedWukongConfigOrRegister();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", StrUtil.trim(request.getPlanId()));
        payload.put("paymentChannel", StrUtil.trim(request.getPaymentChannel()));
        return postRemoteWithBearer(savedConfig.apiUrl(), "/purchase/orders", payload, savedConfig.apiKey());
    }

    public Object getExternalApiPurchaseOrder(String orderNo) {
        SavedExternalAiConfig savedConfig = resolveSavedWukongConfigOrRegister();
        return getRemoteWithBearer(savedConfig.apiUrl(), "/purchase/orders/" + StrUtil.trim(orderNo), savedConfig.apiKey());
    }

    public Object listExternalApiPurchaseOrders(Integer limit) {
        SavedExternalAiConfig savedConfig = resolveSavedWukongConfigOrRegister();
        return getRemoteWithBearer(savedConfig.apiUrl(), "/purchase/orders?limit=" + safeLimit(limit, 10), savedConfig.apiKey());
    }

    public Object getExternalApiUsage(Integer limit) {
        SavedExternalAiConfig savedConfig = resolveSavedWukongConfigOrRegister();
        return getRemoteWithBearer(savedConfig.apiUrl(), "/usage?limit=" + safeLimit(limit, 20), savedConfig.apiKey());
    }

    public static String normalizeExternalApiRoot(String apiUrl) {
        String normalized = StrUtil.trim(apiUrl);
        if (StrUtil.isBlank(normalized)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "远端服务地址不能为空");
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.matches("(?i).*/v1$")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.matches("(?i).*/external-api$")) {
            normalized = normalized + "/external-api";
        }
        return normalized;
    }

    private ExternalAiRegisterAndSaveVO registerAnonymousAndSave() {
        String externalApiRoot = normalizeExternalApiRoot(resolveDefaultApiUrl());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountName", "AI CRM 单机版");

        ExternalAiRegisterAndSaveBO request = new ExternalAiRegisterAndSaveBO();
        request.setApiUrl(externalApiRoot);
        request.setModel(DEFAULT_MODEL);
        request.setTemperature(0.7D);
        request.setMaxTokens(2048);

        Object data = postRemote(externalApiRoot, "/register", payload);
        return saveRemoteRegistrationResult(externalApiRoot, data, request);
    }

    private ExternalAiRegisterAndSaveVO saveRemoteRegistrationResult(String externalApiRoot,
                                                                    Object data,
                                                                    ExternalAiRegisterAndSaveBO request) {
        Map<String, Object> result = asMap(data);
        String apiKey = valueAsString(result.get("apiKey"));
        if (StrUtil.isBlank(apiKey)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "远端注册未返回 API Key");
        }

        String model = StrUtil.blankToDefault(StrUtil.trim(request.getModel()), DEFAULT_MODEL);
        saveWukongConfig(
                externalApiRoot,
                apiKey,
                model,
                request.getTemperature() != null ? request.getTemperature() : 0.7D,
                request.getMaxTokens() != null ? request.getMaxTokens() : 2048,
                StrUtil.nullToEmpty(request.getExtraHeadersJson()).trim()
        );

        ExternalAiRegisterAndSaveVO vo = new ExternalAiRegisterAndSaveVO();
        vo.setProvider(PROVIDER_CODE);
        vo.setApiUrl(externalApiRoot);
        vo.setModel(model);
        vo.setKeyPrefix(valueAsString(result.get("keyPrefix")));
        vo.setApiKeyConfigured(Boolean.TRUE);
        return vo;
    }

    private void saveWukongConfig(String externalApiRoot,
                                  String apiKey,
                                  String model,
                                  Double temperature,
                                  Integer maxTokens,
                                  String extraHeadersJson) {
        AiConfigUpdateBO updateBO = new AiConfigUpdateBO();
        updateBO.setProvider(PROVIDER_CODE);
        updateBO.setApiUrl(externalApiRoot);
        updateBO.setApiKey(apiKey);
        updateBO.setModel(model);
        updateBO.setTemperature(temperature != null ? temperature : 0.7D);
        updateBO.setMaxTokens(maxTokens != null ? maxTokens : 2048);
        updateBO.setExtraHeadersJson(StrUtil.nullToEmpty(extraHeadersJson).trim());
        systemConfigService.updateManagedWukongExternalAiConfig(updateBO);
    }

    private void assertWukongExternalMobileNotCompleted() {
        Map<String, String> configs = systemConfigService.getConfigsByType(AI_CONFIG_TYPE);
        if (Boolean.parseBoolean(configs.getOrDefault(WUKONG_EXTERNAL_MOBILE_COMPLETED_KEY, "false"))) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "手机号已完善，不能重复发送验证码或领取额度");
        }
    }

    private void putMobileRegistrationPayload(Map<String, Object> payload, String mobile, String verificationCode) {
        String normalizedMobile = StrUtil.trim(mobile);
        if (StrUtil.isBlank(normalizedMobile)) {
            return;
        }
        String normalizedVerificationCode = StrUtil.trim(verificationCode);
        if (StrUtil.isBlank(normalizedVerificationCode)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "填写手机号注册时验证码不能为空");
        }
        payload.put("mobile", normalizedMobile);
        payload.put("verificationCode", normalizedVerificationCode);
    }

    private boolean hasConfiguredWukongProvider() {
        AiConfigVO aiConfig = systemConfigService.getAiConfigDetail();
        if (aiConfig == null || aiConfig.getAvailableProviders() == null) {
            return false;
        }
        return aiConfig.getAvailableProviders().stream()
                .filter(provider -> PROVIDER_CODE.equalsIgnoreCase(provider.getValue()))
                .anyMatch(provider -> Boolean.TRUE.equals(provider.getConfigured())
                        && Boolean.TRUE.equals(provider.getApiKeyConfigured()));
    }

    private SavedExternalAiConfig resolveSavedWukongConfigOrRegister() {
        SavedExternalAiConfig savedConfig = resolveSavedWukongConfig();
        if (savedConfig != null) {
            return savedConfig;
        }
        ensureWukongExternalProvider();
        savedConfig = resolveSavedWukongConfig();
        if (savedConfig == null || StrUtil.isBlank(savedConfig.apiKey())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Wukong external AI is not configured");
        }
        return savedConfig;
    }

    private SavedExternalAiConfig resolveSavedWukongConfig() {
        Map<String, String> configs = systemConfigService.getConfigsByType(AI_CONFIG_TYPE);
        SavedExternalAiConfig storedConfig = resolveStoredWukongConfig(configs);
        if (storedConfig != null) {
            return storedConfig;
        }

        String provider = StrUtil.nullToEmpty(configs.get(AI_PROVIDER_KEY)).trim();
        String apiKey = StrUtil.nullToEmpty(configs.get(AI_API_KEY_KEY)).trim();
        if (!PROVIDER_CODE.equalsIgnoreCase(provider) || StrUtil.isBlank(apiKey)) {
            return null;
        }
        return new SavedExternalAiConfig(
                StrUtil.blankToDefault(configs.get(AI_API_URL_KEY), resolveDefaultApiUrl()),
                apiKey,
                StrUtil.blankToDefault(configs.get(AI_MODEL_KEY), DEFAULT_MODEL),
                parseDouble(configs.get(AI_TEMPERATURE_KEY), 0.7D),
                parseInt(configs.get(AI_MAX_TOKENS_KEY), 2048),
                StrUtil.blankToDefault(configs.get(AI_EXTRA_HEADERS_KEY), "")
        );
    }

    private SavedExternalAiConfig resolveStoredWukongConfig(Map<String, String> configs) {
        String providerConfigsJson = configs.get(AI_PROVIDER_CONFIGS_KEY);
        if (StrUtil.isBlank(providerConfigsJson)) {
            return null;
        }
        try {
            Map<String, Map<String, Object>> providerConfigs = objectMapper.readValue(
                    providerConfigsJson,
                    new TypeReference<Map<String, Map<String, Object>>>() {
                    }
            );
            Map<String, Object> wukongConfig = providerConfigs.get(PROVIDER_CODE);
            if (wukongConfig == null) {
                return null;
            }
            String apiKey = valueAsString(wukongConfig.get("apiKey")).trim();
            if (StrUtil.isBlank(apiKey)) {
                return null;
            }
            return new SavedExternalAiConfig(
                    valueAsString(wukongConfig.get("apiUrl")),
                    apiKey,
                    StrUtil.blankToDefault(valueAsString(wukongConfig.get("model")), DEFAULT_MODEL),
                    parseDouble(valueAsString(wukongConfig.get("temperature")), 0.7D),
                    parseInt(valueAsString(wukongConfig.get("maxTokens")), 2048),
                    valueAsString(wukongConfig.get("extraHeadersJson"))
            );
        } catch (Exception e) {
            log.warn("解析悟空云 AI 保存配置失败，将回退到当前激活配置: {}", e.getMessage());
            return null;
        }
    }

    private Object postRemote(String apiUrl, String path, Object payload) {
        String url = normalizeExternalApiRoot(apiUrl) + path;
        try {
            ResponseEntity<Map> response = restOperations.postForEntity(url, payload, Map.class);
            return unwrapRemoteResponse(response.getBody());
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, extractRemoteError(e));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用外部 AI 注册接口失败: {}", e.getMessage());
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "外部 AI 服务暂时不可用，请稍后重试");
        }
    }

    private Object getRemoteWithBearer(String apiUrl, String path, String apiKey) {
        String url = normalizeExternalApiRoot(apiUrl) + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        try {
            ResponseEntity<Map> response = restOperations.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            return unwrapRemoteResponse(response.getBody());
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, extractRemoteError(e));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to call external AI purchase or usage API: {}", e.getMessage());
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "External AI service is temporarily unavailable");
        }
    }

    private Object postRemoteWithBearer(String apiUrl, String path, Object payload, String apiKey) {
        String url = normalizeExternalApiRoot(apiUrl) + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        try {
            ResponseEntity<Map> response = restOperations.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
            return unwrapRemoteResponse(response.getBody());
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, extractRemoteError(e));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用外部 AI 手机号完善接口失败: {}", e.getMessage());
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "外部 AI 服务暂时不可用，请稍后重试");
        }
    }

    private int safeLimit(Integer limit, int defaultValue) {
        int value = limit == null ? defaultValue : limit;
        return Math.max(1, Math.min(value, 100));
    }

    private Object unwrapRemoteResponse(Map<?, ?> body) {
        if (body == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "外部 AI 服务未返回内容");
        }
        Object error = body.get("error");
        if (error instanceof Map<?, ?> errorMap) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, remoteMessage(errorMap));
        }

        Object code = body.get("code");
        if (code != null) {
            int resolvedCode = parseCode(code);
            if (resolvedCode != SystemCodeEnum.SYSTEM_OK.getCode()) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR,
                        StrUtil.blankToDefault(valueAsString(body.get("msg")), "外部 AI 服务返回失败"));
            }
            return body.get("data");
        }
        return body;
    }

    private String extractRemoteError(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        if (StrUtil.isNotBlank(body)) {
            return "外部 AI 服务返回错误：" + StrUtil.maxLength(body, 300);
        }
        return "外部 AI 服务返回错误：" + e.getStatusCode().value();
    }

    private Map<String, Object> safePayload(Map<String, Object> payload) {
        return payload != null ? payload : Map.of();
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "外部 AI 服务返回格式不正确");
    }

    private String remoteMessage(Map<?, ?> errorMap) {
        String message = valueAsString(errorMap.get("message"));
        String code = valueAsString(errorMap.get("code"));
        return StrUtil.isNotBlank(message)
                ? message
                : StrUtil.blankToDefault(code, "外部 AI 服务返回失败");
    }

    private int parseCode(Object code) {
        if (code instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(code, ""));
        } catch (NumberFormatException e) {
            return SystemCodeEnum.SYSTEM_ERROR.getCode();
        }
    }

    private String valueAsString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String resolveDefaultApiUrl() {
        return AiProviderRegistry.get(PROVIDER_CODE).getBaseUrl();
    }

    private Double parseDouble(String value, Double defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Integer parseInt(String value, Integer defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static RestTemplate createDefaultRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(requestFactory);
    }

    private record SavedExternalAiConfig(
            String apiUrl,
            String apiKey,
            String model,
            Double temperature,
            Integer maxTokens,
            String extraHeadersJson
    ) {
    }
}
