package com.speednet.module.subscription.job;

import com.speednet.framework.quartz.core.handler.JobHandler;
import com.speednet.framework.tenant.core.aop.TenantIgnore;
import com.speednet.module.subscription.service.SubscriptionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** Managed in 基础设施 → 定时任务. The worker queue remains asynchronous. */
@Component(SubscriptionScanJob.HANDLER_NAME)
public class SubscriptionScanJob implements JobHandler {
    public static final String HANDLER_NAME = "subscriptionScanJob";

    @Resource
    private SubscriptionService subscriptions;

    @Override
    @TenantIgnore
    public String execute(String param) {
        SubscriptionService.ScanResult result = subscriptions.scan();
        if (result.failed() > 0) {
            throw new IllegalStateException(result.summary());
        }
        return result.summary();
    }
}
