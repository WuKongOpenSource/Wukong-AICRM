package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakarote.ai_crm.ai.DynamicChatClientProvider;
import com.kakarote.ai_crm.ai.provider.AiModelCapabilities;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.service.AiAudioTranscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AI audio transcription implementation.
 */
@Slf4j
@Service
public class AiAudioTranscriptionServiceImpl implements AiAudioTranscriptionService {

    private static final String OPENAI_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe";
    private static final String DASHSCOPE_TRANSCRIPTION_MODEL = "qwen3-asr-flash";
    private static final String OPENAI_TRANSCRIPTION_PROMPT = "请将音频内容准确转写为简体中文文本，只返回转写结果。";
    private static final String UNSUPPORTED_PROVIDER_MESSAGE =
            "当前模型不支持语音识别，请配置支持的模型（目前支持 OpenAI、阿里云百炼）";

    @Autowired
    private DynamicChatClientProvider chatClientProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();

    @Override
    public String transcribe(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请先提供录音文件");
        }

        DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig =
                chatClientProvider.getCurrentRuntimeConfigSnapshot();
        if (runtimeConfig == null || StrUtil.isBlank(runtimeConfig.apiKey())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "当前未配置可用 AI 模型，请先完成配置");
        }

        AiModelCapabilities capabilities = runtimeConfig.capabilities();
        if (capabilities == null || !capabilities.isSupportsAudioTranscription()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, UNSUPPORTED_PROVIDER_MESSAGE);
        }

        String providerCode = StrUtil.blankToDefault(runtimeConfig.providerCode(), "").trim().toLowerCase();
        try {
            return switch (providerCode) {
                case "openai" -> transcribeWithOpenAi(runtimeConfig, audioFile);
                case "dashscope" -> transcribeWithDashscope(runtimeConfig, audioFile);
                default -> throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, UNSUPPORTED_PROVIDER_MESSAGE);
            };
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("AI audio transcription failed: provider={}", providerCode, ex);
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "语音识别失败，请稍后重试");
        }
    }

    private String transcribeWithOpenAi(DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig,
                                        MultipartFile audioFile) throws IOException {
        String endpoint = DynamicChatClientProvider.resolveActualRequestBaseUrl(
                runtimeConfig.providerCode(),
                runtimeConfig.apiUrl()
        ) + "/v1/audio/transcriptions";

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("model", OPENAI_TRANSCRIPTION_MODEL);
        bodyBuilder.part("response_format", "text");
        bodyBuilder.part("prompt", OPENAI_TRANSCRIPTION_PROMPT);
        bodyBuilder.part("file", asResource(audioFile))
                .contentType(resolveMediaType(audioFile.getContentType()));

        String response = webClient.post()
                .uri(endpoint)
                .headers(headers -> applyRequestHeaders(headers, runtimeConfig))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class).map(this::toBusinessException))
                .bodyToMono(String.class)
                .block();

        String transcript = StrUtil.trimToEmpty(response);
        if (StrUtil.isBlank(transcript)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "语音识别结果为空，请重试");
        }
        return transcript;
    }

    private String transcribeWithDashscope(DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig,
                                           MultipartFile audioFile) throws IOException {
        String endpoint = DynamicChatClientProvider.resolveActualRequestBaseUrl(
                runtimeConfig.providerCode(),
                runtimeConfig.apiUrl()
        ) + "/v1/chat/completions";

        String mimeType = resolveMediaType(audioFile.getContentType()).toString();
        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(audioFile.getBytes());

        Map<String, Object> body = Map.of(
                "model", DASHSCOPE_TRANSCRIPTION_MODEL,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "input_audio",
                                "input_audio", Map.of("data", dataUri)
                        ))
                )),
                "asr_options", Map.of("enable_itn", false)
        );

        String response = webClient.post()
                .uri(endpoint)
                .headers(headers -> applyRequestHeaders(headers, runtimeConfig))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class).map(this::toBusinessException))
                .bodyToMono(String.class)
                .block();

        return parseDashscopeTranscript(response);
    }

    private ByteArrayResource asResource(MultipartFile audioFile) throws IOException {
        String filename = StrUtil.blankToDefault(audioFile.getOriginalFilename(), "followup-audio.webm");
        byte[] data = audioFile.getBytes();
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private MediaType resolveMediaType(String contentType) {
        if (StrUtil.isBlank(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private void applyRequestHeaders(HttpHeaders headers,
                                     DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig) {
        headers.setBearerAuth(runtimeConfig.apiKey());
        parseExtraHeaders(runtimeConfig.extraHeadersJson()).forEach(headers::set);
    }

    private Map<String, String> parseExtraHeaders(String extraHeadersJson) {
        if (StrUtil.isBlank(extraHeadersJson)) {
            return Map.of();
        }

        try {
            Map<String, Object> values = objectMapper.readValue(
                    extraHeadersJson,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            return values.entrySet().stream()
                    .filter(entry -> StrUtil.isNotBlank(entry.getKey()) && entry.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> entry.getKey().trim(),
                            entry -> String.valueOf(entry.getValue())
                    ));
        } catch (Exception ex) {
            log.warn("Failed to parse extra headers for audio transcription: {}", ex.getMessage());
            return Map.of();
        }
    }

    private String parseDashscopeTranscript(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isTextual()) {
                String transcript = contentNode.asText("").trim();
                if (StrUtil.isNotBlank(transcript)) {
                    return transcript;
                }
            }
            if (contentNode.isArray()) {
                StringBuilder builder = new StringBuilder();
                for (JsonNode item : contentNode) {
                    if (item.isTextual()) {
                        builder.append(item.asText());
                    } else if (item.hasNonNull("text")) {
                        builder.append(item.get("text").asText());
                    }
                }
                String transcript = builder.toString().trim();
                if (StrUtil.isNotBlank(transcript)) {
                    return transcript;
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse DashScope transcription response: {}", ex.getMessage());
        }
        throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "语音识别结果为空，请重试");
    }

    private BusinessException toBusinessException(String responseBody) {
        String message = extractErrorMessage(responseBody);
        return new BusinessException(SystemCodeEnum.SYSTEM_ERROR, message);
    }

    private String extractErrorMessage(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return "语音识别失败，请稍后重试";
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("error")) {
                JsonNode errorNode = root.get("error");
                if (errorNode.isTextual()) {
                    return errorNode.asText();
                }
                if (errorNode.hasNonNull("message")) {
                    return errorNode.get("message").asText();
                }
                if (errorNode.hasNonNull("msg")) {
                    return errorNode.get("msg").asText();
                }
            }
            if (root.hasNonNull("message")) {
                return root.get("message").asText();
            }
            if (root.hasNonNull("msg")) {
                return root.get("msg").asText();
            }
        } catch (Exception ex) {
            log.warn("Failed to parse provider error response: {}", ex.getMessage());
        }

        return "语音识别失败，请稍后重试";
    }
}
