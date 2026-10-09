package com.speednet.module.support.service;

import com.speednet.framework.common.util.json.JsonUtils;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;
import static com.speednet.module.support.service.TicketPolicy.*;
import static com.speednet.module.support.service.TicketRequests.*;

/** Every SQL statement scopes tenant explicitly; customer and staff visibility are independent. */
@Service
public class TicketService {
    private final JdbcTemplate jdbc;
    @Value("${speednet.support.max-open:5}") private int maxOpen = 5;
    @Value("${speednet.support.create-interval-seconds:60}") private int createInterval = 60;
    @Value("${speednet.support.reply-interval-seconds:5}") private int replyInterval = 5;
    public TicketService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private Map<String, Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        require(!rows.isEmpty(), "记录不存在或无权访问");
        return dates(rows.getFirst());
    }
    private Map<String, Object> dates(Map<String, Object> row) {
        row.replaceAll((key, value) -> value instanceof java.sql.Timestamp t ? t.toLocalDateTime().toString()
                : value instanceof LocalDateTime t ? t.toString() : value);
        return row;
    }
    public long unread(Actor actor) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket_message m JOIN support_ticket t ON t.tenant_id=m.tenant_id AND t.id=m.ticket_id WHERE t.tenant_id=? AND t.member_id=? AND m.visibility='public' AND m.sender_type='staff' AND m.seq>COALESCE((SELECT r.last_seq FROM support_ticket_read r WHERE r.tenant_id=t.tenant_id AND r.ticket_id=t.id AND r.reader_type='member' AND r.reader_id=?),0)", Long.class, tenant(), actor.id(), actor.id());
    }
    private void lockActor(Actor actor) {
        String table = actor.admin() ? "system_users" : "member_user";
        one("SELECT id FROM " + table + " WHERE tenant_id=? AND id=? AND deleted=0 AND status=0 FOR UPDATE", tenant(), actor.id());
    }
    private Map<String, Object> ticket(long id, Actor actor, boolean lock) {
        var row = one("SELECT * FROM support_ticket WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""), tenant(), id);
        require(actor.admin() ? actor.manager() || row.get("assignee_id") == null || Objects.equals(row.get("assignee_id"), actor.id())
                : number(row, "member_id") == actor.id(), "记录不存在或无权访问");
        return row;
    }
    private void event(long id, Actor actor, String action, String detail) {
        jdbc.update("INSERT INTO support_ticket_event(tenant_id,ticket_id,actor_type,actor_id,action,detail) VALUES(?,?,?,?,?,?)", tenant(), id, actor.type(), actor.id(), action, detail);
    }
    private Object order(Long id, long member) {
        return id == null ? null : one("SELECT id,number,plan_name,status,amount,kind FROM subscription_purchase WHERE tenant_id=? AND id=? AND user_id=?", tenant(), id, member);
    }
    private Object subscription(Long id, long member) {
        return id == null ? null : one("SELECT id,number,status,sync_status,expiry_time,total_bytes,used_upload,used_download,last_error FROM subscription WHERE tenant_id=? AND id=? AND user_id=? AND deleted=0", tenant(), id, member);
    }
    private Object currentOrder(Long id, long member) {
        if (id == null) return null;
        var rows = jdbc.queryForList("SELECT id,number,plan_name,status,amount,kind FROM subscription_purchase WHERE tenant_id=? AND id=? AND user_id=?", tenant(), id, member);
        return rows.isEmpty() ? null : dates(rows.getFirst());
    }
    private Object currentSubscription(Long id, long member) {
        if (id == null) return null;
        var rows = jdbc.queryForList("SELECT id,number,status,sync_status,expiry_time,total_bytes,used_upload,used_download,last_error FROM subscription WHERE tenant_id=? AND id=? AND user_id=? AND deleted=0", tenant(), id, member);
        return rows.isEmpty() ? null : dates(rows.getFirst());
    }
    @Transactional
    public long create(Actor actor, Create req) {
        require(!actor.admin(), "请通过客户入口创建工单");
        String title = text(req.title(), 120, "标题"), body = text(req.body(), 5000, "问题描述"), key = text(req.requestKey(), 64, "请求标识");
        require(CATEGORIES.contains(req.category() == null ? "" : req.category()), "请选择问题分类");
        lockActor(actor); // Serializes limits and idempotency across concurrent requests by the member.
        var existing = jdbc.queryForList("SELECT id FROM support_ticket WHERE tenant_id=? AND member_id=? AND request_key=?", tenant(), actor.id(), key);
        if (!existing.isEmpty()) return number(existing.getFirst(), "id");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket WHERE tenant_id=? AND member_id=? AND status<>'closed'", Long.class, tenant(), actor.id()) < maxOpen, "未关闭工单已达上限，请先处理已有工单");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket WHERE tenant_id=? AND member_id=? AND create_time>?", Long.class, tenant(), actor.id(), LocalDateTime.now().minusSeconds(createInterval)) == 0, "提交过于频繁，请稍后再试");
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("order", order(req.orderId(), actor.id()));
        snapshot.put("subscription", subscription(req.subscriptionId(), actor.id()));
        var now = LocalDateTime.now();
        long id = insert("INSERT INTO support_ticket(tenant_id,member_id,number,title,category,order_id,subscription_id,snapshot_json,request_key,last_activity_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                tenant(), actor.id(), "TK" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT), title, req.category(), req.orderId(), req.subscriptionId(), JsonUtils.toJsonString(snapshot), key, now);
        message(id, actor, body, false, key, req.attachmentIds(), 1);
        jdbc.update("UPDATE support_ticket SET message_seq=1 WHERE tenant_id=? AND id=?", tenant(), id);
        event(id, actor, "created", "客户提交工单");
        return id;
    }
    private void message(long id, Actor actor, String body, boolean internal, String key, List<Long> attachments, long seq) {
        long messageId = insert("INSERT INTO support_ticket_message(tenant_id,ticket_id,seq,sender_type,sender_id,visibility,body,request_key) VALUES(?,?,?,?,?,?,?,?)", tenant(), id, seq, actor.type(), actor.id(), internal ? "internal" : "public", body, key);
        var ids = attachments == null ? List.<Long>of() : attachments;
        require(ids.size() <= 3 && new HashSet<>(ids).size() == ids.size() && ids.stream().allMatch(Objects::nonNull), "每条消息最多附带 3 张不同截图");
        for (long attachmentId : ids) {
            int changed = jdbc.update("UPDATE support_ticket_attachment SET ticket_id=?,message_id=? WHERE tenant_id=? AND id=? AND ticket_id=0 AND message_id IS NULL AND uploader_type=? AND uploader_id=? AND create_time>?", id, messageId, tenant(), attachmentId, actor.type(), actor.id(), LocalDateTime.now().minusHours(24));
            require(changed == 1, "截图已失效或无权使用，请重新上传");
        }
    }
    @Transactional
    public void reply(long id, Actor actor, Reply req) {
        String body = text(req.body(), 5000, "回复内容"), key = text(req.requestKey(), 64, "请求标识");
        require(actor.admin() || !req.internal(), "客户不能添加内部备注");
        lockActor(actor);
        var row = ticket(id, actor, true);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket_message WHERE tenant_id=? AND ticket_id=? AND sender_type=? AND sender_id=? AND request_key=?", Long.class, tenant(), id, actor.type(), actor.id(), key) > 0) return;
        String current = (String) row.get("status");
        require(!"closed".equals(current), "工单已关闭，请先重新打开");
        if (!actor.admin()) {
            require(jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket_message WHERE tenant_id=? AND sender_type='member' AND sender_id=? AND create_time>?", Long.class, tenant(), actor.id(), LocalDateTime.now().minusSeconds(replyInterval)) == 0, "回复过于频繁，请稍后再试");
        }
        long seq = number(row, "message_seq") + 1;
        String next = req.internal() ? current : replyStatus(actor.admin(), current, req.status());
        message(id, actor, body, req.internal(), key, req.attachmentIds(), seq);
        // Internal notes never change customer-facing timestamps or workflow state.
        jdbc.update("UPDATE support_ticket SET message_seq=?,status=?,version=version+1" + (req.internal() ? "" : ",last_activity_at=CURRENT_TIMESTAMP(3),update_time=CURRENT_TIMESTAMP(3)") + " WHERE tenant_id=? AND id=?", seq, next, tenant(), id);
        event(id, actor, req.internal() ? "note" : "reply", req.internal() ? "添加内部备注" : current + " → " + next);
    }
    @Transactional
    public void action(long id, Actor actor, Action req) {
        lockActor(actor);
        var row = ticket(id, actor, true);
        require(number(row, "version") == req.version(), "工单已被更新，请刷新后重试");
        String status = (String) row.get("status"), next = status, detail;
        Object assignee = row.get("assignee_id");
        String priority = (String) row.get("priority"), reason = (String) row.get("close_reason");
        LocalDateTime closedAt = row.get("closed_at") == null ? null : LocalDateTime.parse((String) row.get("closed_at"));
        switch (req.action() == null ? "" : req.action()) {
            case "claim" -> {
                require(actor.admin() && !"closed".equals(status), "此工单不可领取");
                require(assignee == null, "工单已被领取");
                assignee = actor.id(); next = "pending".equals(status) ? "processing" : status; detail = "领取工单";
            }
            case "assign" -> {
                require(actor.admin() && actor.manager() && !"closed".equals(status), "需要主管权限且工单未关闭");
                if (req.assigneeId() != null) {
                    one("SELECT id FROM system_users WHERE tenant_id=? AND id=? AND status=0 AND deleted=0", tenant(), req.assigneeId());
                    require(eligibleStaff(req.assigneeId()), "该员工没有工单查询及回复权限");
                }
                assignee = req.assigneeId(); next = assignee != null && "pending".equals(status) ? "processing" : status;
                detail = "负责人 " + row.get("assignee_id") + " → " + assignee;
            }
            case "priority" -> {
                require(actor.admin() && actor.manager() && PRIORITIES.contains(req.priority() == null ? "" : req.priority()), "优先级无效或无主管权限");
                priority = req.priority(); detail = "优先级 " + row.get("priority") + " → " + priority;
            }
            case "close" -> {
                require(!"closed".equals(status), "工单已经关闭");
                reason = text(req.reason(), 500, "关闭原因"); next = "closed"; closedAt = LocalDateTime.now(); detail = reason;
            }
            case "reopen" -> {
                require("closed".equals(status) || "resolved".equals(status), "仅可重新打开已解决或已关闭工单");
                require(actor.admin() || !"closed".equals(status) || canReopen(closedAt, LocalDateTime.now()), "关闭已超过 7 天，请新建工单");
                if (!actor.admin() && "closed".equals(status)) require(jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket WHERE tenant_id=? AND member_id=? AND status<>'closed'", Long.class, tenant(), actor.id()) < maxOpen, "未关闭工单已达上限");
                next = "processing"; reason = ""; closedAt = null; detail = "重新打开工单";
            }
            default -> throw new IllegalArgumentException("未知工单操作");
        }
        jdbc.update("UPDATE support_ticket SET status=?,assignee_id=?,priority=?,closed_at=?,close_reason=?,version=version+1,update_time=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=?", next, assignee, priority, closedAt, reason, tenant(), id);
        event(id, actor, req.action(), detail + "；状态 " + status + " → " + next);
    }
    @jakarta.annotation.Resource private com.speednet.module.system.api.permission.PermissionApi permissionApi;
    private boolean eligibleStaff(long id) {
        return permissionApi.hasAnyPermissions(id, "support:ticket:query") && permissionApi.hasAnyPermissions(id, "support:ticket:reply");
    }
    public List<Map<String, Object>> staff(Actor actor) {
        require(actor.manager(), "需要主管权限");
        return jdbc.queryForList("SELECT id,nickname FROM system_users WHERE tenant_id=? AND deleted=0 AND status=0 ORDER BY id", tenant()).stream().filter(r -> eligibleStaff(number(r, "id"))).toList();
    }
    public Map<String, Object> page(Actor actor, int page, int size, String status, String category, String view, String keyword, Long memberId, Long assigneeId) {
        require(page >= 1 && page <= 100000 && size >= 1 && size <= 100, "分页参数无效");
        StringBuilder where = new StringBuilder(" t.tenant_id=?");
        var args = new ArrayList<Object>(); args.add(tenant());
        if (!actor.admin()) { where.append(" AND t.member_id=?"); args.add(actor.id()); }
        else if (!actor.manager()) { where.append(" AND (t.assignee_id IS NULL OR t.assignee_id=?)"); args.add(actor.id()); }
        if (status != null && !status.isBlank()) { require(STATUSES.contains(status), "状态无效"); where.append(" AND t.status=?"); args.add(status); }
        if (category != null && !category.isBlank()) { require(CATEGORIES.contains(category), "分类无效"); where.append(" AND t.category=?"); args.add(category); }
        if (actor.admin()) {
            if ("mine".equals(view)) { where.append(" AND t.assignee_id=?"); args.add(actor.id()); }
            else if ("pending".equals(view)) where.append(" AND t.status IN ('pending','processing')");
            if (memberId != null) { where.append(" AND t.member_id=?"); args.add(memberId); }
            if (assigneeId != null) { where.append(" AND t.assignee_id=?"); args.add(assigneeId); }
        }
        if (keyword != null && !keyword.isBlank()) {
            require(keyword.length() <= 120, "搜索内容过长");
            where.append(" AND (t.title LIKE ? OR t.number LIKE ? OR EXISTS(SELECT 1 FROM subscription_purchase p WHERE p.tenant_id=t.tenant_id AND p.id=t.order_id AND p.number LIKE ?))");
            for (int i = 0; i < 3; i++) args.add("%" + keyword.trim() + "%");
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket t WHERE " + where, Long.class, args.toArray());
        var listArgs = new ArrayList<Object>(); listArgs.add(actor.type()); listArgs.add(actor.id()); listArgs.add(actor.admin() ? "member" : "staff"); listArgs.addAll(args); listArgs.add(size); listArgs.add((page - 1) * size);
        var rows = jdbc.queryForList("SELECT t.id,t.number,t.title,t.category,t.priority,t.status,t.member_id,t.assignee_id,t.order_id,t.subscription_id,t.version,t.create_time,t.update_time,t.last_activity_at,t.closed_at,t.close_reason,"
                + "(SELECT COUNT(*) FROM support_ticket_message m WHERE m.tenant_id=t.tenant_id AND m.ticket_id=t.id AND m.visibility='public' AND m.seq>COALESCE((SELECT r.last_seq FROM support_ticket_read r WHERE r.tenant_id=t.tenant_id AND r.ticket_id=t.id AND r.reader_type=? AND r.reader_id=?),0) AND m.sender_type=?) unread_count FROM support_ticket t WHERE " + where + " ORDER BY t.update_time DESC,t.id DESC LIMIT ? OFFSET ?", listArgs.toArray());
        rows.forEach(this::dates);
        if (!actor.admin()) rows.forEach(r -> { r.remove("assignee_id"); r.remove("priority"); });
        return Map.of("list", rows, "total", total);
    }
    public Map<String, Object> detail(long id, Actor actor, long beforeSeq) {
        var row = new LinkedHashMap<>(ticket(id, actor, false));
        row.remove("request_key"); row.remove("message_seq");
        if (!actor.admin()) { row.remove("assignee_id"); row.remove("priority"); }
        var snapshot = JsonUtils.parseObject((String) row.remove("snapshot_json"), Map.class);
        String visible = actor.admin() ? "" : " AND visibility='public'";
        var messages = jdbc.queryForList("SELECT id,seq,sender_type,sender_id,visibility,body,create_time FROM support_ticket_message WHERE tenant_id=? AND ticket_id=?" + visible + (beforeSeq > 0 ? " AND seq<?" : "") + " ORDER BY seq DESC LIMIT 50", beforeSeq > 0 ? new Object[]{tenant(), id, beforeSeq} : new Object[]{tenant(), id});
        for (var m : messages) {
            dates(m);
            m.put("attachments", jdbc.queryForList("SELECT id,name,content_type,size FROM support_ticket_attachment WHERE tenant_id=? AND ticket_id=? AND message_id=? ORDER BY id", tenant(), id, m.get("id")));
            if (!actor.admin()) m.remove("sender_id");
        }
        Collections.reverse(messages);
        var result = new LinkedHashMap<String, Object>(); result.put("ticket", row); result.put("snapshot", snapshot); result.put("messages", messages);
        Long latest = jdbc.queryForObject("SELECT MAX(seq) FROM support_ticket_message WHERE tenant_id=? AND ticket_id=?" + visible, Long.class, tenant(), id);
        result.put("latestSeq", latest == null ? 0 : latest);
        result.put("nextBeforeSeq", messages.size() == 50 ? messages.getFirst().get("seq") : 0);
        if (actor.admin()) {
            result.put("member", one("SELECT id,nickname,email FROM member_user WHERE tenant_id=? AND id=?", tenant(), row.get("member_id")));
            result.put("order", currentOrder((Long) row.get("order_id"), number(row, "member_id")));
            result.put("subscription", currentSubscription((Long) row.get("subscription_id"), number(row, "member_id")));
        }
        return result;
    }
    public Map<String, Object> events(long id, Actor actor, long beforeId) {
        require(actor.admin(), "仅员工可查看操作记录"); ticket(id, actor, false);
        var rows = jdbc.queryForList("SELECT id,actor_type,actor_id,action,detail,create_time FROM support_ticket_event WHERE tenant_id=? AND ticket_id=?" + (beforeId > 0 ? " AND id<?" : "") + " ORDER BY id DESC LIMIT 50", beforeId > 0 ? new Object[]{tenant(), id, beforeId} : new Object[]{tenant(), id});
        rows.forEach(this::dates);
        return Map.of("list", rows, "nextBeforeId", rows.size() == 50 ? rows.getLast().get("id") : 0);
    }
    @Transactional
    public void read(long id, Actor actor, long seq) {
        ticket(id, actor, true);
        Long max = jdbc.queryForObject("SELECT MAX(seq) FROM support_ticket_message WHERE tenant_id=? AND ticket_id=?" + (actor.admin() ? "" : " AND visibility='public'"), Long.class, tenant(), id);
        require(seq >= 0 && seq <= (max == null ? 0 : max), "已读位置无效");
        jdbc.update("INSERT INTO support_ticket_read(tenant_id,ticket_id,reader_type,reader_id,last_seq) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE last_seq=GREATEST(last_seq,VALUES(last_seq))", tenant(), id, actor.type(), actor.id(), seq);
    }
    @Transactional
    public Map<String, Object> upload(Actor actor, MultipartFile file) throws java.io.IOException {
        lockActor(actor);
        require(!file.isEmpty() && file.getSize() <= 5 * 1024 * 1024, "截图必须为 5 MB 以内的 JPG、PNG 或 WebP");
        jdbc.update("DELETE FROM support_ticket_attachment WHERE tenant_id=? AND uploader_type=? AND uploader_id=? AND message_id IS NULL AND create_time<?", tenant(), actor.type(), actor.id(), LocalDateTime.now().minusHours(24));
        require(jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket_attachment WHERE tenant_id=? AND uploader_type=? AND uploader_id=? AND message_id IS NULL", Long.class, tenant(), actor.type(), actor.id()) < 9, "待发送截图已达上限，请使用已上传截图或稍后再试");
        byte[] bytes = file.getBytes(); String type = imageType(bytes);
        String name = file.getOriginalFilename() == null ? "截图" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n]", "_");
        if (name.length() > 200) name = name.substring(name.length() - 200);
        long id = insert("INSERT INTO support_ticket_attachment(tenant_id,ticket_id,uploader_type,uploader_id,name,content_type,size,content) VALUES(?,0,?,?,?,?,?,?)", tenant(), actor.type(), actor.id(), name, type, bytes.length, bytes);
        return Map.of("id", id, "name", name, "content_type", type, "size", bytes.length);
    }
    public static String imageType(byte[] bytes) {
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) return "image/jpeg";
        if (bytes.length >= 12 && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        throw com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception(new com.speednet.framework.common.exception.ErrorCode(1_014_000_001, "仅支持 JPG、PNG、WebP 截图"));
    }
    @Transactional
    public void removeAttachment(long id, Actor actor) {
        lockActor(actor);
        int changed = jdbc.update("DELETE FROM support_ticket_attachment WHERE tenant_id=? AND id=? AND ticket_id=0 AND message_id IS NULL AND uploader_type=? AND uploader_id=?", tenant(), id, actor.type(), actor.id());
        require(changed == 1, "截图已发送或无权删除");
    }
    public Map<String, Object> attachment(long attachmentId, Actor actor) {
        var metadata = one("SELECT id,ticket_id,message_id,uploader_type,uploader_id,content_type,name FROM support_ticket_attachment WHERE tenant_id=? AND id=?", tenant(), attachmentId);
        long id = number(metadata, "ticket_id");
        if (id == 0) require(actor.type().equals(metadata.get("uploader_type")) && number(metadata, "uploader_id") == actor.id(), "无权访问截图");
        else {
            ticket(id, actor, false);
            var message = one("SELECT visibility FROM support_ticket_message WHERE tenant_id=? AND ticket_id=? AND id=?", tenant(), id, metadata.get("message_id"));
            require(actor.admin() || "public".equals(message.get("visibility")), "无权访问截图");
        }
        metadata.put("content", jdbc.queryForObject("SELECT content FROM support_ticket_attachment WHERE tenant_id=? AND id=?", byte[].class, tenant(), attachmentId));
        return metadata;
    }
}
