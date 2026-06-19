package com.kakarote.ai_crm.service.impl;

import com.kakarote.ai_crm.entity.BO.ExternalAiPurchaseCreateBO;
import com.kakarote.ai_crm.service.ISystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalAiPurchaseProxyServiceTest {

    @Test
    void getUsageShouldForwardSavedApiKeyAsBearerToken() {
        RestOperations restOperations = mock(RestOperations.class);
        ISystemConfigService systemConfigService = mockSystemConfig();
        when(restOperations.exchange(
                eq("https://www.72crm.com/crmapi/external-api/usage?limit=20"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenAnswer(invocation -> {
            HttpEntity<?> entity = invocation.getArgument(2);
            assertThat(entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-saved");
            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "data", Map.of("creditRemaining", 120L)
            ));
        });

        ExternalAiProviderRegistrationService service =
                new ExternalAiProviderRegistrationService(restOperations, systemConfigService);

        Object result = service.getExternalApiUsage(20);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("creditRemaining")).isEqualTo(120L);
    }

    @Test
    void createPurchaseOrderShouldPostWithBearerToken() {
        RestOperations restOperations = mock(RestOperations.class);
        ISystemConfigService systemConfigService = mockSystemConfig();
        when(restOperations.exchange(
                eq("https://www.72crm.com/crmapi/external-api/purchase/orders"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenAnswer(invocation -> {
            HttpEntity<?> entity = invocation.getArgument(2);
            assertThat(entity.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-saved");
            assertThat(entity.getBody()).isInstanceOf(Map.class);
            assertThat(((Map<?, ?>) entity.getBody()).get("planId")).isEqualTo("starter-5000");
            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "data", Map.of("orderNo", "EP123", "status", "PENDING")
            ));
        });

        ExternalAiProviderRegistrationService service =
                new ExternalAiProviderRegistrationService(restOperations, systemConfigService);
        ExternalAiPurchaseCreateBO request = new ExternalAiPurchaseCreateBO();
        request.setPlanId("starter-5000");
        request.setPaymentChannel("wechat");

        Object result = service.createExternalApiPurchaseOrder(request);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) result).get("orderNo")).isEqualTo("EP123");
    }

    private ISystemConfigService mockSystemConfig() {
        ISystemConfigService systemConfigService = mock(ISystemConfigService.class);
        when(systemConfigService.getConfigsByType("ai")).thenReturn(Map.of(
                "ai_provider", "wukong_external",
                "ai_api_url", "https://www.72crm.com/crmapi/external-api",
                "ai_api_key", "sk-saved",
                "ai_model", "qwen3.6-plus",
                "ai_temperature", "0.7",
                "ai_max_tokens", "2048"
        ));
        return systemConfigService;
    }
}
