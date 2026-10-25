package com.realchat.service;

import com.realchat.repository.AttachmentRepository;
import com.realchat.repository.StoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

/** Nightly: removes files older than a day that no database row points to (failed uploads, interrupted deletes). */
@Configuration
@EnableScheduling
public class StorageCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(StorageCleanupJob.class);
    private final FileStorage storage;
    private final AttachmentRepository attachments;
    private final StoryRepository stories;

    public StorageCleanupJob(FileStorage storage, AttachmentRepository attachments, StoryRepository stories) { this.storage = storage; this.attachments = attachments; this.stories = stories; }

    @Scheduled(cron = "0 30 3 * * *")
    public void removeOrphans() {
        int removed = 0;
        for (String name : storage.listOlderThan(Duration.ofDays(1))) {
            if (!attachments.existsByStoredName(name) && !stories.existsByStoredName(name)) { // story media is referenced from the stories table storage.deleteQuietly(name); removed++; }
            }
            if (removed > 0) log.info("Storage cleanup removed {} orphaned file(s)", removed);
        }
    }
}