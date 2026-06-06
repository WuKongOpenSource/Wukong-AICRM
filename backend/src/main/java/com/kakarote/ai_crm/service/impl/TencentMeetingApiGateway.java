package com.kakarote.ai_crm.service.impl;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

public interface TencentMeetingApiGateway {

    List<JSONObject> listEndedMeetings(TencentMeetingOAuthCredential credential, int syncDays);

    List<JSONObject> listUpcomingMeetings(TencentMeetingOAuthCredential credential);

    List<JSONObject> getMeetingParticipants(TencentMeetingOAuthCredential credential, String meetingId);

    JSONObject getMeetingDetail(TencentMeetingOAuthCredential credential, String meetingId);

    List<JSONObject> listRecordings(TencentMeetingOAuthCredential credential, int syncDays);

    List<JSONObject> getTranscriptSegments(TencentMeetingOAuthCredential credential, String meetingId, String recordFileId);

    JSONObject createMeeting(TencentMeetingOAuthCredential credential, JSONObject requestBody);
}
