package com.speednet.module.subscription.job;

import com.speednet.framework.quartz.core.scheduler.SchedulerManager;
import com.speednet.module.infra.dal.dataobject.job.JobDO;
import com.speednet.module.infra.dal.mysql.job.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SubscriptionScanJobRegistrationTest {
    SubscriptionScanJobRegistration registration;
    JobMapper jobs;
    Scheduler scheduler;
    SchedulerManager manager;
    JobDO job;

    @BeforeEach
    void setup() {
        registration = new SubscriptionScanJobRegistration();
        jobs = mock(JobMapper.class);
        scheduler = mock(Scheduler.class);
        manager = mock(SchedulerManager.class);
        job = JobDO.builder().id(100L).name("订阅扫描").handlerName("subscriptionScanJob")
            .handlerParam("").cronExpression("0 */2 * * * ?").retryCount(0).retryInterval(0).status(1).build();
        ReflectionTestUtils.setField(registration, "jobs", jobs);
        ReflectionTestUtils.setField(registration, "manager", manager);
        ReflectionTestUtils.setField(registration, "scheduler", Optional.of(scheduler));
        when(jobs.selectByHandlerName("subscriptionScanJob")).thenReturn(job);
    }

    @Test
    void registersPersistedConfigurationWithoutReplacingCustomCron() throws Exception {
        registration.register();
        verify(manager).addJob(100L, "subscriptionScanJob", "", "0 */2 * * * ?", 0, 0);
        verify(manager, never()).pauseJob(anyString());
        verify(jobs, never()).updateById(any(JobDO.class));
    }

    @Test
    void existingJdbcQuartzScheduleIsLeftUntouchedOnRestart() throws Exception {
        when(scheduler.checkExists(JobKey.jobKey("subscriptionScanJob"))).thenReturn(true);
        registration.register();
        verifyNoInteractions(manager);
    }

    @Test
    void missingPausedTriggerIsRegisteredAndPausedAgain() throws Exception {
        job.setStatus(2);
        registration.register();
        var sequence = inOrder(manager);
        sequence.verify(manager).addJob(100L, "subscriptionScanJob", "", "0 */2 * * * ?", 0, 0);
        sequence.verify(manager).pauseJob("subscriptionScanJob");
    }

    @Test
    void explicitlyDeletedTaskIsNotRecreatedAtStartup() throws Exception {
        when(jobs.selectByHandlerName("subscriptionScanJob")).thenReturn(null);
        registration.register();
        verifyNoInteractions(manager, scheduler);
    }

    @Test
    void disabledQuartzDoesNotEnableASecondScheduler() throws Exception {
        ReflectionTestUtils.setField(registration, "scheduler", Optional.empty());
        registration.register();
        verifyNoInteractions(manager, scheduler);
    }

    @Test
    void duplicateClusterRegistrationDoesNotFailStartup() throws Exception {
        doThrow(new ObjectAlreadyExistsException("registered by another instance"))
            .when(manager).addJob(anyLong(), anyString(), anyString(), anyString(), anyInt(), anyInt());
        registration.register();
    }
}
