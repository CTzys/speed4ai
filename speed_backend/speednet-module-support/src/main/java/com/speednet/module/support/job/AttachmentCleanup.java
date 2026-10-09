package com.speednet.module.support.job;

import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;

/** Removes only unattached uploads older than 24 hours; linked conversation evidence is retained. */
@Component
public class AttachmentCleanup {
    private final JdbcTemplate jdbc;
    public AttachmentCleanup(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Scheduled(fixedDelayString="${speednet.support.attachment-cleanup-ms:3600000}", initialDelayString="${speednet.support.attachment-cleanup-ms:3600000}")
    public void clean() {
        jdbc.update("DELETE FROM support_ticket_attachment WHERE ticket_id=0 AND message_id IS NULL AND create_time<? LIMIT 500", LocalDateTime.now().minusHours(24));
    }
}
