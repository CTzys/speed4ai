package com.speednet.module.subscription.service;

import com.speednet.framework.common.exception.ErrorCode;
import com.speednet.framework.common.util.json.JsonUtils;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.framework.tenant.core.util.TenantUtils;
import com.speednet.module.subscription.controller.admin.plan.PlanRequest;
import com.speednet.module.subscription.controller.admin.vo.SubscriptionCreateReqVO;
import com.speednet.module.subscription.dal.dataobject.SubscriptionDO;
import com.speednet.module.pay.api.order.PayOrderApi;
import com.speednet.module.pay.api.order.dto.PayOrderCreateReqDTO;
import com.speednet.module.pay.service.order.PayOrderService;
import com.speednet.module.pay.controller.admin.order.vo.PayOrderSubmitReqVO;
import com.speednet.module.pay.controller.admin.order.vo.PayOrderSubmitRespVO;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.*;
import java.util.*;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@lombok.extern.slf4j.Slf4j
public class PackageService {
 private final JdbcTemplate jdbc; private final SubscriptionService subscriptions; private final SubscriptionAccountService accounts;
 private final TransactionTemplate tx; private final PayOrderApi payments; private final PayOrderService paymentService;
 public PackageService(JdbcTemplate jdbc,SubscriptionService subscriptions,SubscriptionAccountService accounts,PlatformTransactionManager manager,PayOrderApi payments,PayOrderService paymentService) {this.jdbc=jdbc;this.subscriptions=subscriptions;this.accounts=accounts;this.tx=new TransactionTemplate(manager);this.payments=payments;this.paymentService=paymentService;}
 private long tenant(){return TenantContextHolder.getRequiredTenantId();}
 private RuntimeException bad(String message){return exception(new ErrorCode(1_013_000_002,message));}
 private long num(Map<String,Object> row,String key){var n=row.get(key);return n==null?0:((Number)n).longValue();}
 private boolean flag(Map<String,Object> row,String key){return Boolean.TRUE.equals(row.get(key))||row.get(key) instanceof Number n&&n.intValue()!=0;}
 private LocalDateTime time(Map<String,Object> row,String key){var v=row.get(key);return v instanceof java.sql.Timestamp t?t.toLocalDateTime():(LocalDateTime)v;}
 private Map<String,Object> plan(long id){var rows=jdbc.queryForList("SELECT * FROM subscription_plan WHERE tenant_id=? AND id=?",tenant(),id);if(rows.isEmpty())throw bad("套餐不存在");return rows.getFirst();}
 public List<Map<String,Object>> plans(boolean admin){var result=jdbc.queryForList("SELECT * FROM subscription_plan WHERE tenant_id=?"+(admin?"":" AND visible=1")+" ORDER BY sort,id",tenant());for(var p:result){p.put("prices",jdbc.queryForList("SELECT * FROM subscription_plan_price WHERE tenant_id=? AND plan_id=?"+(admin?"":" AND enabled=1")+" ORDER BY kind,months,id",tenant(),p.get("id")));if(admin)p.put("assignments",bindings("subscription_plan_node","plan_id",num(p,"id")));}return result;}
 private List<SubscriptionCreateReqVO.Assignment> bindings(String table,String key,long id){return jdbc.query("SELECT * FROM "+table+" WHERE tenant_id=? AND "+key+"=? ORDER BY id",(rs,n)->{var a=new SubscriptionCreateReqVO.Assignment();a.setNodeId(rs.getLong("node_id"));a.setServerId(rs.getLong("server_id"));a.setInboundId(rs.getLong("inbound_id"));a.setPublicHost(rs.getString("public_host"));return a;},tenant(),id);}
 private List<SubscriptionCreateReqVO.Assignment> nodes(long id){var direct=bindings("subscription_plan_node","plan_id",id);if(!direct.isEmpty())return direct;var p=plan(id);return p.get("group_id")==null?direct:bindings("subscription_node_group_binding","group_id",num(p,"group_id")).stream().limit(num(p,"node_limit")).toList();}
 public List<Map<String,Object>> groups(){var list=jdbc.queryForList("SELECT * FROM subscription_node_group WHERE tenant_id=? ORDER BY id",tenant());for(var g:list)g.put("assignments",bindings("subscription_node_group_binding","group_id",num(g,"id")));return list;}
 public long saveGroup(PlanRequest.NodeGroup r){return tx.execute(st->{long id;if(r.getId()==null){jdbc.update("INSERT INTO subscription_node_group(tenant_id,name) VALUES(?,?)",tenant(),r.getName());id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}else{id=r.getId();if(jdbc.update("UPDATE subscription_node_group SET name=? WHERE tenant_id=? AND id=?",r.getName(),tenant(),id)==0)throw bad("节点权限组不存在");}if(r.getAssignments().stream().map(SubscriptionCreateReqVO.Assignment::getNodeId).distinct().count()!=r.getAssignments().size())throw bad("权限组节点不能重复");jdbc.update("DELETE FROM subscription_node_group_binding WHERE tenant_id=? AND group_id=?",tenant(),id);for(var a:r.getAssignments())jdbc.update("INSERT INTO subscription_node_group_binding(tenant_id,group_id,node_id,server_id,inbound_id,public_host) VALUES(?,?,?,?,?,?)",tenant(),id,a.getNodeId(),a.getServerId(),a.getInboundId(),a.getPublicHost());return id;});}
 public long save(PlanRequest r){return tx.execute(status->{
  if(r.getAssignments().size()>r.getNodeLimit())throw bad("节点配置超过套餐节点上限");
  if(r.getAssignments().stream().map(SubscriptionCreateReqVO.Assignment::getNodeId).distinct().count()!=r.getAssignments().size())throw bad("节点不能重复");
  if(r.getGroupId()!=null&&jdbc.queryForList("SELECT id FROM subscription_node_group WHERE tenant_id=? AND id=?",tenant(),r.getGroupId()).isEmpty())throw bad("节点权限组不存在");
  for(var p:r.getPrices())if(p.getName().isBlank())p.setName("traffic".equals(p.getKind())?"补充流量":"reset".equals(p.getKind())?"流量重置":Map.of(1,"月付",3,"季付",6,"半年付",12,"年付",24,"两年付",36,"三年付").getOrDefault(p.getMonths(),p.getMonths()+"个月"));
  for(var p:r.getPrices())if("traffic".equals(p.getKind())&&p.getBytes()<=0)throw bad("流量包额度必须大于零");
  long id;
  if(r.getId()==null){jdbc.update("INSERT INTO subscription_plan (tenant_id,name,total_bytes) VALUES (?,?,?)",tenant(),r.getName(),r.getTotalBytes());id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}else{id=r.getId();plan(id);}
  jdbc.update("UPDATE subscription_plan SET name=?,description=?,level=?,visible=?,enabled=?,renew_enabled=?,total_bytes=?,reset_mode=?,reset_interval_days=?,traffic_mode=?,node_limit=?,capacity_limit=?,sort=?,group_id=?,version=version+1 WHERE tenant_id=? AND id=?",r.getName(),r.getDescription(),r.getLevel(),r.isVisible(),r.isEnabled(),r.isRenewEnabled(),r.getTotalBytes(),r.getResetMode(),r.getResetIntervalDays(),r.getTrafficMode(),r.getNodeLimit(),r.getCapacityLimit(),r.getSort(),r.getGroupId(),tenant(),id);
  jdbc.update("DELETE FROM subscription_plan_price WHERE tenant_id=? AND plan_id=?",tenant(),id);jdbc.update("DELETE FROM subscription_plan_node WHERE tenant_id=? AND plan_id=?",tenant(),id);
  for(var p:r.getPrices())jdbc.update("INSERT INTO subscription_plan_price (tenant_id,plan_id,name,months,price,kind,bytes,enabled) VALUES (?,?,?,?,?,?,?,?)",tenant(),id,p.getName(),p.getMonths(),p.getPrice(),p.getKind(),p.getBytes(),p.isEnabled());
  for(var a:r.getAssignments())jdbc.update("INSERT INTO subscription_plan_node (tenant_id,plan_id,node_id,server_id,inbound_id,public_host) VALUES (?,?,?,?,?,?)",tenant(),id,a.getNodeId(),a.getServerId(),a.getInboundId(),a.getPublicHost());return id;
 });}
 public List<Map<String,Object>> plansForUser(long user){var list=plans(false);var c=current(user);if(c!=null&&c.get("planId") instanceof Number id&&list.stream().noneMatch(p->num(p,"id")==id.longValue())){var owned=plan(id.longValue());owned.put("prices",jdbc.queryForList("SELECT * FROM subscription_plan_price WHERE tenant_id=? AND plan_id=? AND enabled=1 ORDER BY kind,months,id",tenant(),id.longValue()));list.add(owned);}return list;}
 public Map<String,Object> current(long user){return tx.execute(status->{accounts.lock(user);var rows=jdbc.queryForList("SELECT current_subscription_id FROM subscription_account WHERE tenant_id=? AND user_id=?",tenant(),user);if(rows.isEmpty()||rows.getFirst().get("current_subscription_id")==null)return null;
  var s=subscriptions.require(num(rows.getFirst(),"current_subscription_id"));var result=new LinkedHashMap<String,Object>();
  result.put("id",s.getId());result.put("number",s.getNumber());result.put("planId",s.getPlanId());result.put("planLevel",s.getPlanId()==null?null:plan(s.getPlanId()).get("level"));result.put("planName",s.getPlanId()==null?"后台开通订阅":plan(s.getPlanId()).get("name"));result.put("expiryTime",s.getExpiryTime());result.put("status",SubscriptionPolicy.status(s,LocalDateTime.now()));result.put("syncStatus",s.getSyncStatus());result.put("lastError",s.getLastError());result.put("usedBytes",SubscriptionPolicy.used(s));result.put("totalBytes",s.getTotalBytes());result.put("baseBytes",s.getBaseTotalBytes()==null?s.getTotalBytes():s.getBaseTotalBytes());result.put("extraBytes",s.getExpiryTime().isAfter(LocalDateTime.now())?subscriptions.packRemaining(s):0);result.put("nextResetTime",s.getNextResetTime());result.put("nodeLimit",s.getNodeLimit());return result;
 });}
 public record SubscriptionInfo(LocalDateTime expiryTime,String planName,Long totalBytes,Long remainingBytes,
  long usedBytes,long extraBytes,Integer nodeLimit,LocalDateTime nextResetTime,int status,Integer syncStatus,
  boolean unlimited,Double usagePercent,long remainingDays,long pendingOrderCount) {}
 public SubscriptionInfo subscriptionInfo(long user){return tx.execute(status->{
  var account=accounts.lock(user);
  if(account.get("current_subscription_id")==null)return null;
  var s=subscriptions.require(num(account,"current_subscription_id"));
  var now=LocalDateTime.now();
  String name=s.getPlanId()==null?"后台开通订阅":String.valueOf(plan(s.getPlanId()).get("name"));
  boolean unlimited=Boolean.TRUE.equals(s.getUnlimited());
  long used=SubscriptionPolicy.used(s);
  Long total=unlimited?null:(s.getBaseTotalBytes()==null?s.getTotalBytes():s.getBaseTotalBytes());
  // Total entitlement already includes traffic packs; do not add the pack balance twice.
  Long remaining=unlimited?null:Math.max(0,s.getTotalBytes()-used);
  long extra=s.getExpiryTime().isAfter(now)?subscriptions.packRemaining(s):0;
  Double percent=unlimited?null:Math.min(100,Math.max(0,used*100.0/Math.max(1,s.getTotalBytes())));
  long days=s.getExpiryTime().isAfter(now)?(long)Math.ceil(Duration.between(now,s.getExpiryTime()).toMillis()/86400000.0):0;
  long pending=jdbc.queryForObject("SELECT COUNT(*) FROM subscription_purchase WHERE tenant_id=? AND user_id=? AND status IN ('pending','paid','failed')",Long.class,tenant(),user);
  return new SubscriptionInfo(s.getExpiryTime(),name,total,remaining,used,extra,s.getNodeLimit(),s.getNextResetTime(),
   SubscriptionPolicy.status(s,now),s.getSyncStatus(),unlimited,percent,days,pending);
 });}
 private long credit(long subscription,LocalDateTime now){long total=0;for(var o:jdbc.queryForList("SELECT * FROM subscription_purchase WHERE tenant_id=? AND subscription_id=? AND status='completed' AND kind IN ('new','renew','upgrade') AND credited=0 AND service_end>?",tenant(),subscription,now)){
  var start=time(o,"service_start");var end=time(o,"service_end");long all=Duration.between(start,end).getSeconds(),left=Duration.between(now.isAfter(start)?now:start,end).getSeconds();if(all>0&&left>0)total+=Math.multiplyExact(num(o,"original_amount"),left)/all;
 }return total;}
 public Map<String,Object> checkout(long user,long price,String key,String ip){if(key==null||!key.matches("[A-Za-z0-9_-]{8,64}"))throw bad("无效请求标识");settleForUser(user);
 return tx.execute(status->{var a=accounts.lock(user);var duplicate=jdbc.queryForList("SELECT * FROM subscription_purchase WHERE tenant_id=? AND user_id=? AND request_key=?",tenant(),user,key);if(!duplicate.isEmpty())return publicOrder(duplicate.getFirst());
  if(!jdbc.queryForList("SELECT id FROM subscription_purchase WHERE tenant_id=? AND user_id=? AND status IN ('pending','paid','failed')",tenant(),user).isEmpty())throw bad("请先完成或取消当前订单；已付款异常订单请联系客服");
  var prices=jdbc.queryForList("SELECT * FROM subscription_plan_price WHERE tenant_id=? AND id=? AND enabled=1",tenant(),price);if(prices.isEmpty())throw bad("该价格已停售，请刷新套餐");var p=prices.getFirst();var product=jdbc.queryForMap("SELECT * FROM subscription_plan WHERE tenant_id=? AND id=? FOR UPDATE",tenant(),num(p,"plan_id"));
  SubscriptionDO s=a.get("current_subscription_id")==null?null:subscriptions.require(num(a,"current_subscription_id"));
  String kind="new";long discount=0;var now=LocalDateTime.now();
  if(Set.of("traffic","reset").contains(p.get("kind"))){kind=p.get("kind").toString();if(s==null||s.getEndedTime()!=null||!s.getExpiryTime().isAfter(now)||!Objects.equals(s.getPlanId(),num(product,"id")))throw bad("请先开通对应套餐；过期套餐不能补充流量");if(s.getUnlimited())throw bad("无限流量套餐无需加购");}
  else if(s!=null&&s.getEndedTime()==null&&s.getExpiryTime().isAfter(now)){
   if(Objects.equals(s.getPlanId(),num(product,"id"))){kind="renew";if(!flag(product,"renew_enabled"))throw bad("该套餐已停止续订");}
   else {kind="upgrade";if(s.getPlanId()==null)throw bad("后台开通套餐请联系客服升级");if(num(product,"level")<=num(plan(s.getPlanId()),"level"))throw bad("只能升级到更高等级套餐");discount=Math.min(num(p,"price"),credit(s.getId(),now));}
  }
  if(!"renew".equals(kind)&&!flag(product,"enabled"))throw bad("该套餐已停止销售");
  if(Set.of("new","upgrade").contains(kind)&&num(product,"capacity_limit")>0) {
   long occupied=jdbc.queryForObject("SELECT COUNT(*) FROM subscription_account a JOIN subscription s ON s.id=a.current_subscription_id AND s.tenant_id=a.tenant_id WHERE a.tenant_id=? AND s.plan_id=? AND s.ended_time IS NULL AND s.expiry_time>?",Long.class,tenant(),num(product,"id"),now);
   long reserved=jdbc.queryForObject("SELECT COUNT(*) FROM subscription_purchase WHERE tenant_id=? AND plan_id=? AND kind IN ('new','upgrade') AND ((status='pending' AND expires_at>?) OR status IN ('paid','failed'))",Long.class,tenant(),num(product,"id"),now);
   if(occupied+reserved>=num(product,"capacity_limit"))throw bad("该套餐当前容量已满");
  }
  var bindings=nodes(num(product,"id"));if(bindings.isEmpty())throw bad("套餐尚未配置节点");
  String number="PK"+UUID.randomUUID().toString().replace("-","");var expires=now.plusMinutes(20);long amount=num(p,"price")-discount;
  jdbc.update("INSERT INTO subscription_purchase (tenant_id,user_id,number,request_key,subscription_id,plan_id,plan_version,price_id,plan_name,kind,months,bytes,base_bytes,reset_mode,reset_interval_days,traffic_mode,node_limit,nodes_json,original_amount,credit_amount,amount,account_version,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",tenant(),user,number,key,s==null?null:s.getId(),num(product,"id"),num(product,"version"),price,product.get("name"),kind,num(p,"months"),num(p,"bytes"),num(product,"total_bytes"),product.get("reset_mode"),num(product,"reset_interval_days"),product.get("traffic_mode"),num(product,"node_limit"),JsonUtils.toJsonString(bindings),num(p,"price"),discount,amount,num(a,"version"),expires);
  long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  if(amount>0){String name=product.get("name").toString();Long pay=payments.createOrder(new PayOrderCreateReqDTO().setAppKey("subscription").setUserId(user).setUserType(1).setUserIp(ip).setMerchantOrderId(number).setSubject(name.substring(0,Math.min(32,name.length()))).setBody("订阅套餐订单").setPrice(Math.toIntExact(amount)).setExpireTime(expires));jdbc.update("UPDATE subscription_purchase SET pay_order_id=? WHERE tenant_id=? AND id=?",pay,tenant(),id);}
  else jdbc.update("UPDATE subscription_purchase SET status='paid',paid_at=? WHERE tenant_id=? AND id=?",now,tenant(),id);
  return publicOrder(order(id,user));
 });}
 private Map<String,Object> order(long id,long user){var rows=jdbc.queryForList("SELECT * FROM subscription_purchase WHERE tenant_id=? AND id=? AND user_id=?",tenant(),id,user);if(rows.isEmpty())throw bad("订单不存在");return rows.getFirst();}
 private Map<String,Object> publicOrder(Map<String,Object> o){var r=new LinkedHashMap<String,Object>();for(var k:List.of("id","number","plan_name","kind","original_amount","credit_amount","amount","status","error","expires_at","create_time","service_end"))r.put(k,o.get(k));return r;}
 public List<Map<String,Object>> orders(long user,boolean admin){var rows=jdbc.queryForList("SELECT * FROM subscription_purchase WHERE tenant_id=?"+(admin?"":" AND user_id=?")+" ORDER BY id DESC LIMIT 100",admin?new Object[]{tenant()}:new Object[]{tenant(),user});return rows.stream().map(o->{var r=publicOrder(o);if(admin)r.put("user_id",o.get("user_id"));return r;}).toList();}
 public Map<String,Object> refresh(long id,long user){settle(id,user);return publicOrder(order(id,user));}
 public void cancel(long id,long user){settle(id,user);tx.executeWithoutResult(st->{accounts.lock(user);var o=order(id,user);if(!"pending".equals(o.get("status")))throw bad("只能取消未支付订单");jdbc.update("UPDATE subscription_purchase SET status='cancelled' WHERE tenant_id=? AND id=?",tenant(),id);});}
 public List<String> channels(){return jdbc.query("SELECT c.code FROM pay_channel c JOIN pay_app a ON a.id=c.app_id AND a.tenant_id=c.tenant_id WHERE a.tenant_id=? AND a.app_key='subscription' AND a.status=0 AND a.deleted=0 AND c.status=0 AND c.deleted=0 AND c.code IN ('alipay_pc','alipay_wap','alipay_qr','wx_native','wx_wap','mock')",(rs,n)->rs.getString(1),tenant());}
 public PayOrderSubmitRespVO pay(long id,long user,String channel,String ip){var o=order(id,user);if(!"pending".equals(o.get("status"))||!time(o,"expires_at").isAfter(LocalDateTime.now()))throw bad("订单已支付或已失效");if(!channels().contains(channel))throw bad("该支付渠道不可用");return paymentService.submitOrder(new PayOrderSubmitReqVO().setId(num(o,"pay_order_id")).setChannelCode(channel),ip);}
 private void settleForUser(long user){for(var o:jdbc.queryForList("SELECT id FROM subscription_purchase WHERE tenant_id=? AND user_id=? AND status IN ('pending','paid','failed')",tenant(),user))settle(num(o,"id"),user);}
 public void settle(long id,long user){try{Long subscription=tx.execute(st->{var a=accounts.lock(user);var o=order(id,user);if("completed".equals(o.get("status")))return null;
  if("cancelled".equals(o.get("status"))) {var late=o.get("pay_order_id")==null?null:payments.getOrder(num(o,"pay_order_id"));if(late!=null&&late.getStatus()==10)jdbc.update("UPDATE subscription_purchase SET status='failed',error='订单取消后收到付款，请联系客服处理',account_version=-1 WHERE tenant_id=? AND id=?",tenant(),id);return null;}
  var now=LocalDateTime.now();var pay=o.get("pay_order_id")==null?null:payments.getOrder(num(o,"pay_order_id"));
  if(num(o,"amount")>0&&pay!=null&&pay.getStatus()==10&&(pay.getPrice()!=num(o,"amount")||!Objects.equals(pay.getMerchantOrderId(),o.get("number"))))throw bad("支付金额或订单关联异常");
  if("pending".equals(o.get("status"))){if(pay==null||pay.getStatus()!=10){if(!time(o,"expires_at").isAfter(now))jdbc.update("UPDATE subscription_purchase SET status='cancelled' WHERE tenant_id=? AND id=?",tenant(),id);return null;}if(pay.getPrice()!=num(o,"amount")||!pay.getMerchantOrderId().equals(o.get("number")))throw bad("支付金额或订单关联异常");}
  if(num(o,"amount")>0&&(pay==null||pay.getStatus()!=10))throw bad("尚未核验付款");
  if(num(a,"version")!=num(o,"account_version"))throw bad("当前订阅已发生变更，请联系客服处理已支付订单");
  long sid;LocalDateTime start=now,end;String kind=o.get("kind").toString();var assignments=JsonUtils.parseArray(o.get("nodes_json").toString(),SubscriptionCreateReqVO.Assignment.class);
  if("new".equals(kind)){end=now.plusMonths(num(o,"months"));var r=new SubscriptionCreateReqVO();r.setUserId(user);r.setOrderNo(o.get("number").toString());r.setStartTime(now);r.setExpiryTime(end);r.setUnlimited(false);r.setTotalBytes(num(o,"base_bytes"));r.setTrafficMode(o.get("traffic_mode").toString());r.setResetMode(o.get("reset_mode").toString());r.setResetIntervalDays((int)num(o,"reset_interval_days"));r.setNodeLimit((int)num(o,"node_limit"));r.setAssignments(assignments);sid=subscriptions.create(r);jdbc.update("UPDATE subscription SET source='purchase',plan_id=?,base_total_bytes=? WHERE tenant_id=? AND id=?",num(o,"plan_id"),num(o,"base_bytes"),tenant(),sid);}
  else {sid=num(o,"subscription_id");if(num(a,"current_subscription_id")!=sid)throw bad("订单对应订阅已变更");var s=subscriptions.require(sid);if("traffic".equals(kind)){subscriptions.commercialTraffic(sid,id,num(o,"bytes"));end=s.getExpiryTime();}
   else if("reset".equals(kind)){subscriptions.commercialReset(sid);end=s.getExpiryTime();}
   else {if(s.getEndedTime()!=null||!s.getExpiryTime().isAfter(now))throw bad("订阅已过期或结束，不能续费，请购买新订阅");boolean reset="upgrade".equals(kind);start="renew".equals(kind)?s.getExpiryTime():now;end=start.plusMonths(num(o,"months"));subscriptions.commercialChange(sid,num(o,"plan_id"),num(o,"base_bytes"),o.get("reset_mode").toString(),(int)num(o,"reset_interval_days"),o.get("traffic_mode").toString(),(int)num(o,"node_limit"),end,reset,"upgrade".equals(kind)?assignments:null);if("upgrade".equals(kind))jdbc.update("UPDATE subscription_purchase SET credited=1 WHERE tenant_id=? AND subscription_id=? AND kind IN ('new','renew','upgrade') AND status='completed'",tenant(),sid);}
   accounts.bind(user,sid);
  }
  jdbc.update("UPDATE subscription_purchase SET status='completed',paid_at=COALESCE(paid_at,?),subscription_id=?,service_start=?,service_end=?,error='' WHERE tenant_id=? AND id=?",pay==null?now:pay.getSuccessTime(),sid,start,end,tenant(),id);return sid;
 });if(subscription!=null)subscriptions.enqueue(subscription);
 }catch(RuntimeException e){log.error("Subscription purchase fulfilment failed, tenant={}, order={}",tenant(),id,e);var o=order(id,user);var pay=o.get("pay_order_id")==null?null:payments.getOrder(num(o,"pay_order_id"));if("paid".equals(o.get("status"))||"failed".equals(o.get("status"))||pay!=null&&pay.getStatus()==10)jdbc.update("UPDATE subscription_purchase SET status='failed',error=? WHERE tenant_id=? AND id=?","已支付，权益处理失败，请联系客服或等待重试",tenant(),id);else throw e;}}
 public boolean notifyPayment(String number){var rows=jdbc.queryForList("SELECT id,user_id FROM subscription_purchase WHERE tenant_id=? AND number=?",tenant(),number);if(rows.isEmpty())throw bad("订单不存在");var o=rows.getFirst();settle(num(o,"id"),num(o,"user_id"));var refreshed=order(num(o,"id"),num(o,"user_id"));if("pending".equals(refreshed.get("status")))throw bad("尚未核验付款");return true;}
 @Scheduled(fixedDelay=15000) public void reconcilePayments(){for(var o:jdbc.queryForList("SELECT tenant_id,user_id,id FROM subscription_purchase WHERE status IN ('pending','paid','failed') ORDER BY last_checked_at,id LIMIT 100")){try{jdbc.update("UPDATE subscription_purchase SET last_checked_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",num(o,"tenant_id"),num(o,"id"));TenantUtils.execute(num(o,"tenant_id"),()->settle(num(o,"id"),num(o,"user_id")));}catch(RuntimeException ignored){/* durable status remains retryable */}}}
}
