package com.kakarote.ai_crm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.kakarote.ai_crm.entity.PO.ManagerUserRole;
import com.kakarote.ai_crm.mapper.ManagerRoleMapper;
import com.kakarote.ai_crm.service.IManagerUserRoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ManagerMenuServiceImplTest {

    @Mock
    private IManagerUserRoleService userRoleService;

    @Mock
    private ManagerRoleMapper roleMapper;

    @Test
    @SuppressWarnings("unchecked")
    void shouldTreatLegacyImportedSuperAdminRoleAsSuperAdmin() {
        ManagerMenuServiceImpl service = new ManagerMenuServiceImpl();
        LambdaQueryChainWrapper<ManagerUserRole> userRoleQuery = mock(LambdaQueryChainWrapper.class);
        Long userId = 310967000560087040L;
        ManagerUserRole legacySuperAdminUserRole = new ManagerUserRole();
        legacySuperAdminUserRole.setRoleId(310966977172647936L);

        ReflectionTestUtils.setField(service, "userRoleService", userRoleService);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);

        when(userRoleService.lambdaQuery()).thenReturn(userRoleQuery);
        when(userRoleQuery.eq(org.mockito.Mockito.<SFunction<ManagerUserRole, ?>>any(), eq(userId)))
                .thenReturn(userRoleQuery);
        when(userRoleQuery.list()).thenReturn(List.of(legacySuperAdminUserRole));
        when(roleMapper.selectCount(org.mockito.Mockito.any())).thenReturn(0L, 1L);

        Boolean isSuperAdmin = ReflectionTestUtils.invokeMethod(service, "isSuperAdmin", userId);

        assertThat(isSuperAdmin).isTrue();
        verify(roleMapper, times(2)).selectCount(org.mockito.Mockito.any());
    }
}
