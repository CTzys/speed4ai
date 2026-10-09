package com.speednet.module.subscription.service;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.speednet.framework.common.exception.ServiceException;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.module.member.api.user.MemberUserApi;
import com.speednet.module.member.api.user.dto.MemberUserRespDTO;
import com.speednet.module.subscription.controller.admin.vo.*;
import com.speednet.module.subscription.dal.dataobject.*;
import com.speednet.module.subscription.dal.mysql.*;
import com.speednet.module.xray.service.node.XrayNodeService;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeDO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.LocalDateTime;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class SubscriptionServiceTest {
 SubscriptionService service;SubscriptionMapper subscriptions;SubscriptionClientMapper clients;SubscriptionAssignmentMapper assignments;SubscriptionLogMapper logs;SubscriptionOrderMapper orders;MemberUserApi users;SubscriptionGateway gateway;XrayNodeService nodes;
 SubscriptionDO s;SubscriptionClientDO c;
 @BeforeEach void setup(){
  service=spy(new SubscriptionService());doReturn(true).when(service).enqueue(anyLong());subscriptions=mock(SubscriptionMapper.class);clients=mock(SubscriptionClientMapper.class);assignments=mock(SubscriptionAssignmentMapper.class);logs=mock(SubscriptionLogMapper.class);orders=mock(SubscriptionOrderMapper.class);users=mock(MemberUserApi.class);gateway=mock(SubscriptionGateway.class);nodes=mock(XrayNodeService.class);
  for(var entry:Map.of("subscriptions",subscriptions,"clients",clients,"assignments",assignments,"logs",logs,"orders",orders,"users",users,"gateway",gateway,"nodes",nodes).entrySet())ReflectionTestUtils.setField(service,entry.getKey(),entry.getValue());
  var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());ReflectionTestUtils.setField(service,"transactionManager",manager);ReflectionTestUtils.setField(service,"tenants",mock(com.speednet.framework.common.biz.system.tenant.TenantCommonApi.class));
  for(Class<?> type:List.of(SubscriptionDO.class,SubscriptionClientDO.class,SubscriptionAssignmentDO.class))TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"subscription-tests"),type);
  var accounts=mock(SubscriptionAccountService.class);when(accounts.allows(anyLong(),anyLong())).thenReturn(true);ReflectionTestUtils.setField(service,"accounts",accounts);
  var ds=new org.h2.jdbcx.JdbcDataSource();ds.setURL("jdbc:h2:mem:pack"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);jdbc.execute("CREATE TABLE subscription_traffic_pack(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,subscription_id BIGINT,purchase_id BIGINT UNIQUE,total_bytes BIGINT,remaining_bytes BIGINT)");ReflectionTestUtils.setField(service,"jdbc",jdbc);
  var clashRules=mock(ClashRuleService.class);when(clashRules.effective(anyLong())).thenReturn(ClashRuleService.DEFAULT_RULES);ReflectionTestUtils.setField(service,"clashRules",clashRules);
  TenantContextHolder.setTenantId(1L);
  s=new SubscriptionDO().setId(1L).setUserId(10L).setNumber("SN-test").setStartTime(LocalDateTime.now().minusDays(1)).setExpiryTime(LocalDateTime.now().plusDays(30)).setPaused(false).setUnlimited(false).setTotalBytes(1000L).setUsedUpload(0L).setUsedDownload(0L).setLifetimeUpload(0L).setLifetimeDownload(0L).setTrafficMode("both").setResetMode("none").setResetIntervalDays(30).setNodeLimit(1).setStatus(1).setSyncStatus(0).setLastError("");s.setTenantId(1L);
  c=new SubscriptionClientDO().setId(1L).setSubscriptionId(1L).setNodeId(1L).setServerId(1L).setInboundId(1L).setEmail("sn1s1c1").setCredential("original-uuid").setProtocol("vmess").setRemoteCreated(true).setReleased(false).setSyncStatus(2).setNodeVersion(1).setSampleUpload(0L).setSampleDownload(0L).setUsedUpload(0L).setUsedDownload(0L);c.setTenantId(1L);
  when(subscriptions.lock(1L)).thenReturn(s);when(subscriptions.selectById(1L)).thenReturn(s);when(clients.selectList(any())).thenAnswer(i->new ArrayList<>(List.of(c)));
  when(users.getUser(10L)).thenReturn(new MemberUserRespDTO().setId(10L).setStatus(0).setNickname("会员"));
  when(gateway.traffic(c)).thenReturn(new SubscriptionGateway.Traffic(0,0));when(gateway.sync(eq(s),eq(c),anyBoolean())).thenReturn("vmess://fixture");when(nodes.require(1L)).thenReturn(new XrayNodeDO().setConfigVersion(1));
 }
 private SubscriptionCreateReqVO.Assignment entry(Long serverId, Long inboundId) {
  ReflectionTestUtils.setField(service,"regions",mock(com.speednet.module.xray.service.node.XrayRegionService.class));
  ReflectionTestUtils.setField(service,"cities",mock(com.speednet.module.xray.service.node.XrayCityService.class));
  when(nodes.require(1L)).thenReturn(new XrayNodeDO().setShelfStatus(1));
  when(gateway.inbound(serverId,inboundId)).thenReturn(Map.of("enable",true,"protocol","vmess","port",8080,"settings",Map.of(),"streamSettings",Map.of()));
  doAnswer(i->{((SubscriptionClientDO)i.getArgument(0)).setId(99L);return 1;}).when(clients).insert(any(SubscriptionClientDO.class));
  return new SubscriptionCreateReqVO.Assignment().setNodeId(1L).setServerId(serverId).setInboundId(inboundId).setPublicHost("entry.example.com");
 }
 @Test void existingExitCanGainNewServerEntryAtNodeLimit() {
  service.assign(1L,entry(2L,1L));
  verify(clients).insert(argThat((SubscriptionClientDO added)->added.getNodeId().equals(1L)&&added.getServerId().equals(2L)&&added.getInboundId().equals(1L)));
  assertFalse(c.getReleased());assertEquals("original-uuid",c.getCredential());
  verify(assignments).insert(any(SubscriptionAssignmentDO.class));
 }
 @Test void existingExitCanGainSecondInboundOnSameServer() {
  service.assign(1L,entry(1L,2L));
  verify(assignments).insert(any(SubscriptionAssignmentDO.class));
  verify(assignments,never()).updateById(any(SubscriptionAssignmentDO.class));
 }
 @Test void sameExitAndEntryCannotBeAssignedTwice() {
  assertThrows(ServiceException.class,()->service.assign(1L,entry(1L,1L)));
  verify(clients,never()).insert(any(SubscriptionClientDO.class));
 }
 @AfterEach void close(){service.close();TenantContextHolder.clear();}
 @Test void reconciliationUsesIncrementalSamplesAndReusesUuid(){when(gateway.traffic(c)).thenReturn(new SubscriptionGateway.Traffic(20,40));service.reconcile(1L);assertEquals(20,s.getUsedUpload());assertEquals(40,s.getUsedDownload());assertEquals(60,SubscriptionPolicy.used(s));service.reconcile(1L);assertEquals(60,SubscriptionPolicy.used(s));assertEquals("original-uuid",c.getCredential());verify(gateway,times(2)).sync(s,c,true);assertEquals(2,s.getSyncStatus());}
 @Test void quotaExhaustionRevokesInsteadOfEnabling(){when(gateway.traffic(c)).thenReturn(new SubscriptionGateway.Traffic(400,600));service.reconcile(1L);assertEquals(3,s.getStatus());verify(gateway).sync(s,c,false);}
 @Test void expiredAndEndedSubscriptionsRevokeEvenIfTrafficCollectionFails(){s.setExpiryTime(LocalDateTime.now().minusSeconds(1));when(gateway.traffic(c)).thenThrow(new IllegalStateException("offline"));service.reconcile(1L);assertEquals(4,s.getStatus());verify(gateway).sync(s,c,false);assertEquals(3,s.getSyncStatus());s.setEndedTime(LocalDateTime.now());service.reconcile(1L);assertEquals(5,s.getStatus());}
 @Test void disabledMemberFailsClosed(){when(users.getUser(10L)).thenReturn(new MemberUserRespDTO().setStatus(1));service.reconcile(1L);assertEquals(2,s.getStatus());verify(gateway).sync(s,c,false);}
 @Test void trafficFailureDoesNotLeaveClientEnabled(){when(gateway.traffic(c)).thenThrow(new IllegalStateException("offline"));service.reconcile(1L);verify(gateway).sync(s,c,false);assertEquals(3,s.getSyncStatus());assertNull(s.getLastTrafficTime());}
 @Test void remoteFailureIsVisibleAndAssignmentNotAuthorized(){when(gateway.sync(s,c,true)).thenThrow(new IllegalStateException("readback mismatch"));service.reconcile(1L);assertEquals(3,s.getSyncStatus());assertEquals(3,c.getSyncStatus());assertTrue(s.getLastError().contains("readback mismatch"));verify(assignments).update(isNull(),any());}
 @Test void resetCapturesOldUsageBeforeStartingNewCycle(){when(gateway.traffic(c)).thenReturn(new SubscriptionGateway.Traffic(20,30));var req=new SubscriptionActionReqVO().setId(1L).setAction("reset-traffic");service.action(req);assertEquals(0,s.getUsedUpload());assertEquals(0,s.getUsedDownload());assertEquals(20,s.getLifetimeUpload());assertEquals(30,s.getLifetimeDownload());assertEquals(20,c.getSampleUpload());assertEquals("original-uuid",c.getCredential());}
 @Test void resetRefusesOfflineCounters(){when(gateway.traffic(c)).thenThrow(new IllegalStateException("offline"));assertThrows(ServiceException.class,()->service.action(new SubscriptionActionReqVO().setId(1L).setAction("reset-traffic")));verify(clients,never()).update(isNull(),any());}
 @Test void endedSubscriptionsCannotBeExtended(){s.setEndedTime(LocalDateTime.now());assertThrows(ServiceException.class,()->service.action(new SubscriptionActionReqVO().setId(1L).setAction("extend").setDays(10)));}
 @Test void cannotExtendHistoricalSubscriptionWhenAnotherIsValid(){
  s.setExpiryTime(LocalDateTime.now().minusDays(1));
  var other=new SubscriptionDO().setId(2L).setUserId(10L).setExpiryTime(LocalDateTime.now().plusDays(30));
  when(subscriptions.selectList(any())).thenReturn(List.of(s,other));
  assertThrows(ServiceException.class,()->service.action(new SubscriptionActionReqVO().setId(1L).setAction("extend").setDays(30)));
  verify(subscriptions,never()).updateById(any(SubscriptionDO.class));
 }
 @Test void pendingSubscriptionDoesNotDeployBeforeStart(){s.setStartTime(LocalDateTime.now().plusDays(1));c.setRemoteCreated(false).setNodeVersion(0);service.reconcile(1L);assertEquals(0,s.getStatus());verify(gateway,never()).sync(any(),any(),anyBoolean());verifyNoInteractions(nodes);}
 @Test void managedScanRestoresTenantContextAndReportsPartialSubmission(){
  var second=new SubscriptionDO().setId(2L);second.setTenantId(2L);
  var third=new SubscriptionDO().setId(3L);third.setTenantId(3L);
  when(subscriptions.selectList(any())).thenAnswer(i->{assertTrue(TenantContextHolder.isIgnore());return List.of(s,second,third);});
  doAnswer(i->{Long id=i.getArgument(0);assertEquals(id,TenantContextHolder.getRequiredTenantId());assertFalse(TenantContextHolder.isIgnore());if(id==3L)throw new IllegalStateException("submission failed");return id==1L;}).when(service).enqueue(anyLong());
  TenantContextHolder.setTenantId(42L);
  var result=service.scan();assertEquals(new SubscriptionService.ScanResult(3,1,1,1),result);
  assertEquals(42L,TenantContextHolder.getRequiredTenantId());assertFalse(TenantContextHolder.isIgnore());
  verifyNoInteractions(gateway);
 }
 @Test void credentialsRequireClientOwnership(){when(clients.selectById(1L)).thenReturn(c);assertEquals("original-uuid",service.credentials(1L,1L).get("credential"));when(clients.selectById(2L)).thenReturn(new SubscriptionClientDO().setSubscriptionId(2L));assertThrows(ServiceException.class,()->service.credentials(1L,2L));}
 @Test void explicitCredentialResetRevokesOldCredentialAndSchedulesNewOne(){when(clients.selectById(1L)).thenReturn(c);service.resetCredential(1L,1L);assertNotEquals("original-uuid",c.getCredential());assertEquals(1,c.getNodeVersion());assertFalse(c.getRemoteCreated());verify(gateway).sync(s,c,false);verify(gateway).removeClient(c);assertEquals(0,s.getSyncStatus());}
 @Test void clientCannotBeReleasedThroughAnotherSubscription(){when(clients.selectById(99L)).thenReturn(new SubscriptionClientDO().setSubscriptionId(2L));assertThrows(ServiceException.class,()->service.release(1L,99L));verify(clients,never()).updateById(any(SubscriptionClientDO.class));}
 @Test void feedRejectsExpiredOrUnsynchronizedSubscriptions(){String token=SubscriptionPolicy.token();when(subscriptions.selectOne(any())).thenReturn(s);s.setExpiryTime(LocalDateTime.now().minusSeconds(1));assertThrows(ServiceException.class,()->service.feed(token));s.setExpiryTime(LocalDateTime.now().plusDays(1)).setSyncStatus(3);assertThrows(ServiceException.class,()->service.feed(token));}
 @Test void successfulFeedIncludesOnlyEnabledAssignments(){when(subscriptions.selectOne(any())).thenReturn(s);s.setSyncStatus(2);c.setConnectionUri("vmess://fixture");var feed=service.feed(SubscriptionPolicy.token());assertEquals("vmess://fixture",new String(Base64.getDecoder().decode(feed.content())));c.setReleased(true);assertThrows(ServiceException.class,()->service.feed(SubscriptionPolicy.token()));}
 @Test void firstSyncCreatesClientBeforeCollectingAndEnabling(){
  c.setRemoteCreated(false).setNodeVersion(0);
  service.reconcile(1L);
  var order=inOrder(gateway);
  order.verify(gateway).prepareClient(s,c);
  order.verify(gateway).traffic(c);
  order.verify(gateway).sync(s,c,true);
  assertEquals(2,s.getSyncStatus());
 }
 @Test void failedClientCreationCannotEnableSubscription(){
  c.setRemoteCreated(false).setNodeVersion(0);
  doThrow(new IllegalStateException("creation failed")).when(gateway).prepareClient(s,c);
  service.reconcile(1L);
  verify(gateway,never()).sync(s,c,true);
  assertEquals(3,s.getSyncStatus());assertTrue(s.getLastError().contains("创建失败"));
 }
 @Test void renewalPreservesSupplementalBalanceAndExtendsExpiry(){
  s.setPlanId(7L).setBaseTotalBytes(1000L).setExtraUsedBytes(0L).setTotalBytes(1500L);
  var jdbc=(org.springframework.jdbc.core.JdbcTemplate)ReflectionTestUtils.getField(service,"jdbc");jdbc.update("INSERT INTO subscription_traffic_pack(tenant_id,subscription_id,purchase_id,total_bytes,remaining_bytes) VALUES(1,1,1,500,500)");
  var expiry=s.getExpiryTime().plusMonths(1);service.commercialChange(1L,7L,1000L,"monthly",30,"both",1,expiry,false,null);
  assertEquals(expiry,s.getExpiryTime());assertEquals(500L,service.packRemaining(s));assertEquals(1500L,s.getTotalBytes());
 }
 @Test void expiredSubscriptionCannotBeRestoredByCommercialRenewal(){
  s.setBaseTotalBytes(1000L).setExtraUsedBytes(0L).setTotalBytes(1500L).setExpiryTime(LocalDateTime.now().minusMinutes(1));
  var jdbc=(org.springframework.jdbc.core.JdbcTemplate)ReflectionTestUtils.getField(service,"jdbc");jdbc.update("INSERT INTO subscription_traffic_pack(tenant_id,subscription_id,purchase_id,total_bytes,remaining_bytes) VALUES(1,1,1,500,500)");
  assertThrows(ServiceException.class,()->service.commercialChange(1L,7L,1000L,"monthly",30,"both",1,LocalDateTime.now().plusMonths(1),true,null));
  verify(gateway,never()).traffic(any());
 }
 @Test void cycleResetPreservesOnlyUnconsumedSupplement(){
  s.setBaseTotalBytes(100L).setExtraUsedBytes(0L).setTotalBytes(150L).setResetMode("monthly").setNextResetTime(LocalDateTime.now().minusSeconds(1));
  var jdbc=(org.springframework.jdbc.core.JdbcTemplate)ReflectionTestUtils.getField(service,"jdbc");jdbc.update("INSERT INTO subscription_traffic_pack(tenant_id,subscription_id,purchase_id,total_bytes,remaining_bytes) VALUES(1,1,1,50,50)");
  when(gateway.traffic(c)).thenReturn(new SubscriptionGateway.Traffic(0,120));service.reconcile(1L);
  assertEquals(30L,service.packRemaining(s));assertEquals(130L,s.getTotalBytes());assertEquals(0L,s.getUsedDownload());assertEquals(0L,s.getExtraUsedBytes());
 }
 @Test void clashFeedUsesSameEntitlementChecksAndPreservesLegacyFeed(){
  when(subscriptions.selectOne(any())).thenReturn(s);s.setSyncStatus(2).setToken(SubscriptionPolicy.token());
  c.setConnectionUri("vless://test-uuid@edge.example:443?type=ws&security=tls&sni=edge.example&path=%2Fws#Node");
  var feed=service.feed(s.getToken(),"clash");Map<?,?> yaml=new org.yaml.snakeyaml.Yaml().load(feed.content());
  assertEquals(1,((List<?>)yaml.get("proxies")).size());assertEquals(s.getTotalBytes(),feed.total());
  assertEquals(c.getConnectionUri(),new String(Base64.getDecoder().decode(service.feed(s.getToken()).content())));
  var rules=(ClashRuleService)ReflectionTestUtils.getField(service,"clashRules");
  when(rules.effective(10L)).thenReturn(List.of("DOMAIN-SUFFIX,example.com,DIRECT","MATCH,SpeedNet"));
  Map<?,?> updated=new org.yaml.snakeyaml.Yaml().load(service.feed(s.getToken(),"clash").content());
  assertEquals(List.of("DOMAIN-SUFFIX,example.com,DIRECT","MATCH,SpeedNet"),updated.get("rules"));
  s.setPaused(true);assertThrows(ServiceException.class,()->service.feed(s.getToken(),"clash"));
 }

}
