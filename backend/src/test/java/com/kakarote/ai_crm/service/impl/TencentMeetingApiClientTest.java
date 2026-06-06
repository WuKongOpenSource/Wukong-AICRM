package com.kakarote.ai_crm.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.kakarote.ai_crm.entity.PO.TencentMeetingCorpConfig;
import com.kakarote.ai_crm.entity.PO.TencentMeetingUserMapping;
import com.kakarote.ai_crm.utils.SecretTextCipher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TencentMeetingApiClientTest {

    @Test
    void createMeetingShouldUseOAuthHeadersOnly() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TencentMeetingApiClient client = new TencentMeetingApiClient(restTemplate);
        ReflectionTestUtils.setField(client, "secretTextCipher", new SecretTextCipher("0123456789abcdef"));

        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        TencentMeetingUserMapping account = new TencentMeetingUserMapping();
        account.setMeetingUserId("open-1");
        TencentMeetingOAuthCredential credential = new TencentMeetingOAuthCredential(config, account, "access-1");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        client.createMeeting(credential, new JSONObject());

        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        assertThat(entityCaptor.getValue().getHeaders().getFirst("AccessToken")).isEqualTo("access-1");
        assertThat(entityCaptor.getValue().getHeaders().getFirst("OpenId")).isEqualTo("open-1");
        assertThat(entityCaptor.getValue().getHeaders().containsKey("X-TC-Key")).isFalse();
        assertThat(entityCaptor.getValue().getHeaders().containsKey("X-TC-Signature")).isFalse();
    }

    @Test
    void getMeetingDetailShouldQueryByMeetingIdAndReturnFirstMeetingInfo() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TencentMeetingApiClient client = new TencentMeetingApiClient(restTemplate);
        ReflectionTestUtils.setField(client, "secretTextCipher", new SecretTextCipher("0123456789abcdef"));

        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        TencentMeetingUserMapping account = new TencentMeetingUserMapping();
        account.setMeetingUserId("open-1");
        TencentMeetingOAuthCredential credential = new TencentMeetingOAuthCredential(config, account, "access-1");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"meeting_info_list":[{"meeting_id":"meeting-1","join_url":"https://meeting.tencent.com/s/abc"}]}
                        """));

        JSONObject detail = client.getMeetingDetail(credential, "meeting-1");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().toString())
                .contains("/v1/meetings/meeting-1")
                .contains("userid=open-1")
                .contains("instanceid=1");
        assertThat(entityCaptor.getValue().getHeaders().getFirst("AccessToken")).isEqualTo("access-1");
        assertThat(entityCaptor.getValue().getHeaders().getFirst("OpenId")).isEqualTo("open-1");
        assertThat(detail.getString("join_url")).isEqualTo("https://meeting.tencent.com/s/abc");
    }

    @Test
    void listUpcomingMeetingsShouldQueryCurrentUserMeetings() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TencentMeetingApiClient client = new TencentMeetingApiClient(restTemplate);
        ReflectionTestUtils.setField(client, "secretTextCipher", new SecretTextCipher("0123456789abcdef"));

        TencentMeetingCorpConfig config = new TencentMeetingCorpConfig();
        TencentMeetingUserMapping account = new TencentMeetingUserMapping();
        account.setMeetingUserId("open-1");
        TencentMeetingOAuthCredential credential = new TencentMeetingOAuthCredential(config, account, "access-1");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"meeting_info_list":[{"meeting_id":"meeting-upcoming","status":"MEETING_STATE_INIT"}]}
                        """));

        var meetings = client.listUpcomingMeetings(credential);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        assertThat(uriCaptor.getValue().toString())
                .contains("/v1/meetings")
                .contains("userid=open-1")
                .contains("instanceid=1");
        assertThat(meetings).hasSize(1);
        assertThat(meetings.get(0).getString("meeting_id")).isEqualTo("meeting-upcoming");
    }
}
