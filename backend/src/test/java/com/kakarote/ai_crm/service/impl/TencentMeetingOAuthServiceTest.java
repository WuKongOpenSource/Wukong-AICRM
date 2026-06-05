package com.kakarote.ai_crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.kakarote.ai_crm.common.redis.Redis;
import com.kakarote.ai_crm.entity.BO.LoginUser;
import com.kakarote.ai_crm.entity.PO.ManagerUser;
import com.kakarote.ai_crm.entity.PO.TencentMeetingCorpConfig;
import com.kakarote.ai_crm.entity.PO.TencentMeetingUserMapping;
import com.kakarote.ai_crm.mapper.TencentMeetingCorpConfigMapper;
import com.kakarote.ai_crm.mapper.TencentMeetingUserMappingMapper;
import com.kakarote.ai_crm.service.ManageUserService;
import com.kakarote.ai_crm.utils.SecretTextCipher;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentCaptor.forClass;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TencentMeetingOAuthServiceTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAuthorizeUrlShouldStoreStateAndUseThirdPartyAppConfig() {
        TencentMeetingOAuthService service = new TencentMeetingOAuthService(mock(RestTemplate.class));
        TencentMeetingCorpConfigMapper configMapper = mock(TencentMeetingCorpConfigMapper.class);
        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setAppId("corp-1");
        config.setSdkId("sdk-1");
        config.setAppSecretEncrypted(new SecretTextCipher("0123456789abcdef").encrypt("secret"));
        when(configMapper.selectLatestOAuthConfigIgnoreTenant()).thenReturn(config);
        Redis redis = mock(Redis.class);
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "userMappingMapper", mock(TencentMeetingUserMappingMapper.class));
        ReflectionTestUtils.setField(service, "secretTextCipher", new SecretTextCipher("0123456789abcdef"));
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "manageUserService", mock(ManageUserService.class));
        mockLoginUser();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("crm.example.com");
        when(request.getServerPort()).thenReturn(443);
        when(request.getRequestURI()).thenReturn("/tencent-meeting/oauth/authorize");

        var result = service.createAuthorizeUrl("https://crm.example.com/#/tencent-meetings", request);

        assertThat(result.getAuthorizeUrl()).contains("corp_id=corp-1");
        assertThat(result.getAuthorizeUrl()).contains("sdk_id=sdk-1");
        assertThat(result.getAuthorizeUrl()).contains("redirect_uri=");
        verify(redis).setex(anyString(), anyInt(), any());
        verify(configMapper).selectLatestOAuthConfigIgnoreTenant();
    }

    @Test
    void createAuthorizeUrlShouldUseConfiguredCallbackUrlInsteadOfRequestHost() {
        TencentMeetingOAuthService service = new TencentMeetingOAuthService(mock(RestTemplate.class));
        TencentMeetingCorpConfigMapper configMapper = mock(TencentMeetingCorpConfigMapper.class);
        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setAppId("219307879");
        config.setSdkId("32360100872");
        config.setAppSecretEncrypted(new SecretTextCipher("0123456789abcdef").encrypt("secret"));
        when(configMapper.selectLatestOAuthConfigIgnoreTenant()).thenReturn(config);
        Redis redis = mock(Redis.class);
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "userMappingMapper", mock(TencentMeetingUserMappingMapper.class));
        ReflectionTestUtils.setField(service, "secretTextCipher", new SecretTextCipher("0123456789abcdef"));
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "manageUserService", mock(ManageUserService.class));
        ReflectionTestUtils.setField(service, "configuredCallbackUrl", "https://aicrm-saas.5kcrm.cn/crmapi/tencent-meeting/oauth/callback");
        mockLoginUser();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("192.168.1.116");
        when(request.getServerPort()).thenReturn(8088);
        when(request.getRequestURI()).thenReturn("/tencent-meeting/oauth/authorize");

        var result = service.createAuthorizeUrl("https://aicrm-saas.5kcrm.cn/#/tencent-meetings", request);

        assertThat(result.getAuthorizeUrl()).contains("redirect_uri=https://aicrm-saas.5kcrm.cn/crmapi/tencent-meeting/oauth/callback");
        assertThat(result.getAuthorizeUrl()).doesNotContain("192.168.1.116");
    }

    @Test
    void createAuthorizeUrlShouldNotStoreCallbackUrlAsFrontendRedirect() {
        TencentMeetingOAuthService service = new TencentMeetingOAuthService(mock(RestTemplate.class));
        TencentMeetingCorpConfigMapper configMapper = mock(TencentMeetingCorpConfigMapper.class);
        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setAppId("219307879");
        config.setSdkId("32360100872");
        config.setAppSecretEncrypted(new SecretTextCipher("0123456789abcdef").encrypt("secret"));
        when(configMapper.selectLatestOAuthConfigIgnoreTenant()).thenReturn(config);
        Redis redis = mock(Redis.class);
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "userMappingMapper", mock(TencentMeetingUserMappingMapper.class));
        ReflectionTestUtils.setField(service, "secretTextCipher", new SecretTextCipher("0123456789abcdef"));
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "manageUserService", mock(ManageUserService.class));
        ReflectionTestUtils.setField(service, "configuredFrontendRedirectUrl", "https://aicrm-saas.5kcrm.cn/#/tencent-meetings");
        mockLoginUser();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("aicrm-saas.5kcrm.cn");
        when(request.getServerPort()).thenReturn(443);
        when(request.getRequestURI()).thenReturn("/tencent-meeting/oauth/authorize");

        service.createAuthorizeUrl("https://aicrm-saas.5kcrm.cn/crmapi/tencent-meeting/oauth/callback", request);

        var stateCaptor = forClass(String.class);
        verify(redis).setex(anyString(), anyInt(), stateCaptor.capture());
        assertThat(stateCaptor.getValue()).contains("\"redirect\":\"https://aicrm-saas.5kcrm.cn/#/tencent-meetings\"");
        assertThat(stateCaptor.getValue()).doesNotContain("/crmapi/tencent-meeting/oauth/callback");
    }

    @Test
    void getStatusShouldQueryOnlyActiveAccountForCurrentUser() {
        initTencentMeetingUserMappingTableInfo();
        TencentMeetingOAuthService service = new TencentMeetingOAuthService(mock(RestTemplate.class));
        TencentMeetingCorpConfigMapper configMapper = mock(TencentMeetingCorpConfigMapper.class);
        TencentMeetingUserMappingMapper userMappingMapper = mock(TencentMeetingUserMappingMapper.class);
        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setAppId("219307879");
        config.setSdkId("32360100872");
        when(configMapper.selectLatestOAuthConfigIgnoreTenant()).thenReturn(config);
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "userMappingMapper", userMappingMapper);
        mockLoginUser();

        service.getStatus();

        var wrapperCaptor = forClass(LambdaQueryWrapper.class);
        verify(userMappingMapper).selectOne(wrapperCaptor.capture());
        LambdaQueryWrapper<?> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("auth_status");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TencentMeetingOAuthService.AUTH_STATUS_ACTIVE);
    }

    private void initTencentMeetingUserMappingTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TencentMeetingUserMapping.class);
    }

    private void mockLoginUser() {
        ManagerUser user = new ManagerUser();
        user.setUserId(9L);
        user.setTenantId(99L);
        user.setUsername("user@example.com");
        user.setPassword("password");
        user.setStatus(1);
        LoginUser loginUser = new LoginUser();
        loginUser.setUser(user);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(loginUser, null));
    }
}
