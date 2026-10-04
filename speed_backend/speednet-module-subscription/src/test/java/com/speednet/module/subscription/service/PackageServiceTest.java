package com.speednet.module.subscription.service;
import org.junit.jupiter.api.*;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.module.subscription.controller.admin.plan.PlanRequest;
import com.speednet.module.subscription.controller.admin.vo.SubscriptionCreateReqVO;
import com.speednet.module.subscription.dal.dataobject.SubscriptionDO;
import com.speednet.module.pay.api.order.PayOrderApi;
import com.speednet.module.pay.api.order.dto.*;
import com.speednet.module.pay.service.order.PayOrderService;
import java.nio.file.*;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class PackageServiceTest {
 JdbcTemplate jdbc;PackageService service;SubscriptionAccountService accounts;SubscriptionService subscriptions;PayOrderApi payments;
 Map<Long,PayOrderRespDTO> pay=new HashMap<>();Map<Long,SubscriptionDO> subs=new HashMap<>();long plan,price;
 private static String migrationSection(String name) throws Exception {
  String sql=Files.readString(Path.of("../speednet-server/src/main/resources/db/migration/V9__subscription_commerce_and_menu_cleanup.sql"));
  String begin="-- BEGIN "+name+"\n",end="-- END "+name;
  int start=sql.indexOf(begin);assertTrue(start>=0,"Missing migration section: "+name);
  int finish=sql.indexOf(end,start);assertTrue(finish>start,"Missing section end: "+name);
  return sql.substring(start+begin.length(),finish);
 }
 @BeforeEach void setup() throws Exception {
  var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:products"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TABLE subscription(id BIGINT PRIMARY KEY,tenant_id BIGINT,user_id BIGINT,source VARCHAR(32),ended_time DATETIME,expiry_time DATETIME,deleted BOOLEAN DEFAULT FALSE)");
  var sql=migrationSection("V11__subscription_products.sql");sql=sql.substring(0,sql.indexOf("INSERT INTO system_menu")).replaceAll(",\\s*ADD COLUMN", "; ALTER TABLE subscription ADD COLUMN");
  try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ByteArrayResource(sql.getBytes()));}
  var groupSql=migrationSection("V15__optional_product_fields_and_node_groups.sql");groupSql=groupSql.substring(0,groupSql.indexOf("-- Optional"));try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ByteArrayResource(groupSql.getBytes()));}
  TenantContextHolder.setTenantId(1L);accounts=new SubscriptionAccountService(jdbc);subscriptions=mock(SubscriptionService.class);payments=mock(PayOrderApi.class);
  service=new PackageService(jdbc,subscriptions,accounts,new DataSourceTransactionManager(ds),payments,mock(PayOrderService.class));
  when(payments.createOrder(any())).thenAnswer(i->{var r=(PayOrderCreateReqDTO)i.getArgument(0);long id=pay.size()+1;pay.put(id,new PayOrderRespDTO().setId(id).setPrice(r.getPrice()).setMerchantOrderId(r.getMerchantOrderId()).setStatus(0));return id;});
  when(payments.getOrder(anyLong())).thenAnswer(i->pay.get(i.getArgument(0)));
  when(subscriptions.require(anyLong())).thenAnswer(i->subs.get(i.getArgument(0)));
  when(subscriptions.create(any())).thenAnswer(i->{var r=(SubscriptionCreateReqVO)i.getArgument(0);long id=subs.size()+1;var s=new SubscriptionDO().setId(id).setUserId(r.getUserId()).setExpiryTime(r.getExpiryTime()).setStartTime(r.getStartTime()).setEndedTime(null).setUnlimited(false);subs.put(id,s);jdbc.update("INSERT INTO subscription(id,tenant_id,user_id,expiry_time) VALUES(?,1,?,?)",id,r.getUserId(),r.getExpiryTime());accounts.bind(r.getUserId(),id);return id;});
  doAnswer(i->{long id=i.getArgument(0);var s=subs.get(id);s.setPlanId(i.getArgument(1)).setExpiryTime(i.getArgument(7));jdbc.update("UPDATE subscription SET plan_id=?,expiry_time=? WHERE id=?",s.getPlanId(),s.getExpiryTime(),id);return null;}).when(subscriptions).commercialChange(anyLong(),anyLong(),anyLong(),anyString(),anyInt(),anyString(),anyInt(),any(),anyBoolean(),any());
  plan=createPlan("基础",1,3000,0);price=price(plan,"period");
 }
 @AfterEach void close(){TenantContextHolder.clear();}
 long createPlan(String name,int level,int amount,int capacity){var r=new PlanRequest();r.setName(name);r.setLevel(level);r.setEnabled(true);r.setVisible(true);r.setTotalBytes(1000L);r.setCapacityLimit(capacity);var p=new PlanRequest.Price();p.setName("月付");p.setPrice(amount);var traffic=new PlanRequest.Price();traffic.setKind("traffic");traffic.setName("流量包");traffic.setPrice(500);traffic.setBytes(500);r.setPrices(List.of(p,traffic));var a=new SubscriptionCreateReqVO.Assignment();a.setNodeId(1L);a.setServerId(1L);a.setInboundId(1L);a.setPublicHost("example.com");r.setAssignments(List.of(a));return service.save(r);}
 long price(long plan,String kind){return jdbc.queryForObject("SELECT id FROM subscription_plan_price WHERE plan_id=? AND kind=?",Long.class,plan,kind);}
 long id(Map<String,Object> o){return ((Number)o.get("id")).longValue();}
 void paid(long id){long payId=jdbc.queryForObject("SELECT pay_order_id FROM subscription_purchase WHERE id=?",Long.class,id);pay.get(payId).setStatus(10).setSuccessTime(LocalDateTime.now());service.refresh(id,10);long sid=jdbc.queryForObject("SELECT subscription_id FROM subscription_purchase WHERE id=?",Long.class,id);subs.get(sid).setPlanId(jdbc.queryForObject("SELECT plan_id FROM subscription WHERE id=?",Long.class,sid));}
 @Test void repeatedPaymentAndCheckoutDoNotCreateAnotherSubscription(){var o=service.checkout(10,price,"first-order-key","127.0.0.1");paid(id(o));service.refresh(id(o),10);assertEquals(id(o),id(service.checkout(10,price,"first-order-key","127.0.0.1")));verify(subscriptions,times(1)).create(any());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM subscription_account",Integer.class));}
 @Test void upgradeDeductsUnusedOldValueAndKeepsOneSubscription(){var o=service.checkout(10,price,"first-order-key","127.0.0.1");paid(id(o));long next=createPlan("高级",2,6000,0);var upgrade=service.checkout(10,price(next,"period"),"upgrade-order-key","127.0.0.1");assertTrue(((Number)upgrade.get("credit_amount")).longValue()>2900);assertEquals(6000,((Number)upgrade.get("original_amount")).intValue());paid(id(upgrade));verify(subscriptions,times(1)).create(any());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM subscription",Integer.class));}
 @Test void expiredSubscriptionCanBuyAnyPlanAsNew() {
  var first=service.checkout(10,price,"first-order-key","127.0.0.1");paid(id(first));
  var expired=LocalDateTime.now().minusSeconds(1);subs.get(1L).setExpiryTime(expired);
  jdbc.update("UPDATE subscription SET expiry_time=? WHERE id=1",expired);
  long lower=createPlan("新套餐",0,2000,0);
  var next=service.checkout(10,price(lower,"period"),"expired-new-key","127.0.0.1");
  assertEquals("new",next.get("kind"));assertEquals(0,((Number)next.get("credit_amount")).intValue());paid(id(next));
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM subscription",Integer.class));
  assertEquals(2L,((Number)accounts.lock(10).get("current_subscription_id")).longValue());
  assertFalse(accounts.allows(10,1));assertTrue(accounts.allows(10,2));
 }
 @Test void expiredSamePlanIsNewPurchaseEvenWhenRenewalDisabled() {
  var first=service.checkout(10,price,"first-order-key","127.0.0.1");paid(id(first));
  var expired=LocalDateTime.now().minusSeconds(1);subs.get(1L).setExpiryTime(expired);
  jdbc.update("UPDATE subscription SET expiry_time=? WHERE id=1",expired);
  jdbc.update("UPDATE subscription_plan SET renew_enabled=0 WHERE id=?",plan);
  var next=service.checkout(10,price,"expired-same-key","127.0.0.1");assertEquals("new",next.get("kind"));paid(id(next));
  verify(subscriptions,times(2)).create(any());
 }
 @Test void renewalOrderCannotRestoreSubscriptionAfterExpiry(){
  var first=service.checkout(10,price,"first-order-key","127.0.0.1");paid(id(first));
  var renewal=service.checkout(10,price,"renew-before-expiry","127.0.0.1");assertEquals("renew",renewal.get("kind"));
  var expired=LocalDateTime.now().minusSeconds(1);subs.get(1L).setExpiryTime(expired);jdbc.update("UPDATE subscription SET expiry_time=? WHERE id=1",expired);
  long payId=jdbc.queryForObject("SELECT pay_order_id FROM subscription_purchase WHERE id=?",Long.class,id(renewal));pay.get(payId).setStatus(10).setSuccessTime(LocalDateTime.now());
  assertEquals("failed",service.refresh(id(renewal),10).get("status"));
  verify(subscriptions,never()).commercialChange(anyLong(),anyLong(),anyLong(),anyString(),anyInt(),anyString(),anyInt(),any(),anyBoolean(),any());
 }
 @Test void anotherUserCannotReadOrCancelOrder(){var o=service.checkout(10,price,"first-order-key","127.0.0.1");assertThrows(RuntimeException.class,()->service.refresh(id(o),11));assertThrows(RuntimeException.class,()->service.cancel(id(o),11));}
 @Test void capacityIncludesPendingOrders(){long limited=createPlan("限量",2,5000,1);service.checkout(10,price(limited,"period"),"capacity-first-key","127.0.0.1");assertThrows(RuntimeException.class,()->service.checkout(11,price(limited,"period"),"capacity-next-key","127.0.0.1"));}
 @Test void expiredSubscriptionCannotBuyExtraTraffic(){var o=service.checkout(10,price,"first-order-key","127.0.0.1");paid(id(o));subs.get(1L).setExpiryTime(LocalDateTime.now().minusSeconds(1));assertThrows(RuntimeException.class,()->service.checkout(10,price(plan,"traffic"),"traffic-order-key","127.0.0.1"));}
 @Test void zeroPriceOrderUsesSameIdempotentFulfilment(){long free=createPlan("免费",1,0,0);var o=service.checkout(10,price(free,"period"),"free-order-key","127.0.0.1");service.refresh(id(o),10);service.refresh(id(o),10);verify(payments,never()).createOrder(any());verify(subscriptions,times(1)).create(any());}
 @Test void invalidPaidAmountRemainsFailedOnRepeatedRetry(){var o=service.checkout(10,price,"invalid-paid-key","127.0.0.1");long payId=jdbc.queryForObject("SELECT pay_order_id FROM subscription_purchase WHERE id=?",Long.class,id(o));pay.get(payId).setStatus(10).setPrice(1);assertEquals("failed",service.refresh(id(o),10).get("status"));assertEquals("failed",service.refresh(id(o),10).get("status"));verify(subscriptions,never()).create(any());}
 @Test void latePaymentOfCancelledOrderDoesNotGrantEntitlements(){var o=service.checkout(10,price,"late-paid-order-key","127.0.0.1");service.cancel(id(o),10);long payId=jdbc.queryForObject("SELECT pay_order_id FROM subscription_purchase WHERE id=?",Long.class,id(o));pay.get(payId).setStatus(10);assertEquals("failed",service.refresh(id(o),10).get("status"));service.refresh(id(o),10);verify(subscriptions,never()).create(any());}
 @Test void endingConflictKeepsOnlyTheRemainingCurrentAuthorization(){jdbc.update("INSERT INTO subscription(id,tenant_id,user_id,expiry_time) VALUES(100,1,10,DATEADD('DAY',1,CURRENT_TIMESTAMP)),(101,1,10,DATEADD('DAY',1,CURRENT_TIMESTAMP))");accounts.lock(10,true);assertFalse(accounts.allows(10,100));assertFalse(accounts.allows(10,101));jdbc.update("UPDATE subscription SET ended_time=CURRENT_TIMESTAMP WHERE id=100");accounts.changed(10);assertFalse(accounts.allows(10,100));assertTrue(accounts.allows(10,101));}
 @Test void minimalDraftNeedsNoPriceOrNodes(){var r=new PlanRequest();r.setName("草稿");r.setTotalBytes(0L);long id=service.save(r);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM subscription_plan_price WHERE plan_id=?",Integer.class,id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM subscription_plan_node WHERE plan_id=?",Integer.class,id));}
 @Test void v2FieldsAcceptNullOptionalPricesAndZeroIsFree(){var r=com.speednet.framework.common.util.json.JsonUtils.parseObject("{\"name\":\"V2套餐\",\"transfer_enable\":0.5,\"content\":null,\"show\":true,\"renew\":null,\"capacity_limit\":null,\"month_price\":0,\"quarter_price\":null,\"reset_traffic_method\":0}",PlanRequest.class);assertEquals(536870912L,r.getTotalBytes());assertEquals("",r.getDescription());assertEquals("monthly_first",r.getResetMode());assertTrue(r.isRenewEnabled());assertTrue(r.isEnabled());assertTrue(jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator().validate(r).isEmpty());long id=service.save(r);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM subscription_plan_price WHERE plan_id=?",Integer.class,id));assertEquals(0,jdbc.queryForObject("SELECT price FROM subscription_plan_price WHERE plan_id=?",Integer.class,id));}
 @Test void permissionGroupSuppliesPurchaseNodesWithoutProductSpecificBinding(){var group=new PlanRequest.NodeGroup();group.setName("可用节点组");var a=new SubscriptionCreateReqVO.Assignment();a.setNodeId(7L);a.setServerId(1L);a.setInboundId(1L);a.setPublicHost("example.com");group.setAssignments(List.of(a));long gid=service.saveGroup(group);var r=new PlanRequest();r.setName("组套餐");r.setTotalBytes(1000L);r.setGroupId(gid);r.setEnabled(true);var p=new PlanRequest.Price();p.setPrice(3000);r.setPrices(List.of(p));long id=service.save(r);var o=service.checkout(10,price(id,"period"),"group-order-key","127.0.0.1");paid(id(o));verify(subscriptions).create(argThat(req->req.getAssignments().getFirst().getNodeId()==7L));}
 @Test void missingNodesAreBlockedBeforeCreatingPayment(){var r=new PlanRequest();r.setName("无节点");r.setTotalBytes(1000L);r.setEnabled(true);var p=new PlanRequest.Price();p.setPrice(1000);r.setPrices(List.of(p));long id=service.save(r);assertThrows(RuntimeException.class,()->service.checkout(10,price(id,"period"),"empty-node-key","127.0.0.1"));verify(payments,never()).createOrder(any());}
 @Test void resetPriceIsIdempotentAndDoesNotCreateTrafficPacks(){var o=service.checkout(10,price,"first-order-key","127.0.0.1");paid(id(o));var v=new PlanRequest.Price();v.setKind("reset");v.setPrice(500);jdbc.update("INSERT INTO subscription_plan_price(tenant_id,plan_id,name,kind,price) VALUES(1,?,'重置','reset',500)",plan);long p=price(plan,"reset");var reset=service.checkout(10,p,"reset-order-key","127.0.0.1");paid(id(reset));service.refresh(id(reset),10);assertEquals("reset",reset.get("kind"));verify(subscriptions,times(1)).commercialReset(1L);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM subscription_traffic_pack",Integer.class));}
 @Test void unsupportedConstraintsAreNotSilentlySaved(){assertThrows(RuntimeException.class,()->com.speednet.framework.common.util.json.JsonUtils.parseObject("{\"name\":\"限速\",\"transfer_enable\":1,\"speed_limit\":10}",PlanRequest.class));assertThrows(RuntimeException.class,()->com.speednet.framework.common.util.json.JsonUtils.parseObject("{\"name\":\"永久\",\"transfer_enable\":1,\"onetime_price\":0}",PlanRequest.class));}
 @Test void foreignTenantCannotBuyPrice(){TenantContextHolder.setTenantId(2L);assertThrows(RuntimeException.class,()->service.checkout(10,price,"foreign-tenant-key","127.0.0.1"));}
 @Test void conflictsMustBeResolvedWithoutDeletingHistory(){jdbc.update("INSERT INTO subscription(id,tenant_id,user_id,expiry_time) VALUES(100,1,10,DATEADD('DAY',1,CURRENT_TIMESTAMP)),(101,1,10,DATEADD('DAY',1,CURRENT_TIMESTAMP))");assertThrows(RuntimeException.class,()->service.checkout(10,price,"conflict-order-key","127.0.0.1"));}
}
