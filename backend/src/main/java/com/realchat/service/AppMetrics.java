package com.realchat.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/** Custom metrics, visible at /actuator/metrics/realchat.* (operator credentials required). Never records message content. */
@Component
public class AppMetrics {
    private final Counter messagesSent;
    private final AtomicInteger websocketSessions = new AtomicInteger();

    public AppMetrics(MeterRegistry registry) {
        messagesSent = Counter.builder("realchat.messages.sent").description("Messages persisted and broadcast").register(registry);
        registry.gauge("realchat.websocket.sessions", websocketSessions);
    }
    public void messageSent() { messagesSent.increment(); }
    public void wsOpened() { websocketSessions.incrementAndGet(); }
    public void wsClosed() { websocketSessions.updateAndGet(v -> Math.max(0, v - 1)); }
}