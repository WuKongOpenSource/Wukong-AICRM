package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakarote.ai_crm.ai.DynamicChatClientProvider;
import com.kakarote.ai_crm.ai.context.AiContextHolder;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.KnowledgeAskBO;
import com.kakarote.ai_crm.entity.BO.KnowledgeAiSearchBO;
import com.kakarote.ai_crm.entity.BO.KnowledgeQueryBO;
import com.kakarote.ai_crm.entity.PO.Customer;
import com.kakarote.ai_crm.entity.PO.Knowledge;
import com.kakarote.ai_crm.entity.PO.KnowledgeTag;
import com.kakarote.ai_crm.entity.VO.KnowledgeAiAnalyzeVO;
import com.kakarote.ai_crm.entity.VO.KnowledgeAiSearchVO;
import com.kakarote.ai_crm.entity.VO.KnowledgeVO;
import com.kakarote.ai_crm.entity.VO.WeKnoraChunk;
import com.kakarote.ai_crm.entity.VO.WeKnoraKnowledge;
import com.kakarote.ai_crm.mapper.KnowledgeMapper;
import com.kakarote.ai_crm.mapper.KnowledgeTagMapper;
import com.kakarote.ai_crm.mapper.CustomerMapper;
import com.kakarote.ai_crm.service.FileStorageService;
import com.kakarote.ai_crm.service.IKnowledgeService;
import com.kakarote.ai_crm.service.WeKnoraClient;
import com.kakarote.ai_crm.utils.UserUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库服务实现
 */
@Slf4j
@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements IKnowledgeService {

    @Value("${file.upload-path:./uploads}")
    private String uploadPath;

    @Autowired
    private KnowledgeTagMapper knowledgeTagMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private WeKnoraClient weKnoraClient;

    @Autowired
    private FileStorageService fileStorageService;

    @Lazy
    @Autowired
    private KnowledgeServiceImpl self;

    @Lazy
    @Autowired
    private DynamicChatClientProvider chatClientProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int DEFAULT_AI_SEARCH_LIMIT = 5;
    private static final int MAX_AI_SEARCH_LIMIT = 8;
    private static final int MAX_AI_SEARCH_CONTEXT_LENGTH = 1200;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadFile(MultipartFile file, String type, Long customerId, String summary) {
        if (customerId != null) {
            Customer customer = customerMapper.selectById(customerId);
            if (ObjectUtil.isNull(customer)) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "客户不存在或无权限访问");
            }
        }
        // Generate file path
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileName = IdUtil.fastSimpleUUID() + "." + FileUtil.extName(file.getOriginalFilename());
        String relativePath = datePath + "/" + fileName;

        // Use FileStorageService to upload file
        fileStorageService.upload(file, relativePath);

        // Create knowledge record
        Knowledge knowledge = new Knowledge();
        knowledge.setName(file.getOriginalFilename());
        knowledge.setType(StrUtil.isEmpty(type) ? "document" : type);
        knowledge.setCustomerId(customerId);
        knowledge.setFilePath(relativePath);
        knowledge.setFileSize(file.getSize());
        knowledge.setSummary(summary);
        knowledge.setUploadUserId(UserUtil.getUserId());

        // 检查文件类型是否被 WeKnora 支持
        boolean weKnoraSupported = weKnoraClient.isSupportedFileType(file.getOriginalFilename());
        if (weKnoraSupported) {
            knowledge.setWeKnoraParseStatus("pending");
        } else {
            knowledge.setWeKnoraParseStatus("unsupported");
            log.info("文件类型不被 WeKnora 支持，跳过 RAG 处理: {}", file.getOriginalFilename());
        }

        save(knowledge);

        // Async upload to WeKnora (only for supported file types)
        if (weKnoraClient.isEnabled() && weKnoraSupported) {
            self.asyncUploadToWeKnora(knowledge.getKnowledgeId(), relativePath, file.getOriginalFilename());
        }

        return knowledge.getKnowledgeId();
    }

    /**
     * 异步上传文件到 WeKnora。
     */
    @Async
    public void asyncUploadToWeKnora(Long knowledgeId, String relativePath, String originalFilename) {
        File tempFile = null;
        try {
            // 在文件名前加上 knowledgeId 前缀，防止不同用户上传同名文件冲突
            String uniqueFilename = knowledgeId + "_" + originalFilename;
            log.info("开始上传文件到 WeKnora: knowledgeId={}, file={}", knowledgeId, uniqueFilename);

            String localPath = fileStorageService.getLocalPath(relativePath);
            String uploadFilePath;

            if (localPath != null) {
                uploadFilePath = localPath;
            } else {
                tempFile = File.createTempFile("weknora_", "_" + originalFilename);
                try (InputStream is = fileStorageService.getFileStream(relativePath)) {
                    Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                uploadFilePath = tempFile.getAbsolutePath();
            }

            WeKnoraKnowledge result = weKnoraClient.uploadDocument(uploadFilePath, uniqueFilename);

            // Update knowledge record
            Knowledge knowledge = baseMapper.selectByIdIgnoreDataPermission(knowledgeId);
            if (knowledge != null) {
                if (result == null) {
                    baseMapper.updateParseStatusIgnoreDataPermission(knowledgeId, "failed");
                    log.error("WeKnora 上传未返回有效结果: knowledgeId={}", knowledgeId);
                    return;
                }
                baseMapper.updateWeKnoraInfoIgnoreDataPermission(
                    knowledgeId, result.getId(), result.getParseStatus()
                );
                log.info("WeKnora 上传成功: knowledgeId={}, weKnoraId={}, status={}",
                    knowledgeId, result.getId(), result.getParseStatus());

                // 轮询等待 WeKnora 解析完成
                String currentStatus = result.getParseStatus();
                if ("pending".equals(currentStatus) || "processing".equals(currentStatus)) {
                    pollWeKnoraParseStatus(knowledgeId, result.getId());
                }
            }
        } catch (Exception e) {
            log.error("WeKnora 上传失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            Knowledge knowledge = baseMapper.selectByIdIgnoreDataPermission(knowledgeId);
            if (knowledge != null) {
                baseMapper.updateParseStatusIgnoreDataPermission(knowledgeId, "failed");
            }
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (ObjectUtil.isNull(knowledge)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "知识库文件不存在");
        }

        // Delete from WeKnora
        if (weKnoraClient.isEnabled() && StrUtil.isNotEmpty(knowledge.getWeKnoraKnowledgeId())) {
            try {
                weKnoraClient.deleteKnowledge(knowledge.getWeKnoraKnowledgeId());
                log.info("WeKnora 删除成功: weKnoraId={}", knowledge.getWeKnoraKnowledgeId());
            } catch (Exception e) {
                log.warn("WeKnora 删除失败: weKnoraId={}, error={}",
                    knowledge.getWeKnoraKnowledgeId(), e.getMessage());
            }
        }

        // Delete file from storage
        if (StrUtil.isNotEmpty(knowledge.getFilePath())) {
            fileStorageService.delete(knowledge.getFilePath());
        }

        // Delete record
        removeById(knowledgeId);

        // Delete tags
        knowledgeTagMapper.delete(
            new LambdaQueryWrapper<KnowledgeTag>().eq(KnowledgeTag::getKnowledgeId, knowledgeId)
        );
    }

    @Override
    public BasePage<KnowledgeVO> queryPageList(KnowledgeQueryBO queryBO) {
        BasePage<KnowledgeVO> page = queryBO.parse();
        baseMapper.queryPageList(page, queryBO);
        return page;
    }

    @Override
    public KnowledgeAiSearchVO aiSearch(KnowledgeAiSearchBO searchBO) {
        long startedAt = System.currentTimeMillis();
        String keyword = searchBO == null ? null : StrUtil.trim(searchBO.getKeyword());
        if (StrUtil.isBlank(keyword)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "Search keyword is required");
        }

        String type = searchBO == null ? null : StrUtil.trimToNull(searchBO.getType());
        int limit = normalizeAiSearchLimit(searchBO == null ? null : searchBO.getLimit());

        List<KnowledgeAiSearchVO.ReferenceItem> references = buildSemanticReferences(keyword, type, limit);
        if (references.isEmpty()) {
            references = buildLocalReferences(keyword, type, limit);
        }

        KnowledgeAiSearchVO result = new KnowledgeAiSearchVO();
        result.setKeyword(keyword);
        result.setReferences(references);
        result.setTotalHits(references.size());
        result.setMatchPercent(calculateOverallMatchPercent(references));
        result.setAnswer(buildAiSearchAnswer(keyword, references));
        result.setTookMs(System.currentTimeMillis() - startedAt);
        return result;
    }

    private int normalizeAiSearchLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_AI_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_AI_SEARCH_LIMIT);
    }

    private List<KnowledgeAiSearchVO.ReferenceItem> buildSemanticReferences(String keyword, String type, int limit) {
        if (!weKnoraClient.isEnabled()) {
            return List.of();
        }

        try {
            List<WeKnoraChunk> chunks = weKnoraClient.searchKnowledge(keyword);
            if (chunks == null || chunks.isEmpty()) {
                return List.of();
            }

            Map<String, SemanticSearchHit> hits = new LinkedHashMap<>();
            for (WeKnoraChunk chunk : chunks) {
                if (chunk == null || StrUtil.isBlank(chunk.getKnowledgeId())) {
                    continue;
                }
                SemanticSearchHit hit = hits.computeIfAbsent(chunk.getKnowledgeId(), ignored -> new SemanticSearchHit());
                if (StrUtil.isBlank(hit.excerpt) && StrUtil.isNotBlank(chunk.getContent())) {
                    hit.excerpt = chunk.getContent();
                }
                if (chunk.getScore() != null && (hit.score == null || chunk.getScore() > hit.score)) {
                    hit.score = chunk.getScore();
                }
            }

            if (hits.isEmpty()) {
                return List.of();
            }

            LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<Knowledge>()
                    .in(Knowledge::getWeKnoraKnowledgeId, hits.keySet())
                    .ne(Knowledge::getStatus, 2);
            if (StrUtil.isNotBlank(type)) {
                wrapper.eq(Knowledge::getType, type);
            }

            List<Knowledge> knowledges = list(wrapper);
            if (knowledges.isEmpty()) {
                return List.of();
            }

            Map<String, Knowledge> knowledgeByWeKnoraId = new LinkedHashMap<>();
            for (Knowledge knowledge : knowledges) {
                if (StrUtil.isNotBlank(knowledge.getWeKnoraKnowledgeId())) {
                    knowledgeByWeKnoraId.put(knowledge.getWeKnoraKnowledgeId(), knowledge);
                }
            }

            Map<Long, String> customerNames = loadCustomerNameMap(knowledges);
            List<KnowledgeAiSearchVO.ReferenceItem> references = new ArrayList<>();
            for (Map.Entry<String, SemanticSearchHit> entry : hits.entrySet()) {
                Knowledge knowledge = knowledgeByWeKnoraId.get(entry.getKey());
                if (knowledge == null) {
                    continue;
                }

                KnowledgeAiSearchVO.ReferenceItem reference = toReferenceItem(
                        knowledge,
                        customerNames.get(knowledge.getCustomerId())
                );
                SemanticSearchHit hit = entry.getValue();
                reference.setExcerpt(StrUtil.blankToDefault(abbreviate(hit.excerpt, 360),
                        extractSnippet(knowledge, keyword)));
                reference.setMatchPercent(toMatchPercent(hit.score));
                references.add(reference);
                if (references.size() >= limit) {
                    break;
                }
            }
            return references;
        } catch (Exception e) {
            log.warn("Knowledge semantic search failed, falling back to local search: {}", e.getMessage());
            return List.of();
        }
    }

    private List<KnowledgeAiSearchVO.ReferenceItem> buildLocalReferences(String keyword, String type, int limit) {
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<Knowledge>()
                .ne(Knowledge::getStatus, 2)
                .and(query -> query.like(Knowledge::getName, keyword)
                        .or()
                        .like(Knowledge::getSummary, keyword)
                        .or()
                        .like(Knowledge::getContentText, keyword))
                .orderByDesc(Knowledge::getCreateTime)
                .last("LIMIT " + limit);
        if (StrUtil.isNotBlank(type)) {
            wrapper.eq(Knowledge::getType, type);
        }

        List<Knowledge> knowledges = list(wrapper);
        if (knowledges.isEmpty()) {
            return List.of();
        }

        Map<Long, String> customerNames = loadCustomerNameMap(knowledges);
        List<KnowledgeAiSearchVO.ReferenceItem> references = new ArrayList<>();
        for (Knowledge knowledge : knowledges) {
            KnowledgeAiSearchVO.ReferenceItem reference = toReferenceItem(
                    knowledge,
                    customerNames.get(knowledge.getCustomerId())
            );
            reference.setExcerpt(extractSnippet(knowledge, keyword));
            reference.setMatchPercent(calculateLocalMatchPercent(knowledge, keyword));
            references.add(reference);
        }
        return references;
    }

    private Map<Long, String> loadCustomerNameMap(List<Knowledge> knowledges) {
        Set<Long> customerIds = new LinkedHashSet<>();
        for (Knowledge knowledge : knowledges) {
            if (knowledge.getCustomerId() != null) {
                customerIds.add(knowledge.getCustomerId());
            }
        }
        if (customerIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> names = new LinkedHashMap<>();
        List<Customer> customers = customerMapper.selectBatchIds(customerIds);
        for (Customer customer : customers) {
            names.put(customer.getCustomerId(), customer.getCompanyName());
        }
        return names;
    }

    private KnowledgeAiSearchVO.ReferenceItem toReferenceItem(Knowledge knowledge, String customerName) {
        KnowledgeAiSearchVO.ReferenceItem reference = new KnowledgeAiSearchVO.ReferenceItem();
        reference.setKnowledgeId(knowledge.getKnowledgeId());
        reference.setName(knowledge.getName());
        reference.setType(knowledge.getType());
        reference.setCustomerName(customerName);
        reference.setSummary(StrUtil.trimToNull(knowledge.getSummary()));
        reference.setFileSize(knowledge.getFileSize());
        reference.setCreateTime(knowledge.getCreateTime());
        return reference;
    }

    private String buildAiSearchAnswer(String keyword, List<KnowledgeAiSearchVO.ReferenceItem> references) {
        if (references.isEmpty()) {
            return "未找到与「" + keyword + "」相关的知识库内容。请尝试更换关键词，或先上传并解析相关文档。";
        }
        if (!chatClientProvider.isApiKeyConfigured()) {
            return buildFallbackAiSearchAnswer(keyword, references);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a CRM knowledge assistant. Answer in Simplified Chinese.\n");
        prompt.append("Use only the reference documents below. If evidence is insufficient, say so clearly.\n\n");
        prompt.append("Question: ").append(keyword).append("\n\n");
        prompt.append("References:\n");
        for (int i = 0; i < references.size(); i++) {
            KnowledgeAiSearchVO.ReferenceItem reference = references.get(i);
            prompt.append(i + 1)
                    .append(". Title: ").append(StrUtil.blankToDefault(reference.getName(), "-"))
                    .append("\n   Type: ").append(StrUtil.blankToDefault(reference.getType(), "-"))
                    .append("\n   Customer: ").append(StrUtil.blankToDefault(reference.getCustomerName(), "-"))
                    .append("\n   Summary: ").append(StrUtil.blankToDefault(reference.getSummary(), "-"))
                    .append("\n   Excerpt: ").append(StrUtil.blankToDefault(
                            abbreviate(reference.getExcerpt(), MAX_AI_SEARCH_CONTEXT_LENGTH), "-"))
                    .append("\n");
        }
        prompt.append("\nReturn a concise answer with key points and mention the most relevant documents.");

        try {
            String answer = chatClientProvider.getChatClient()
                    .prompt()
                    .user(prompt.toString())
                    .call()
                    .content();
            if (StrUtil.isNotBlank(answer)) {
                return answer.trim();
            }
        } catch (Exception e) {
            log.warn("Knowledge AI search answer generation failed: {}", e.getMessage());
        }
        return buildFallbackAiSearchAnswer(keyword, references);
    }

    private String buildFallbackAiSearchAnswer(String keyword, List<KnowledgeAiSearchVO.ReferenceItem> references) {
        StringBuilder answer = new StringBuilder();
        answer.append("已找到 ").append(references.size()).append(" 份与「").append(keyword).append("」相关的知识资料。\n\n");
        for (KnowledgeAiSearchVO.ReferenceItem reference : references) {
            answer.append("- **").append(StrUtil.blankToDefault(reference.getName(), "未命名文档")).append("**");
            if (reference.getMatchPercent() != null) {
                answer.append("，匹配度 ").append(reference.getMatchPercent()).append("%");
            }
            String detail = StrUtil.firstNonBlank(reference.getSummary(), reference.getExcerpt());
            if (StrUtil.isNotBlank(detail)) {
                answer.append("：").append(abbreviate(detail, 160));
            }
            answer.append("\n");
        }
        answer.append("\n配置自建 AI Key 后，可以获得更完整的归纳和建议。");
        return answer.toString();
    }

    private String extractSnippet(Knowledge knowledge, String keyword) {
        String source = StrUtil.firstNonBlank(knowledge.getContentText(), knowledge.getSummary(), knowledge.getName());
        String normalized = normalizeSearchableContent(source);
        if (StrUtil.isBlank(normalized)) {
            return null;
        }

        String lowerSource = normalized.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int index = lowerSource.indexOf(lowerKeyword);
        if (index < 0) {
            return abbreviate(normalized, 260);
        }

        int start = Math.max(0, index - 80);
        int end = Math.min(normalized.length(), index + lowerKeyword.length() + 180);
        String snippet = normalized.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < normalized.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private int calculateLocalMatchPercent(Knowledge knowledge, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        int score = 40;
        if (StrUtil.blankToDefault(knowledge.getName(), "").toLowerCase().contains(lowerKeyword)) {
            score += 35;
        }
        if (StrUtil.blankToDefault(knowledge.getSummary(), "").toLowerCase().contains(lowerKeyword)) {
            score += 20;
        }
        if (StrUtil.blankToDefault(knowledge.getContentText(), "").toLowerCase().contains(lowerKeyword)) {
            score += 15;
        }
        return Math.min(score, 98);
    }

    private int calculateOverallMatchPercent(List<KnowledgeAiSearchVO.ReferenceItem> references) {
        if (references.isEmpty()) {
            return 0;
        }
        int sum = 0;
        int count = 0;
        for (KnowledgeAiSearchVO.ReferenceItem reference : references) {
            if (reference.getMatchPercent() != null) {
                sum += reference.getMatchPercent();
                count += 1;
            }
        }
        return count == 0 ? 0 : Math.round((float) sum / count);
    }

    private int toMatchPercent(Double score) {
        if (score == null) {
            return 86;
        }
        double normalized = score <= 1 ? score * 100 : score;
        return (int) Math.max(30, Math.min(99, Math.round(normalized)));
    }

    private String normalizeSearchableContent(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String abbreviate(String text, int maxLength) {
        String normalized = normalizeSearchableContent(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    @Override
    public KnowledgeVO getKnowledgeDetail(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (ObjectUtil.isNull(knowledge)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "知识库文件不存在");
        }
        return BeanUtil.copyProperties(knowledge, KnowledgeVO.class);
    }

    /**
     * 轮询 WeKnora 解析状态，直到完成或失败
     */
    private void pollWeKnoraParseStatus(Long knowledgeId, String weKnoraKnowledgeId) {
        int maxAttempts = 60;
        long intervalMs = 20000; // 20秒

        for (int i = 0; i < maxAttempts; i++) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                WeKnoraKnowledge detail = weKnoraClient.getKnowledgeDetail(weKnoraKnowledgeId);
                if (detail == null) {
                    log.warn("WeKnora 状态查询返回 null: weKnoraId={}", weKnoraKnowledgeId);
                    continue;
                }

                String status = detail.getParseStatus();
                log.debug("WeKnora 解析状态轮询: knowledgeId={}, attempt={}, status={}",
                    knowledgeId, i + 1, status);

                if ("completed".equals(status) || "success".equals(status) || "failed".equals(status)) {
                    Knowledge knowledge = baseMapper.selectByIdIgnoreDataPermission(knowledgeId);
                    if (knowledge != null) {
                        baseMapper.updateParseStatusIgnoreDataPermission(
                            knowledgeId, "success".equals(status) ? "completed" : status
                        );
                        log.info("WeKnora 解析完成: knowledgeId={}, status={}", knowledgeId, status);
                    }
                    return;
                }
            } catch (Exception e) {
                log.warn("WeKnora 状态查询异常: knowledgeId={}, error={}", knowledgeId, e.getMessage());
            }
        }

        log.warn("WeKnora 解析状态轮询超时(20min): knowledgeId={}, weKnoraId={}", knowledgeId, weKnoraKnowledgeId);
    }

    @Override
    public void reparseKnowledge(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (ObjectUtil.isNull(knowledge)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "知识库文件不存在");
        }
        if (!weKnoraClient.isEnabled()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "WeKnora 未启用");
        }
        if (!weKnoraClient.isSupportedFileType(knowledge.getName())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "该文件类型不支持 RAG 解析");
        }

        // 删除 WeKnora 中的旧记录（如果存在）
        if (StrUtil.isNotEmpty(knowledge.getWeKnoraKnowledgeId())) {
            try {
                weKnoraClient.deleteKnowledge(knowledge.getWeKnoraKnowledgeId());
                log.info("WeKnora 旧记录已删除: weKnoraId={}", knowledge.getWeKnoraKnowledgeId());
            } catch (Exception e) {
                log.warn("删除 WeKnora 旧记录失败: {}", e.getMessage());
            }
        }

        // 重置状态
        knowledge.setWeKnoraParseStatus("pending");
        knowledge.setWeKnoraKnowledgeId(null);
        updateById(knowledge);

        // 重新异步上传（含轮询）
        self.asyncUploadToWeKnora(knowledge.getKnowledgeId(), knowledge.getFilePath(), knowledge.getName());
    }

    @Override
    public void addTag(Long knowledgeId, String tagName) {
        Knowledge knowledge = getById(knowledgeId);
        if (ObjectUtil.isNull(knowledge)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "知识库文件不存在");
        }

        // Check if tag already exists
        Long count = knowledgeTagMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeTag>()
                .eq(KnowledgeTag::getKnowledgeId, knowledgeId)
                .eq(KnowledgeTag::getTagName, tagName)
        );
        if (count > 0) {
            return;
        }

        KnowledgeTag tag = new KnowledgeTag();
        tag.setKnowledgeId(knowledgeId);
        tag.setTagName(tagName);
        knowledgeTagMapper.insert(tag);
    }

    // ==================== AI 文档分析 ====================

    private static final String AI_ANALYZE_PROMPT_TEMPLATE = """
        你是一个专业的 CRM 助手。请分析以下知识库文档，提取关键信息并以 JSON 格式返回。

        文档名称: %s
        文档类型: %s
        文档摘要: %s

        文档内容:
        %s

        请严格按以下 JSON 格式返回，不要包含任何其他文字、代码块标记或解释：
        {
          "coreHighlights": "文档核心内容的精炼总结（2-3句话，突出关键信息和价值点）",
          "talkingPoints": ["基于文档内容的销售话术建议1", "话术建议2", "话术建议3"],
          "relatedEntities": [{"name": "相关的客户或商机名称", "type": "customer 或 opportunity"}]
        }

        注意：
        - coreHighlights 应该是对文档最重要内容的高度概括
        - talkingPoints 应该是3-5条可以在销售场景中使用的话术建议
        - relatedEntities 从文档中提取提到的客户名称、公司名称或商机/项目名称
        """;

    private static final String DOC_QA_SYSTEM_PROMPT_TEMPLATE = """
        你是一个专业的文档问答助手。请基于以下文档内容回答用户的问题。

        文档名称: %s
        文档类型: %s

        文档内容:
        %s

        请用中文回答，回答要准确、简洁。如果文档中没有相关信息，请如实说明。
        """;

    @Override
    public KnowledgeAiAnalyzeVO aiAnalyzeDocument(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (ObjectUtil.isNull(knowledge)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "知识库文件不存在");
        }

        KnowledgeAiAnalyzeVO cachedResult = readCachedAnalyzeResult(knowledge);
        if (cachedResult != null) {
            return cachedResult;
        }

        String contentText = StrUtil.blankToDefault(knowledge.getContentText(), "");
        // 截断过长的内容
        if (contentText.length() > 4000) {
            contentText = contentText.substring(0, 4000) + "...";
        }

        String summary = StrUtil.blankToDefault(knowledge.getSummary(), "无");
        String prompt = String.format(AI_ANALYZE_PROMPT_TEMPLATE,
                knowledge.getName(),
                StrUtil.blankToDefault(knowledge.getType(), "document"),
                summary,
                contentText);

        try {
            String response = chatClientProvider.getChatClient()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("AI 文档分析原始响应: {}", response);
            KnowledgeAiAnalyzeVO result = parseAnalyzeResponse(response, knowledge);
            persistAnalyzeResult(knowledge, result);
            return result;
        } catch (Exception e) {
            log.error("AI 文档分析失败，返回默认值", e);
            return buildFallbackAnalyzeResult(knowledge);
        }
    }

    private KnowledgeAiAnalyzeVO readCachedAnalyzeResult(Knowledge knowledge) {
        String snapshot = StrUtil.trimToNull(knowledge.getAiAnalysisSnapshot());
        if (snapshot == null) {
            return null;
        }

        try {
            KnowledgeAiAnalyzeVO cached = objectMapper.readValue(snapshot, KnowledgeAiAnalyzeVO.class);
            return normalizeAnalyzeResult(cached, knowledge);
        } catch (Exception e) {
            log.warn("解析知识库缓存分析结果失败: knowledgeId={}, error={}",
                    knowledge.getKnowledgeId(), e.getMessage());
            return null;
        }
    }

    private void persistAnalyzeResult(Knowledge knowledge, KnowledgeAiAnalyzeVO result) {
        try {
            KnowledgeAiAnalyzeVO normalized = normalizeAnalyzeResult(result, knowledge);
            knowledge.setAiAnalysisSnapshot(objectMapper.writeValueAsString(normalized));
            knowledge.setAiAnalysisTime(new Date());
            updateById(knowledge);
        } catch (Exception e) {
            log.warn("保存知识库 AI 分析缓存失败: knowledgeId={}, error={}",
                    knowledge.getKnowledgeId(), e.getMessage());
        }
    }

    private KnowledgeAiAnalyzeVO normalizeAnalyzeResult(KnowledgeAiAnalyzeVO source, Knowledge knowledge) {
        KnowledgeAiAnalyzeVO normalized = new KnowledgeAiAnalyzeVO();
        if (source != null) {
            normalized.setCoreHighlights(source.getCoreHighlights());
            normalized.setTalkingPoints(source.getTalkingPoints());
            normalized.setRelatedEntities(source.getRelatedEntities());
        }
        if (StrUtil.isBlank(normalized.getCoreHighlights())) {
            normalized.setCoreHighlights(StrUtil.blankToDefault(knowledge.getSummary(), "暂无摘要"));
        }
        if (normalized.getTalkingPoints() == null) {
            normalized.setTalkingPoints(List.of());
        }
        if (normalized.getRelatedEntities() == null) {
            normalized.setRelatedEntities(List.of());
        }
        return normalized;
    }

    private KnowledgeAiAnalyzeVO parseAnalyzeResponse(String response, Knowledge knowledge) {
        try {
            // Strip markdown code block markers if present
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```(?:json)?\\s*", "");
                json = json.replaceFirst("\\s*```$", "");
            }

            JsonNode root = objectMapper.readTree(json);
            KnowledgeAiAnalyzeVO vo = new KnowledgeAiAnalyzeVO();

            // 核心提炼
            if (root.has("coreHighlights") && !root.get("coreHighlights").isNull()) {
                vo.setCoreHighlights(root.get("coreHighlights").asText());
            } else {
                vo.setCoreHighlights(StrUtil.blankToDefault(knowledge.getSummary(), "暂无摘要"));
            }

            // 推荐话术
            List<String> talkingPoints = new ArrayList<>();
            if (root.has("talkingPoints") && root.get("talkingPoints").isArray()) {
                root.get("talkingPoints").forEach(n -> talkingPoints.add(n.asText()));
            }
            vo.setTalkingPoints(talkingPoints);

            // 关联实体
            List<KnowledgeAiAnalyzeVO.RelatedEntity> entities = new ArrayList<>();
            if (root.has("relatedEntities") && root.get("relatedEntities").isArray()) {
                root.get("relatedEntities").forEach(n -> {
                    KnowledgeAiAnalyzeVO.RelatedEntity entity = new KnowledgeAiAnalyzeVO.RelatedEntity();
                    entity.setName(n.has("name") ? n.get("name").asText() : "");
                    entity.setType(n.has("type") ? n.get("type").asText() : "");
                    if (StrUtil.isNotBlank(entity.getName())) {
                        entities.add(entity);
                    }
                });
            }
            vo.setRelatedEntities(entities);

            return vo;
        } catch (Exception e) {
            log.warn("AI 文档分析 JSON 解析失败: {}", e.getMessage());
            return buildFallbackAnalyzeResult(knowledge);
        }
    }

    private KnowledgeAiAnalyzeVO buildFallbackAnalyzeResult(Knowledge knowledge) {
        KnowledgeAiAnalyzeVO vo = new KnowledgeAiAnalyzeVO();
        vo.setCoreHighlights(StrUtil.blankToDefault(knowledge.getSummary(), "暂无摘要"));
        vo.setTalkingPoints(List.of());
        vo.setRelatedEntities(List.of());
        return vo;
    }

    @Override
    public Flux<String> askDocumentQuestion(Long knowledgeId, KnowledgeAskBO askBO) {
        Knowledge knowledge = getById(knowledgeId);
        if (ObjectUtil.isNull(knowledge)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "知识库文件不存在");
        }

        // 设置 AI 上下文
        Long currentUserId = UserUtil.getUserIdOrNull();
        if (currentUserId != null) {
            AiContextHolder.setContext(knowledgeId, currentUserId);
        }

        // 构建文档内容上下文
        String contentText = StrUtil.blankToDefault(knowledge.getContentText(), "");
        if (contentText.length() > 4000) {
            contentText = contentText.substring(0, 4000) + "...";
        }

        String systemPrompt = String.format(DOC_QA_SYSTEM_PROMPT_TEMPLATE,
                knowledge.getName(),
                StrUtil.blankToDefault(knowledge.getType(), "document"),
                contentText);

        // 构建对话历史
        List<Message> history = new ArrayList<>();
        if (askBO.getHistory() != null) {
            for (KnowledgeAskBO.ChatHistoryItem item : askBO.getHistory()) {
                if ("user".equals(item.getRole())) {
                    history.add(new UserMessage(item.getContent()));
                } else if ("assistant".equals(item.getRole())) {
                    history.add(new AssistantMessage(item.getContent()));
                }
            }
        }

        // 流式调用
        return chatClientProvider.getChatClient()
                .prompt()
                .system(systemPrompt)
                .messages(history)
                .user(askBO.getQuestion())
                .stream()
                .chatResponse()
                .mapNotNull(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        return chatResponse.getResult().getOutput().getText();
                    }
                    return null;
                })
                .doOnComplete(AiContextHolder::clear)
                .doOnError(error -> {
                    log.error("文档问答错误: {}", error.getMessage(), error);
                    AiContextHolder.clear();
                });
    }

    private static final class SemanticSearchHit {
        private String excerpt;
        private Double score;
    }
}
