package com.kakarote.ai_crm.controller;

import com.kakarote.ai_crm.common.result.Result;
import com.kakarote.ai_crm.entity.BO.ChatSendBO;
import com.kakarote.ai_crm.entity.BO.SessionCreateBO;
import com.kakarote.ai_crm.entity.BO.SessionPinBO;
import com.kakarote.ai_crm.entity.VO.AiConfigVO;
import com.kakarote.ai_crm.entity.VO.ChatAppOptionVO;
import com.kakarote.ai_crm.entity.VO.ChatMessageVO;
import com.kakarote.ai_crm.entity.VO.ChatStreamEventVO;
import com.kakarote.ai_crm.entity.VO.ChatSessionVO;
import com.kakarote.ai_crm.service.IChatService;
import com.kakarote.ai_crm.service.ISystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI聊天控制器
 */
@RestController
@RequestMapping("/chat")
@Tag(name = "AI聊天")
public class ChatController {

    @Autowired
    private IChatService chatService;

    @Autowired
    private ISystemConfigService systemConfigService;

    @PostMapping("/session/create")
    @Operation(summary = "创建会话")
    public Result<Long> createSession(@Valid @RequestBody SessionCreateBO sessionCreateBO) {
        Long sessionId = chatService.createSession(sessionCreateBO);
        return Result.ok(sessionId);
    }

    @GetMapping("/session/list")
    @Operation(summary = "获取会话列表")
    public Result<List<ChatSessionVO>> getSessionList() {
        return Result.ok(chatService.getSessionList());
    }

    @GetMapping("/applications")
    @Operation(summary = "获取聊天应用列表")
    public Result<List<ChatAppOptionVO>> listChatApplications() {
        return Result.ok(chatService.listChatApplications());
    }

    @GetMapping("/app/options")
    @Operation(summary = "获取聊天可选应用")
    public Result<List<ChatAppOptionVO>> listChatAppOptions() {
        return Result.ok(chatService.listChatApplications());
    }

    @GetMapping("/model/options")
    @Operation(summary = "获取聊天可选模型")
    public Result<List<Map<String, Object>>> listChatModelOptions() {
        AiConfigVO config = systemConfigService.getAiConfig();
        List<Map<String, Object>> options = new java.util.ArrayList<>();
        if (config != null && config.getAvailableProviders() != null) {
            for (AiConfigVO.ProviderOptionVO provider : config.getAvailableProviders()) {
                String modelName = provider.getSavedModel();
                if (!Boolean.TRUE.equals(provider.getConfigured())
                        || !Boolean.TRUE.equals(provider.getApiKeyConfigured())
                        || modelName == null
                        || modelName.isBlank()) {
                    continue;
                }
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("provider", provider.getValue());
                option.put("providerLabel", provider.getLabel());
                option.put("modelName", modelName);
                option.put("displayName", provider.getLabel() == null || provider.getLabel().isBlank()
                        ? modelName
                        : provider.getLabel() + " / " + modelName);
                option.put("modelSource", "custom");
                Map<String, Object> capabilities = new LinkedHashMap<>();
                capabilities.put("supportsStream", provider.getSupportsStream());
                capabilities.put("supportsToolCall", provider.getSupportsToolCall());
                capabilities.put("supportsVision", provider.getSupportsVision());
                capabilities.put("supportsAudioTranscription", provider.getSupportsAudioTranscription());
                option.put("capabilities", capabilities);
                options.add(option);
            }
        }
        if (!options.isEmpty()) {
            return Result.ok(options);
        }
        if (config == null || !Boolean.TRUE.equals(config.getReady()) || config.getModel() == null || config.getModel().isBlank()) {
            return Result.ok(options);
        }
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("provider", config.getProvider());
        option.put("providerLabel", config.getProviderLabel());
        option.put("modelName", config.getModel());
        option.put("displayName", config.getProviderLabel() == null || config.getProviderLabel().isBlank()
                ? config.getModel()
                : config.getProviderLabel() + " / " + config.getModel());
        option.put("modelSource", "custom");
        option.put("capabilities", config.getCapabilities());
        options.add(option);
        return Result.ok(options);
    }

    @PostMapping("/session/delete/{id}")
    @Operation(summary = "删除会话")
    public Result<String> deleteSession(@PathVariable("id") Long id) {
        chatService.deleteSession(id);
        return Result.ok();
    }

    @PostMapping("/session/pin/{id}")
    @Operation(summary = "更新会话置顶状态")
    public Result<String> updateSessionPin(@PathVariable("id") Long id, @Valid @RequestBody SessionPinBO pinBO) {
        chatService.updateSessionPin(id, pinBO);
        return Result.ok();
    }

    @GetMapping("/message/list/{sessionId}")
    @Operation(summary = "获取会话消息历史")
    public Result<List<ChatMessageVO>> getMessageList(@PathVariable("sessionId") Long sessionId) {
        return Result.ok(chatService.getMessageList(sessionId));
    }

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（流式响应，支持附件）")
    public Flux<ServerSentEvent<String>> send(@RequestBody ChatSendBO sendBO) {
        return chatService.streamChat(sendBO)
            .filter(event -> event != null && event.data() != null && !event.data().isEmpty())
            .map(event -> ServerSentEvent.<String>builder()
                .event(resolveStreamEventName(event))
                .data(event.data())
                .build());
    }

    @PostMapping("/sendSync")
    @Operation(summary = "发送消息（同步响应，支持附件）")
    public Result<String> sendSync(@RequestBody ChatSendBO sendBO) {
        String response = chatService.chat(sendBO);
        return Result.ok(response);
    }

    private String resolveStreamEventName(ChatStreamEventVO event) {
        return event.event() == null || event.event().isBlank()
                ? ChatStreamEventVO.EVENT_MESSAGE
                : event.event();
    }
}
