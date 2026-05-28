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

/**
 * AI audio transcription implementation.
 */
@Slf4j
@Service
public class AiAudioTranscriptionServiceImpl implements AiAudioTranscriptionService {

    private static final String OPENAI_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe";
    private static final String DASHSCOPE_TRANSCRIPTION_MODEL = "qwen3-asr-flash";
    private static final long DASHSCOPE_MAX_AUDIO_SECONDS = 300L;
    private static final String OPENAI_TRANSCRIPTION_PROMPT = "请将音频内容准确转写为简体中文文本，只返回转写结果。";
    private static final String UNSUPPORTED_PROVIDER_MESSAGE =
        "当前模型不支持语音识别，请配置支持的模型（目前支持 OpenAI、阿里云百炼）。";
    private static final String DASHSCOPE_DURATION_UNSUPPORTED_MESSAGE =
        "仅支持 5 分钟以内音频的ai分析";

    @Lazy
    @Autowired
    private DynamicChatClientProvider chatClientProvider;

    @Autowired
    private AiQuotaService aiQuotaService;

    @Value("${system-ai.openai.proxy-base-url:http://52.198.150.151}")
    private String openAiProxyBaseUrl;

    @Value("${media-analysis.ffprobe.path:ffprobe}")
    private String ffprobePath;

    @Value("${media-analysis.audio.probe-timeout-seconds:10}")
    private long audioProbeTimeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();

    /**
     * 处理transcribe方法逻辑。
     */
    @Override
    public String transcribe(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请先提供录音文件");
        }
        try {
            return transcribe(
                audioFile.getBytes(),
                StrUtil.blankToDefault(audioFile.getOriginalFilename(), "followup-audio.webm"),
                audioFile.getContentType()
            );
        } catch (IOException ex) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "语音识别失败，请稍后重试");
        }
    }

    /**
     * 处理transcribe方法逻辑。
     */
    @Override
    public String transcribe(byte[] audioBytes, String filename, String contentType) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "请先提供录音文件");
        }

        aiQuotaService.ensureQuotaAvailable("audio_transcription");

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
            TranscriptionResult result = switch (providerCode) {
                case "openai" -> new TranscriptionResult(
                    transcribeWithOpenAi(runtimeConfig, audioBytes, filename, contentType),
                    null,
                    null
                );
                case "dashscope" -> transcribeWithDashscope(runtimeConfig, audioBytes, filename, contentType);
                default -> throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, UNSUPPORTED_PROVIDER_MESSAGE);
            };
            consumeTranscriptionUsage(providerCode, filename, result);
            return result.transcript();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("AI audio transcription failed: provider={}", providerCode, ex);
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "语音识别失败，请稍后重试");
        }
    }

    /**
     * 处理transcribe包含OpenAi方法逻辑。
     */
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
        bodyBuilder.part("file", asResource(audioBytes, filename))
            .contentType(resolveMediaType(contentType));

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

    /**
     * 处理transcribe包含Dashscope方法逻辑。
     */
    private TranscriptionResult transcribeWithDashscope(DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig,
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

        return parseDashscopeTranscriptionResult(response);
    }

    /**
     * 处理asResource方法逻辑。
     */
    private ByteArrayResource asResource(byte[] data, String filename) {
        String actualFilename = StrUtil.blankToDefault(filename, "followup-audio.webm");
        return new ByteArrayResource(data) {
            /**
             * 获取Filename。
             */
            @Override
            public String getFilename() {
                return actualFilename;
            }
        };
    }

    /**
     * 解析媒体类型。
     */
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

    /**
     * 处理applyRequestHeaders方法逻辑。
     */
    private void applyRequestHeaders(HttpHeaders headers,
                                     DynamicChatClientProvider.AiRuntimeConfigSnapshot runtimeConfig) {
        headers.setBearerAuth(runtimeConfig.apiKey());
        parseExtraHeaders(runtimeConfig.extraHeadersJson()).forEach(headers::set);
    }

    /**
     * 解析Extra请求头。
     */
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

    /**
     * 按提供商返回的真实 usage 扣费；仅 OpenAI 文本转写接口继续保留估算兜底。
     */
    private void consumeTranscriptionUsage(String providerCode, String filename, TranscriptionResult result) {
        if (result == null || StrUtil.isBlank(result.transcript())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "语音识别结果为空，请重试");
        }
        if (result.usageSnapshot() != null) {
            aiQuotaService.consumeResolvedTokens("audio_transcription", result.usageSnapshot());
            log.info("AI audio transcription usage consumed: provider={}, model={}, file={}, seconds={}, totalTokens={}",
                providerCode, DASHSCOPE_TRANSCRIPTION_MODEL, filename, result.seconds(), result.usageSnapshot().totalTokens());
            return;
        }
        log.warn("AI audio transcription usage missing, fallback to estimated billing: provider={}, file={}",
            providerCode, filename);
        aiQuotaService.consumeEstimatedTokens(
            "audio_transcription",
            StrUtil.blankToDefault(filename, "audio"),
            result.transcript()
        );
    }

    /**
     * qwen3-asr-flash 只支持 5 分钟以内音频，能探测到超限时直接拦截。
     */
    private void ensureDashscopeAudioWithinDuration(byte[] audioBytes, String filename) {
        OptionalDouble durationSeconds = probeAudioDurationSeconds(audioBytes, filename);
        if (durationSeconds.isPresent() && durationSeconds.getAsDouble() > DASHSCOPE_MAX_AUDIO_SECONDS) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, DASHSCOPE_DURATION_UNSUPPORTED_MESSAGE);
        }
    }

    /**
     * 使用 ffprobe 探测音频时长；探测失败时交由 provider 返回更具体的错误。
     */
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
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "音频时长检测被中断，请稍后重试");
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

    /**
     * 解析 DashScope 转写文本和真实 usage。
     */
    private TranscriptionResult parseDashscopeTranscriptionResult(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String transcript = extractDashscopeTranscript(root);
            AiQuotaService.TokenUsageSnapshot usageSnapshot = parseDashscopeUsage(root.path("usage"));
            Long seconds = readPositiveJsonLong(root.path("usage").get("seconds"));
            return new TranscriptionResult(transcript, usageSnapshot, seconds);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to parse DashScope transcription response: {}", ex.getMessage());
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "语音识别结果为空，请重试");
        }
    }

    private String extractDashscopeTranscript(JsonNode root) {
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
        throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "语音识别结果为空，请重试");
    }

    private AiQuotaService.TokenUsageSnapshot parseDashscopeUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            return null;
        }

        Integer promptTokens = readPositiveJsonInt(usageNode.get("prompt_tokens"));
        Integer completionTokens = firstPositive(
            readPositiveJsonInt(usageNode.get("completion_tokens")),
            readPositiveJsonInt(usageNode.path("completion_tokens_details").get("text_tokens"))
        );
        if (promptTokens == null) {
            promptTokens = sumPositive(
                readPositiveJsonInt(usageNode.path("prompt_tokens_details").get("audio_tokens")),
                readPositiveJsonInt(usageNode.path("prompt_tokens_details").get("text_tokens"))
            );
        }

        Integer totalTokens = readPositiveJsonInt(usageNode.get("total_tokens"));
        if (totalTokens == null && promptTokens != null && completionTokens != null) {
            totalTokens = promptTokens + completionTokens;
        }
        if (totalTokens == null || totalTokens <= 0) {
            return null;
        }

        int resolvedPromptTokens = promptTokens != null
            ? promptTokens
            : Math.max(totalTokens - (completionTokens != null ? completionTokens : 0), 0);
        int resolvedCompletionTokens = completionTokens != null
            ? completionTokens
            : Math.max(totalTokens - resolvedPromptTokens, 0);
        return new AiQuotaService.TokenUsageSnapshot(resolvedPromptTokens, resolvedCompletionTokens, totalTokens);
    }

    private Integer firstPositive(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private Integer sumPositive(Integer... values) {
        if (values == null) {
            return null;
        }
        int total = 0;
        for (Integer value : values) {
            if (value != null && value > 0) {
                total += value;
            }
        }
        return total > 0 ? total : null;
    }

    private Integer readPositiveJsonInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            int value = node.asInt();
            return value > 0 ? value : null;
        }
        if (node.isTextual()) {
            try {
                int value = Integer.parseInt(node.asText().trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Long readPositiveJsonLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long value = node.asLong();
            return value > 0 ? value : null;
        }
        if (node.isTextual()) {
            try {
                long value = Long.parseLong(node.asText().trim());
                return value > 0 ? value : null;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * 转换为Business异常。
     */
    private BusinessException toBusinessException(String responseBody) {
        String message = extractErrorMessage(responseBody);
        return new BusinessException(SystemCodeEnum.SYSTEM_ERROR, message);
    }

    /**
     * 处理extractErrorMessage方法逻辑。
     */
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

    private record TranscriptionResult(
        String transcript,
        AiQuotaService.TokenUsageSnapshot usageSnapshot,
        Long seconds
    ) {
    }
}
