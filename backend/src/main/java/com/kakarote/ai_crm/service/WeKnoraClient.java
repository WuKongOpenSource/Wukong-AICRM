package com.kakarote.ai_crm.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakarote.ai_crm.config.WeKnoraConfig;
import com.kakarote.ai_crm.entity.PO.CrmTenant;
import com.kakarote.ai_crm.entity.VO.WeKnoraChunk;
import com.kakarote.ai_crm.entity.VO.WeKnoraKnowledge;
import com.kakarote.ai_crm.mapper.CrmTenantMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WeKnora REST 客户端
 * 支持多租户隔离：每个 CRM 租户对应一个 WeKnora 租户（独立 API Key）
 */
@Slf4j
@Service
public class WeKnoraClient {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "txt", "md", "markdown",
            "xls", "xlsx", "csv",
            "html", "htm", "json", "xml"
    );

    private final RestTemplate restTemplate;
    private final WeKnoraConfig config;
    private final ObjectMapper objectMapper;
    private final CrmTenantMapper crmTenantMapper;
    private final ConcurrentHashMap<Long, Object> tenantLocks = new ConcurrentHashMap<>();

    public WeKnoraClient(WeKnoraConfig config, CrmTenantMapper crmTenantMapper) {
        this.config = config;
        this.crmTenantMapper = crmTenantMapper;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isSupportedFileType(String filename) {
        if (filename == null || !filename.contains(".")) return false;
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    public Set<String> getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    // ==================== 租户上下文（核心） ====================

    /**
     * 租户的 WeKnora 上下文（API Key + 知识库 ID）
     */
    @Data
    public static class TenantWeKnoraContext {
        private final String apiKey;
        private final String knowledgeBaseId;
    }

    /**
     * 获取或创建租户的 WeKnora 上下文（懒初始化）
     * 流程：注册 WeKnora 租户 → 创建 Embedding 模型 → 创建知识库
     */
    public TenantWeKnoraContext getOrCreateTenantContext(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }

        CrmTenant tenant = crmTenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new IllegalStateException("租户不存在: " + tenantId);
        }

        // 快速路径：已完成初始化
        if (StrUtil.isNotBlank(tenant.getWeKnoraApiKey()) && StrUtil.isNotBlank(tenant.getWeKnoraKnowledgeBaseId())) {
            return new TenantWeKnoraContext(tenant.getWeKnoraApiKey(), tenant.getWeKnoraKnowledgeBaseId());
        }

        // 需要初始化 — 加锁防并发
        Object lock = tenantLocks.computeIfAbsent(tenantId, k -> new Object());
        synchronized (lock) {
            try {
                // double-check
                tenant = crmTenantMapper.selectById(tenantId);
                if (StrUtil.isNotBlank(tenant.getWeKnoraApiKey()) && StrUtil.isNotBlank(tenant.getWeKnoraKnowledgeBaseId())) {
                    return new TenantWeKnoraContext(tenant.getWeKnoraApiKey(), tenant.getWeKnoraKnowledgeBaseId());
                }

                String apiKey = tenant.getWeKnoraApiKey();
                String kbId = tenant.getWeKnoraKnowledgeBaseId();

                // Step 1: 注册 WeKnora 租户（如果没有 API Key）
                if (StrUtil.isBlank(apiKey)) {
                    String email = "tenant_" + tenantId + "@crm.internal";
                    String password = "CrmTenant!" + tenantId;
                    String username = "tenant_" + tenantId;
                    apiKey = registerWeKnoraTenant(email, password, username);

                    LambdaUpdateWrapper<CrmTenant> update = new LambdaUpdateWrapper<>();
                    update.eq(CrmTenant::getTenantId, tenantId).set(CrmTenant::getWeKnoraApiKey, apiKey);
                    crmTenantMapper.update(null, update);
                    log.info("WeKnora 租户注册成功: tenantId={}", tenantId);
                }

                // Step 2: 创建 Embedding 模型 + 知识库（如果没有 KB ID）
                if (StrUtil.isBlank(kbId)) {
                    String embeddingModelId = createEmbeddingModel(apiKey);
                    kbId = createKnowledgeBase(apiKey, "default", embeddingModelId);

                    LambdaUpdateWrapper<CrmTenant> update = new LambdaUpdateWrapper<>();
                    update.eq(CrmTenant::getTenantId, tenantId).set(CrmTenant::getWeKnoraKnowledgeBaseId, kbId);
                    crmTenantMapper.update(null, update);
                    log.info("WeKnora 知识库创建成功: tenantId={}, kbId={}", tenantId, kbId);
                }

                return new TenantWeKnoraContext(apiKey, kbId);
            } finally {
                tenantLocks.remove(tenantId);
            }
        }
    }

    // ==================== WeKnora 租户注册 ====================

    /**
     * 在 WeKnora 中注册租户，返回 API Key
     * POST /auth/register（无需认证）
     */
    private String registerWeKnoraTenant(String email, String password, String username) {
        String url = config.getBaseUrl() + "/auth/register";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("email", email, "password", password, "username", username);
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("tenant") && root.get("tenant").has("api_key")) {
                    return root.get("tenant").get("api_key").asText();
                }
            }
            throw new RuntimeException("WeKnora 注册失败: " + response.getBody());
        } catch (Exception e) {
            log.error("WeKnora 租户注册异常: {}", e.getMessage(), e);
            throw new RuntimeException("WeKnora 租户注册失败: " + e.getMessage(), e);
        }
    }

    // ==================== 模型管理 ====================

    /**
     * 为租户创建 Embedding 模型，返回 model ID
     * POST /models（需租户 X-API-Key）
     */
    private String createEmbeddingModel(String tenantApiKey) {
        String url = config.getBaseUrl() + "/models";

        HttpHeaders headers = createHeaders(tenantApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        WeKnoraConfig.InitModels.EmbeddingModel embCfg = config.getInitModels().getEmbedding();

        Map<String, Object> embeddingParams = new HashMap<>();
        embeddingParams.put("dimension", embCfg.getDimension());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("api_key", embCfg.getApiKey());
        parameters.put("base_url", embCfg.getBaseUrl());
        parameters.put("provider", embCfg.getProvider());
        parameters.put("embedding_parameters", embeddingParams);

        Map<String, Object> body = new HashMap<>();
        body.put("name", embCfg.getName());
        body.put("type", "Embedding");
        body.put("source", embCfg.getSource());
        body.put("parameters", parameters);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("data") && root.get("data").has("id")) {
                    String modelId = root.get("data").get("id").asText();
                    log.info("WeKnora Embedding 模型创建成功: modelId={}", modelId);
                    return modelId;
                }
                // 有些 API 直接返回 id
                if (root.has("id")) {
                    return root.get("id").asText();
                }
            }
            throw new RuntimeException("WeKnora 创建 Embedding 模型失败: " + response.getBody());
        } catch (Exception e) {
            log.error("WeKnora 创建 Embedding 模型异常: {}", e.getMessage(), e);
            throw new RuntimeException("WeKnora 创建 Embedding 模型失败: " + e.getMessage(), e);
        }
    }

    // ==================== 知识库管理 ====================

    /**
     * 创建知识库，返回 KB ID
     * POST /knowledge-bases（需租户 X-API-Key）
     */
    public String createKnowledgeBase(String tenantApiKey, String name, String embeddingModelId) {
        String url = config.getBaseUrl() + "/knowledge-bases";

        HttpHeaders headers = createHeaders(tenantApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("embedding_model_id", embeddingModelId);
        body.put("type", "document");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("data") && root.get("data").has("id")) {
                    return root.get("data").get("id").asText();
                }
                if (root.has("id")) {
                    return root.get("id").asText();
                }
            }
            throw new RuntimeException("WeKnora 创建知识库失败: " + response.getBody());
        } catch (Exception e) {
            log.error("WeKnora 创建知识库异常: {}", e.getMessage(), e);
            throw new RuntimeException("WeKnora 创建知识库失败: " + e.getMessage(), e);
        }
    }

    // ==================== 文档上传 ====================

    public WeKnoraKnowledge uploadDocument(String filePath, String originalFilename, String knowledgeBaseId, String tenantApiKey) throws IOException {
        File file = new File(filePath);
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        return uploadDocument(fileBytes, originalFilename, knowledgeBaseId, tenantApiKey);
    }

    public WeKnoraKnowledge uploadDocument(byte[] fileBytes, String originalFilename, String knowledgeBaseId, String tenantApiKey) throws IOException {
        if (!isEnabled()) {
            log.debug("WeKnora 未启用，跳过上传");
            return null;
        }

        String url = config.getBaseUrl() + "/knowledge-bases/" + knowledgeBaseId + "/knowledge/file";

        HttpHeaders headers = createHeaders(tenantApiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return originalFilename;
            }
        });
        body.add("enable_multimodel", "true");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("success") && root.get("success").asBoolean() && root.has("data")) {
                    return objectMapper.treeToValue(root.get("data"), WeKnoraKnowledge.class);
                }
            }
            log.error("WeKnora 上传失败: {}", response.getBody());
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException.Conflict e) {
            log.info("WeKnora 文件已存在，使用已有记录: {}", originalFilename);
            try {
                JsonNode root = objectMapper.readTree(e.getResponseBodyAsString());
                if (root.has("data")) {
                    return objectMapper.treeToValue(root.get("data"), WeKnoraKnowledge.class);
                }
            } catch (Exception parseEx) {
                log.warn("解析重复文件响应失败: {}", parseEx.getMessage());
            }
            return null;
        } catch (Exception e) {
            log.error("WeKnora 上传异常: {}", e.getMessage(), e);
            throw new RuntimeException("WeKnora 上传失败: " + e.getMessage(), e);
        }
    }

    // ==================== 删除 ====================

    public void deleteKnowledge(String weKnoraKnowledgeId, String tenantApiKey) {
        if (!isEnabled() || weKnoraKnowledgeId == null) return;

        String url = config.getBaseUrl() + "/knowledge/" + weKnoraKnowledgeId;
        HttpEntity<?> requestEntity = new HttpEntity<>(createHeaders(tenantApiKey));

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("WeKnora 知识删除成功: {}", weKnoraKnowledgeId);
            } else {
                log.warn("WeKnora 知识删除失败: {} - {}", weKnoraKnowledgeId, response.getBody());
            }
        } catch (Exception e) {
            log.error("WeKnora 删除异常: {}", e.getMessage(), e);
        }
    }

    // ==================== 搜索 ====================

    public List<WeKnoraChunk> searchKnowledge(String query, String knowledgeBaseId, String tenantApiKey) {
        if (!isEnabled()) return Collections.emptyList();

        String url = config.getBaseUrl() + "/knowledge-search";

        HttpHeaders headers = createHeaders(tenantApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("knowledge_base_id", knowledgeBaseId);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("success") && root.get("success").asBoolean() && root.has("data")) {
                    List<WeKnoraChunk> chunks = objectMapper.convertValue(
                            root.get("data"), new TypeReference<List<WeKnoraChunk>>() {});
                    double threshold = config.getSearch().getVectorThreshold();
                    int maxCount = config.getSearch().getMatchCount();
                    return chunks.stream()
                            .filter(c -> c.getScore() == null || c.getScore() >= threshold)
                            .limit(maxCount)
                            .toList();
                }
            }
            log.warn("WeKnora 搜索返回空结果: {}", response.getBody());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("WeKnora 搜索异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public List<WeKnoraChunk> hybridSearch(String query, String knowledgeBaseId, String tenantApiKey) {
        if (!isEnabled()) return Collections.emptyList();

        String url = config.getBaseUrl() + "/knowledge-bases/" + knowledgeBaseId + "/hybrid-search";

        HttpHeaders headers = createHeaders(tenantApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query_text", query);
        requestBody.put("vector_threshold", config.getSearch().getVectorThreshold());
        requestBody.put("match_count", config.getSearch().getMatchCount());

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("success") && root.get("success").asBoolean() && root.has("data")) {
                    return objectMapper.convertValue(root.get("data"), new TypeReference<List<WeKnoraChunk>>() {});
                }
            }
            log.warn("WeKnora 混合搜索返回空结果: {}", response.getBody());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("WeKnora 混合搜索异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ==================== 知识详情 ====================

    public WeKnoraKnowledge getKnowledgeDetail(String weKnoraKnowledgeId, String tenantApiKey) {
        if (!isEnabled() || weKnoraKnowledgeId == null) return null;

        String url = config.getBaseUrl() + "/knowledge/" + weKnoraKnowledgeId;
        HttpEntity<?> requestEntity = new HttpEntity<>(createHeaders(tenantApiKey));

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("success") && root.get("success").asBoolean() && root.has("data")) {
                    return objectMapper.treeToValue(root.get("data"), WeKnoraKnowledge.class);
                }
            }
            return null;
        } catch (Exception e) {
            log.error("WeKnora 获取知识详情异常: {}", e.getMessage(), e);
            return null;
        }
    }

    private HttpHeaders createHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }
}
