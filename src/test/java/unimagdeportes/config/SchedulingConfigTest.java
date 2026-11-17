package unimagdeportes.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import unimagdeportes.scheduler.StorageCleanupJob;

class SchedulingConfigTest {
    @Test
    void blockedStorageWorkDoesNotDelayBusinessJobsAndSchedulersClose() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var businessExecuted = new CountDownLatch(1);
        try (var context = new AnnotationConfigApplicationContext(SchedulingConfig.class)) {
            var storage = context.getBean("storageTaskScheduler", ThreadPoolTaskScheduler.class);
            var business = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
            storage.schedule(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }, Instant.EPOCH);
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                business.schedule(businessExecuted::countDown, Instant.EPOCH);
                assertThat(businessExecuted.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
            assertThat(StorageCleanupJob.class.getMethod("clean").getAnnotation(Scheduled.class).scheduler())
                    .isEqualTo("storageTaskScheduler");
        }
    }
}
