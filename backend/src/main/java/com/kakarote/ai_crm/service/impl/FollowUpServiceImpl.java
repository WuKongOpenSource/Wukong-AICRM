package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakarote.ai_crm.ai.DynamicChatClientProvider;
import com.kakarote.ai_crm.ai.provider.AiModelCapabilities;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.ChatSendBO;
import com.kakarote.ai_crm.entity.BO.FollowUpAddBO;
import com.kakarote.ai_crm.entity.BO.FollowUpAiParseBO;
import com.kakarote.ai_crm.entity.BO.FollowUpQueryBO;
import com.kakarote.ai_crm.entity.BO.FollowUpSuggestedTaskBO;
import com.kakarote.ai_crm.entity.BO.FollowUpUpdateBO;
import com.kakarote.ai_crm.entity.BO.TaskAddBO;
import com.kakarote.ai_crm.entity.PO.Customer;
import com.kakarote.ai_crm.entity.PO.FollowUp;
import com.kakarote.ai_crm.entity.PO.FollowUpAttachment;
import com.kakarote.ai_crm.entity.PO.Relation;
import com.kakarote.ai_crm.entity.PO.Task;
import com.kakarote.ai_crm.entity.VO.FollowUpAiParseVO;
import com.kakarote.ai_crm.entity.VO.FollowUpAttachmentVO;
import com.kakarote.ai_crm.entity.VO.FollowUpLinkedTaskVO;
import com.kakarote.ai_crm.entity.VO.FollowUpVO;
import com.kakarote.ai_crm.mapper.CustomerMapper;
import com.kakarote.ai_crm.mapper.FollowUpAttachmentMapper;
import com.kakarote.ai_crm.mapper.FollowUpMapper;
import com.kakarote.ai_crm.mapper.RelationMapper;
import com.kakarote.ai_crm.service.AiAudioTranscriptionService;
import com.kakarote.ai_crm.service.FileStorageService;
import com.kakarote.ai_crm.service.ICustomerService;
import com.kakarote.ai_crm.service.IFollowUpService;
import com.kakarote.ai_crm.service.ITaskService;
import com.kakarote.ai_crm.utils.AiMediaUtil;
import com.kakarote.ai_crm.utils.DocumentTextExtractor;
import com.kakarote.ai_crm.utils.UserUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 跟进记录服务实现
 */
@Slf4j
@Service
public class FollowUpServiceImpl extends ServiceImpl<FollowUpMapper, FollowUp> implements IFollowUpService {

    private static final DateTimeFormatter AI_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_EXTRACTED_TEXT_LENGTH = 3000;
    private static final int MAX_ATTACHMENT_ANALYSIS_LENGTH = 4000;
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
    private static final Pattern CHINESE_RELATIVE_TIME_PATTERN = Pattern.compile(
        "(明天|后天|明早|明晚|今天)\\s*(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|今晚)?\\s*(\\d{1,2})(?:\\s*[:点时]\\s*(\\d{1,2}))?(半)?\\s*(?:分)?"
    );
    private static final Pattern ENGLISH_RELATIVE_TIME_PATTERN = Pattern.compile(
        "(?i)\\b(today|tomorrow|day after tomorrow)\\b(?:\\s+at)?(?:\\s+(morning|afternoon|evening))?\\s*(\\d{1,2})(?::(\\d{1,2}))?\\s*(am|pm)?"
    );

    private static final String AI_PARSE_PROMPT_TEMPLATE = """
        你是一个专业的 CRM 助手。请分析以下跟进记录，提取关键信息并以 JSON 格式返回。
        客户名称: %s
        当前时间: %s

        跟进内容:
        %s

        不要臆造沟通渠道：如果内容只表达报价、完成报价或完成事项，没有明确电话、会议、邮件、拜访等渠道词，type 必须使用 other。
        请严格按照以下 JSON 格式返回，不要包含任何其他文字、代码块标记或解释：
        {
          "summary": "简明扼要的摘要，1-2句话",
          "type": "跟进类型，只能是以下之一: call, meeting, email, visit, other",
          "followTime": "跟进发生的时间，格式 yyyy-MM-dd HH:mm:ss（如未提及则用当前时间）",
          "nextFollowTime": "建议的下次跟进时间，格式 yyyy-MM-dd HH:mm:ss（根据内容合理推断，通常3-7天后）",
          "keyPoints": ["要点1", "要点2"],
          "todos": ["待办事项1", "待办事项2"]
        }
        """;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private RelationMapper relationMapper;

    @Autowired
    private FollowUpAttachmentMapper followUpAttachmentMapper;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private ICustomerService customerService;

    @Autowired
    private AiAudioTranscriptionService aiAudioTranscriptionService;

    @Lazy
    @Autowired
    private DynamicChatClientProvider chatClientProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addFollowUp(FollowUpAddBO followUpAddBO) {
        Relation relation = validateRelation(followUpAddBO.getRelationId());
        if (followUpAddBO.getCustomerId() == null && relation != null) {
            followUpAddBO.setCustomerId(relation.getCustomerId());
        }
        if (followUpAddBO.getCustomerId() == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "客户或关系人不能为空");
        }

        Customer customer = customerMapper.selectById(followUpAddBO.getCustomerId());
        if (ObjectUtil.isNull(customer)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "客户不存在或无权限访问");
        }

        FollowUp followUp = BeanUtil.copyProperties(followUpAddBO, FollowUp.class);
        if (followUp.getFollowTime() == null) {
            followUp.setFollowTime(new Date());
        }
        if (followUp.getAiGenerated() == null) {
            followUp.setAiGenerated(0);
        }
        followUp.setSummary(normalizeStoredSummary(followUp.getSummary()));
        save(followUp);
        saveAttachments(followUp.getFollowUpId(), followUpAddBO.getAttachments());
        createSuggestedTasks(followUp, followUpAddBO.getSuggestedTasks());
        syncCustomerFollowUpSummary(followUp.getCustomerId());

        return followUp.getFollowUpId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFollowUp(FollowUpUpdateBO followUpUpdateBO) {
        FollowUp followUp = getById(followUpUpdateBO.getFollowUpId());
        if (ObjectUtil.isNull(followUp)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "跟进记录不存在");
        }
        validateRelation(followUpUpdateBO.getRelationId());

        followUp.setRelationId(followUpUpdateBO.getRelationId());
        followUp.setContactId(followUpUpdateBO.getContactId());
        followUp.setType(followUpUpdateBO.getType());
        followUp.setContent(followUpUpdateBO.getContent());
        followUp.setSummary(followUpUpdateBO.getSummary());
        followUp.setSceneType(followUpUpdateBO.getSceneType());
        followUp.setAiGenerated(followUpUpdateBO.getAiGenerated());
        followUp.setFollowTime(followUpUpdateBO.getFollowTime());
        followUp.setNextFollowTime(followUpUpdateBO.getNextFollowTime());
        followUp.setSummary(normalizeStoredSummary(followUp.getSummary()));
        updateById(followUp);
        syncCustomerFollowUpSummary(followUp.getCustomerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFollowUp(Long followUpId) {
        FollowUp followUp = getById(followUpId);
        if (ObjectUtil.isNull(followUp)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "跟进记录不存在");
        }
        Long customerId = followUp.getCustomerId();
        List<FollowUpAttachment> attachments = followUpAttachmentMapper.selectList(
            Wrappers.<FollowUpAttachment>lambdaQuery().eq(FollowUpAttachment::getFollowUpId, followUpId)
        );
        for (FollowUpAttachment attachment : attachments) {
            safeDeleteFile(attachment.getFilePath());
        }
        if (CollUtil.isNotEmpty(attachments)) {
            followUpAttachmentMapper.deleteBatchIds(attachments.stream().map(FollowUpAttachment::getAttachmentId).toList());
        }
        removeById(followUpId);
        syncCustomerFollowUpSummary(customerId);
    }

    @Override
    public List<FollowUpVO> queryByCustomer(Long customerId) {
        List<FollowUp> followUps = lambdaQuery()
            .eq(FollowUp::getCustomerId, customerId)
            .orderByDesc(FollowUp::getFollowTime)
            .list();
        List<FollowUpVO> result = BeanUtil.copyToList(followUps, FollowUpVO.class);
        enrichFollowUps(result);
        return result;
    }

    @Override
    public BasePage<FollowUpVO> queryPageList(FollowUpQueryBO queryBO) {
        BasePage<FollowUpVO> page = queryBO.parse();
        baseMapper.queryPageList(page, queryBO);
        enrichFollowUps(page.getList());
        return page;
    }

    @Override
    public FollowUpAttachmentVO getAttachment(Long attachmentId) {
        FollowUpAttachment attachment = followUpAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "附件不存在");
        }
        return toAttachmentVO(attachment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAttachment(Long attachmentId) {
        FollowUpAttachment attachment = followUpAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            return;
        }
        safeDeleteFile(attachment.getFilePath());
        followUpAttachmentMapper.deleteById(attachmentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowUpAttachmentVO analyzeAttachment(Long attachmentId) {
        FollowUpAttachment attachment = followUpAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "附件不存在");
        }

        attachment.setAnalysisStatus("processing");
        followUpAttachmentMapper.updateById(attachment);

        try {
            String analysis = buildAttachmentAnalysis(attachment);
            attachment.setAnalysisStatus("completed");
            attachment.setAnalysisContent(analysis);
            attachment.setAnalysisTime(new Date());
            followUpAttachmentMapper.updateById(attachment);
            return toAttachmentVO(attachment);
        } catch (BusinessException ex) {
            attachment.setAnalysisStatus("failed");
            attachment.setAnalysisContent(ex.getMessage());
            attachment.setAnalysisTime(new Date());
            followUpAttachmentMapper.updateById(attachment);
            return toAttachmentVO(attachment);
        } catch (Exception ex) {
            log.error("Attachment AI analysis failed, attachmentId={}", attachmentId, ex);
            attachment.setAnalysisStatus("failed");
            attachment.setAnalysisContent("AI analysis failed, please try again later.");
            attachment.setAnalysisTime(new Date());
            followUpAttachmentMapper.updateById(attachment);
            return toAttachmentVO(attachment);
        }
    }

    @Override
    public FollowUpAiParseVO aiParseFollowUp(FollowUpAiParseBO parseBO) {
        String customerName = StrUtil.blankToDefault(parseBO.getCustomerName(), "未知客户");
        String now = LocalDateTime.now().format(AI_TIME_FORMATTER);
        String prompt = String.format(AI_PARSE_PROMPT_TEMPLATE, customerName, now, parseBO.getContent());

        try {
            String response = chatClientProvider.getChatClient()
                .prompt()
                .user(prompt)
                .call()
                .content();

            log.info("AI 跟进解析原始响应: {}", response);
            return parseAiResponse(response, parseBO.getContent(), now);
        } catch (Exception e) {
            log.error("AI 跟进解析失败，返回默认值", e);
            return buildFallbackResult(parseBO.getContent(), now);
        }
    }

    private Relation validateRelation(Long relationId) {
        if (relationId == null) {
            return null;
        }
        Relation relation = relationMapper.selectById(relationId);
        if (relation == null || Objects.equals(relation.getStatus(), 0)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "关系人不存在");
        }
        Long currentUserId = UserUtil.getUserIdOrNull();
        if (currentUserId != null && relation.getCreateUserId() != null
            && !Objects.equals(currentUserId, relation.getCreateUserId())) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "关系人不存在");
        }
        return relation;
    }

    private void saveAttachments(Long followUpId, List<ChatSendBO.AttachmentDTO> attachments) {
        if (followUpId == null || CollUtil.isEmpty(attachments)) {
            return;
        }
        int sort = 0;
        for (ChatSendBO.AttachmentDTO dto : attachments) {
            if (dto == null || StrUtil.isBlank(dto.getFilePath())) {
                continue;
            }
            FollowUpAttachment attachment = new FollowUpAttachment();
            attachment.setFollowUpId(followUpId);
            attachment.setFileName(StrUtil.blankToDefault(dto.getFileName(), "attachment"));
            attachment.setFilePath(dto.getFilePath());
            attachment.setFileSize(dto.getFileSize());
            attachment.setMimeType(dto.getMimeType());
            attachment.setSort(sort++);
            attachment.setAnalysisStatus("idle");
            followUpAttachmentMapper.insert(attachment);
        }
    }

    private void createSuggestedTasks(FollowUp followUp, List<FollowUpSuggestedTaskBO> suggestedTasks) {
        if (followUp == null || (followUp.getCustomerId() == null && followUp.getRelationId() == null) || CollUtil.isEmpty(suggestedTasks)) {
            return;
        }

        for (FollowUpSuggestedTaskBO suggestedTask : suggestedTasks) {
            if (suggestedTask == null || StrUtil.isBlank(suggestedTask.getTitle())) {
                continue;
            }

            TaskAddBO taskAddBO = new TaskAddBO();
            taskAddBO.setTitle(suggestedTask.getTitle().trim());
            taskAddBO.setDescription(StrUtil.blankToDefault(StrUtil.trim(suggestedTask.getDescription()), followUp.getSummary()));
            taskAddBO.setDueDate(suggestedTask.getDueDate() != null ? suggestedTask.getDueDate() : followUp.getNextFollowTime());
            taskAddBO.setPriority("medium");
            taskAddBO.setTaskType(StrUtil.blankToDefault(suggestedTask.getTaskType(), "跟进"));
            taskAddBO.setCustomerId(followUp.getCustomerId());
            taskAddBO.setRelationId(followUp.getRelationId());
            taskAddBO.setGeneratedByAi(1);
            taskAddBO.setAiContext("Generated from follow-up " + followUp.getFollowUpId());
            taskAddBO.setSourceFollowUpId(followUp.getFollowUpId());
            taskService.addTask(taskAddBO);
        }
    }

    private void enrichFollowUps(List<FollowUpVO> followUps) {
        if (CollUtil.isEmpty(followUps)) {
            return;
        }
        List<Long> followUpIds = followUps.stream()
            .map(FollowUpVO::getFollowUpId)
            .filter(ObjectUtil::isNotNull)
            .toList();
        if (followUpIds.isEmpty()) {
            return;
        }
        Map<Long, List<FollowUpAttachmentVO>> attachmentMap = followUpAttachmentMapper.selectList(
                Wrappers.<FollowUpAttachment>lambdaQuery()
                    .in(FollowUpAttachment::getFollowUpId, followUpIds)
                    .orderByAsc(FollowUpAttachment::getSort)
                    .orderByAsc(FollowUpAttachment::getAttachmentId)
            ).stream()
            .map(this::toAttachmentVO)
            .collect(Collectors.groupingBy(
                FollowUpAttachmentVO::getFollowUpId,
                LinkedHashMap::new,
                Collectors.toList()
            ));
        Map<Long, List<FollowUpLinkedTaskVO>> taskMap = taskService.lambdaQuery()
            .in(Task::getSourceFollowUpId, followUpIds)
            .orderByAsc(Task::getDueDate)
            .orderByDesc(Task::getCreateTime)
            .list()
            .stream()
            .map(this::toLinkedTaskVO)
            .collect(Collectors.groupingBy(
                FollowUpLinkedTaskVO::getFollowUpId,
                LinkedHashMap::new,
                Collectors.toList()
            ));
        for (FollowUpVO followUp : followUps) {
            followUp.setSummary(normalizeStoredSummary(followUp.getSummary()));
            followUp.setAttachments(attachmentMap.getOrDefault(followUp.getFollowUpId(), List.of()));
            followUp.setTasks(taskMap.getOrDefault(followUp.getFollowUpId(), List.of()));
        }
    }

    private FollowUpAttachmentVO toAttachmentVO(FollowUpAttachment attachment) {
        return BeanUtil.copyProperties(attachment, FollowUpAttachmentVO.class);
    }

    private FollowUpLinkedTaskVO toLinkedTaskVO(Task task) {
        FollowUpLinkedTaskVO vo = new FollowUpLinkedTaskVO();
        vo.setFollowUpId(task.getSourceFollowUpId());
        vo.setTaskId(task.getTaskId());
        vo.setTitle(task.getTitle());
        vo.setDescription(task.getDescription());
        vo.setDueDate(task.getDueDate());
        vo.setStatus(task.getStatus());
        vo.setTaskType(task.getTaskType());
        vo.setGeneratedByAi(task.getGeneratedByAi());
        return vo;
    }

    private String buildAttachmentAnalysis(FollowUpAttachment attachment) {
        String mimeType = StrUtil.blankToDefault(attachment.getMimeType(), "");
        String fileName = StrUtil.blankToDefault(attachment.getFileName(), "");
        if (mimeType.startsWith("image/")) {
            return analyzeImageAttachment(attachment);
        }
        if (isAudioFile(mimeType, fileName)) {
            return analyzeAudioAttachment(attachment);
        }

        String content;
        if (isTextFile(mimeType, fileName)) {
            content = extractFileText(attachment.getFilePath());
        } else if (isDocumentFile(mimeType, fileName)) {
            content = extractDocumentText(attachment.getFilePath(), mimeType, fileName);
        } else {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID,
                "当前附件类型不支持 AI 分析");
        }

        if (StrUtil.isBlank(content)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID,
                "无法从该附件中提取可读内容");
        }
        return analyzeTextLikeAttachment(fileName, content);
    }

    private String analyzeImageAttachment(FollowUpAttachment attachment) {
        AiModelCapabilities capabilities = chatClientProvider.getCurrentCapabilities();
        if (capabilities == null || !capabilities.isSupportsVision()) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID,
                "当前模型不支持图片分析");
        }

        String prompt = """
            You are a CRM assistant.
            Analyze this follow-up attachment image and reply in concise Simplified Chinese.
            Keep the answer to 2-3 sentences. Mention the most important visible facts first, then at most one practical implication.
            Do not use markdown or bullet points.
            """;

        try {
            Media media = AiMediaUtil.buildMedia(
                fileStorageService,
                attachment.getFilePath(),
                MimeType.valueOf(StrUtil.blankToDefault(attachment.getMimeType(), "image/jpeg"))
            );
            String response = chatClientProvider.getChatClient()
                .prompt()
                .user(user -> user.text(prompt).media(media))
                .call()
                .content();
            return StrUtil.blankToDefault(response, "未能生成有效的图片分析。").trim();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "图片分析失败，请稍后重试");
        }
    }

    private String analyzeAudioAttachment(FollowUpAttachment attachment) {
        try (InputStream inputStream = fileStorageService.getFileStream(attachment.getFilePath())) {
            if (inputStream == null) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "附件音频加载失败");
            }
            byte[] bytes = inputStream.readAllBytes();
            String transcript = aiAudioTranscriptionService.transcribe(
                bytes,
                attachment.getFileName(),
                attachment.getMimeType()
            );
            if (StrUtil.isBlank(transcript)) {
                throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID,
                    "No valid transcript could be produced from this audio");
            }
            return analyzeTextLikeAttachment(
                attachment.getFileName(),
                "Audio transcript:\n" + limitText(transcript, MAX_ATTACHMENT_ANALYSIS_LENGTH)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "音频分析失败，请稍后重试");
        }
    }

    private String analyzeTextLikeAttachment(String fileName, String content) {
        String prompt = """
            You are a CRM assistant.
            Please analyze the attachment content below and reply in concise Simplified Chinese.
            Requirements:
            1. Keep the answer within 2-3 sentences.
            2. Summarize the most important concrete facts from the attachment.
            3. If helpful, add one practical follow-up implication in the last sentence.
            4. Do not use markdown, bullet points, or quotation marks.

            Attachment: %s
            Content:
            %s
            """.formatted(fileName, limitText(content, MAX_ATTACHMENT_ANALYSIS_LENGTH));

        try {
            String response = chatClientProvider.getChatClient()
                .prompt()
                .user(prompt)
                .call()
                .content();
            return StrUtil.blankToDefault(response, "附件内容已读取，但未生成有效分析。")
                .trim();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_ERROR, "附件分析失败，请稍后重试");
        }
    }

    private boolean isTextFile(String mimeType, String fileName) {
        if (mimeType != null && (mimeType.startsWith("text/") || "application/json".equals(mimeType))) {
            return true;
        }
        String lowerFileName = StrUtil.blankToDefault(fileName, "").toLowerCase(Locale.ROOT);
        return lowerFileName.endsWith(".txt")
            || lowerFileName.endsWith(".md")
            || lowerFileName.endsWith(".csv")
            || lowerFileName.endsWith(".json")
            || lowerFileName.endsWith(".xml")
            || lowerFileName.endsWith(".yaml")
            || lowerFileName.endsWith(".yml")
            || lowerFileName.endsWith(".log");
    }

    private boolean isDocumentFile(String mimeType, String fileName) {
        String lowerMimeType = StrUtil.blankToDefault(mimeType, "").toLowerCase(Locale.ROOT);
        if (lowerMimeType.equals("application/pdf")
            || lowerMimeType.equals("application/msword")
            || lowerMimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            || lowerMimeType.equals("application/vnd.ms-excel")
            || lowerMimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            || lowerMimeType.equals("application/vnd.ms-powerpoint")
            || lowerMimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return true;
        }
        String lowerFileName = StrUtil.blankToDefault(fileName, "").toLowerCase(Locale.ROOT);
        return lowerFileName.endsWith(".pdf")
            || lowerFileName.endsWith(".doc")
            || lowerFileName.endsWith(".docx")
            || lowerFileName.endsWith(".xls")
            || lowerFileName.endsWith(".xlsx")
            || lowerFileName.endsWith(".ppt")
            || lowerFileName.endsWith(".pptx");
    }

    private boolean isAudioFile(String mimeType, String fileName) {
        String lowerMimeType = StrUtil.blankToDefault(mimeType, "").toLowerCase(Locale.ROOT);
        if (lowerMimeType.startsWith("audio/")) {
            return true;
        }
        String lowerFileName = StrUtil.blankToDefault(fileName, "").toLowerCase(Locale.ROOT);
        return lowerFileName.endsWith(".mp3")
            || lowerFileName.endsWith(".wav")
            || lowerFileName.endsWith(".m4a")
            || lowerFileName.endsWith(".aac")
            || lowerFileName.endsWith(".webm")
            || lowerFileName.endsWith(".ogg");
    }

    private String extractFileText(String filePath) {
        try (InputStream inputStream = fileStorageService.getFileStream(filePath)) {
            if (inputStream == null) {
                return null;
            }
            byte[] bytes = inputStream.readNBytes(MAX_EXTRACTED_TEXT_LENGTH * 3);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.length() > MAX_EXTRACTED_TEXT_LENGTH) {
                text = text.substring(0, MAX_EXTRACTED_TEXT_LENGTH) + "\n...(truncated)";
            }
            return text;
        } catch (Exception e) {
            log.warn("Failed to read text attachment: {}", filePath, e);
            return null;
        }
    }

    private String extractDocumentText(String filePath, String mimeType, String fileName) {
        try (InputStream inputStream = fileStorageService.getFileStream(filePath)) {
            if (inputStream == null) {
                return null;
            }
            String text = DocumentTextExtractor.parseToString(inputStream, mimeType, fileName);
            if (StrUtil.isBlank(text)) {
                return null;
            }
            if (text.length() > MAX_EXTRACTED_TEXT_LENGTH) {
                text = text.substring(0, MAX_EXTRACTED_TEXT_LENGTH) + "\n...(truncated)";
            }
            return text.trim();
        } catch (Exception e) {
            log.warn("Failed to extract document text: {}", filePath, e);
            return null;
        }
    }

    private String limitText(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "\n...(truncated)";
    }

    private void safeDeleteFile(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return;
        }
        try {
            fileStorageService.delete(filePath);
        } catch (Exception e) {
            log.warn("Failed to delete follow-up attachment file: {}", filePath, e);
        }
    }

    private void syncCustomerFollowUpSummary(Long customerId) {
        if (customerId == null) {
            return;
        }

        FollowUp latestFollowUp = lambdaQuery()
            .eq(FollowUp::getCustomerId, customerId)
            .orderByDesc(FollowUp::getFollowTime)
            .orderByDesc(FollowUp::getCreateTime)
            .orderByDesc(FollowUp::getFollowUpId)
            .last("LIMIT 1")
            .one();

        LambdaUpdateWrapper<Customer> updateWrapper = new LambdaUpdateWrapper<Customer>()
            .eq(Customer::getCustomerId, customerId)
            .set(Customer::getLastContactTime, latestFollowUp != null ? latestFollowUp.getFollowTime() : null)
            .set(Customer::getNextFollowTime, latestFollowUp != null ? latestFollowUp.getNextFollowTime() : null);
        customerMapper.update(null, updateWrapper);
        customerService.refreshCustomerActivity(customerId);
    }

    private String normalizeStoredSummary(String summary) {
        if (StrUtil.isBlank(summary)) {
            return null;
        }
        return compactSummary(summary);
    }

    private String normalizeGeneratedSummary(String summary, String originalContent) {
        String compacted = compactSummary(summary);
        if (StrUtil.isNotBlank(compacted)) {
            return compacted;
        }
        return compactSummary(originalContent);
    }

    private String compactSummary(String text) {
        String normalized = normalizeSingleLineText(text);
        if (StrUtil.isBlank(normalized)) {
            return "";
        }

        String sentence = firstSegment(normalized, "[。！？!?；;\\r\\n]+");
        String clause = firstSegment(sentence, "[，,:：]+");
        String candidate = StrUtil.isNotBlank(clause) ? clause : sentence;
        candidate = candidate.replaceFirst("^[\\s\\-•·*#\\d.、）)]+", "");
        candidate = candidate.replaceFirst("[\\s,，.。!！?？;；:：]+$", "");
        candidate = normalizeSingleLineText(candidate);
        if (StrUtil.isBlank(candidate)) {
            candidate = normalized;
        }

        if (candidate.length() <= 22) {
            return candidate;
        }
        return candidate.substring(0, 22) + "...";
    }

    private String normalizeSingleLineText(String text) {
        return StrUtil.blankToDefault(text, "")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String firstSegment(String text, String regex) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String[] parts = text.split(regex, 2);
        return parts.length == 0 ? "" : StrUtil.trim(parts[0]);
    }

    private FollowUpAiParseVO parseAiResponse(String response, String originalContent, String now) {
        try {
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```(?:json)?\\s*", "");
                json = json.replaceFirst("\\s*```$", "");
            }

            JsonNode root = objectMapper.readTree(json);
            FollowUpAiParseVO vo = new FollowUpAiParseVO();
            vo.setSummary(normalizeGeneratedSummary(getTextOrDefault(root, "summary", ""), originalContent));
            vo.setSceneType(getTextOrDefault(root, "sceneType", ""));
            vo.setType(getTextOrDefault(root, "type", "other"));
            vo.setFollowTime(normalizeRequiredDateTime(getTextOrDefault(root, "followTime", now), now));
            vo.setNextFollowTime(normalizeOptionalDateTime(getTextOrDefault(root, "nextFollowTime", ""), now));

            List<String> keyPoints = new ArrayList<>();
            if (root.has("keyPoints") && root.get("keyPoints").isArray()) {
                root.get("keyPoints").forEach(node -> keyPoints.add(node.asText()));
            }
            vo.setKeyPoints(keyPoints);

            List<String> todos = new ArrayList<>();
            if (root.has("todos") && root.get("todos").isArray()) {
                root.get("todos").forEach(node -> todos.add(node.asText()));
            }
            vo.setTodos(todos);

            return normalizeParsedTimes(vo, originalContent, now);
        } catch (Exception e) {
            log.warn("AI 响应 JSON 解析失败: {}", e.getMessage());
            return buildFallbackResult(originalContent, now);
        }
    }

    private String getTextOrDefault(JsonNode root, String field, String defaultValue) {
        if (root.has(field) && !root.get(field).isNull()) {
            String value = root.get(field).asText();
            return StrUtil.isNotBlank(value) ? value.trim() : defaultValue;
        }
        return defaultValue;
    }

    private FollowUpAiParseVO buildFallbackResult(String content, String now) {
        FollowUpAiParseVO vo = new FollowUpAiParseVO();
        vo.setSummary(normalizeGeneratedSummary("", content));
        vo.setSceneType("");
        vo.setType("other");
        vo.setFollowTime(now);
        vo.setNextFollowTime("");
        vo.setKeyPoints(List.of());
        vo.setTodos(List.of());
        return vo;
    }

    private FollowUpAiParseVO normalizeParsedTimes(FollowUpAiParseVO vo, String originalContent, String now) {
        LocalDateTime nowTime = parseDateTime(now, LocalTime.now());
        if (nowTime == null) {
            return vo;
        }

        LocalDateTime followTime = parseDateTime(vo.getFollowTime(), nowTime.toLocalTime());
        LocalDateTime nextFollowTime = parseDateTime(vo.getNextFollowTime(), nowTime.toLocalTime());
        LocalDateTime inferredNextFollowTime = inferNextFollowTimeFromContent(originalContent, nowTime);

        if (followTime != null && followTime.isAfter(nowTime.plusMinutes(5))) {
            LocalDateTime normalizedNextFollowTime = chooseFutureNextFollowTime(
                inferredNextFollowTime,
                nextFollowTime,
                followTime,
                nowTime
            );
            if (normalizedNextFollowTime != null) {
                vo.setNextFollowTime(AI_TIME_FORMATTER.format(normalizedNextFollowTime));
                nextFollowTime = normalizedNextFollowTime;
            }
            vo.setFollowTime(now);
            followTime = nowTime;
        }

        if (inferredNextFollowTime != null
            && inferredNextFollowTime.isAfter(nowTime.plusMinutes(5))
            && (nextFollowTime == null || !nextFollowTime.isAfter(nowTime.plusMinutes(5)))) {
            vo.setNextFollowTime(AI_TIME_FORMATTER.format(inferredNextFollowTime));
        }

        return vo;
    }

    private LocalDateTime chooseFutureNextFollowTime(LocalDateTime inferredNextFollowTime,
                                                     LocalDateTime parsedNextFollowTime,
                                                     LocalDateTime parsedFollowTime,
                                                     LocalDateTime nowTime) {
        LocalDateTime[] candidates = {inferredNextFollowTime, parsedNextFollowTime, parsedFollowTime};
        for (LocalDateTime candidate : candidates) {
            if (candidate != null && candidate.isAfter(nowTime.plusMinutes(5))) {
                return candidate;
            }
        }
        return null;
    }

    private LocalDateTime inferNextFollowTimeFromContent(String content, LocalDateTime nowTime) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        LocalDateTime chinese = parseChineseRelativeTime(content, nowTime);
        if (chinese != null) {
            return chinese;
        }
        return parseEnglishRelativeTime(content, nowTime);
    }

    private LocalDateTime parseChineseRelativeTime(String content, LocalDateTime nowTime) {
        Matcher matcher = CHINESE_RELATIVE_TIME_PATTERN.matcher(content);
        while (matcher.find()) {
            String dayToken = matcher.group(1);
            String periodToken = StrUtil.blankToDefault(matcher.group(2), deriveChinesePeriodToken(dayToken));
            Integer hour = parseHourToken(matcher.group(3));
            Integer minute = parseMinuteToken(matcher.group(4), matcher.group(5));
            Integer normalizedHour = normalizeHour(hour, periodToken);
            if (normalizedHour == null || minute == null) {
                continue;
            }

            LocalDateTime candidate = LocalDateTime.of(
                nowTime.toLocalDate().plusDays(resolveChineseDayOffset(dayToken)),
                LocalTime.of(normalizedHour, minute)
            );
            if (candidate.isAfter(nowTime)) {
                return candidate;
            }
        }
        return null;
    }

    private LocalDateTime parseEnglishRelativeTime(String content, LocalDateTime nowTime) {
        Matcher matcher = ENGLISH_RELATIVE_TIME_PATTERN.matcher(content);
        while (matcher.find()) {
            String dayToken = matcher.group(1);
            String periodToken = matcher.group(5) != null ? matcher.group(5) : matcher.group(2);
            Integer hour = parseHourToken(matcher.group(3));
            Integer minute = parseMinuteToken(matcher.group(4), null);
            Integer normalizedHour = normalizeHour(hour, periodToken);
            if (normalizedHour == null || minute == null) {
                continue;
            }

            LocalDateTime candidate = LocalDateTime.of(
                nowTime.toLocalDate().plusDays(resolveEnglishDayOffset(dayToken)),
                LocalTime.of(normalizedHour, minute)
            );
            if (candidate.isAfter(nowTime)) {
                return candidate;
            }
        }
        return null;
    }

    private long resolveChineseDayOffset(String dayToken) {
        return switch (dayToken) {
            case "后天" -> 2L;
            case "明天", "明早", "明晚" -> 1L;
            default -> 0L;
        };
    }

    private long resolveEnglishDayOffset(String dayToken) {
        if ("day after tomorrow".equalsIgnoreCase(dayToken)) {
            return 2L;
        }
        if ("tomorrow".equalsIgnoreCase(dayToken)) {
            return 1L;
        }
        return 0L;
    }

    private String deriveChinesePeriodToken(String dayToken) {
        if ("明早".equals(dayToken)) {
            return "早上";
        }
        if ("明晚".equals(dayToken)) {
            return "晚上";
        }
        return "";
    }

    private Integer parseHourToken(String token) {
        try {
            return StrUtil.isBlank(token) ? null : Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseMinuteToken(String minuteToken, String halfToken) {
        if (StrUtil.isNotBlank(halfToken)) {
            return 30;
        }
        if (StrUtil.isBlank(minuteToken)) {
            return 0;
        }
        try {
            int minute = Integer.parseInt(minuteToken);
            return minute >= 0 && minute <= 59 ? minute : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer normalizeHour(Integer hour, String periodToken) {
        if (hour == null || hour < 0 || hour > 23) {
            return null;
        }
        String period = StrUtil.blankToDefault(periodToken, "").toLowerCase(Locale.ROOT);
        if (period.contains("pm") || period.contains("下午") || period.contains("晚上")
            || period.contains("傍晚") || period.contains("今晚") || period.contains("evening")) {
            return hour < 12 ? hour + 12 : hour;
        }
        if (period.contains("am") || period.contains("凌晨") || period.contains("早上")
            || period.contains("早晨") || period.contains("上午") || period.contains("morning")) {
            return hour == 12 ? 0 : hour;
        }
        if (period.contains("中午")) {
            return hour < 11 ? hour + 12 : hour;
        }
        return hour;
    }

    private String normalizeRequiredDateTime(String value, String fallback) {
        LocalDateTime fallbackTime = parseDateTime(fallback, LocalTime.now());
        LocalDateTime parsed = parseDateTime(StrUtil.blankToDefault(value, fallback), fallbackTime != null ? fallbackTime.toLocalTime() : LocalTime.now());
        if (parsed == null) {
            return fallback;
        }
        return AI_TIME_FORMATTER.format(parsed);
    }

    private String normalizeOptionalDateTime(String value, String fallback) {
        if (StrUtil.isBlank(value)) {
            return "";
        }

        LocalDateTime fallbackTime = parseDateTime(fallback, LocalTime.now());
        LocalDateTime parsed = parseDateTime(value, fallbackTime != null ? fallbackTime.toLocalTime() : LocalTime.now());
        if (parsed == null) {
            return "";
        }
        return AI_TIME_FORMATTER.format(parsed);
    }

    private LocalDateTime parseDateTime(String value, LocalTime defaultTime) {
        String trimmed = StrUtil.trim(value);
        if (StrUtil.isBlank(trimmed)) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }

        try {
            return LocalDate.parse(trimmed, DATE_ONLY_FORMATTER).atTime(defaultTime);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
