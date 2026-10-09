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

class TicketTestFixture {
    JdbcTemplate jdbc;
    TicketService service;
    TransactionTemplate tx;
    final Actor member = new Actor(10, false, false), other = new Actor(11, false, false), staff = new Actor(20, true, false), otherStaff = new Actor(21, true, false), manager = new Actor(22, true, true);
    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:tickets" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var sql = Files.readString(Path.of("../speednet-server/src/main/resources/db/migration/V10__customer_support_and_clash_rules.sql"));
        try (var c = ds.getConnection()) { ScriptUtils.executeSqlScript(c, new ByteArrayResource(sql.substring(0, sql.indexOf("INSERT INTO system_menu")).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        jdbc.execute("CREATE TABLE member_user(id bigint,tenant_id bigint,status int,deleted boolean,nickname varchar(50),email varchar(100))");
        jdbc.execute("CREATE TABLE system_users(id bigint,tenant_id bigint,status int,deleted boolean,nickname varchar(50))");
        jdbc.execute("CREATE TABLE subscription_purchase(id bigint,tenant_id bigint,user_id bigint,number varchar(64),plan_name varchar(100),status varchar(24),amount int,kind varchar(24))");
        jdbc.execute("CREATE TABLE subscription(id bigint,tenant_id bigint,user_id bigint,number varchar(64),status int,sync_status int,expiry_time datetime,total_bytes bigint,used_upload bigint,used_download bigint,last_error varchar(200),deleted boolean)");
        jdbc.update("INSERT INTO member_user VALUES(10,1,0,0,'客户','customer@test'),(11,1,0,0,'其他','other@test'),(10,2,0,0,'租户二','tenant@test')");
        jdbc.update("INSERT INTO system_users VALUES(20,1,0,0,'客服'),(21,1,0,0,'客服二'),(22,1,0,0,'主管')");
        jdbc.update("INSERT INTO subscription_purchase VALUES(100,1,10,'ORDER100','基础套餐','completed',1000,'period'),(101,1,11,'ORDER101','其他套餐','pending',2000,'period')");
        service = new TicketService(jdbc); ReflectionTestUtils.setField(service, "createInterval", 0); ReflectionTestUtils.setField(service, "replyInterval", 0);
        var permissions = mock(PermissionApi.class); when(permissions.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true); ReflectionTestUtils.setField(service, "permissionApi", permissions);
        TenantContextHolder.setTenantId(1L);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    <T> T inTx(Supplier<T> work) { return tx.execute(status -> work.get()); }
    long create(String key) { return inTx(() -> service.create(member, new Create("连接问题", "connection", "无法连接节点", 100L, null, key, List.of()))); }
    void reply(long id, Actor who, String text, boolean internal, String next, String key, List<Long> ids) { inTx(() -> { service.reply(id, who, new Reply(text, internal, next, key, ids)); return null; }); }
    Map<String,Object> ticket(long id) { return jdbc.queryForMap("SELECT * FROM support_ticket WHERE id=?", id); }
    void action(long id, Actor who, String action, Long assignee, String priority, String reason) { inTx(() -> { service.action(id, who, new Action(((Number)ticket(id).get("version")).intValue(), action, assignee, priority, reason)); return null; }); }
}
