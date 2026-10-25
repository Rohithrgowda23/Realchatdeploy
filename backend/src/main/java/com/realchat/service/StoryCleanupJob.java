package com.realchat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 10 minutes: expired stories are deleted together with their media files. They are already invisible the moment they expire. */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class StoryCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(StoryCleanupJob.class);
    private final StoryService stories;

    public StoryCleanupJob(StoryService stories) { this.stories = stories; }

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void purge() {
        try { int n = stories.cleanupExpired(); if (n > 0) log.info("Removed {} expired stories", n); }
        catch (RuntimeException e) { log.warn("Story cleanup failed: {}", e.getClass().getSimpleName()); }
    }
}