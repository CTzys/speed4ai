package com.speednet.module.subscription.job;

import com.speednet.framework.quartz.core.handler.JobHandlerInvoker;
import com.speednet.framework.quartz.core.scheduler.SchedulerManager;
import com.speednet.framework.quartz.core.service.JobLogFrameworkService;
import com.speednet.module.infra.dal.dataobject.job.JobDO;
import com.speednet.module.infra.dal.mysql.job.JobMapper;
import com.speednet.module.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionScanQuartzTest {
    @Test
    void managedQuartzSupportsPauseResumeManualTriggerAndExecutionLogs() throws Exception {
        var properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "subscription-test-" + System.nanoTime());
        properties.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        Scheduler scheduler = new StdSchedulerFactory(properties).getScheduler();
        var context = new StaticApplicationContext();
        try {
            var subscriptions = mock(SubscriptionService.class);
            when(subscriptions.scan()).thenReturn(new SubscriptionService.ScanResult(4, 4, 0, 0));
            var handler = new SubscriptionScanJob();
            ReflectionTestUtils.setField(handler, "subscriptions", subscriptions);
            context.getBeanFactory().registerSingleton("subscriptionScanJob", handler);
            context.refresh();
            var logs = mock(JobLogFrameworkService.class);
            when(logs.createJobLog(anyLong(), any(), anyString(), anyString(), anyInt())).thenReturn(1L);
            var logged = new CountDownLatch(1);
            doAnswer(i -> { logged.countDown(); return null; }).when(logs)
                .updateJobLogResultAsync(anyLong(), any(), anyInt(), anyBoolean(), anyString());
            scheduler.setJobFactory((bundle, ignored) -> {
                var invoker = new JobHandlerInvoker();
                ReflectionTestUtils.setField(invoker, "applicationContext", context);
                ReflectionTestUtils.setField(invoker, "jobLogFrameworkService", logs);
                return invoker;
            });
            var manager = new SchedulerManager(scheduler);
            var jobs = mock(JobMapper.class);
            var row = JobDO.builder().id(100L).handlerName("subscriptionScanJob").handlerParam("")
                .cronExpression("0 0 0 * * ?").retryCount(0).retryInterval(0).status(1).build();
            when(jobs.selectByHandlerName("subscriptionScanJob")).thenReturn(row);
            var registration = new SubscriptionScanJobRegistration();
            ReflectionTestUtils.setField(registration, "jobs", jobs);
            ReflectionTestUtils.setField(registration, "manager", manager);
            ReflectionTestUtils.setField(registration, "scheduler", Optional.of(scheduler));
            registration.register();
            TriggerKey key = TriggerKey.triggerKey("subscriptionScanJob");
            assertEquals("0 0 0 * * ?", ((CronTrigger) scheduler.getTrigger(key)).getCronExpression());
            manager.updateJob("subscriptionScanJob", "", "0 0 1 * * ?", 0, 0);
            manager.pauseJob("subscriptionScanJob");
            registration.register();
            assertEquals("0 0 1 * * ?", ((CronTrigger) scheduler.getTrigger(key)).getCronExpression());
            assertEquals(Trigger.TriggerState.PAUSED, scheduler.getTriggerState(key));
            manager.resumeJob("subscriptionScanJob");
            assertEquals(Trigger.TriggerState.NORMAL, scheduler.getTriggerState(key));
            scheduler.start();
            manager.triggerJob(100L, "subscriptionScanJob", "");
            assertTrue(logged.await(5, TimeUnit.SECONDS), "Quartz should invoke the handler and record a log");
            verify(subscriptions).scan();
            verify(logs).updateJobLogResultAsync(eq(1L), any(), anyInt(), eq(true), contains("扫描 4"));
        } finally {
            scheduler.shutdown(true);
            context.close();
        }
    }
}
