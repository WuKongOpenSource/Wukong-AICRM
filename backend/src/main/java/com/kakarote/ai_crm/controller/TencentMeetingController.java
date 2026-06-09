package com.kakarote.ai_crm.controller;

import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.auth.RequirePermission;
import com.kakarote.ai_crm.common.result.Result;
import com.kakarote.ai_crm.entity.BO.TencentMeetingBindBO;
import com.kakarote.ai_crm.entity.BO.TencentMeetingCandidateQueryBO;
import com.kakarote.ai_crm.entity.BO.TencentMeetingCreateBO;
import com.kakarote.ai_crm.entity.BO.TencentMeetingQueryBO;
import com.kakarote.ai_crm.entity.BO.TencentMeetingSyncRunBO;
import com.kakarote.ai_crm.entity.BO.TencentMeetingUnbindBO;
import com.kakarote.ai_crm.entity.VO.TencentMeetingCandidateVO;
import com.kakarote.ai_crm.entity.VO.TencentMeetingDetailVO;
import com.kakarote.ai_crm.entity.VO.TencentMeetingOAuthAuthorizeVO;
import com.kakarote.ai_crm.entity.VO.TencentMeetingOAuthStatusVO;
import com.kakarote.ai_crm.entity.VO.TencentMeetingSyncStatusVO;
import com.kakarote.ai_crm.entity.VO.TencentMeetingVO;
import com.kakarote.ai_crm.service.impl.TencentMeetingServiceImpl;
import com.kakarote.ai_crm.service.impl.TencentMeetingOAuthService;
import com.kakarote.ai_crm.service.impl.TencentMeetingWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/tencent-meeting")
@Tag(name = "腾讯会议")
public class TencentMeetingController {

    @Autowired
    private TencentMeetingServiceImpl meetingService;

    @Autowired
    private TencentMeetingWebhookService webhookService;

    @Autowired
    private TencentMeetingOAuthService oauthService;

    @PostMapping("/sync/run")
    @Operation(summary = "运行腾讯会议同步")
    @RequirePermission("tencentMeeting:sync")
    public Result<TencentMeetingSyncStatusVO> runSync(@RequestBody(required = false) TencentMeetingSyncRunBO runBO) {
        return Result.ok(meetingService.runSync(runBO));
    }

    @GetMapping("/sync/status")
    @Operation(summary = "获取腾讯会议同步状态")
    @RequirePermission("tencentMeeting:view")
    public Result<TencentMeetingSyncStatusVO> getSyncStatus() {
        return Result.ok(meetingService.getSyncStatus());
    }

    @GetMapping("/oauth/authorize")
    @Operation(summary = "腾讯会议OAuth授权链接")
    @RequirePermission("tencentMeeting:view")
    public Result<TencentMeetingOAuthAuthorizeVO> oauthAuthorize(@RequestParam(required = false) String redirect,
                                                                HttpServletRequest request) {
        return Result.ok(oauthService.createAuthorizeUrl(redirect, request));
    }

    @GetMapping("/oauth/callback")
    @Operation(summary = "腾讯会议OAuth回调")
    public void oauthCallback(@RequestParam(value = "auth_code", required = false) String authCode,
                              @RequestParam(value = "code", required = false) String code,
                              @RequestParam(required = false) String state,
                              @RequestParam(required = false) String error,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        response.sendRedirect(oauthService.handleCallback(authCode == null ? code : authCode, state, error, request));
    }

    @GetMapping("/oauth/status")
    @Operation(summary = "当前用户腾讯会议授权状态")
    @RequirePermission("tencentMeeting:view")
    public Result<TencentMeetingOAuthStatusVO> oauthStatus() {
        return Result.ok(oauthService.getStatus());
    }

    @PostMapping("/oauth/unbind")
    @Operation(summary = "取消当前用户腾讯会议授权")
    @RequirePermission("tencentMeeting:view")
    public Result<String> oauthUnbind() {
        oauthService.unbindCurrentUser();
        return Result.ok();
    }

    @PostMapping("/webhook")
    @Operation(summary = "腾讯会议Webhook")
    public String webhook(@RequestBody String body,
                                   @RequestHeader(value = "timestamp", required = false) String timestamp,
                                   @RequestHeader(value = "nonce", required = false) String nonce,
                                   @RequestHeader(value = "signature", required = false) String signature,
                                   @RequestHeader(value = "encrypt", required = false) String encrypt) {
        webhookService.handleWebhook(body, timestamp, nonce, signature, encrypt);
        return "successfully received callback";
    }

    @GetMapping("/webhook")
    @Operation(summary = "腾讯会议Webhook URL验证")
    public ResponseEntity<byte[]> verifyWebhook(@RequestParam("check_str") String checkStr,
                                                @RequestHeader(value = "timestamp", required = false) String timestamp,
                                                @RequestHeader(value = "nonce", required = false) String nonce,
                                                @RequestHeader(value = "signature", required = false) String signature,
                                                @RequestHeader(value = "encrypt", required = false) String encrypt) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(webhookService.verifyWebhookUrl(checkStr, timestamp, nonce, signature, encrypt));
    }

    @PostMapping("/queryPageList")
    @Operation(summary = "分页查询腾讯会议")
    @RequirePermission("tencentMeeting:view")
    public Result<BasePage<TencentMeetingVO>> queryPageList(@RequestBody(required = false) TencentMeetingQueryBO queryBO) {
        return Result.ok(meetingService.queryPageList(queryBO));
    }

    @PostMapping("/create")
    @Operation(summary = "创建腾讯会议")
    @RequirePermission("tencentMeeting:sync")
    public Result<TencentMeetingVO> create(@RequestBody TencentMeetingCreateBO createBO) {
        return Result.ok(meetingService.createMeeting(createBO));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取腾讯会议详情")
    @RequirePermission("tencentMeeting:detail")
    public Result<TencentMeetingDetailVO> getDetail(@PathVariable("id") Long id) {
        return Result.ok(meetingService.getDetail(id));
    }

    @PostMapping("/{id}/refresh")
    @Operation(summary = "刷新腾讯会议")
    @RequirePermission("tencentMeeting:sync")
    public Result<String> refresh(@PathVariable("id") Long id) {
        meetingService.refreshMeeting(id);
        return Result.ok();
    }

    @PostMapping("/{id}/join-url")
    @Operation(summary = "获取腾讯会议入会链接")
    @RequirePermission("tencentMeeting:detail")
    public Result<TencentMeetingVO> refreshJoinUrl(@PathVariable("id") Long id) {
        return Result.ok(meetingService.refreshJoinUrl(id));
    }

    @PostMapping("/bind")
    @Operation(summary = "关联客户")
    @RequirePermission("tencentMeeting:bind")
    public Result<String> bind(@RequestBody TencentMeetingBindBO bindBO) {
        meetingService.bind(bindBO);
        return Result.ok();
    }

    @PostMapping("/unbind")
    @Operation(summary = "取消关联客户")
    @RequirePermission("tencentMeeting:unbind")
    public Result<String> unbind(@RequestBody TencentMeetingUnbindBO unbindBO) {
        meetingService.unbind(unbindBO);
        return Result.ok();
    }

    @PostMapping("/candidates")
    @Operation(summary = "腾讯会议候选")
    @RequirePermission("tencentMeeting:view")
    public Result<List<TencentMeetingCandidateVO>> candidates(@RequestBody(required = false) TencentMeetingCandidateQueryBO queryBO) {
        return Result.ok(meetingService.queryCandidates(queryBO));
    }
}
