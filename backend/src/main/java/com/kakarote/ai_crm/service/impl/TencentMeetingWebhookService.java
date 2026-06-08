package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.kakarote.ai_crm.config.tenant.TenantContextHolder;
import com.kakarote.ai_crm.entity.PO.TencentMeetingCorpConfig;
import com.kakarote.ai_crm.entity.PO.TencentMeetingWebhookEvent;
import com.kakarote.ai_crm.mapper.TencentMeetingCorpConfigMapper;
import com.kakarote.ai_crm.mapper.TencentMeetingWebhookEventMapper;
import com.kakarote.ai_crm.utils.SecretTextCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class TencentMeetingWebhookService {

    @Autowired
    private TencentMeetingWebhookEventMapper eventMapper;

    @Autowired
    private TencentMeetingSyncServiceImpl syncService;

    @Autowired
    private TencentMeetingCorpConfigMapper configMapper;

    @Autowired
    private SecretTextCipher secretTextCipher;

    @Transactional(rollbackFor = Exception.class)
    public boolean handleWebhook(String body) {
        return handleWebhook(body, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean handleWebhook(String body, String timestamp, String nonce, String signature) {
        return handleWebhook(body, timestamp, nonce, signature, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean handleWebhook(String body, String timestamp, String nonce, String signature, String encrypt) {
        if (StrUtil.isBlank(body)) {
            return false;
        }
        TencentMeetingCorpConfig matchedConfig = resolveWebhookConfig(body, timestamp, nonce, signature);
        Long previousTenantId = TenantContextHolder.getTenantId();
        if (matchedConfig != null && matchedConfig.getTenantId() != null) {
            TenantContextHolder.setTenantId(matchedConfig.getTenantId());
        }
        try {
            return doHandleWebhook(decryptWebhookBodyIfNeeded(body, matchedConfig, encrypt), matchedConfig);
        } finally {
            if (previousTenantId != null) {
                TenantContextHolder.setTenantId(previousTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    public byte[] verifyWebhookUrl(String checkStr, String timestamp, String nonce, String signature) {
        return verifyWebhookUrl(checkStr, timestamp, nonce, signature, null);
    }

    public byte[] verifyWebhookUrl(String checkStr, String timestamp, String nonce, String signature, String encrypt) {
        String actualCheckStr = StrUtil.nullToEmpty(checkStr);
        TencentMeetingCorpConfig matchedConfig = resolveWebhookConfig(actualCheckStr, timestamp, nonce, signature);
        String decryptedCheckStr = decryptEncryptedTextIfNeeded(actualCheckStr, matchedConfig);
        if (StrUtil.isBlank(decryptedCheckStr)) {
            throw new IllegalArgumentException("Tencent Meeting webhook check_str decrypt failed");
        }
        return decryptedCheckStr.getBytes(StandardCharsets.UTF_8);
    }

    private boolean doHandleWebhook(String body, TencentMeetingCorpConfig config) {
        JSONObject root = JSONObject.parseObject(body);
        String traceId = root.getString("trace_id");
        String eventName = root.getString("event");
        if (StrUtil.isNotBlank(traceId)) {
            TencentMeetingWebhookEvent existing = eventMapper.selectOne(
                    Wrappers.<TencentMeetingWebhookEvent>lambdaQuery()
                            .eq(TencentMeetingWebhookEvent::getTraceId, traceId)
                            .last("LIMIT 1"));
            if (existing != null) {
                return false;
            }
        }

        TencentMeetingWebhookEvent event = new TencentMeetingWebhookEvent();
        event.setEventName(eventName);
        event.setTraceId(traceId);
        event.setRawJson(body);
        event.setProcessStatus("processing");
        eventMapper.insert(event);

        try {
            JSONObject meetingInfo = extractMeetingInfo(root);
            String meetingId = meetingInfo == null ? null : meetingInfo.getString("meeting_id");
            if (StrUtil.isNotBlank(meetingId) && isRefreshEvent(eventName)) {
                syncService.refreshMeetingFromWebhook(eventName, meetingInfo, config);
            }
            event.setProcessStatus("success");
            eventMapper.updateById(event);
            return true;
        } catch (Exception e) {
            event.setProcessStatus("failed");
            event.setProcessError(e.getMessage());
            eventMapper.updateById(event);
            log.error("Tencent Meeting webhook failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    private TencentMeetingCorpConfig resolveWebhookConfig(String data, String timestamp, String nonce, String signature) {
        List<TencentMeetingCorpConfig> configs = loadWebhookConfigs();
        if (configs.isEmpty()) {
            return null;
        }
        if (StrUtil.hasBlank(timestamp, nonce, signature)) {
            throw new IllegalArgumentException("Tencent Meeting webhook signature headers are required");
        }

        String signaturePayload = extractSignaturePayload(data);
        for (TencentMeetingCorpConfig config : configs) {
            String token = decryptWebhookToken(config);
            if (StrUtil.isBlank(token)) {
                continue;
            }
            String expected = calculateSignature(token, timestamp, nonce, signaturePayload);
            if (expected.equalsIgnoreCase(signature)) {
                return config;
            }
        }
        throw new IllegalArgumentException("Tencent Meeting webhook signature verification failed");
    }

    private List<TencentMeetingCorpConfig> loadWebhookConfigs() {
        if (configMapper == null) {
            return Collections.emptyList();
        }
        List<TencentMeetingCorpConfig> configs = configMapper.selectWebhookConfigsIgnoreTenant();
        if (configs == null) {
            return Collections.emptyList();
        }
        return configs;
    }

    private String decryptWebhookToken(TencentMeetingCorpConfig config) {
        if (config == null || StrUtil.isBlank(config.getWebhookTokenEncrypted()) || secretTextCipher == null) {
            return null;
        }
        return secretTextCipher.decrypt(config.getWebhookTokenEncrypted());
    }

    private String decryptEncodingAesKey(TencentMeetingCorpConfig config) {
        if (config == null || StrUtil.isBlank(config.getWebhookSecretEncrypted()) || secretTextCipher == null) {
            return null;
        }
        return secretTextCipher.decrypt(config.getWebhookSecretEncrypted());
    }

    private String decryptWebhookBodyIfNeeded(String body, TencentMeetingCorpConfig config, String encrypt) {
        if (StrUtil.isBlank(body)) {
            return body;
        }
        try {
            JSONObject root = JSONObject.parseObject(body);
            String encryptedData = root.getString("data");
            if (StrUtil.isBlank(encryptedData)) {
                return body;
            }
            String decrypted = decryptEncryptedTextIfNeeded(encryptedData, config);
            if (StrUtil.isBlank(decrypted)) {
                throw new IllegalArgumentException("Tencent Meeting webhook EncodingAESKey is required");
            }
            return decrypted;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception ignored) {
            return body;
        }
    }

    private String decryptEncryptedTextIfNeeded(String encryptedText, TencentMeetingCorpConfig config) {
        String encodingAesKey = decryptEncodingAesKey(config);
        if (StrUtil.isBlank(encryptedText) || StrUtil.isBlank(encodingAesKey)) {
            return null;
        }
        return decryptTencentMeetingAes(encryptedText, encodingAesKey);
    }

    private String decryptTencentMeetingAes(String encryptedText, String encodingAesKey) {
        try {
            byte[] key = Base64.getDecoder().decode(encodingAesKey + "=");
            if (key.length != 32) {
                throw new IllegalArgumentException("Tencent Meeting webhook EncodingAESKey must decode to 32 bytes");
            }
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
            if (encryptedBytes.length == 0 || encryptedBytes.length % 16 != 0) {
                throw new IllegalArgumentException("Tencent Meeting webhook encrypted data length is invalid");
            }
            byte[] iv = new byte[16];
            System.arraycopy(key, 0, iv, 0, iv.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            int padding = decryptedBytes[decryptedBytes.length - 1] & 0xff;
            if (padding < 1 || padding > 32 || padding > decryptedBytes.length) {
                throw new IllegalArgumentException("Tencent Meeting webhook encrypted data padding is invalid");
            }
            return new String(decryptedBytes, 0, decryptedBytes.length - padding, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Tencent Meeting webhook decrypt failed", e);
        }
    }

    private String extractSignaturePayload(String data) {
        if (StrUtil.isBlank(data)) {
            return "";
        }
        try {
            JSONObject root = JSONObject.parseObject(data);
            String encryptedData = root.getString("data");
            if (StrUtil.isNotBlank(encryptedData)) {
                return encryptedData;
            }
        } catch (Exception ignored) {
            // Plain callback validation payloads are signed directly.
        }
        return data;
    }

    static String calculateSignature(String token, String timestamp, String nonce, String data) {
        List<String> parts = new ArrayList<>();
        parts.add(StrUtil.nullToEmpty(token));
        parts.add(StrUtil.nullToEmpty(timestamp));
        parts.add(StrUtil.nullToEmpty(nonce));
        parts.add(StrUtil.nullToEmpty(data));
        parts.sort(Comparator.naturalOrder());
        StringBuilder raw = new StringBuilder();
        for (String part : parts) {
            raw.append(part);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate Tencent Meeting webhook signature", e);
        }
    }

    private boolean isRefreshEvent(String eventName) {
        return "meeting.created".equals(eventName)
                || "meeting.started".equals(eventName)
                || "meeting.end".equals(eventName)
                || "meeting.canceled".equals(eventName)
                || "meeting.cancelled".equals(eventName)
                || "recording.completed".equals(eventName)
                || "smart.transcripts".equals(eventName);
    }

    private JSONObject extractMeetingInfo(JSONObject root) {
        JSONArray payload = root.getJSONArray("payload");
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        JSONObject first = payload.getJSONObject(0);
        if (first == null) {
            return null;
        }
        JSONObject meetingInfo = first.getJSONObject("meeting_info");
        return meetingInfo != null ? meetingInfo : first;
    }
}
