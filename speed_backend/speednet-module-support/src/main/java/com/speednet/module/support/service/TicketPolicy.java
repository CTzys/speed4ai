package com.speednet.module.support.service;

import com.speednet.framework.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Set;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;

public final class TicketPolicy {
    private TicketPolicy() {}
    public static final Set<String> CATEGORIES = Set.of("connection", "subscription", "payment", "account", "other");
    public static final Set<String> STATUSES = Set.of("pending", "processing", "waiting", "resolved", "closed");
    public static final Set<String> PRIORITIES = Set.of("low", "normal", "high", "urgent");
    public static void require(boolean condition, String message) {
        if (!condition) throw exception(new ErrorCode(1_014_000_001, message));
    }
    public static String text(String value, int max, String field) {
        require(value != null && !value.isBlank() && value.trim().length() <= max, field + "不能为空且不能超过 " + max + " 个字符");
        return value.trim();
    }
    public static boolean canReopen(LocalDateTime closedAt, LocalDateTime now) {
        return closedAt != null && !now.isAfter(closedAt.plusDays(7));
    }
    public static String replyStatus(boolean admin, String current, String requested) {
        require(!"closed".equals(current), "工单已关闭，请先重新打开");
        if (!admin) return "processing";
        require(Set.of("processing", "waiting", "resolved").contains(requested == null ? "" : requested), "请选择回复后的工单状态");
        return requested;
    }
}
