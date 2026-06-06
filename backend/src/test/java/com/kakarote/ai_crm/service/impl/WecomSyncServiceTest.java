package com.kakarote.ai_crm.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kakarote.ai_crm.entity.BO.WecomSyncRunBO;
import com.kakarote.ai_crm.entity.PO.Customer;
import com.kakarote.ai_crm.entity.PO.ManagerDept;
import com.kakarote.ai_crm.entity.PO.ManagerUser;
import com.kakarote.ai_crm.entity.PO.WecomConversation;
import com.kakarote.ai_crm.entity.PO.WecomCorpConfig;
import com.kakarote.ai_crm.entity.PO.WecomEmployee;
import com.kakarote.ai_crm.entity.PO.WecomExternalCustomer;
import com.kakarote.ai_crm.entity.PO.WecomExternalCustomerFollow;
import com.kakarote.ai_crm.entity.PO.WecomMessage;
import com.kakarote.ai_crm.entity.PO.WecomSyncCursor;
import com.kakarote.ai_crm.entity.PO.WecomSyncLog;
import com.kakarote.ai_crm.entity.VO.WecomSyncStatusVO;
import com.kakarote.ai_crm.mapper.CustomerMapper;
import com.kakarote.ai_crm.mapper.ManageUserMapper;
import com.kakarote.ai_crm.mapper.ManagerDeptMapper;
import com.kakarote.ai_crm.mapper.WecomConversationMapper;
import com.kakarote.ai_crm.mapper.WecomEmployeeMapper;
import com.kakarote.ai_crm.mapper.WecomExternalCustomerFollowMapper;
import com.kakarote.ai_crm.mapper.WecomExternalCustomerMapper;
import com.kakarote.ai_crm.mapper.WecomMessageMapper;
import com.kakarote.ai_crm.mapper.WecomSyncCursorMapper;
import com.kakarote.ai_crm.mapper.WecomSyncLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WecomSyncServiceTest {

    @Test
    void runSyncShouldSaveArchiveMessagesAndAdvanceCursor() {
        WecomSyncServiceImpl service = newService();
        WecomFinanceArchiveGateway archiveGateway = mapper(service, "archiveGateway");
        WecomConversationMapper conversationMapper = mapper(service, "conversationMapper");
        WecomMessageMapper messageMapper = mapper(service, "messageMapper");
        WecomSyncCursorMapper cursorMapper = mapper(service, "cursorMapper");

        WecomCorpConfig config = config();
        config.setArchiveEnabled(true);
        WecomSyncRunBO runBO = new WecomSyncRunBO();
        runBO.setSyncEmployees(false);
        runBO.setSyncCustomers(false);
        runBO.setSyncConversations(true);
        runBO.setArchiveLimit(10);
        JSONObject rawMessage = textArchiveMessage();

        when(cursorMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(archiveGateway.fetchMessages(eq(config), eq(0L), eq(10))).thenReturn(List.of(rawMessage));
        when(conversationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            WecomConversation conversation = invocation.getArgument(0);
            conversation.setId(300L);
            return 1;
        }).when(conversationMapper).insert(any(WecomConversation.class));
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        WecomSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("success");
        assertThat(status.getSavedCount()).isEqualTo(1);
        ArgumentCaptor<WecomMessage> messageCaptor = ArgumentCaptor.forClass(WecomMessage.class);
        verify(messageMapper).insert(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getCorpId()).isEqualTo("corp_1");
        assertThat(messageCaptor.getValue().getConversationId()).isEqualTo(300L);
        assertThat(messageCaptor.getValue().getMsgId()).isEqualTo("msg_1");
        assertThat(messageCaptor.getValue().getContentText()).isEqualTo("hello");

        ArgumentCaptor<WecomSyncCursor> cursorCaptor = ArgumentCaptor.forClass(WecomSyncCursor.class);
        verify(cursorMapper).insert(cursorCaptor.capture());
        assertThat(cursorCaptor.getValue().getSeq()).isEqualTo(7L);
    }

    @Test
    void runSyncShouldRecordFailedStatusWhenRealApiFails() {
        WecomSyncServiceImpl service = newService();
        WecomTokenService tokenService = mapper(service, "tokenService");
        WecomSyncLogMapper syncLogMapper = mapper(service, "syncLogMapper");

        WecomCorpConfig config = config();
        WecomSyncRunBO runBO = new WecomSyncRunBO();
        runBO.setSyncEmployees(true);
        when(tokenService.fetchAppAccessToken(config)).thenThrow(new RuntimeException("network down"));

        WecomSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("failed");
        assertThat(status.getFailedCount()).isEqualTo(1);
        assertThat(status.getLastSyncError()).contains("network down");
        ArgumentCaptor<WecomSyncLog> logCaptor = ArgumentCaptor.forClass(WecomSyncLog.class);
        verify(syncLogMapper).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("failed");
    }

    @Test
    void runSyncShouldMirrorExternalCustomerIntoCrmCustomer() {
        WecomSyncServiceImpl service = newService();
        WecomTokenService tokenService = mapper(service, "tokenService");
        WecomApiClient apiClient = mapper(service, "apiClient");
        WecomExternalCustomerMapper externalCustomerMapper = mapper(service, "externalCustomerMapper");
        CustomerMapper customerMapper = mapper(service, "customerMapper");

        WecomCorpConfig config = config();
        WecomSyncRunBO runBO = new WecomSyncRunBO();
        runBO.setSyncEmployees(false);
        runBO.setSyncCustomers(true);
        runBO.setSyncConversations(false);
        when(tokenService.fetchContactAccessToken(config)).thenReturn("contact-token");
        when(apiClient.listFollowUsers("contact-token")).thenReturn(List.of("sales_1"));
        when(apiClient.listExternalUserIds("contact-token", "sales_1")).thenReturn(List.of("wm_ext_1"));
        when(apiClient.getExternalCustomer("contact-token", "wm_ext_1")).thenReturn(externalCustomerPayload());
        when(externalCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(customerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setCustomerId(9001L);
            return 1;
        }).when(customerMapper).insert(any(Customer.class));

        WecomSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("success");
        assertThat(status.getSavedCount()).isEqualTo(1);
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).insert(customerCaptor.capture());
        Customer customer = customerCaptor.getValue();
        assertThat(customer.getCompanyName()).isEqualTo("悟空科技有限公司");
        assertThat(customer.getPrimaryContactName()).isEqualTo("孙悟空");
        assertThat(customer.getSource()).isEqualTo("企业微信");
        assertThat(customer.getWecomCustomer()).isTrue();
        assertThat(customer.getWecomCorpId()).isEqualTo("corp_1");
        assertThat(customer.getWecomExternalUserId()).isEqualTo("wm_ext_1");
        assertThat(customer.getWecomSyncedAt()).isNotNull();

        ArgumentCaptor<WecomExternalCustomer> wecomCustomerCaptor = ArgumentCaptor.forClass(WecomExternalCustomer.class);
        verify(externalCustomerMapper).insert(wecomCustomerCaptor.capture());
        assertThat(wecomCustomerCaptor.getValue().getBindStatus()).isEqualTo("BOUND");
        assertThat(wecomCustomerCaptor.getValue().getCustomerId()).isEqualTo(9001L);
    }

    @Test
    void syncVisibleCustomersShouldUseBoundWecomMemberAndCurrentUserOwner() {
        WecomSyncServiceImpl service = newService();
        WecomTokenService tokenService = mapper(service, "tokenService");
        WecomApiClient apiClient = mapper(service, "apiClient");
        WecomExternalCustomerMapper externalCustomerMapper = mapper(service, "externalCustomerMapper");
        CustomerMapper customerMapper = mapper(service, "customerMapper");

        WecomCorpConfig config = config();
        when(tokenService.fetchContactAccessToken(config)).thenReturn("contact-token");
        when(apiClient.listExternalUserIds("contact-token", "sales_1")).thenReturn(List.of("wm_ext_1"));
        when(apiClient.getExternalCustomer("contact-token", "wm_ext_1")).thenReturn(externalCustomerPayload());
        when(externalCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(customerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setCustomerId(9001L);
            return 1;
        }).when(customerMapper).insert(any(Customer.class));

        int saved = service.syncVisibleCustomers(config, "sales_1", 77L);

        assertThat(saved).isEqualTo(1);
        verify(apiClient).listExternalUserIds("contact-token", "sales_1");
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerMapper).insert(customerCaptor.capture());
        assertThat(customerCaptor.getValue().getOwnerId()).isEqualTo(77L);
        assertThat(customerCaptor.getValue().getWecomCustomer()).isTrue();
    }

    @Test
    void runSyncShouldContinueCustomerBatchWhenOneExternalCustomerFails() {
        WecomSyncServiceImpl service = newService();
        WecomTokenService tokenService = mapper(service, "tokenService");
        WecomApiClient apiClient = mapper(service, "apiClient");
        WecomExternalCustomerMapper externalCustomerMapper = mapper(service, "externalCustomerMapper");
        CustomerMapper customerMapper = mapper(service, "customerMapper");

        WecomCorpConfig config = config();
        WecomSyncRunBO runBO = new WecomSyncRunBO();
        runBO.setSyncEmployees(false);
        runBO.setSyncCustomers(true);
        runBO.setSyncConversations(false);
        when(tokenService.fetchContactAccessToken(config)).thenReturn("contact-token");
        when(apiClient.listFollowUsers("contact-token")).thenReturn(List.of("sales_1"));
        when(apiClient.listExternalUserIds("contact-token", "sales_1")).thenReturn(List.of("wm_bad", "wm_ext_1"));
        when(apiClient.getExternalCustomer("contact-token", "wm_bad")).thenThrow(new RuntimeException("bad customer"));
        when(apiClient.getExternalCustomer("contact-token", "wm_ext_1")).thenReturn(externalCustomerPayload());
        when(externalCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(customerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setCustomerId(9001L);
            return 1;
        }).when(customerMapper).insert(any(Customer.class));

        WecomSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("success");
        assertThat(status.getFetchedCount()).isEqualTo(2);
        assertThat(status.getSavedCount()).isEqualTo(1);
        assertThat(status.getFailedCount()).isEqualTo(1);
        assertThat(status.getLastSyncError()).contains("bad customer");
        verify(apiClient).getExternalCustomer("contact-token", "wm_ext_1");
    }

    @Test
    void runSyncShouldPersistExternalCustomerFollowUsers() {
        WecomSyncServiceImpl service = newService();
        WecomTokenService tokenService = mapper(service, "tokenService");
        WecomApiClient apiClient = mapper(service, "apiClient");
        WecomExternalCustomerMapper externalCustomerMapper = mapper(service, "externalCustomerMapper");
        CustomerMapper customerMapper = mapper(service, "customerMapper");
        WecomEmployeeMapper employeeMapper = mapper(service, "employeeMapper");
        WecomExternalCustomerFollowMapper followMapper = mock(WecomExternalCustomerFollowMapper.class);
        ReflectionTestUtils.setField(service, "externalCustomerFollowMapper", followMapper);

        WecomCorpConfig config = config();
        WecomSyncRunBO runBO = new WecomSyncRunBO();
        runBO.setSyncEmployees(false);
        runBO.setSyncCustomers(true);
        runBO.setSyncConversations(false);
        when(tokenService.fetchContactAccessToken(config)).thenReturn("contact-token");
        when(apiClient.listFollowUsers("contact-token")).thenReturn(List.of("sales_1"));
        when(apiClient.listExternalUserIds("contact-token", "sales_1")).thenReturn(List.of("wm_ext_1"));
        when(apiClient.getExternalCustomer("contact-token", "wm_ext_1")).thenReturn(externalCustomerPayloadWithFollowUser());
        when(externalCustomerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(customerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setCustomerId(9001L);
            return 1;
        }).when(customerMapper).insert(any(Customer.class));
        doAnswer(invocation -> {
            WecomExternalCustomer customer = invocation.getArgument(0);
            customer.setId(8001L);
            return 1;
        }).when(externalCustomerMapper).insert(any(WecomExternalCustomer.class));
        WecomEmployee employee = new WecomEmployee();
        employee.setId(7001L);
        employee.setUserId("sales_1");
        when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(employee);
        when(followMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        WecomSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("success");
        ArgumentCaptor<WecomExternalCustomerFollow> followCaptor = ArgumentCaptor.forClass(WecomExternalCustomerFollow.class);
        verify(followMapper).insert(followCaptor.capture());
        WecomExternalCustomerFollow follow = followCaptor.getValue();
        assertThat(follow.getCorpId()).isEqualTo("corp_1");
        assertThat(follow.getExternalCustomerId()).isEqualTo(8001L);
        assertThat(follow.getExternalUserId()).isEqualTo("wm_ext_1");
        assertThat(follow.getEmployeeId()).isEqualTo(7001L);
        assertThat(follow.getEmployeeUserId()).isEqualTo("sales_1");
        assertThat(follow.getRemark()).isEqualTo("VIP");
        assertThat(follow.getAddWay()).isEqualTo(1);
        assertThat(follow.getState()).isEqualTo("state_1");
        assertThat(follow.getRelationCreateTime()).isNotNull();
        assertThat(follow.getStatus()).isEqualTo(1);
    }

    @Test
    void syncOrganizationShouldUpsertDepartmentsUsersAndEmployeeMappings() {
        WecomSyncServiceImpl service = newService();
        WecomTokenService tokenService = mapper(service, "tokenService");
        WecomApiClient apiClient = mapper(service, "apiClient");
        ManagerDeptMapper deptMapper = mapper(service, "deptMapper");
        ManageUserMapper userMapper = mapper(service, "userMapper");
        WecomEmployeeMapper employeeMapper = mapper(service, "employeeMapper");

        WecomCorpConfig config = config();
        when(tokenService.fetchAppAccessToken(config)).thenReturn("app-token");
        when(apiClient.listDepartments("app-token")).thenReturn(List.of(
                departmentPayload(1L, 0L, "Headquarters"),
                departmentPayload(2L, 1L, "Sales")
        ));
        when(apiClient.listDepartmentUsers("app-token", 1L)).thenReturn(List.of());
        when(apiClient.listDepartmentUsers("app-token", 2L)).thenReturn(List.of(wecomUserPayload()));
        when(deptMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        AtomicLong nextDeptId = new AtomicLong(100L);
        doAnswer(invocation -> {
            ManagerDept dept = invocation.getArgument(0);
            dept.setDeptId(nextDeptId.getAndIncrement());
            return 1;
        }).when(deptMapper).insert(any(ManagerDept.class));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            ManagerUser user = invocation.getArgument(0);
            user.setUserId(700L);
            return 1;
        }).when(userMapper).insert(any(ManagerUser.class));
        when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        int saved = service.syncOrganization(config);

        assertThat(saved).isEqualTo(3);
        ArgumentCaptor<ManagerDept> deptCaptor = ArgumentCaptor.forClass(ManagerDept.class);
        verify(deptMapper, org.mockito.Mockito.times(2)).insert(deptCaptor.capture());
        assertThat(deptCaptor.getAllValues())
                .extracting(ManagerDept::getWecomDeptId)
                .containsExactly(1L, 2L);

        ArgumentCaptor<ManagerUser> userCaptor = ArgumentCaptor.forClass(ManagerUser.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getWecomCorpId()).isEqualTo("corp_1");
        assertThat(userCaptor.getValue().getWecomUserId()).isEqualTo("sales_1");
        assertThat(userCaptor.getValue().getMobile()).isEqualTo("13800000001");
        assertThat(userCaptor.getValue().getDeptId()).isEqualTo(101L);

        ArgumentCaptor<WecomEmployee> employeeCaptor = ArgumentCaptor.forClass(WecomEmployee.class);
        verify(employeeMapper).insert(employeeCaptor.capture());
        assertThat(employeeCaptor.getValue().getCrmUserId()).isEqualTo(700L);
        assertThat(employeeCaptor.getValue().getMobile()).isEqualTo("13800000001");
    }

    @Test
    void syncOrganizationShouldReuseExistingWecomEmployeeMapping() {
        WecomSyncServiceImpl service = newService();
        WecomTokenService tokenService = mapper(service, "tokenService");
        WecomApiClient apiClient = mapper(service, "apiClient");
        ManagerDeptMapper deptMapper = mapper(service, "deptMapper");
        ManageUserMapper userMapper = mapper(service, "userMapper");
        WecomEmployeeMapper employeeMapper = mapper(service, "employeeMapper");

        WecomCorpConfig config = config();
        when(tokenService.fetchAppAccessToken(config)).thenReturn("app-token");
        when(apiClient.listDepartments("app-token")).thenReturn(List.of(departmentPayload(1L, 0L, "Headquarters")));
        when(apiClient.listDepartmentUsers("app-token", 1L)).thenReturn(List.of(wecomUserPayload()));
        when(deptMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            ManagerDept dept = invocation.getArgument(0);
            dept.setDeptId(100L);
            return 1;
        }).when(deptMapper).insert(any(ManagerDept.class));
        WecomEmployee existingEmployee = new WecomEmployee();
        existingEmployee.setId(500L);
        existingEmployee.setCorpId("corp_1");
        existingEmployee.setUserId("sales_1");
        existingEmployee.setCrmUserId(700L);
        when(employeeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingEmployee);
        ManagerUser mappedUser = new ManagerUser();
        mappedUser.setUserId(700L);
        mappedUser.setUsername("sales_1");
        when(userMapper.selectById(700L)).thenReturn(mappedUser);

        int saved = service.syncOrganization(config);

        assertThat(saved).isEqualTo(2);
        verify(userMapper, never()).insert(any(ManagerUser.class));
        ArgumentCaptor<ManagerUser> userCaptor = ArgumentCaptor.forClass(ManagerUser.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getUserId()).isEqualTo(700L);
        assertThat(userCaptor.getValue().getWecomCorpId()).isEqualTo("corp_1");
        assertThat(userCaptor.getValue().getWecomUserId()).isEqualTo("sales_1");
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(WecomSyncServiceImpl service, String fieldName) {
        return (T) ReflectionTestUtils.getField(service, fieldName);
    }

    private static WecomSyncServiceImpl newService() {
        WecomSyncServiceImpl service = new WecomSyncServiceImpl();
        ReflectionTestUtils.setField(service, "tokenService", mock(WecomTokenService.class));
        ReflectionTestUtils.setField(service, "apiClient", mock(WecomApiClient.class));
        ReflectionTestUtils.setField(service, "archiveGateway", mock(WecomFinanceArchiveGateway.class));
        ReflectionTestUtils.setField(service, "messageNormalizeService", new WecomMessageNormalizeService());
        ReflectionTestUtils.setField(service, "employeeMapper", mock(WecomEmployeeMapper.class));
        ReflectionTestUtils.setField(service, "externalCustomerMapper", mock(WecomExternalCustomerMapper.class));
        ReflectionTestUtils.setField(service, "conversationMapper", mock(WecomConversationMapper.class));
        ReflectionTestUtils.setField(service, "messageMapper", mock(WecomMessageMapper.class));
        ReflectionTestUtils.setField(service, "cursorMapper", mock(WecomSyncCursorMapper.class));
        ReflectionTestUtils.setField(service, "syncLogMapper", mock(WecomSyncLogMapper.class));
        ReflectionTestUtils.setField(service, "customerMapper", mock(CustomerMapper.class));
        ReflectionTestUtils.setField(service, "deptMapper", mock(ManagerDeptMapper.class));
        ReflectionTestUtils.setField(service, "userMapper", mock(ManageUserMapper.class));
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(PasswordEncoder.class));
        return service;
    }

    private static WecomCorpConfig config() {
        WecomCorpConfig config = new WecomCorpConfig();
        config.setCorpId("corp_1");
        return config;
    }

    private static JSONObject textArchiveMessage() {
        JSONObject raw = new JSONObject();
        raw.put("seq", 7L);
        raw.put("msgid", "msg_1");
        raw.put("msgtype", "text");
        raw.put("from", "employee_1");
        raw.put("tolist", new JSONArray(List.of("wm_customer_1")));
        raw.put("msgtime", 1710000000000L);
        JSONObject text = new JSONObject();
        text.put("content", "hello");
        raw.put("text", text);
        return raw;
    }

    private static JSONObject externalCustomerPayload() {
        JSONObject externalContact = new JSONObject();
        externalContact.put("external_userid", "wm_ext_1");
        externalContact.put("name", "孙悟空");
        externalContact.put("corp_name", "悟空科技");
        externalContact.put("corp_full_name", "悟空科技有限公司");
        JSONObject payload = new JSONObject();
        payload.put("external_contact", externalContact);
        return payload;
    }

    private static JSONObject externalCustomerPayloadWithFollowUser() {
        JSONObject payload = externalCustomerPayload();
        JSONObject followUser = new JSONObject();
        followUser.put("userid", "sales_1");
        followUser.put("remark", "VIP");
        followUser.put("description", "Important customer");
        followUser.put("createtime", 1710000000L);
        followUser.put("add_way", 1);
        followUser.put("state", "state_1");
        followUser.put("tags", new JSONArray(List.of(new JSONObject().fluentPut("tag_name", "Key"))));
        payload.put("follow_user", new JSONArray(List.of(followUser)));
        return payload;
    }

    private static JSONObject departmentPayload(Long id, Long parentId, String name) {
        JSONObject department = new JSONObject();
        department.put("id", id);
        department.put("parentid", parentId);
        department.put("name", name);
        department.put("order", id.intValue());
        return department;
    }

    private static JSONObject wecomUserPayload() {
        JSONObject user = new JSONObject();
        user.put("userid", "sales_1");
        user.put("name", "Sales User");
        user.put("mobile", "13800000001");
        user.put("email", "sales@example.com");
        user.put("department", new JSONArray(List.of(2L)));
        user.put("position", "Sales");
        user.put("avatar", "https://example.com/a.png");
        user.put("status", 1);
        return user;
    }
}
