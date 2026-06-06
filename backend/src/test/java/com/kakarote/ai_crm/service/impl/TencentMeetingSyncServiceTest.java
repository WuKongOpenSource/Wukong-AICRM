package com.kakarote.ai_crm.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.LoginUser;
import com.kakarote.ai_crm.entity.BO.TencentMeetingSyncRunBO;
import com.kakarote.ai_crm.entity.PO.ManagerUser;
import com.kakarote.ai_crm.entity.PO.TencentMeeting;
import com.kakarote.ai_crm.entity.PO.TencentMeetingCorpConfig;
import com.kakarote.ai_crm.entity.PO.TencentMeetingParticipant;
import com.kakarote.ai_crm.entity.PO.TencentMeetingRecording;
import com.kakarote.ai_crm.entity.PO.TencentMeetingSyncLog;
import com.kakarote.ai_crm.entity.PO.TencentMeetingTranscriptSegment;
import com.kakarote.ai_crm.entity.PO.TencentMeetingUserMapping;
import com.kakarote.ai_crm.entity.VO.TencentMeetingSyncStatusVO;
import com.kakarote.ai_crm.mapper.ManageUserMapper;
import com.kakarote.ai_crm.mapper.TencentMeetingMapper;
import com.kakarote.ai_crm.mapper.TencentMeetingParticipantMapper;
import com.kakarote.ai_crm.mapper.TencentMeetingRecordingMapper;
import com.kakarote.ai_crm.mapper.TencentMeetingSyncLogMapper;
import com.kakarote.ai_crm.mapper.TencentMeetingTranscriptSegmentMapper;
import com.kakarote.ai_crm.mapper.TencentMeetingUserMappingMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TencentMeetingSyncServiceTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runSyncShouldSaveMeetingRecordingTranscriptAndSuccessLog() {
        mockLoginUser();
        TencentMeetingSyncServiceImpl service = newService();
        TencentMeetingApiGateway apiGateway = mapper(service, "apiGateway");
        TencentMeetingOAuthTokenProvider tokenProvider = mapper(service, "tokenProvider");
        TencentMeetingUserMappingMapper userMappingMapper = mapper(service, "userMappingMapper");
        TencentMeetingMapper meetingMapper = mapper(service, "meetingMapper");
        TencentMeetingParticipantMapper participantMapper = mapper(service, "participantMapper");
        TencentMeetingRecordingMapper recordingMapper = mapper(service, "recordingMapper");
        TencentMeetingTranscriptSegmentMapper transcriptMapper = mapper(service, "transcriptMapper");
        TencentMeetingSyncLogMapper syncLogMapper = mapper(service, "syncLogMapper");

        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setId(1L);
        config.setAppId("app-1");
        config.setSyncEnabled(true);
        config.setTranscriptEnabled(true);

        TencentMeetingUserMapping mapping = new TencentMeetingUserMapping();
        mapping.setMeetingUserId("host-1");
        mapping.setCrmUserId(9L);
        mapping.setAuthStatus("ACTIVE");
        mapping.setStatus(1);
        TencentMeetingOAuthCredential credential = new TencentMeetingOAuthCredential(config, mapping, "access-token");
        when(userMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping));
        when(tokenProvider.credential(config, mapping)).thenReturn(credential);
        when(apiGateway.listEndedMeetings(credential, 30)).thenReturn(List.of(meetingJson()));
        when(apiGateway.getMeetingParticipants(credential, "meeting-1")).thenReturn(List.of(participantJson()));
        when(apiGateway.listRecordings(credential, 30)).thenReturn(List.of(recordingJson()));
        when(apiGateway.getTranscriptSegments(credential, "meeting-1", "record-1")).thenReturn(List.of(segmentJson()));
        when(meetingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            TencentMeeting meeting = invocation.getArgument(0);
            meeting.setId(300L);
            return 1;
        }).when(meetingMapper).insert(any(TencentMeeting.class));
        doAnswer(invocation -> {
            TencentMeetingRecording recording = invocation.getArgument(0);
            recording.setId(400L);
            return 1;
        }).when(recordingMapper).insert(any(TencentMeetingRecording.class));

        TencentMeetingSyncRunBO runBO = new TencentMeetingSyncRunBO();
        runBO.setSyncDays(30);
        runBO.setSyncRecordings(true);
        TencentMeetingSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("success");
        assertThat(status.getSavedCount()).isEqualTo(4);

        ArgumentCaptor<TencentMeeting> meetingCaptor = ArgumentCaptor.forClass(TencentMeeting.class);
        verify(meetingMapper).insert(meetingCaptor.capture());
        assertThat(meetingCaptor.getValue().getMeetingId()).isEqualTo("meeting-1");
        assertThat(meetingCaptor.getValue().getSubject()).isEqualTo("报价方案会");
        assertThat(meetingCaptor.getValue().getCreatorUserId()).isEqualTo("host-1");
        assertThat(meetingCaptor.getValue().getCrmCreatorUserId()).isEqualTo(9L);

        verify(participantMapper).insert(any(TencentMeetingParticipant.class));
        verify(recordingMapper).insert(any(TencentMeetingRecording.class));
        verify(transcriptMapper).insert(any(TencentMeetingTranscriptSegment.class));

        ArgumentCaptor<TencentMeetingSyncLog> logCaptor = ArgumentCaptor.forClass(TencentMeetingSyncLog.class);
        verify(syncLogMapper).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("success");
    }

    @Test
    void runSyncShouldKeepMeetingWhenParticipantDetailHasNoPermission() {
        mockLoginUser();
        TencentMeetingSyncServiceImpl service = newService();
        TencentMeetingApiGateway apiGateway = mapper(service, "apiGateway");
        TencentMeetingOAuthTokenProvider tokenProvider = mapper(service, "tokenProvider");
        TencentMeetingUserMappingMapper userMappingMapper = mapper(service, "userMappingMapper");
        TencentMeetingMapper meetingMapper = mapper(service, "meetingMapper");
        TencentMeetingSyncLogMapper syncLogMapper = mapper(service, "syncLogMapper");

        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setId(1L);
        config.setAppId("app-1");

        TencentMeetingUserMapping mapping = new TencentMeetingUserMapping();
        mapping.setMeetingUserId("host-1");
        mapping.setCrmUserId(9L);
        mapping.setAuthStatus("ACTIVE");
        mapping.setStatus(1);
        TencentMeetingOAuthCredential credential = new TencentMeetingOAuthCredential(config, mapping, "access-token");
        when(userMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping));
        when(tokenProvider.credential(config, mapping)).thenReturn(credential);
        when(apiGateway.listEndedMeetings(credential, 30)).thenReturn(List.of(meetingJson()));
        when(apiGateway.getMeetingParticipants(credential, "meeting-1"))
                .thenThrow(new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Tencent Meeting API participants failed: 9042 无权限操作"));
        when(meetingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            TencentMeeting meeting = invocation.getArgument(0);
            meeting.setId(300L);
            return 1;
        }).when(meetingMapper).insert(any(TencentMeeting.class));

        TencentMeetingSyncRunBO runBO = new TencentMeetingSyncRunBO();
        runBO.setSyncDays(30);
        runBO.setSyncRecordings(false);
        TencentMeetingSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("success");
        assertThat(status.getFetchedCount()).isEqualTo(1);
        assertThat(status.getSavedCount()).isEqualTo(1);
        assertThat(status.getFailedCount()).isEqualTo(1);
        verify(meetingMapper).insert(any(TencentMeeting.class));

        ArgumentCaptor<TencentMeetingSyncLog> logCaptor = ArgumentCaptor.forClass(TencentMeetingSyncLog.class);
        verify(syncLogMapper).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("success");
        assertThat(logCaptor.getValue().getFailedCount()).isEqualTo(1);
    }

    @Test
    void runSyncShouldUseCrmDisplayNameAndAggregateParticipantDuration() {
        mockLoginUser();
        TencentMeetingSyncServiceImpl service = newService();
        TencentMeetingApiGateway apiGateway = mapper(service, "apiGateway");
        TencentMeetingOAuthTokenProvider tokenProvider = mapper(service, "tokenProvider");
        TencentMeetingUserMappingMapper userMappingMapper = mapper(service, "userMappingMapper");
        TencentMeetingMapper meetingMapper = mapper(service, "meetingMapper");
        TencentMeetingParticipantMapper participantMapper = mapper(service, "participantMapper");
        TencentMeetingSyncLogMapper syncLogMapper = mapper(service, "syncLogMapper");
        ManageUserMapper manageUserMapper = mock(ManageUserMapper.class);
        ReflectionTestUtils.setField(service, "manageUserMapper", manageUserMapper);

        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setId(1L);
        config.setAppId("app-1");

        TencentMeetingUserMapping mapping = new TencentMeetingUserMapping();
        mapping.setMeetingUserId("open-1");
        mapping.setUserName("open-1");
        mapping.setCrmUserId(9L);
        mapping.setAuthStatus("ACTIVE");
        mapping.setStatus(1);
        ManagerUser crmUser = new ManagerUser();
        crmUser.setUserId(9L);
        crmUser.setRealname("Crm User");
        crmUser.setUsername("crm@example.com");
        when(manageUserMapper.selectById(9L)).thenReturn(crmUser);

        TencentMeetingOAuthCredential credential = new TencentMeetingOAuthCredential(config, mapping, "access-token");
        when(userMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping));
        when(tokenProvider.credential(config, mapping)).thenReturn(credential);
        when(apiGateway.listEndedMeetings(credential, 30)).thenReturn(List.of(historyMeetingWithoutCreatorJson()));
        when(apiGateway.getMeetingParticipants(credential, "meeting-actual")).thenReturn(List.of(
                participantJson("open-1", base64("Crm User"), "1710000120", "1710000720"),
                participantJson("guest-1", base64("Guest One"), "1710000300", "1710000600")
        ));
        when(meetingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            TencentMeeting meeting = invocation.getArgument(0);
            meeting.setId(300L);
            return 1;
        }).when(meetingMapper).insert(any(TencentMeeting.class));

        TencentMeetingSyncRunBO runBO = new TencentMeetingSyncRunBO();
        runBO.setSyncDays(30);
        runBO.setSyncRecordings(false);
        TencentMeetingSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("success");
        assertThat(status.getSavedCount()).isEqualTo(3);

        ArgumentCaptor<TencentMeeting> meetingInsertCaptor = ArgumentCaptor.forClass(TencentMeeting.class);
        verify(meetingMapper).insert(meetingInsertCaptor.capture());
        assertThat(meetingInsertCaptor.getValue().getCreatorUserId()).isEqualTo("open-1");
        assertThat(meetingInsertCaptor.getValue().getCreatorName()).isEqualTo("Crm User");

        ArgumentCaptor<TencentMeeting> meetingUpdateCaptor = ArgumentCaptor.forClass(TencentMeeting.class);
        verify(meetingMapper).updateById(meetingUpdateCaptor.capture());
        TencentMeeting updatedMeeting = meetingUpdateCaptor.getValue();
        assertThat(updatedMeeting.getParticipantCount()).isEqualTo(2);
        assertThat(updatedMeeting.getParticipantNames()).isEqualTo("Crm User,Guest One");
        assertThat(updatedMeeting.getDurationSeconds()).isEqualTo(600L);

        ArgumentCaptor<TencentMeetingParticipant> participantCaptor = ArgumentCaptor.forClass(TencentMeetingParticipant.class);
        verify(participantMapper, times(2)).insert(participantCaptor.capture());
        assertThat(participantCaptor.getAllValues().get(0).getUserName()).isEqualTo("Crm User");

        ArgumentCaptor<TencentMeetingSyncLog> logCaptor = ArgumentCaptor.forClass(TencentMeetingSyncLog.class);
        verify(syncLogMapper).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("success");
    }

    @Test
    void runSyncShouldIncludeUpcomingMeetings() {
        mockLoginUser();
        TencentMeetingSyncServiceImpl service = newService();
        TencentMeetingApiGateway apiGateway = mapper(service, "apiGateway");
        TencentMeetingOAuthTokenProvider tokenProvider = mapper(service, "tokenProvider");
        TencentMeetingUserMappingMapper userMappingMapper = mapper(service, "userMappingMapper");
        TencentMeetingMapper meetingMapper = mapper(service, "meetingMapper");
        TencentMeetingSyncLogMapper syncLogMapper = mapper(service, "syncLogMapper");

        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setId(1L);
        config.setAppId("app-1");

        TencentMeetingUserMapping mapping = new TencentMeetingUserMapping();
        mapping.setMeetingUserId("host-1");
        mapping.setCrmUserId(9L);
        mapping.setAuthStatus("ACTIVE");
        mapping.setStatus(1);
        TencentMeetingOAuthCredential credential = new TencentMeetingOAuthCredential(config, mapping, "access-token");
        when(userMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping));
        when(tokenProvider.credential(config, mapping)).thenReturn(credential);
        when(apiGateway.listUpcomingMeetings(credential)).thenReturn(List.of(upcomingMeetingJson()));
        when(apiGateway.listEndedMeetings(credential, 30)).thenReturn(List.of());
        when(apiGateway.getMeetingParticipants(credential, "meeting-upcoming")).thenReturn(List.of());
        when(meetingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            TencentMeeting meeting = invocation.getArgument(0);
            meeting.setId(301L);
            return 1;
        }).when(meetingMapper).insert(any(TencentMeeting.class));

        TencentMeetingSyncRunBO runBO = new TencentMeetingSyncRunBO();
        runBO.setSyncDays(30);
        runBO.setSyncRecordings(false);
        TencentMeetingSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("success");
        assertThat(status.getFetchedCount()).isEqualTo(1);
        assertThat(status.getSavedCount()).isEqualTo(1);

        ArgumentCaptor<TencentMeeting> meetingCaptor = ArgumentCaptor.forClass(TencentMeeting.class);
        verify(meetingMapper).insert(meetingCaptor.capture());
        assertThat(meetingCaptor.getValue().getMeetingId()).isEqualTo("meeting-upcoming");
        assertThat(meetingCaptor.getValue().getStatus()).isEqualTo("not_started");

        ArgumentCaptor<TencentMeetingSyncLog> logCaptor = ArgumentCaptor.forClass(TencentMeetingSyncLog.class);
        verify(syncLogMapper).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("success");
    }

    @Test
    void runSyncShouldExposeAccountErrorWhenNoMeetingsSynced() {
        mockLoginUser();
        TencentMeetingSyncServiceImpl service = newService();
        TencentMeetingApiGateway apiGateway = mapper(service, "apiGateway");
        TencentMeetingOAuthTokenProvider tokenProvider = mapper(service, "tokenProvider");
        TencentMeetingUserMappingMapper userMappingMapper = mapper(service, "userMappingMapper");
        TencentMeetingSyncLogMapper syncLogMapper = mapper(service, "syncLogMapper");

        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        config.setId(1L);
        config.setAppId("app-1");

        TencentMeetingUserMapping mapping = new TencentMeetingUserMapping();
        mapping.setMeetingUserId("host-1");
        mapping.setCrmUserId(9L);
        mapping.setAuthStatus("ACTIVE");
        mapping.setStatus(1);
        TencentMeetingOAuthCredential credential = new TencentMeetingOAuthCredential(config, mapping, "access-token");
        when(userMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping));
        when(tokenProvider.credential(config, mapping)).thenReturn(credential);
        when(apiGateway.listUpcomingMeetings(credential))
                .thenThrow(new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "source ip not in whitelist"));

        TencentMeetingSyncRunBO runBO = new TencentMeetingSyncRunBO();
        runBO.setSyncDays(30);
        runBO.setSyncRecordings(false);
        TencentMeetingSyncStatusVO status = service.runSync(config, runBO);

        assertThat(status.getLastSyncStatus()).isEqualTo("failed");
        assertThat(status.getLastSyncError()).contains("source ip not in whitelist");
        assertThat(status.getFetchedCount()).isZero();
        assertThat(status.getSavedCount()).isZero();
        assertThat(status.getFailedCount()).isEqualTo(1);

        ArgumentCaptor<TencentMeetingUserMapping> mappingCaptor = ArgumentCaptor.forClass(TencentMeetingUserMapping.class);
        verify(userMappingMapper).updateById(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().getLastSyncError()).contains("source ip not in whitelist");

        ArgumentCaptor<TencentMeetingSyncLog> logCaptor = ArgumentCaptor.forClass(TencentMeetingSyncLog.class);
        verify(syncLogMapper).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("failed");
        assertThat(logCaptor.getValue().getErrorMessage()).contains("source ip not in whitelist");
    }

    @SuppressWarnings("unchecked")
    private static <T> T mapper(TencentMeetingSyncServiceImpl service, String fieldName) {
        return (T) ReflectionTestUtils.getField(service, fieldName);
    }

    private static TencentMeetingSyncServiceImpl newService() {
        TencentMeetingSyncServiceImpl service = new TencentMeetingSyncServiceImpl();
        ReflectionTestUtils.setField(service, "apiGateway", mock(TencentMeetingApiGateway.class));
        ReflectionTestUtils.setField(service, "userMappingMapper", mock(TencentMeetingUserMappingMapper.class));
        ReflectionTestUtils.setField(service, "meetingMapper", mock(TencentMeetingMapper.class));
        ReflectionTestUtils.setField(service, "participantMapper", mock(TencentMeetingParticipantMapper.class));
        ReflectionTestUtils.setField(service, "recordingMapper", mock(TencentMeetingRecordingMapper.class));
        ReflectionTestUtils.setField(service, "transcriptMapper", mock(TencentMeetingTranscriptSegmentMapper.class));
        ReflectionTestUtils.setField(service, "syncLogMapper", mock(TencentMeetingSyncLogMapper.class));
        ReflectionTestUtils.setField(service, "tokenProvider", mock(TencentMeetingOAuthTokenProvider.class));
        return service;
    }

    private static void mockLoginUser() {
        ManagerUser user = new ManagerUser();
        user.setUserId(9L);
        user.setTenantId(99L);
        user.setUsername("user@example.com");
        user.setStatus(1);
        LoginUser loginUser = new LoginUser();
        loginUser.setUser(user);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(loginUser, null));
    }

    private static JSONObject meetingJson() {
        JSONObject raw = new JSONObject();
        raw.put("meeting_id", "meeting-1");
        raw.put("meeting_code", "123456789");
        raw.put("subject", "报价方案会");
        raw.put("status", "已结束");
        raw.put("start_time", "1710000000");
        raw.put("end_time", "1710003600");
        raw.put("creator_userid", "host-1");
        raw.put("creator_name", "张三");
        return raw;
    }

    private static JSONObject participantJson() {
        JSONObject raw = new JSONObject();
        raw.put("userid", "guest-1");
        raw.put("user_name", "李四");
        raw.put("join_time", "1710000001");
        raw.put("left_time", "1710003500");
        return raw;
    }

    private static JSONObject historyMeetingWithoutCreatorJson() {
        JSONObject raw = new JSONObject();
        raw.put("meeting_id", "meeting-actual");
        raw.put("meeting_code", "987654321");
        raw.put("subject", "Actual duration meeting");
        raw.put("status", "MEETING_STATE_ENDED");
        raw.put("start_time", "1710000000");
        raw.put("end_time", "1710003600");
        return raw;
    }

    private static JSONObject upcomingMeetingJson() {
        JSONObject raw = new JSONObject();
        raw.put("meeting_id", "meeting-upcoming");
        raw.put("meeting_code", "456789123");
        raw.put("subject", "Upcoming customer meeting");
        raw.put("status", "MEETING_STATE_INIT");
        raw.put("start_time", "1710100000");
        raw.put("end_time", "1710103600");
        raw.put("creator_userid", "host-1");
        raw.put("creator_name", "Host User");
        return raw;
    }

    private static JSONObject participantJson(String userId, String userName, String joinTime, String leftTime) {
        JSONObject raw = new JSONObject();
        raw.put("userid", userId);
        raw.put("user_name", userName);
        raw.put("join_time", joinTime);
        raw.put("left_time", leftTime);
        return raw;
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject recordingJson() {
        JSONObject raw = new JSONObject();
        raw.put("meeting_id", "meeting-1");
        raw.put("record_file_id", "record-1");
        raw.put("record_file_name", "报价方案会.mp4");
        raw.put("download_url", "https://example.com/record.mp4");
        raw.put("meeting_summary", "客户关注报价有效期");
        raw.put("todo_text", "下周三前发送报价方案");
        return raw;
    }

    private static JSONObject segmentJson() {
        JSONObject raw = new JSONObject();
        raw.put("pid", "1");
        raw.put("speaker_name", "张三");
        raw.put("text", "下周三之前把报价方案发给客户");
        raw.put("start_time", 1000L);
        raw.put("end_time", 5000L);
        return raw;
    }
}
