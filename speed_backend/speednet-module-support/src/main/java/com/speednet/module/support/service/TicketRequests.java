package com.speednet.module.support.service;
import java.util.List;

public final class TicketRequests {
    private TicketRequests() {}
    public record Create(String title, String category, String body, Long orderId, Long subscriptionId, String requestKey, List<Long> attachmentIds) {}
    public record Reply(String body, boolean internal, String status, String requestKey, List<Long> attachmentIds) {}
    public record Action(int version, String action, Long assigneeId, String priority, String reason) {}
    public record Read(long lastSeq) {}
    public record Actor(long id, boolean admin, boolean manager) {
        public String type() { return admin ? "staff" : "member"; }
    }
}
