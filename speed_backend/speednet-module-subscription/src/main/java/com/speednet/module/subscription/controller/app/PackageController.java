package com.speednet.module.subscription.controller.app;
import com.speednet.module.subscription.service.*;
import com.speednet.framework.common.pojo.CommonResult;
import com.speednet.framework.common.exception.ErrorCode;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.framework.common.util.json.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.time.LocalDateTime;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;
@RestController @RequestMapping("/subscription-products")
public class PackageController {
 @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 private final PackageService service;private final SubscriptionService subscriptions;private final JdbcTemplate jdbc;
 public PackageController(PackageService service,SubscriptionService subscriptions,JdbcTemplate jdbc){this.service=service;this.subscriptions=subscriptions;this.jdbc=jdbc;}
 private long user(HttpServletRequest r){String auth=r.getHeader("Authorization");if(auth==null||!auth.startsWith("Bearer "))throw exception(new ErrorCode(401,"请先登录"));
  var rows=jdbc.queryForList("SELECT s.member_user_id FROM custom_session s JOIN member_user u ON u.id=s.member_user_id AND u.tenant_id=s.tenant_id WHERE s.tenant_id=? AND s.access_hash=? AND s.revoked=0 AND s.access_expires_at>? AND u.status=0 AND u.deleted=0",TenantContextHolder.getRequiredTenantId(),SubscriptionPolicy.hash(auth.substring(7)),LocalDateTime.now());
  if(rows.isEmpty())throw exception(new ErrorCode(401,"登录已过期，请重新登录"));long id=((Number)rows.getFirst().get("member_user_id")).longValue();
  com.speednet.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(new com.speednet.framework.security.core.LoginUser().setId(id).setUserType(1).setTenantId(TenantContextHolder.getRequiredTenantId()),r);return id;}
 @PermitAll @PostMapping("/notify") public CommonResult<?> notify(@RequestBody Map<String,Object> body){return CommonResult.success(service.notifyPayment(String.valueOf(body.get("merchantOrderId"))));}
 public record Checkout(long priceId,String requestKey){} public record OrderAction(long orderId,String channelCode){}
 @PermitAll @GetMapping("/plans") public CommonResult<?> plans(HttpServletRequest r){return CommonResult.success(service.plansForUser(user(r)));}
 @PermitAll @GetMapping("/current") public CommonResult<?> current(HttpServletRequest r){return CommonResult.success(service.current(user(r)));}
 @PermitAll @GetMapping("/info") public CommonResult<?> info(HttpServletRequest r){return CommonResult.success(service.subscriptionInfo(user(r)));}
 @PermitAll @GetMapping("/orders") public CommonResult<?> orders(HttpServletRequest r){return CommonResult.success(service.orders(user(r),false));}
 @PermitAll @GetMapping("/regions") public CommonResult<?> regions(HttpServletRequest r){
  user(r);long tenant=TenantContextHolder.getRequiredTenantId();
  var regions=jdbc.queryForList("SELECT id,name FROM xray_region WHERE tenant_id=? AND deleted=0 AND status=0 ORDER BY sort,id",tenant);
  var cities=jdbc.queryForList("SELECT id,name,region_id FROM xray_city WHERE tenant_id=? AND deleted=0 AND status=0 ORDER BY sort,id",tenant);
  for(var region:regions)region.put("cities",cities.stream().filter(city->((Number)city.get("region_id")).longValue()==((Number)region.get("id")).longValue()).map(city->Map.of("id",city.get("id"),"name",city.get("name"))).toList());
  return CommonResult.success(regions);
 }
 @PermitAll @GetMapping("/channels") public CommonResult<?> channels(HttpServletRequest r){user(r);return CommonResult.success(service.channels());}
 @PermitAll @PostMapping("/checkout") public CommonResult<?> checkout(@RequestBody Checkout body,HttpServletRequest r){return CommonResult.success(service.checkout(user(r),body.priceId(),body.requestKey(),com.speednet.framework.common.util.servlet.ServletUtils.getClientIP(r)));}
 @PermitAll @PostMapping("/refresh") public CommonResult<?> refresh(@RequestBody OrderAction body,HttpServletRequest r){return CommonResult.success(service.refresh(body.orderId(),user(r)));}
 @PermitAll @PostMapping("/cancel") public CommonResult<?> cancel(@RequestBody OrderAction body,HttpServletRequest r){service.cancel(body.orderId(),user(r));return CommonResult.success(true);}
 @PermitAll @PostMapping("/pay") public CommonResult<?> pay(@RequestBody OrderAction body,HttpServletRequest r){return CommonResult.success(service.pay(body.orderId(),user(r),body.channelCode(),com.speednet.framework.common.util.servlet.ServletUtils.getClientIP(r)));}
 @com.speednet.framework.apilog.core.annotation.ApiAccessLog(enable=false) @PermitAll @GetMapping("/link") public CommonResult<?> link(HttpServletRequest r){var s=service.current(user(r));if(s==null||!Integer.valueOf(1).equals(s.get("status"))||!Integer.valueOf(2).equals(s.get("syncStatus")))throw exception(new ErrorCode(1_013_000_003,"订阅未生效或节点尚未配置完成"));return CommonResult.success(subscriptions.link(((Number)s.get("id")).longValue()));}
 private long currentSubscription(HttpServletRequest r){var s=service.current(user(r));if(s==null)throw exception(new ErrorCode(1_013_000_003,"尚未开通订阅"));return ((Number)s.get("id")).longValue();}
 @com.speednet.framework.apilog.core.annotation.ApiAccessLog(enable=false) @PermitAll @GetMapping("/nodes")
 public CommonResult<?> nodes(HttpServletRequest r){return CommonResult.success(subscriptions.nodes(currentSubscription(r)));}
 @com.speednet.framework.apilog.core.annotation.ApiAccessLog(enable=false) @PermitAll @GetMapping("/clash-link")
 public CommonResult<?> clashLink(HttpServletRequest r){long id=currentSubscription(r);subscriptions.clash(id);return CommonResult.success(Map.of("path",subscriptions.link(id).get("path")+"?format=clash"));}
 @com.speednet.framework.apilog.core.annotation.ApiAccessLog(enable=false) @PermitAll @GetMapping("/clash")
 public org.springframework.http.ResponseEntity<String> clash(HttpServletRequest r){var result=subscriptions.clash(currentSubscription(r));return org.springframework.http.ResponseEntity.ok()
  .contentType(org.springframework.http.MediaType.parseMediaType("application/yaml"))
  .header("Cache-Control","no-store").header("Content-Disposition","attachment; filename=subscription.yaml")
  .header("subscription-userinfo","upload="+result.upload()+"; download="+result.download()+"; total="+result.total()+"; expire="+result.expiry())
  .header("profile-update-interval","1").body(result.content());}

}
