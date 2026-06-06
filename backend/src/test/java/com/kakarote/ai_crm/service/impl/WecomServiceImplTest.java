package com.kakarote.ai_crm.service.impl;

import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.entity.VO.WecomConfigVO;
import com.kakarote.ai_crm.mapper.CustomerMapper;
import com.kakarote.ai_crm.mapper.ExternalAuthIdentityMapper;
import com.kakarote.ai_crm.mapper.WecomConversationMapper;
import com.kakarote.ai_crm.mapper.WecomCorpConfigMapper;
import com.kakarote.ai_crm.mapper.WecomEmployeeMapper;
import com.kakarote.ai_crm.mapper.WecomExternalCustomerMapper;
import com.kakarote.ai_crm.mapper.WecomMessageMapper;
import com.kakarote.ai_crm.mapper.WecomSyncLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WecomServiceImplTest {

    @Test
    void getConfigShouldExposeThirdPartyStateWhenTenantHasNotAuthorizedYet() {
        WecomServiceImpl service = newService();
        WecomOpenPlatformService openPlatformService = mapper(service, "openPlatformService");
        when(openPlatformService.isUsable()).thenReturn(true);

        WecomConfigVO config = service.getConfig();

        assertThat(config.getThirdPartyEnabled()).isTrue();
        assertThat(config.getThirdPartyAuthorized()).isFalse();
    }

    @Test
    void syncOrganizationShouldAskForWecomAuthorizationWhenConfigIsMissing() {
        WecomServiceImpl service = newService();
        WecomOpenPlatformService openPlatformService = mapper(service, "openPlatformService");
        when(openPlatformService.isUsable()).thenReturn(true);

        assertThatThrownBy(service::syncOrganization)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Please authorize WeCom third-party app first");
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(WecomServiceImpl service, String fieldName) {
        return (T) ReflectionTestUtils.getField(service, fieldName);
    }

    private static WecomServiceImpl newService() {
        WecomServiceImpl service = new WecomServiceImpl();
        ReflectionTestUtils.setField(service, "configMapper", mock(WecomCorpConfigMapper.class));
        ReflectionTestUtils.setField(service, "employeeMapper", mock(WecomEmployeeMapper.class));
        ReflectionTestUtils.setField(service, "externalCustomerMapper", mock(WecomExternalCustomerMapper.class));
        ReflectionTestUtils.setField(service, "conversationMapper", mock(WecomConversationMapper.class));
        ReflectionTestUtils.setField(service, "messageMapper", mock(WecomMessageMapper.class));
        ReflectionTestUtils.setField(service, "syncLogMapper", mock(WecomSyncLogMapper.class));
        ReflectionTestUtils.setField(service, "customerMapper", mock(CustomerMapper.class));
        ReflectionTestUtils.setField(service, "identityMapper", mock(ExternalAuthIdentityMapper.class));
        ReflectionTestUtils.setField(service, "syncService", mock(WecomSyncServiceImpl.class));
        ReflectionTestUtils.setField(service, "openPlatformService", mock(WecomOpenPlatformService.class));
        ReflectionTestUtils.setField(service, "bindingService", mock(WecomCustomerBindingServiceImpl.class));
        ReflectionTestUtils.setField(service, "dataPermissionService", mock(com.kakarote.ai_crm.service.DataPermissionService.class));
        return service;
    }
}
