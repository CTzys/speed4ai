package com.speednet.module.support.service;

import com.speednet.framework.common.exception.ErrorCode;
import com.speednet.framework.security.core.util.SecurityFrameworkUtils;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.module.system.api.permission.PermissionApi;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;

@Component
public class TicketIdentity {
    private final JdbcTemplate jdbc;
    private final PermissionApi permissions;
    public TicketIdentity(JdbcTemplate jdbc, PermissionApi permissions) { this.jdbc = jdbc; this.permissions = permissions; }
    public TicketRequests.Actor member(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ") || auth.length() > 512) throw exception(new ErrorCode(401, "请先登录"));
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(auth.substring(7).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        long tenant = TenantContextHolder.getRequiredTenantId();
        var rows = jdbc.queryForList("SELECT s.member_user_id FROM custom_session s JOIN member_user u ON u.id=s.member_user_id AND u.tenant_id=s.tenant_id WHERE s.tenant_id=? AND s.access_hash=? AND s.revoked=0 AND s.access_expires_at>? AND u.status=0 AND u.deleted=0", tenant, hash, LocalDateTime.now());
        if (rows.isEmpty()) throw exception(new ErrorCode(401, "登录已过期，请重新登录"));
        long id = ((Number) rows.getFirst().get("member_user_id")).longValue();
        SecurityFrameworkUtils.setLoginUser(new com.speednet.framework.security.core.LoginUser().setId(id).setUserType(1).setTenantId(tenant), request);
        return new TicketRequests.Actor(id, false, false);
    }
    public TicketRequests.Actor staff() {
        Long id = SecurityFrameworkUtils.getLoginUserId();
        if (id == null) throw exception(new ErrorCode(401, "请先登录"));
        return new TicketRequests.Actor(id, true, permissions.hasAnyPermissions(id, "support:ticket:manage"));
    }
    public static org.springframework.http.ResponseEntity<byte[]> image(java.util.Map<String, Object> file) {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType((String) file.get("content_type")))
                .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body((byte[]) file.get("content"));
    }
}
