package com.speednet.module.subscription.job;

import com.speednet.framework.quartz.core.scheduler.SchedulerManager;
import com.speednet.module.infra.dal.dataobject.job.JobDO;
import com.speednet.module.infra.dal.mysql.job.JobMapper;
import com.speednet.module.infra.enums.job.JobStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Seeds the Quartz trigger once without replacing admin changes on subsequent restarts. */
@Component
@Slf4j
public class SubscriptionScanJobRegistration {
    @Resource
    private JobMapper jobs;
    @Resource
    private SchedulerManager manager;
    @Resource
    private Optional<Scheduler> scheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void register() throws SchedulerException {
        JobDO job = jobs.selectByHandlerName(SubscriptionScanJob.HANDLER_NAME);
        if (job == null) {
            // A deleted job must stay deleted; only the versioned migration creates the default row.
            log.warn("[订阅扫描] 未找到任务配置，请在基础设施 → 定时任务中添加 subscriptionScanJob");
            return;
        }
        if (scheduler.isEmpty()) {
            log.warn("[订阅扫描] Quartz 未启用，自动扫描不执行；业务操作和手动同步仍可入队");
            return;
        }
        JobKey key = JobKey.jobKey(SubscriptionScanJob.HANDLER_NAME);
        if (scheduler.get().checkExists(key)) {
            // JDBC Quartz already retains cron and paused state; never reset them at startup.
            return;
        }
        try {
            manager.addJob(job.getId(), job.getHandlerName(), job.getHandlerParam(),
                job.getCronExpression(), job.getRetryCount(), job.getRetryInterval());
        } catch (ObjectAlreadyExistsException ignored) {
            // Another instance may have registered the same persisted job after checkExists().
        }
        if (JobStatusEnum.STOP.getStatus().equals(job.getStatus())) {
            manager.pauseJob(job.getHandlerName());
        }
        log.info("[订阅扫描] 已注册管理任务，Cron={}，状态={}", job.getCronExpression(), job.getStatus());
    }
}
