package unimagdeportes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SchedulingConfig {
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        return scheduler("business-job-", 3);
    }

    @Bean
    ThreadPoolTaskScheduler storageTaskScheduler() {
        return scheduler("storage-cleanup-", 1);
    }

    private ThreadPoolTaskScheduler scheduler(String prefix, int poolSize) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
