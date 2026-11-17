package unimagdeportes.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import unimagdeportes.storage.StorageObjectCleanup;

@Component
public class StorageCleanupJob {
    private final StorageObjectCleanup cleanup;

    public StorageCleanupJob(StorageObjectCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "${app.jobs.storage-cleanup-cron:0 */5 * * * *}", scheduler = "storageTaskScheduler")
    public void clean() {
        cleanup.clean();
    }
}
