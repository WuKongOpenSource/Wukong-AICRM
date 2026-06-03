package com.kakarote.ai_crm.service.impl;

import com.kakarote.ai_crm.ai.context.AiContextHolder;
import com.kakarote.ai_crm.common.exception.BusinessException;
import com.kakarote.ai_crm.entity.PO.Contact;
import com.kakarote.ai_crm.entity.PO.Relation;
import com.kakarote.ai_crm.mapper.ContactMapper;
import com.kakarote.ai_crm.mapper.RelationMapper;
import com.kakarote.ai_crm.service.ICustomFieldService;
import com.kakarote.ai_crm.service.IGlobalSearchIndexService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationServiceImplTest {

    @Mock
    private RelationMapper relationMapper;

    @Mock
    private ContactMapper contactMapper;

    @Mock
    private ICustomFieldService customFieldService;

    @Mock
    private IGlobalSearchIndexService globalSearchIndexService;

    @InjectMocks
    private RelationServiceImpl relationService;

    @BeforeEach
    void setUp() {
        AiContextHolder.bindThreadContext(100L, 1L);
        ReflectionTestUtils.setField(relationService, "baseMapper", relationMapper);
    }

    @AfterEach
    void tearDown() {
        AiContextHolder.clearThreadContext();
    }

    @Test
    void addFromContactReturnsExistingRelationForCurrentUser() {
        Relation existing = new Relation();
        existing.setRelationId(9001L);
        existing.setCreateUserId(100L);
        existing.setSourceContactId(3001L);
        existing.setStatus(1);
        when(relationMapper.selectOne(any())).thenReturn(existing);

        Long relationId = relationService.addFromContact(3001L);

        assertThat(relationId).isEqualTo(9001L);
        verify(contactMapper, never()).selectById(any());
        verify(relationMapper, never()).insert(any(Relation.class));
    }

    @Test
    void getOwnedRelationRejectsRelationOwnedByAnotherUser() {
        Relation relation = new Relation();
        relation.setRelationId(9002L);
        relation.setCreateUserId(200L);
        relation.setStatus(1);
        when(relationMapper.selectById(9002L)).thenReturn(relation);

        assertThatThrownBy(() -> relationService.getOwnedRelation(9002L))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void addFromContactCopiesContactFieldsWhenNoExistingRelation() {
        when(relationMapper.selectOne(any())).thenReturn(null);
        Contact contact = new Contact();
        contact.setContactId(3002L);
        contact.setCustomerId(7001L);
        contact.setName("张三");
        contact.setPhone("13800000000");
        contact.setEmail("zhangsan@example.com");
        contact.setWechat("zhangsan_wx");
        contact.setNotes("重点联系人");
        when(contactMapper.selectById(3002L)).thenReturn(contact);

        relationService.addFromContact(3002L);

        verify(relationMapper).insert(any(Relation.class));
    }
}
