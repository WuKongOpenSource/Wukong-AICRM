package com.kakarote.ai_crm.service.impl;

import com.kakarote.ai_crm.ai.context.AiContextHolder;
import com.kakarote.ai_crm.entity.BO.SessionPinBO;
import com.kakarote.ai_crm.entity.PO.ChatSession;
import com.kakarote.ai_crm.mapper.ChatSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceImplTest {

    @AfterEach
    void tearDown() {
        AiContextHolder.clear();
    }

    @Test
    void updateSessionPinPinsCurrentUserSessionWithoutChangingUpdateTime() {
        ChatServiceImpl service = new ChatServiceImpl();
        ChatSessionMapper chatSessionMapper = mock(ChatSessionMapper.class);
        ReflectionTestUtils.setField(service, "chatSessionMapper", chatSessionMapper);
        AiContextHolder.setContext(1001L, 7L);

        ChatSession session = new ChatSession();
        session.setSessionId(1001L);
        session.setUserId(7L);
        session.setPinned(false);
        when(chatSessionMapper.selectById(1001L)).thenReturn(session);

        SessionPinBO bo = new SessionPinBO();
        bo.setPinned(true);
        service.updateSessionPin(1001L, bo);

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById(sessionCaptor.capture());
        ChatSession updated = sessionCaptor.getValue();
        assertThat(updated.getPinned()).isTrue();
        assertThat(updated.getPinnedTime()).isNotNull();
        assertThat(updated.getUpdateTime()).isNull();
    }
}
