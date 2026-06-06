package com.kakarote.ai_crm.service.impl;

import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.redis.Redis;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.EnterpriseConfigUpdateBO;
import com.kakarote.ai_crm.entity.BO.ExternalTenantRegisterBO;
import com.kakarote.ai_crm.entity.BO.RegisterBO;
import com.kakarote.ai_crm.entity.PO.CrmTenant;
import com.kakarote.ai_crm.entity.PO.ManagerUser;
import com.kakarote.ai_crm.service.ICrmTenantService;
import com.kakarote.ai_crm.service.ICustomFieldService;
import com.kakarote.ai_crm.service.IManagerMenuService;
import com.kakarote.ai_crm.service.IManagerRoleMenuService;
import com.kakarote.ai_crm.service.IManagerRoleService;
import com.kakarote.ai_crm.service.IManagerUserRoleService;
import com.kakarote.ai_crm.service.ISystemConfigService;
import com.kakarote.ai_crm.service.ManageUserService;
import com.kakarote.ai_crm.service.WeKnoraClient;
import com.kakarote.ai_crm.utils.CloudUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    @Mock
    private ICrmTenantService tenantService;

    @Mock
    private com.kakarote.ai_crm.mapper.ManagerDeptMapper deptMapper;

    @Mock
    private IManagerRoleService roleService;

    @Mock
    private IManagerMenuService menuService;

    @Mock
    private IManagerRoleMenuService roleMenuService;

    @Mock
    private ManageUserService manageUserService;

    @Mock
    private IManagerUserRoleService userRoleService;

    @Mock
    private ICustomFieldService customFieldService;

    @Mock
    private ISystemConfigService systemConfigService;

    @Mock
    private WeKnoraClient weKnoraClient;

    @Mock
    private CloudUtil cloudUtil;

    @Mock
    private Redis redis;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    @Test
    void sendEmail_shouldRejectAlreadyRegisteredEmail_forRegisterScene() {
        when(tenantService.count(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> registrationService.sendEmail("Used@Example.com", 1)
        );

        assertEquals(SystemCodeEnum.SYSTEM_EMAIL_ALREADY_REGISTERED.getCode(), exception.getCode());
        verify(cloudUtil, never()).sendVerificationCodeEmail(anyString(), anyString(), anyString(), anyString());
        verify(redis, never()).ttl(anyString());
        verify(redis, never()).setex(any(), anyInt(), any());
    }

    @Test
    void sendEmail_shouldAllowEnterpriseRegistration_whenEmailOnlyBelongsToEmployee() {
        when(tenantService.count(any())).thenReturn(0L);
        when(redis.ttl("register:email:code:new@example.com")).thenReturn(0L);
        when(cloudUtil.sendVerificationCodeEmail(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        registrationService.sendEmail("new@example.com", 1);

        verify(cloudUtil).sendVerificationCodeEmail(
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
        verify(redis).setex(anyString(), anyInt(), anyString());
        verify(manageUserService, never()).queryUsersByUsername(anyString());
    }

    @Test
    void register_shouldRejectAlreadyRegisteredEmail() {
        RegisterBO registerBO = new RegisterBO();
        registerBO.setEmail("owner@example.com");
        registerBO.setPassword("123456");
        registerBO.setVerificationCode("123456");
        registerBO.setCompanyName("Acme AI");
        registerBO.setRealname("Owner");

        when(redis.get("register:email:code:owner@example.com")).thenReturn("123456");
        when(tenantService.count(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> registrationService.register(registerBO));

        assertEquals(SystemCodeEnum.SYSTEM_EMAIL_ALREADY_REGISTERED.getCode(), exception.getCode());
        verify(tenantService, never()).save(any(CrmTenant.class));
        verify(manageUserService, never()).save(any(ManagerUser.class));
    }

    @Test
    void register_shouldAllowEnterpriseRegistration_whenEmailOnlyBelongsToEmployee() {
        RegisterBO registerBO = new RegisterBO();
        registerBO.setEmail("owner@example.com");
        registerBO.setPassword("123456");
        registerBO.setVerificationCode("123456");
        registerBO.setCompanyName("Acme AI");
        registerBO.setRealname("Owner");

        when(redis.get("register:email:code:owner@example.com")).thenReturn("123456");
        when(tenantService.count(any())).thenReturn(0L);
        when(passwordEncoder.encode("123456")).thenReturn("encoded-password");
        when(weKnoraClient.isEnabled()).thenReturn(false);
        when(menuService.list()).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            CrmTenant tenant = invocation.getArgument(0);
            tenant.setTenantId(10001L);
            return true;
        }).when(tenantService).save(any(CrmTenant.class));

        registrationService.register(registerBO);

        verify(tenantService).save(any(CrmTenant.class));
        verify(tenantService).save(org.mockito.ArgumentMatchers.argThat(tenant ->
                ICrmTenantService.DEFAULT_GIFT_CREDIT_TOTAL == tenant.getGiftCreditTotal()
                        && Long.valueOf(0L).equals(tenant.getGiftCreditUsed())
        ));
        verify(systemConfigService).updateEnterpriseConfig(any(EnterpriseConfigUpdateBO.class));
        verify(manageUserService).save(any(ManagerUser.class));
        verify(manageUserService, never()).queryUsersByUsername(anyString());
    }

    @Test
    void registerExternalTenantShouldGenerateUnknownPasswordWhenPasswordIsMissing() {
        ExternalTenantRegisterBO registerBO = new ExternalTenantRegisterBO();
        registerBO.setUsername("user_a");
        registerBO.setCompanyName("Corp One");
        registerBO.setRealname("Alice");
        registerBO.setEmailVerificationRequired(Boolean.FALSE);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-generated-password");
        when(weKnoraClient.isEnabled()).thenReturn(false);
        when(menuService.list()).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            CrmTenant tenant = invocation.getArgument(0);
            tenant.setTenantId(10002L);
            return true;
        }).when(tenantService).save(any(CrmTenant.class));

        registrationService.registerExternalTenant(registerBO);

        ArgumentCaptor<ManagerUser> userCaptor = ArgumentCaptor.forClass(ManagerUser.class);
        verify(manageUserService).save(userCaptor.capture());
        assertEquals("user_a", userCaptor.getValue().getUsername());
        assertEquals("Alice", userCaptor.getValue().getRealname());
        assertEquals(null, userCaptor.getValue().getEmail());
        assertEquals("encoded-generated-password", userCaptor.getValue().getPassword());
        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertNotNull(passwordCaptor.getValue());
        assertTrue(passwordCaptor.getValue().length() >= 16);
    }

    @Test
    void loginUserMapperShouldQueryByUsernameEmailOrMobile() throws Exception {
        String mapperXml = Files.readString(Path.of("src/main/resources/mapper/ManageUserMapper.xml"));

        assertTrue(mapperXml.contains("a.username=#{username}"));
        assertTrue(mapperXml.contains("a.email=#{username}"));
        assertTrue(mapperXml.contains("a.mobile=#{username}"));
    }
}
