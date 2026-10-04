package com.speednet.module.subscription.dal;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.*;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.speednet.framework.mybatis.core.handler.DefaultDBFieldHandler;
import com.speednet.framework.mybatis.core.type.EncryptTypeHandler;
import com.speednet.framework.tenant.config.TenantProperties;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.speednet.module.subscription.dal.dataobject.*;
import com.speednet.module.subscription.dal.mysql.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class SubscriptionPersistenceTest {
 JdbcDataSource ds;SqlSession session;SubscriptionMapper subscriptions;SubscriptionClientMapper clients;Object previousEncryptor;
 String migrationSection(int version) throws Exception {
  String sql=java.nio.file.Files.readString(java.nio.file.Path.of("../speednet-server/src/main/resources/db/migration/V8__xray_and_subscription_management.sql"));
  int start=sql.indexOf("-- ===== V"+version+"__");
  int end=sql.indexOf("-- ===== V"+(version+1)+"__",start);
  if(start<0)throw new IllegalStateException("Migration section not found: "+version);
  return sql.substring(start,end<0?sql.length():end);
 }
 @BeforeEach void setup() throws Exception {
  previousEncryptor=ReflectionTestUtils.getField(EncryptTypeHandler.class,"aes");ReflectionTestUtils.setField(EncryptTypeHandler.class,"aes",SecureUtil.aes("subscriptiontest".getBytes()));
  ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:sub"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
  try(var connection=ds.getConnection();var statement=connection.createStatement()) {
   statement.execute("CREATE TABLE system_menu(id BIGINT PRIMARY KEY,name VARCHAR,permission VARCHAR,type INT,sort INT,parent_id BIGINT,path VARCHAR,icon VARCHAR,component VARCHAR,component_name VARCHAR,status INT,visible BIT,keep_alive BIT,always_show BIT,creator VARCHAR,create_time TIMESTAMP,updater VARCHAR,update_time TIMESTAMP,deleted BIT)");
   statement.execute("CREATE TABLE infra_job(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR,status INT,handler_name VARCHAR,handler_param VARCHAR,cron_expression VARCHAR,retry_count INT,retry_interval INT,monitor_timeout INT,creator VARCHAR,create_time TIMESTAMP,updater VARCHAR,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE)");
   statement.execute("CREATE TABLE xray_node_assignment(id BIGINT AUTO_INCREMENT PRIMARY KEY,node_id BIGINT,server_id BIGINT,user_id BIGINT,subscription_id BIGINT,assigned_time TIMESTAMP,expiry_time TIMESTAMP,subscription_status INT,authorization_status INT,creator VARCHAR,create_time TIMESTAMP,updater VARCHAR,update_time TIMESTAMP,deleted BOOLEAN DEFAULT FALSE,tenant_id BIGINT)");
   // H2's MySQL mode does not parse MySQL bit literals; keep the production migration otherwise intact.
   String migration=migrationSection(14).replace("b'0'","0").replace("b'1'","1");
   ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(migration.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(migrationSection(15).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   statement.execute("ALTER TABLE subscription ADD COLUMN plan_id BIGINT"); statement.execute("ALTER TABLE subscription ADD COLUMN base_total_bytes BIGINT"); statement.execute("ALTER TABLE subscription ADD COLUMN extra_used_bytes BIGINT DEFAULT 0");
   String jobMigration=migrationSection(16).replace("b'0'","0");
   ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(jobMigration.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }
  var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),ds));
  GlobalConfigUtils.setGlobalConfig(config,new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO)).setMetaObjectHandler(new DefaultDBFieldHandler()));
  var interceptors=new MybatisPlusInterceptor();interceptors.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));interceptors.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));config.addInterceptor(interceptors);
  config.addMapper(SubscriptionMapper.class);config.addMapper(SubscriptionClientMapper.class);config.addMapper(SubscriptionAssignmentMapper.class);config.addMapper(SubscriptionLogMapper.class);
  session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);subscriptions=session.getMapper(SubscriptionMapper.class);clients=session.getMapper(SubscriptionClientMapper.class);TenantContextHolder.setTenantId(1L);
 }
 @AfterEach void close(){if(session!=null)session.close();TenantContextHolder.clear();ReflectionTestUtils.setField(EncryptTypeHandler.class,"aes",previousEncryptor);}
 SubscriptionDO subscription(){var now=LocalDateTime.now();return new SubscriptionDO().setRemark("订阅备注").setNumber("SN-test").setUserId(10L).setSource("admin").setStartTime(now).setExpiryTime(now.plusDays(1)).setToken("secret-token").setTokenHash("a".repeat(64));}
 @Test void migrationCreatesIndependentMenuAndEncryptedStorage() throws Exception {
  var s=subscription();subscriptions.insert(s);assertNotNull(s.getId());assertEquals("secret-token",subscriptions.selectById(s.getId()).getToken());
  try(var connection=ds.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT token FROM subscription")){assertTrue(rows.next());assertNotEquals("secret-token",rows.getString(1));}
  try(var connection=ds.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT parent_id,path FROM system_menu WHERE id=6100")){assertTrue(rows.next());assertEquals(0,rows.getLong(1));assertEquals("/subscription",rows.getString(2));}
 }
 @Test void managedJobMigrationSeedsOnceAndPreservesAdminSettings() throws Exception {
  try(var connection=ds.getConnection();var statement=connection.createStatement()) {
   try(var rows=statement.executeQuery("SELECT status,cron_expression FROM infra_job WHERE handler_name='subscriptionScanJob'")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));assertEquals("0 * * * * ?",rows.getString(2));}
   statement.executeUpdate("UPDATE infra_job SET status=2,cron_expression='0 */5 * * * ?' WHERE handler_name='subscriptionScanJob'");
   String migration=migrationSection(16).replace("b'0'","0");
   ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ByteArrayResource(migration.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   try(var rows=statement.executeQuery("SELECT status,cron_expression FROM infra_job WHERE handler_name='subscriptionScanJob'")){assertTrue(rows.next());assertEquals(2,rows.getInt(1));assertEquals("0 */5 * * * ?",rows.getString(2));assertFalse(rows.next());}
  }
 }
 @Test void tenantScopeAppliesToLockReadAndUpdate(){var s=subscription();subscriptions.insert(s);TenantContextHolder.setTenantId(2L);session.clearCache();assertNull(subscriptions.selectById(s.getId()));assertNull(subscriptions.lock(s.getId()));assertEquals(0,subscriptions.updateById(new SubscriptionDO().setId(s.getId()).setRemark("cross-tenant")));TenantContextHolder.setTenantId(1L);session.clearCache();assertEquals("订阅备注",subscriptions.selectById(s.getId()).getRemark());}
 @Test void clientsPersistUuidEncryptedAndKeepNullableOrder() throws Exception {
  var s=subscription();subscriptions.insert(s);var c=new SubscriptionClientDO().setSubscriptionId(s.getId()).setNodeId(1L).setServerId(1L).setInboundId(2L).setProtocol("vmess").setEmail("sn1s1c1").setCredential("uuid-fixture").setPublicHost("edge.example").setConnectionName("美国-纽约").setAssignedTime(LocalDateTime.now());clients.insert(c);assertEquals("uuid-fixture",clients.selectById(c.getId()).getCredential());assertNull(subscriptions.selectById(s.getId()).getOrderNo());
  try(var connection=ds.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT credential FROM subscription_client")){assertTrue(rows.next());assertNotEquals("uuid-fixture",rows.getString(1));}
 }
 @Test void logPaginationIncludesOlderRecordsAndScopesSubscriptionAndTenant() {
  var logs=session.getMapper(SubscriptionLogMapper.class);
  for(int i=0;i<105;i++)logs.insert(new SubscriptionLogDO().setSubscriptionId(1L).setAction("traffic").setMessage("record-"+i).setUploadBytes(0L).setDownloadBytes(0L).setSuccess(1));
  logs.insert(new SubscriptionLogDO().setSubscriptionId(2L).setAction("other").setMessage("other subscription").setSuccess(1));
  TenantContextHolder.setTenantId(2L);
  logs.insert(new SubscriptionLogDO().setSubscriptionId(1L).setAction("other").setMessage("other tenant").setSuccess(1));
  TenantContextHolder.setTenantId(1L);
  var req=new com.speednet.module.subscription.controller.admin.vo.SubscriptionLogPageReqVO();req.setSubscriptionId(1L);req.setPageNo(11);req.setPageSize(10);
  var query=new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubscriptionLogDO>().eq(SubscriptionLogDO::getSubscriptionId,req.getSubscriptionId()).orderByDesc(SubscriptionLogDO::getId);
  var page=logs.selectPage(req,query);
  assertEquals(105L,page.getTotal());assertEquals(5,page.getList().size());
  assertEquals("record-4",page.getList().get(0).getMessage());assertEquals("record-0",page.getList().get(4).getMessage());
  req.setPageNo(12);assertTrue(logs.selectPage(req,query).getList().isEmpty());
 }

}
