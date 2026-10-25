package com.realchat.service;

import com.realchat.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Polls every 15 s. Works after restarts (state is in MySQL) and is safe with several instances (atomic claims). */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledMessageJob {
    private static final Logger log = LoggerFactory.getLogger(ScheduledMessageJob.class);
    private final ScheduledMessageService service;
    private final NotificationRepository notifications;

    public ScheduledMessageJob(ScheduledMessageService service, NotificationRepository notifications) { this.service = service; this.notifications = notifications; }

    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void deliverDue() {
        try {
            int released = service.releaseStuck();
            if (released > 0) log.warn("Released {} stuck scheduled message(s) for retry", released);
            for (Long id : service.claimDue(50)) {
                try { service.deliver(id); }
                catch (RuntimeException e) {
                    log.warn("Scheduled message {} not delivered: {}", id, e.getClass().getSimpleName());
                    try { service.recordFailure(id, e); } catch (RuntimeException inner) { log.error("Could not record failure for scheduled message {}", id); }
                }
            }
        } catch (RuntimeException e) { log.error("Scheduled-message run failed: {}", e.getClass().getSimpleName()); }
    }

    @Scheduled(cron = "0 45 3 * * *")
    public void purgeOldNotifications() { notifications.purgeOlderThan(Instant.now().minus(Duration.ofDays(90))); }
}