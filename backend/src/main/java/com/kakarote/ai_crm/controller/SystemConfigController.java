package com.kakarote.ai_crm.controller;

import com.kakarote.ai_crm.common.auth.RequirePermission;
import com.kakarote.ai_crm.common.result.Result;
import com.kakarote.ai_crm.config.OidcConfig;
import com.kakarote.ai_crm.entity.BO.AiProviderActivateBO;
import com.kakarote.ai_crm.entity.BO.AiConfigUpdateBO;
import com.kakarote.ai_crm.entity.BO.EnterpriseConfigUpdateBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiCaptchaProxyBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiCompleteMobileBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiPurchaseCreateBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiRegisterAndSaveBO;
import com.kakarote.ai_crm.entity.BO.ExternalAiSmsCodeBO;
import com.kakarote.ai_crm.entity.BO.LoginUser;
import com.kakarote.ai_crm.entity.VO.AiConfigVO;
import com.kakarote.ai_crm.entity.VO.AiConnectionTestVO;
import com.kakarote.ai_crm.entity.VO.EnterpriseConfigVO;
import com.kakarote.ai_crm.entity.VO.ExternalAiRegisterAndSaveVO;
import com.kakarote.ai_crm.service.ISystemConfigService;
import com.kakarote.ai_crm.service.OidcService;
import com.kakarote.ai_crm.service.impl.ExternalAiProviderRegistrationService;
import com.kakarote.ai_crm.utils.RequestContextUtil;
import com.kakarote.ai_crm.utils.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/systemConfig")
@Tag(name = "System Config APIs")
public class SystemConfigController {

    @Autowired
    private ISystemConfigService systemConfigService;

    @Autowired
    private OidcService oidcService;

    @Autowired
    private OidcConfig oidcConfig;

    @Autowired
    private ExternalAiProviderRegistrationService externalAiProviderRegistrationService;

    @Value("${minio.enabled:false}")
    private boolean minioEnabled;

    @GetMapping("/ai")
    @Operation(summary = "Get AI config")
    public Result<AiConfigVO> getAiConfig() {
        return Result.ok(systemConfigService.getAiConfig());
    }

    @GetMapping("/ai/detail")
    @Operation(summary = "Get AI config detail")
    @RequirePermission("config:ai")
    public Result<AiConfigVO> getAiConfigDetail() {
        return Result.ok(systemConfigService.getAiConfigDetail());
    }

    @PostMapping("/ai/update")
    @Operation(summary = "Update AI config")
    @RequirePermission("config:ai")
    public Result<String> updateAiConfig(@Valid @RequestBody AiConfigUpdateBO updateBO) {
        systemConfigService.updateAiConfig(updateBO);
        return Result.ok();
    }

    @PostMapping("/ai/activate")
    @Operation(summary = "Activate saved AI provider")
    @RequirePermission("config:ai")
    public Result<String> activateAiProvider(@Valid @RequestBody AiProviderActivateBO activateBO) {
        systemConfigService.activateAiProvider(activateBO.getProvider());
        return Result.ok();
    }

    @PostMapping("/ai/useCustom")
    @Operation(summary = "Use saved custom AI config")
    @RequirePermission("config:ai")
    public Result<String> useCustomAiConfig() {
        systemConfigService.useCustomAiConfig();
        return Result.ok();
    }

    @PostMapping("/ai/test")
    @Operation(summary = "Test AI connection")
    @RequirePermission("config:ai")
    public Result<AiConnectionTestVO> testAiConnection(@Valid @RequestBody AiConfigUpdateBO configBO) {
        return Result.ok(systemConfigService.testAiConnection(configBO));
    }

    @PostMapping("/ai/external-api/getCaptcha")
    @Operation(summary = "代理获取外部 AI 图形验证码")
    @RequirePermission("config:ai")
    public Result<Object> externalAiGetCaptcha(@Valid @RequestBody ExternalAiCaptchaProxyBO request) {
        return Result.ok(externalAiProviderRegistrationService.getCaptcha(request));
    }

    @PostMapping("/ai/external-api/checkCaptcha")
    @Operation(summary = "代理校验外部 AI 图形验证码")
    @RequirePermission("config:ai")
    public Result<Object> externalAiCheckCaptcha(@Valid @RequestBody ExternalAiCaptchaProxyBO request) {
        return Result.ok(externalAiProviderRegistrationService.checkCaptcha(request));
    }

    @PostMapping("/ai/external-api/sms-code")
    @Operation(summary = "代理发送外部 AI 手机验证码")
    @RequirePermission("config:ai")
    public Result<Object> externalAiSmsCode(@Valid @RequestBody ExternalAiSmsCodeBO request) {
        return Result.ok(externalAiProviderRegistrationService.sendSmsCode(request));
    }

    @PostMapping("/ai/external-api/register-and-save")
    @Operation(summary = "注册外部 AI 并保存为系统模型")
    @RequirePermission("config:ai")
    public Result<ExternalAiRegisterAndSaveVO> externalAiRegisterAndSave(
            @Valid @RequestBody ExternalAiRegisterAndSaveBO request) {
        return Result.ok(externalAiProviderRegistrationService.registerAndSave(request));
    }

    @PostMapping("/ai/external-api/complete-mobile")
    @Operation(summary = "完善外部 AI 手机号")
    @RequirePermission("config:ai")
    public Result<ExternalAiRegisterAndSaveVO> externalAiCompleteMobile(
            @Valid @RequestBody ExternalAiCompleteMobileBO request) {
        return Result.ok(externalAiProviderRegistrationService.completeMobile(request));
    }

    @GetMapping("/ai/external-api/purchase/options")
    @Operation(summary = "Proxy External AI purchase options")
    @RequirePermission("config:ai")
    public Result<Object> externalAiPurchaseOptions() {
        return Result.ok(externalAiProviderRegistrationService.getExternalApiPurchaseOptions());
    }

    @PostMapping("/ai/external-api/purchase/orders")
    @Operation(summary = "Proxy External AI purchase order creation")
    @RequirePermission("config:ai")
    public Result<Object> externalAiCreatePurchaseOrder(@Valid @RequestBody ExternalAiPurchaseCreateBO request) {
        return Result.ok(externalAiProviderRegistrationService.createExternalApiPurchaseOrder(request));
    }

    @GetMapping("/ai/external-api/purchase/orders/{orderNo}")
    @Operation(summary = "Proxy External AI purchase order query")
    @RequirePermission("config:ai")
    public Result<Object> externalAiPurchaseOrder(@PathVariable String orderNo) {
        return Result.ok(externalAiProviderRegistrationService.getExternalApiPurchaseOrder(orderNo));
    }

    @GetMapping("/ai/external-api/purchase/orders")
    @Operation(summary = "Proxy External AI purchase order list")
    @RequirePermission("config:ai")
    public Result<Object> externalAiPurchaseOrders(
            @RequestParam(name = "limit", defaultValue = "10") Integer limit) {
        return Result.ok(externalAiProviderRegistrationService.listExternalApiPurchaseOrders(limit));
    }

    @GetMapping("/ai/external-api/usage")
    @Operation(summary = "Proxy External AI usage")
    @RequirePermission("config:ai")
    public Result<Object> externalAiUsage(
            @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        return Result.ok(externalAiProviderRegistrationService.getExternalApiUsage(limit));
    }

    @GetMapping("/byType/{type}")
    @Operation(summary = "Get config by type")
    @RequirePermission("config")
    public Result<Map<String, String>> getConfigsByType(
            @PathVariable("type") @Parameter(description = "Config type") String type) {
        return Result.ok(systemConfigService.getConfigsByType(type));
    }

    @PostMapping("/clearCache")
    @Operation(summary = "Clear config cache")
    @RequirePermission("config")
    public Result<String> clearCache() {
        systemConfigService.clearConfigCache();
        return Result.ok();
    }

    @GetMapping("/minio/consoleUrl")
    @Operation(summary = "Get MinIO console url")
    @RequirePermission("config:storage")
    public Result<Map<String, Object>> getMinioConsoleUrl() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", minioEnabled);
        String consoleUrl = RequestContextUtil.getMinioConsoleUrl(oidcConfig.getMinioConsolePort());
        result.put("consoleUrl", consoleUrl);
        return Result.ok(result);
    }

    @GetMapping("/minio/ssoUrl")
    @Operation(summary = "Get MinIO SSO url")
    @RequirePermission("config:storage")
    public Result<Map<String, Object>> getMinioSsoUrl() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", minioEnabled);

        if (!minioEnabled) {
            result.put("ssoUrl", null);
            return Result.ok(result);
        }

        LoginUser loginUser = UserUtil.getLoginUser();
        String sessionToken = oidcService.createSession(loginUser);

        String baseUrl = RequestContextUtil.getBaseUrl();
        String ssoUrl = baseUrl + "/oauth2/minio-sso?session_token=" + sessionToken;
        result.put("ssoUrl", ssoUrl);
        return Result.ok(result);
    }

    @GetMapping("/enterprise")
    @Operation(summary = "Get enterprise config")
    public Result<EnterpriseConfigVO> getEnterpriseConfig() {
        return Result.ok(systemConfigService.getEnterpriseConfig());
    }

    @PostMapping("/enterprise/update")
    @Operation(summary = "Update enterprise config")
    @RequirePermission("config:storage")
    public Result<String> updateEnterpriseConfig(@Valid @RequestBody EnterpriseConfigUpdateBO updateBO) {
        systemConfigService.updateEnterpriseConfig(updateBO);
        return Result.ok();
    }
}
