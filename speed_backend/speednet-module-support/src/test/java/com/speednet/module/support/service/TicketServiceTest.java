package com.speednet.module.support.service;

import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.module.system.api.permission.PermissionApi;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static com.speednet.module.support.service.TicketRequests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketServiceTest extends TicketTestFixture {
    @Test void jdbcDateTypesHaveTheSameStringContract() {
        var date = LocalDateTime.of(2026, 10, 6, 11, 9, 11);
        var row = new LinkedHashMap<String, Object>();
        row.put("create_time", date);
        row.put("update_time", java.sql.Timestamp.valueOf(date));
        row.put("closed_at", null);
        row.put("id", 1L);
        ReflectionTestUtils.invokeMethod(service, "dates", row);
        assertEquals("2026-10-06T11:09:11", row.get("create_time"));
        assertEquals(row.get("create_time"), row.get("update_time"));
        assertNull(row.get("closed_at"));
        assertEquals(1L, row.get("id"));
    }

    @Test void createAndReplyRetriesAreIdempotent() {
        long id = create("create1"); assertEquals(id, create("create1"));
        reply(id, staff, "请提供截图", false, "waiting", "reply1", List.of());
        reply(id, staff, "请提供截图", false, "waiting", "reply1", List.of());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket_message", Integer.class));
        assertEquals("waiting", ticket(id).get("status"));
    }
    @Test void ownershipTenantAndAssignedStaffAreEnforced() {
        long id = create("c1"); assertThrows(RuntimeException.class, () -> service.detail(id, other, 0));
        assertEquals(0L, service.page(other, 1, 20, null, null, null, null, null, null).get("total"));
        action(id, staff, "claim", null, null, null);
        assertThrows(RuntimeException.class, () -> service.detail(id, otherStaff, 0));
        assertEquals(0L, service.page(otherStaff, 1, 20, null, null, "all", null, null, null).get("total"));
        assertNotNull(service.detail(id, manager, 0));
        TenantContextHolder.setTenantId(2L); assertThrows(RuntimeException.class, () -> service.detail(id, member, 0));
        assertEquals(0L, service.page(member, 1, 20, null, null, null, null, null, null).get("total"));
    }
    @Test void internalNotesAndTheirAttachmentsNeverReachMember() throws Exception {
        long id = create("c1"); byte[] png = {(byte)137,80,78,71,13,10,26,10,0};
        long attachment = inTx(() -> { try { return ((Number)service.upload(staff, new MockMultipartFile("file", "note.png", "image/png", png)).get("id")).longValue(); } catch(Exception ex){throw new RuntimeException(ex);} });
        var before = ticket(id); reply(id, staff, "服务器排查内部记录", true, null, "note1", List.of(attachment));
        assertEquals(before.get("status"), ticket(id).get("status")); assertEquals(before.get("update_time"), ticket(id).get("update_time"));
        assertFalse(JsonString(service.detail(id, member, 0)).contains("服务器排查内部记录"));
        assertThrows(RuntimeException.class, () -> service.attachment(attachment, member));
        assertArrayEquals(png, (byte[])service.attachment(attachment, staff).get("content"));
        var customerRow = ((List<Map<String,Object>>)service.page(member, 1, 20, null, null, null, null, null, null).get("list")).getFirst();
        assertEquals(0L, ((Number)customerRow.get("unread_count")).longValue());
    }
    String JsonString(Object data) { return com.speednet.framework.common.util.json.JsonUtils.toJsonString(data); }
    @Test void unreadCursorDoesNotConsumeRepliesArrivingAfterRead() {
        long id = create("c1"); reply(id, staff, "回复一", false, "waiting", "r1", List.of());
        long seen = ((Number)service.detail(id, member, 0).get("latestSeq")).longValue();
        reply(id, staff, "回复二", false, "waiting", "r2", List.of());
        inTx(() -> { service.read(id, member, seen); return null; });
        var row = ((List<Map<String,Object>>)service.page(member, 1, 20, null, null, null, null, null, null).get("list")).getFirst();
        assertEquals(1L, ((Number)row.get("unread_count")).longValue());
    }
    @Test void attachmentFailureRollsBackMessageAndTicket() {
        long id = create("c1"); assertThrows(RuntimeException.class, () -> reply(id, staff, "回复", false, "resolved", "r1", List.of(999L)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket_message", Integer.class));
        assertEquals("pending", ticket(id).get("status"));
    }
    @Test void memberCannotLinkSomeoneElsesOrderOrUseInternalNotes() {
        assertThrows(RuntimeException.class, () -> inTx(() -> service.create(member, new Create("付款问题", "payment", "未到账", 101L, null, "bad", List.of()))));
        long id = create("c1"); assertThrows(RuntimeException.class, () -> reply(id, member, "内部备注", true, null, "r1", List.of()));
    }
    @Test void closedTicketsRequireReopenAndRespectSevenDayLimit() {
        long id = create("c1"); action(id, member, "close", null, null, "客户撤回");
        assertThrows(RuntimeException.class, () -> reply(id, member, "补充", false, null, "r1", List.of()));
        action(id, member, "reopen", null, null, null); assertEquals("processing", ticket(id).get("status"));
        action(id, member, "close", null, null, "客户撤回"); jdbc.update("UPDATE support_ticket SET closed_at=? WHERE id=?", LocalDateTime.now().minusDays(8), id);
        assertThrows(RuntimeException.class, () -> action(id, member, "reopen", null, null, null));
        action(id, manager, "reopen", null, null, null); assertEquals("processing", ticket(id).get("status"));
    }
    @Test void staleVersionAndConcurrentClaimsDoNotOverwriteAssignee() throws Exception {
        long id = create("c1"); var pool = Executors.newFixedThreadPool(2);
        try {
            var gate = new CountDownLatch(1);
            Callable<Boolean> first = () -> claim(id, staff, gate), second = () -> claim(id, otherStaff, gate);
            var a=pool.submit(first); var b=pool.submit(second); gate.countDown(); assertNotEquals(a.get(), b.get());
            assertThrows(RuntimeException.class, () -> inTx(() -> { service.action(id, manager, new Action(0, "priority", null, "urgent", null)); return null; }));
        } finally { pool.shutdownNow(); }
    }
    boolean claim(long id, Actor actor, CountDownLatch gate) throws Exception { gate.await(); TenantContextHolder.setTenantId(1L); try { inTx(() -> { service.action(id, actor, new Action(0,"claim",null,null,null)); return null; }); return true; } catch (RuntimeException ex) { return false; } finally { TenantContextHolder.clear(); } }
    @Test void limitsAndImageValidation() {
        for(int i=0;i<5;i++) create("c"+i); assertThrows(RuntimeException.class, () -> create("sixth"));
        assertThrows(RuntimeException.class, () -> TicketService.imageType("<svg onload='alert(1)'/>".getBytes()));
        assertTrue(TicketPolicy.canReopen(LocalDateTime.now().minusDays(6), LocalDateTime.now()));
        assertFalse(TicketPolicy.canReopen(LocalDateTime.now().minusDays(8), LocalDateTime.now()));
    }
    @Test void stagingAttachmentsCanOnlyBeRemovedByTheirOwnerAndBeforeSending() {
        byte[] png = {(byte)137,80,78,71,13,10,26,10,0};
        long file=inTx(() -> { try { return ((Number)service.upload(member,new MockMultipartFile("file","test.png","image/png",png)).get("id")).longValue(); } catch(Exception ex){throw new RuntimeException(ex);} });
        assertThrows(RuntimeException.class,() -> inTx(() -> { service.removeAttachment(file,other); return null; }));
        long id=inTx(() -> service.create(member,new Create("测试截图","connection","无法连接",null,null,"with-file",List.of(file))));
        assertThrows(RuntimeException.class,() -> inTx(() -> { service.removeAttachment(file,member); return null; }));
        assertArrayEquals(png,(byte[])service.attachment(file,member).get("content"));
        assertThrows(RuntimeException.class,() -> service.attachment(file,other));
    }
    @Test void deletedRelatedSubscriptionDoesNotHideTicketHistory() {
        jdbc.update("INSERT INTO subscription VALUES(200,1,10,'SUB200',1,2,?,100,10,20,'',0)",LocalDateTime.now().plusDays(1));
        long id=inTx(() -> service.create(member,new Create("订阅问题","subscription","流量异常",null,200L,"sub-history",List.of())));
        jdbc.update("UPDATE subscription SET deleted=1 WHERE id=200");
        assertNull(service.detail(id,manager,0).get("subscription"));
        assertTrue(JsonString(service.detail(id,member,0)).contains("SUB200"));
    }
    @Test void rateLimitsAndCustomerReplyWorkflowAreEnforced() {
        ReflectionTestUtils.setField(service,"createInterval",60);
        long id=create("c1"); assertThrows(RuntimeException.class,() -> create("c2"));
        assertEquals(id,create("c1"));
        reply(id,staff,"已修复",false,"resolved","r1",List.of());
        reply(id,member,"还有问题",false,null,"r2",List.of());
        assertEquals("processing",ticket(id).get("status"));
        ReflectionTestUtils.setField(service,"replyInterval",60);
        assertThrows(RuntimeException.class,() -> reply(id,member,"再次回复",false,null,"r3",List.of()));
    }
    @Test void cleanupOnlyRemovesExpiredUnsentScreenshots() {
        byte[] png={(byte)137,80,78,71,13,10,26,10,0};
        Supplier<Long> upload=() -> { try { return ((Number)service.upload(member,new MockMultipartFile("file","test.png","image/png",png)).get("id")).longValue(); } catch(Exception ex){throw new RuntimeException(ex);} };
        long attached=inTx(upload), stale=inTx(upload), fresh=inTx(upload);
        inTx(() -> service.create(member,new Create("保留截图","connection","已发送截图",null,null,"retain",List.of(attached))));
        jdbc.update("UPDATE support_ticket_attachment SET create_time=? WHERE id IN (?,?)",LocalDateTime.now().minusDays(2),attached,stale);
        new com.speednet.module.support.job.AttachmentCleanup(jdbc).clean();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket_attachment WHERE id=?",Integer.class,stale));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket_attachment WHERE id IN (?,?)",Integer.class,attached,fresh));
    }
    @Test void messageHistoryIsPagedWithoutExposingInternalNotes() {
        long id=create("c1"); for(int i=0;i<60;i++) reply(id,staff,"消息"+i,i%2==0,"waiting","r"+i,List.of());
        var customer=service.detail(id,member,0); assertEquals(31, ((List<?>)customer.get("messages")).size());
        var first=service.detail(id,manager,0); assertEquals(50, ((List<?>)first.get("messages")).size());
        var older=service.detail(id,manager,((Number)first.get("nextBeforeSeq")).longValue()); assertEquals(11,((List<?>)older.get("messages")).size());
    }
}
