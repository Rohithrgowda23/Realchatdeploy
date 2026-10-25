package com.realchat;

import com.realchat.domain.ConversationMember;
import com.realchat.repository.MemberRepository;
import com.realchat.repository.MessageRepository;
import com.realchat.service.DeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {
    @Mock MemberRepository members;
    @Mock MessageRepository messages;
    @Mock SimpMessagingTemplate ws;
    DeliveryService service;

    @BeforeEach void setUp() { service = new DeliveryService(members, messages, ws); }

    private static ConversationMember m(Long conv, long delivered) { ConversationMember x = new ConversationMember(); x.setConversationId(conv); x.setUserId(2L); x.setLastDeliveredMessageId(delivered); return x; }

    @Test void connectingMarksNewerMessagesDeliveredAndTellsTheSender() {
        when(members.findByUserId(2L)).thenReturn(List.of(m(1L, 5)));
        when(messages.maxId(1L)).thenReturn(9L);
        when(members.memberIds(1L)).thenReturn(List.of(1L, 2L));
        service.deliverPending(2L);
        verify(members).markDelivered(1L, List.of(2L), 9L);
        verify(ws).convertAndSendToUser(eq("1"), eq("/queue/events"), any());
        verify(ws, never()).convertAndSendToUser(eq("2"), any(), any()); // the recipient is not told about their own delivery
    }

    @Test void nothingNewMeansNoWritesAndNoEvents() {
        when(members.findByUserId(2L)).thenReturn(List.of(m(1L, 9), m(2L, 0)));
        when(messages.maxId(1L)).thenReturn(9L);
        when(messages.maxId(2L)).thenReturn(null); // empty conversation
        service.deliverPending(2L);
        verify(members, never()).markDelivered(any(), any(), anyLong());
        verifyNoInteractions(ws);
    }
}