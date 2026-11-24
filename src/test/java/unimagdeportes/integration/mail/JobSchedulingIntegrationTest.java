package unimagdeportes.integration.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import unimagdeportes.support.AuthTestSupport;

class JobSchedulingIntegrationTest extends AuthTestSupport {
    @Autowired ScheduledTaskHolder scheduledTasks;

    @Test
    void integrationTestsRunJobsExplicitlyWithoutBackgroundDatabaseAccess() {
        assertThat(scheduledTasks.getScheduledTasks()).isEmpty();
    }
}
