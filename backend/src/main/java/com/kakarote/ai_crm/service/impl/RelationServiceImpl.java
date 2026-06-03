package com.kakarote.ai_crm.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.common.result.SystemCodeEnum;
import com.kakarote.ai_crm.entity.BO.FollowUpQueryBO;
import com.kakarote.ai_crm.entity.BO.KnowledgeQueryBO;
import com.kakarote.ai_crm.entity.BO.RelationAddBO;
import com.kakarote.ai_crm.entity.BO.RelationQueryBO;
import com.kakarote.ai_crm.entity.BO.RelationUpdateBO;
import com.kakarote.ai_crm.entity.BO.ScheduleQueryBO;
import com.kakarote.ai_crm.entity.BO.TaskQueryBO;
import com.kakarote.ai_crm.entity.PO.Contact;
import com.kakarote.ai_crm.entity.PO.Customer;
import com.kakarote.ai_crm.entity.PO.Relation;
import com.kakarote.ai_crm.entity.VO.RelationDetailVO;
import com.kakarote.ai_crm.entity.VO.RelationVO;
import com.kakarote.ai_crm.mapper.ContactMapper;
import com.kakarote.ai_crm.mapper.CustomerMapper;
import com.kakarote.ai_crm.mapper.FollowUpMapper;
import com.kakarote.ai_crm.mapper.KnowledgeMapper;
import com.kakarote.ai_crm.mapper.RelationMapper;
import com.kakarote.ai_crm.mapper.ScheduleMapper;
import com.kakarote.ai_crm.mapper.TaskMapper;
import com.kakarote.ai_crm.service.ICustomFieldService;
import com.kakarote.ai_crm.service.IGlobalSearchIndexService;
import com.kakarote.ai_crm.service.IRelationService;
import com.kakarote.ai_crm.utils.UserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 关系人服务实现。
 */
@Service
public class RelationServiceImpl extends ServiceImpl<RelationMapper, Relation> implements IRelationService {

    private static final String ENTITY_RELATION = "relation";
    private static final String SOURCE_MANUAL = "manual";
    private static final String SOURCE_CUSTOMER_CONTACT = "customer_contact";
    private static final String RELATION_TYPE_OTHER = "other";

    @Autowired
    private ContactMapper contactMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private ScheduleMapper scheduleMapper;

    @Autowired
    private FollowUpMapper followUpMapper;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private ICustomFieldService customFieldService;

    @Autowired
    private IGlobalSearchIndexService globalSearchIndexService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addRelation(RelationAddBO relationAddBO) {
        Relation relation = BeanUtil.copyProperties(relationAddBO, Relation.class);
        relation.setStatus(1);
        relation.setSource(SOURCE_MANUAL);
        if (StrUtil.isBlank(relation.getRelationType())) {
            relation.setRelationType(RELATION_TYPE_OTHER);
        }
        customFieldService.validateUniqueCustomFieldValues(ENTITY_RELATION, null,
                buildRelationUniqueFieldValues(relation, relationAddBO.getCustomFields()));
        save(relation);
        updateCustomFields(relation.getRelationId(), relationAddBO.getCustomFields());
        refreshRelationIndex(relation.getRelationId());
        return relation.getRelationId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRelation(RelationUpdateBO relationUpdateBO) {
        Relation relation = getOwnedRelation(relationUpdateBO.getRelationId());
        BeanUtil.copyProperties(relationUpdateBO, relation, "relationId", "source",
                "sourceCustomerId", "sourceContactId", "createUserId", "createTime", "customFields");
        customFieldService.validateUniqueCustomFieldValues(ENTITY_RELATION, relation.getRelationId(),
                buildRelationUniqueFieldValues(relation, relationUpdateBO.getCustomFields()));
        updateById(relation);
        updateCustomFields(relation.getRelationId(), relationUpdateBO.getCustomFields());
        refreshRelationIndex(relation.getRelationId());
    }

    @Override
    public void deleteRelation(Long relationId) {
        Relation relation = getOwnedRelation(relationId);
        relation.setStatus(0);
        updateById(relation);
        refreshRelationIndex(relation.getRelationId());
    }

    @Override
    public BasePage<RelationVO> queryPageList(RelationQueryBO queryBO) {
        BasePage<Relation> page = queryBO.parse();
        page(page, buildQueryWrapper(queryBO));
        BasePage<RelationVO> resultPage = page.copy(RelationVO.class, this::toRelationVO);
        fillCustomFields(resultPage.getRecords());
        return resultPage;
    }

    @Override
    public RelationDetailVO detail(Long relationId) {
        getOwnedRelation(relationId);
        RelationDetailVO detailVO = new RelationDetailVO();
        detailVO.setRelation(getOwnedRelationVO(relationId));
        detailVO.setTasks(queryRelationTasks(relationId));
        detailVO.setSchedules(queryRelationSchedules(relationId));
        detailVO.setAttachments(queryRelationAttachments(relationId));
        detailVO.setHistories(queryRelationHistories(relationId));
        return detailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addFromContact(Long contactId) {
        Long currentUserId = UserUtil.getUserId();
        Relation existing = lambdaQuery()
                .eq(Relation::getSourceContactId, contactId)
                .eq(Relation::getCreateUserId, currentUserId)
                .eq(Relation::getStatus, 1)
                .one();
        if (existing != null) {
            return existing.getRelationId();
        }

        Contact contact = contactMapper.selectById(contactId);
        if (ObjectUtil.isNull(contact)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "联系人不存在");
        }

        Relation relation = new Relation();
        relation.setName(contact.getName());
        relation.setPhone(contact.getPhone());
        relation.setEmail(contact.getEmail());
        relation.setWechat(contact.getWechat());
        relation.setRemark(contact.getNotes());
        relation.setRelationType(SOURCE_CUSTOMER_CONTACT);
        relation.setSource(SOURCE_CUSTOMER_CONTACT);
        relation.setSourceCustomerId(contact.getCustomerId());
        relation.setSourceContactId(contact.getContactId());
        if (customerMapper != null && contact.getCustomerId() != null) {
            Customer customer = customerMapper.selectById(contact.getCustomerId());
            if (customer != null) {
                relation.setCompany(customer.getCompanyName());
            }
        }
        relation.setStatus(1);
        save(relation);
        refreshRelationIndex(relation.getRelationId());
        return relation.getRelationId();
    }

    @Override
    public Relation getOwnedRelation(Long relationId) {
        Relation relation = getById(relationId);
        if (ObjectUtil.isNull(relation) || Objects.equals(relation.getStatus(), 0)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "关系人不存在");
        }
        Long currentUserId = UserUtil.getUserId();
        if (relation.getCreateUserId() != null && !Objects.equals(relation.getCreateUserId(), currentUserId)) {
            throw new BusinessException(SystemCodeEnum.SYSTEM_NO_VALID, "关系人不存在或无权限访问");
        }
        return relation;
    }

    @Override
    public RelationVO getOwnedRelationVO(Long relationId) {
        RelationVO relationVO = toRelationVO(getOwnedRelation(relationId));
        relationVO.setCustomFields(customFieldService.getCustomFieldValues(ENTITY_RELATION, relationId));
        return relationVO;
    }

    private LambdaQueryWrapper<Relation> buildQueryWrapper(RelationQueryBO queryBO) {
        LambdaQueryWrapper<Relation> wrapper = Wrappers.lambdaQuery(Relation.class)
                .eq(Relation::getStatus, 1)
                .eq(Relation::getCreateUserId, UserUtil.getUserId())
                .eq(StrUtil.isNotBlank(queryBO.getRelationType()), Relation::getRelationType, queryBO.getRelationType())
                .eq(StrUtil.isNotBlank(queryBO.getSource()), Relation::getSource, queryBO.getSource())
                .eq(queryBO.getSourceCustomerId() != null, Relation::getSourceCustomerId, queryBO.getSourceCustomerId())
                .eq(queryBO.getSourceContactId() != null, Relation::getSourceContactId, queryBO.getSourceContactId())
                .orderByDesc(Relation::getCreateTime);
        if (StrUtil.isNotBlank(queryBO.getKeyword())) {
            wrapper.and(w -> w.like(Relation::getName, queryBO.getKeyword())
                    .or()
                    .like(Relation::getPhone, queryBO.getKeyword())
                    .or()
                    .like(Relation::getWechat, queryBO.getKeyword())
                    .or()
                    .like(Relation::getEmail, queryBO.getKeyword())
                    .or()
                    .like(Relation::getCompany, queryBO.getKeyword()));
        }
        return wrapper;
    }

    private RelationVO toRelationVO(Relation relation) {
        RelationVO vo = BeanUtil.copyProperties(relation, RelationVO.class);
        vo.setRelationTypeName(resolveRelationTypeName(relation.getRelationType()));
        vo.setSourceName(resolveSourceName(relation.getSource()));
        return vo;
    }

    private void fillCustomFields(List<RelationVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> relationIds = records.stream()
                .map(RelationVO::getRelationId)
                .filter(Objects::nonNull)
                .toList();
        if (relationIds.isEmpty()) {
            return;
        }
        Map<Long, Map<String, Object>> customFieldMap = customFieldService.getBatchCustomFieldValues(ENTITY_RELATION, relationIds);
        for (RelationVO record : records) {
            record.setCustomFields(customFieldMap.getOrDefault(record.getRelationId(), Collections.emptyMap()));
        }
    }

    private List<com.kakarote.ai_crm.entity.VO.TaskVO> queryRelationTasks(Long relationId) {
        if (taskMapper == null) {
            return Collections.emptyList();
        }
        TaskQueryBO queryBO = new TaskQueryBO();
        queryBO.setRelationId(relationId);
        queryBO.setPage(1);
        queryBO.setLimit(20);
        return taskMapper.queryPageList(queryBO.parse(), queryBO).getRecords();
    }

    private List<com.kakarote.ai_crm.entity.VO.ScheduleVO> queryRelationSchedules(Long relationId) {
        if (scheduleMapper == null) {
            return Collections.emptyList();
        }
        ScheduleQueryBO queryBO = new ScheduleQueryBO();
        queryBO.setRelationId(relationId);
        queryBO.setPage(1);
        queryBO.setLimit(20);
        return scheduleMapper.queryPageList(queryBO.parse(), queryBO).getRecords();
    }

    private List<com.kakarote.ai_crm.entity.VO.KnowledgeVO> queryRelationAttachments(Long relationId) {
        if (knowledgeMapper == null) {
            return Collections.emptyList();
        }
        KnowledgeQueryBO queryBO = new KnowledgeQueryBO();
        queryBO.setRelationId(relationId);
        queryBO.setPage(1);
        queryBO.setLimit(20);
        return knowledgeMapper.queryPageList(queryBO.parse(), queryBO).getRecords();
    }

    private List<com.kakarote.ai_crm.entity.VO.FollowUpVO> queryRelationHistories(Long relationId) {
        if (followUpMapper == null) {
            return Collections.emptyList();
        }
        FollowUpQueryBO queryBO = new FollowUpQueryBO();
        queryBO.setRelationId(relationId);
        queryBO.setPage(1);
        queryBO.setLimit(20);
        return followUpMapper.queryPageList(queryBO.parse(), queryBO).getRecords();
    }

    private void updateCustomFields(Long relationId, Map<String, Object> customFields) {
        if (customFields != null && !customFields.isEmpty()) {
            customFieldService.updateCustomFieldValues(ENTITY_RELATION, relationId, customFields);
        }
    }

    private void refreshRelationIndex(Long relationId) {
        if (globalSearchIndexService != null) {
            globalSearchIndexService.refreshRelationIndex(relationId);
        }
    }

    private Map<String, Object> buildRelationUniqueFieldValues(Relation relation, Map<String, Object> customFields) {
        Map<String, Object> values = new HashMap<>();
        if (relation != null) {
            values.put("name", relation.getName());
            values.put("phone", relation.getPhone());
            values.put("wechat", relation.getWechat());
            values.put("email", relation.getEmail());
            values.put("relationType", relation.getRelationType());
            values.put("company", relation.getCompany());
        }
        if (customFields != null && !customFields.isEmpty()) {
            values.putAll(customFields);
        }
        return values;
    }

    private String resolveRelationTypeName(String relationType) {
        if (StrUtil.isBlank(relationType)) {
            return "其他";
        }
        return switch (relationType) {
            case "friend" -> "朋友";
            case "family" -> "家人";
            case "relative" -> "亲戚";
            case "partner" -> "合作伙伴";
            case SOURCE_CUSTOMER_CONTACT -> "客户联系人";
            case "supplier" -> "供应商";
            case "investor" -> "投资人";
            case RELATION_TYPE_OTHER -> "其他";
            default -> relationType;
        };
    }

    private String resolveSourceName(String source) {
        if (SOURCE_CUSTOMER_CONTACT.equals(source)) {
            return "客户联系人";
        }
        if (SOURCE_MANUAL.equals(source)) {
            return "手动录入";
        }
        return source;
    }
}
