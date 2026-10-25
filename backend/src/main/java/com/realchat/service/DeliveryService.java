package com.realchat.service;

import com.realchat.domain.ConversationMember;
import com.realchat.dto.Dto.Event;
import com.realchat.repository.MemberRepository;
import com.realchat.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * "Delivered" = the recipient's app was connected when a message was sent (see ChatService), or it connected afterwards
 * and caught up. This covers the second case: when a user connects, everything already in their conversations is
 * marked delivered and the senders are told.
 */
@Component
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final Executor EXEC = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "delivery"); t.setDaemon(true); return t; });

    private final MemberRepository members;
    private final MessageRepository messages;
    private final SimpMessagingTemplate ws;

    public DeliveryService(MemberRepository members, MessageRepository messages, SimpMessagingTemplate ws) { this.members = members; this.messages = messages; this.ws = ws; }

    @EventListener
    public void onConnected(SessionConnectedEvent e) {
        Principal p = e.getUser();
        if (p == null) return;
        Long userId = Long.valueOf(p.getName());
        CompletableFuture.runAsync(() -> deliverPending(userId), EXEC);
    }

    public void deliverPending(Long userId) {
        try {
            for (ConversationMember m : members.findByUserId(userId)) {
                Long max = messages.maxId(m.getConversationId());
                if (max == null || max <= m.getLastDeliveredMessageId()) continue;
                members.markDelivered(m.getConversationId(), List.of(userId), max);
                Event ev = new Event("DELIVERED", Map.of("conversationId", m.getConversationId(), "userIds", List.of(userId), "lastDeliveredMessageId", max));
                for (Long other : members.memberIds(m.getConversationId())) {
                    if (!other.equals(userId)) ws.convertAndSendToUser(String.valueOf(other), "/queue/events", ev);
                }
            }
        } catch (RuntimeException ex) { log.warn("Could not mark messages delivered: {}", ex.getClass().getSimpleName()); }
    }
}