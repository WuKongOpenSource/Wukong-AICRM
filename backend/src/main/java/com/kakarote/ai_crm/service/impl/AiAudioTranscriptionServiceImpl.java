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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AiAudioTranscriptionServiceImpl implements AiAudioTranscriptionService {

    private static final String OPENAI_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe";
    private static final String DASHSCOPE_TRANSCRIPTION_MODEL = "qwen3-asr-flash";
    private static final long DASHSCOPE_MAX_AUDIO_SECONDS = 300L;
    private static final String OPENAI_TRANSCRIPTION_PROMPT =
        "请将音频内容准确转写为简体中文文本，只返回转写结果。";
    private static final String UNSUPPORTED_PROVIDER_MESSAGE =
        "当前模型不支持语音识别，请配置支持的模型（目前支持 OpenAI、阿里云百炼）。";
    private static final String DASHSCOPE_DURATION_UNSUPPORTED_MESSAGE =
        "仅支持 5 分钟以内音频的AI分析";

    @Lazy
    @Autowired
    private DynamicChatClientProvider chatClientProvider;

    @Value("${media-analysis.ffprobe.path:ffprobe}")
    private String ffprobePath;

    @Value("${media-analysis.audio.probe-timeout-seconds:10}")
    private long audioProbeTimeoutSeconds;

    @Value("${system-ai.openai.proxy-base-url:http://52.198.150.151}")
    private String openAiProxyBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();

    @Override
    public String transcribe(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请先上传音频文件");
        }
        try {
            return transcribe(
                audioFile.getBytes(),
                StrUtil.blankToDefault(audioFile.getOriginalFilename(), "followup-audio.webm"),
                audioFile.getContentType()
            );
        } catch (IOException ex) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "音频转写失败");
        }
    }

    @Override
    public String transcribe(byte[] audioBytes, String filename, String contentType) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请先上传音频文件");
        }

        DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig =
            chatClientProvider.getCurrentRuntimeConfigSnapshot();
        if (runtimeConfig == null || StrUtil.isBlank(runtimeConfig.apiKey())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请先配置 AI API Key");
        }

        AiModelCapabilities capabilities = runtimeConfig.capabilities();
        if (capabilities == null || !capabilities.isSupportsAudioTranscription()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, UNSUPPORTED_PROVIDER_MESSAGE);
        }

        String providerCode = StrUtil.blankToDefault(runtimeConfig.providerCode(), "").trim().toLowerCase();
        try {
            String transcript = switch (providerCode) {
                case "openai" -> transcribeWithOpenAi(runtimeConfig, audioBytes, filename, contentType);
                case "dashscope" -> transcribeWithDashscope(runtimeConfig, audioBytes, filename, contentType);
                default -> throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, UNSUPPORTED_PROVIDER_MESSAGE);
            };
            if (StrUtil.isBlank(transcript)) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "音频转写结果为空");
            }
            return transcript.trim();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("AI audio transcription failed: provider={}", providerCode, ex);
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "音频转写失败");
        }
    }

    private String transcribeWithOpenAi(DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig,
                                        byte[] audioBytes,
                                        String filename,
                                        String contentType) {
        String endpoint = DynamicChatClientProvider.resolveActualRequestBaseUrl(
            runtimeConfig.providerCode(),
            runtimeConfig.apiUrl(),
            openAiProxyBaseUrl
        ) + "/v1/audio/transcriptions";

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("model", OPENAI_TRANSCRIPTION_MODEL);
        bodyBuilder.part("response_format", "text");
        bodyBuilder.part("prompt", OPENAI_TRANSCRIPTION_PROMPT);
        bodyBuilder.part("file", asResource(audioBytes, filename)).contentType(resolveMediaType(contentType));

        return webClient.post()
            .uri(endpoint)
            .headers(headers -> applyRequestHeaders(headers, runtimeConfig))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                clientResponse.bodyToMono(String.class).map(this::toBusinessException))
            .bodyToMono(String.class)
            .block();
    }

    private String transcribeWithDashscope(DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig,
                                           byte[] audioBytes,
                                           String filename,
                                           String contentType) {
        ensureDashscopeAudioWithinDuration(audioBytes, filename);

        String endpoint = DynamicChatClientProvider.resolveActualRequestBaseUrl(
            runtimeConfig.providerCode(),
            runtimeConfig.apiUrl(),
            openAiProxyBaseUrl
        ) + "/v1/chat/completions";

        String mimeType = resolveMediaType(contentType).toString();
        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(audioBytes);
        Map<String, Object> body = Map.of(
            "model", DASHSCOPE_TRANSCRIPTION_MODEL,
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of(
                    "type", "input_audio",
                    "input_audio", Map.of("data", dataUri)
                ))
            )),
            "stream", false,
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

        return extractDashscopeTranscript(response);
    }

    private void ensureDashscopeAudioWithinDuration(byte[] audioBytes, String filename) {
        OptionalDouble durationSeconds = probeAudioDurationSeconds(audioBytes, filename);
        if (durationSeconds.isPresent() && durationSeconds.getAsDouble() > DASHSCOPE_MAX_AUDIO_SECONDS) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "仅支持 5 分钟以内音频的 AI 分析");
        }
    }

    private OptionalDouble probeAudioDurationSeconds(byte[] audioBytes, String filename) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("aicrm-asr-", resolveAudioSuffix(filename));
            Files.write(tempFile, audioBytes);
            List<String> command = List.of(
                StrUtil.blankToDefault(ffprobePath, "ffprobe").trim(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                tempFile.toString()
            );
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
            Duration timeout = Duration.ofSeconds(Math.max(1L, audioProbeTimeoutSeconds));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Audio duration probe timed out: file={}", filename);
                return OptionalDouble.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                log.warn("Audio duration probe failed: file={}, output={}", filename, output);
                return OptionalDouble.empty();
            }
            double seconds = Double.parseDouble(output);
            return seconds > 0 ? OptionalDouble.of(seconds) : OptionalDouble.empty();
        } catch (IOException ex) {
            log.warn("Audio duration probe unavailable, skip pre-check: {}", ex.getMessage());
            return OptionalDouble.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "Audio duration probe interrupted");
        } catch (Exception ex) {
            log.warn("Audio duration probe failed, skip pre-check: {}", ex.getMessage());
            return OptionalDouble.empty();
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.debug("Failed to delete temporary audio probe file: {}", tempFile, ex);
                }
            }
        }
    }

    private String resolveAudioSuffix(String filename) {
        if (StrUtil.isBlank(filename)) {
            return ".audio";
        }
        String actualFilename = filename.trim();
        int dotIndex = actualFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == actualFilename.length() - 1) {
            return ".audio";
        }
        String extension = actualFilename.substring(dotIndex + 1).replaceAll("[^A-Za-z0-9]", "");
        return StrUtil.isBlank(extension) ? ".audio" : "." + extension;
    }

    private ByteArrayResource asResource(byte[] data, String filename) {
        String actualFilename = StrUtil.blankToDefault(filename, "followup-audio.webm");
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return actualFilename;
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

    private String extractDashscopeTranscript(String responseBody) {
        try {
            JsonNode contentNode = objectMapper.readTree(responseBody)
                .path("choices")
                .path(0)
                .path("message")
                .path("content");
            if (contentNode.isTextual()) {
                return contentNode.asText("").trim();
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
                return builder.toString().trim();
            }
        } catch (Exception ex) {
            log.warn("Failed to parse DashScope transcription response: {}", ex.getMessage());
        }
        throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "音频转写结果为空");
    }

    private BusinessException toBusinessException(String responseBody) {
        return new BusinessException(SystemCodeEnum.SYSTEM_ERROR, extractErrorMessage(responseBody));
    }

    private String extractErrorMessage(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return "音频转写失败";
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
        return "音频转写失败";
    }
}
