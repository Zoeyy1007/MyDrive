package com.mydrive.drive.trash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduling adapter only. TrashService owns the actual cleanup rules.
 */
@Component
@ConditionalOnProperty(
        name = "app.trash.cleanup-enabled",
        havingValue = "true")
public class TrashCleanupJob {
    private static final Logger logger = LoggerFactory.getLogger(TrashCleanupJob.class);

    private final TrashService trashService;

    public TrashCleanupJob(TrashService trashService) {
        this.trashService = trashService;
    }

    @Scheduled(cron = "${app.trash.cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredTrash() {
        int deletedCount = trashService.deleteExpiredTrash();
        if (deletedCount > 0) {
            logger.info("Permanently deleted {} expired trashed files", deletedCount);
        }
    }
}
