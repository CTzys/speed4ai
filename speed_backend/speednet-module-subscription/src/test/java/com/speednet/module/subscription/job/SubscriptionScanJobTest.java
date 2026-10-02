package com.speednet.module.subscription.job;

import com.speednet.module.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionScanJobTest {
    @Test
    void reportsSubmissionSummaryInsteadOfClaimingRemoteSyncSuccess() {
        var subscriptions = mock(SubscriptionService.class);
        var job = new SubscriptionScanJob();
        ReflectionTestUtils.setField(job, "subscriptions", subscriptions);
        when(subscriptions.scan()).thenReturn(new SubscriptionService.ScanResult(10, 8, 2, 0));
        String result = job.execute("");
        assertTrue(result.contains("扫描 10"));
        assertTrue(result.contains("队列满延期 2"));
        assertTrue(result.contains("具体同步结果请查看订阅详情"));
        verify(subscriptions).scan();
    }

    @Test
    void submissionErrorsAreFailuresInManagedJobLogs() {
        var subscriptions = mock(SubscriptionService.class);
        var job = new SubscriptionScanJob();
        ReflectionTestUtils.setField(job, "subscriptions", subscriptions);
        when(subscriptions.scan()).thenReturn(new SubscriptionService.ScanResult(3, 2, 0, 1));
        var error = assertThrows(IllegalStateException.class, () -> job.execute(""));
        assertTrue(error.getMessage().contains("提交失败 1"));
    }

    @Test
    void databaseScanFailureIsNotReportedAsSuccess() {
        var subscriptions = mock(SubscriptionService.class);
        var job = new SubscriptionScanJob();
        ReflectionTestUtils.setField(job, "subscriptions", subscriptions);
        when(subscriptions.scan()).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> job.execute(""));
    }
}
