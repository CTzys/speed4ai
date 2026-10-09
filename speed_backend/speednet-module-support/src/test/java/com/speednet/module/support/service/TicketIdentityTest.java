package com.speednet.module.support.service;

import com.speednet.framework.security.core.util.SecurityFrameworkUtils;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.module.system.api.permission.PermissionApi;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketIdentityTest {
    JdbcTemplate jdbc;
    TicketIdentity identity;
    MockHttpServletRequest request;
    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:identity" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE member_user(id bigint,tenant_id bigint,status int,deleted boolean)");
        jdbc.execute("CREATE TABLE custom_session(tenant_id bigint,member_user_id bigint,access_hash varchar(64),revoked boolean,access_expires_at timestamp)");
        jdbc.update("INSERT INTO member_user VALUES(10,1,0,0)");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("test-token".getBytes(StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO custom_session VALUES(1,10,?,0,?)", hash, LocalDateTime.now().plusHours(1));
        identity = new TicketIdentity(jdbc, mock(PermissionApi.class));
        request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer test-token");
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }
    @Test void establishesMemberIdentityFromStoredSession() {
        assertEquals(10, identity.member(request).id());
        assertEquals(10L, SecurityFrameworkUtils.getLoginUserId());
    }
    @Test void expiredRevokedDisabledAndWrongTenantSessionsAreRejected() {
        jdbc.update("UPDATE custom_session SET revoked=1"); assertThrows(RuntimeException.class, () -> identity.member(request));
        jdbc.update("UPDATE custom_session SET revoked=0,access_expires_at=?", LocalDateTime.now().minusHours(1)); assertThrows(RuntimeException.class, () -> identity.member(request));
        jdbc.update("UPDATE custom_session SET access_expires_at=?", LocalDateTime.now().plusHours(1));
        jdbc.update("UPDATE member_user SET status=1"); assertThrows(RuntimeException.class, () -> identity.member(request));
        jdbc.update("UPDATE member_user SET status=0"); TenantContextHolder.setTenantId(2L); assertThrows(RuntimeException.class, () -> identity.member(request));
    }
    @Test void missingAuthorizationCannotReachMemberApi() { assertThrows(RuntimeException.class, () -> identity.member(new MockHttpServletRequest())); }
}
