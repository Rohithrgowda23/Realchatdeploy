package com.realchat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory presence (single backend instance). A user is online while at least one WebSocket session is open. */
@Component
@RequiredArgsConstructor
public class PresenceService {
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Integer> counts = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate ws;
    private final AppMetrics metrics;

    public boolean isOnline(Long userId) { return counts.containsKey(userId); }
    public List<Long> onlineIds() { return List.copyOf(counts.keySet()); }

    @EventListener
    public void onConnected(SessionConnectedEvent e) {
        Principal p = e.getUser();
        if (p == null) return;
        Long id = Long.valueOf(p.getName());
        String sid = StompHeaderAccessor.wrap(e.getMessage()).getSessionId();
        if (sid == null) return;
        boolean fresh = sessions.put(sid, id) == null;
        if (fresh) metrics.wsOpened();
        if (fresh && counts.merge(id, 1, Integer::sum) == 1) publish(id, true);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        Long id = sessions.remove(e.getSessionId());
        if (id == null) return;
        metrics.wsClosed();
        if (counts.merge(id, -1, Integer::sum) <= 0) {
            counts.remove(id);
            publish(id, false);
        }
    }

    private void publish(Long id, boolean online) {
        ws.convertAndSend("/topic/presence", Map.of("userId", id, "online", online));
    }
}