package com.speednet.module.subscription.controller.app;
import com.speednet.framework.apilog.core.annotation.ApiAccessLog;
import com.speednet.framework.tenant.core.aop.TenantIgnore;
import com.speednet.framework.tenant.core.util.TenantUtils;
import com.speednet.module.subscription.service.SubscriptionService;
import jakarta.annotation.Resource;import jakarta.annotation.security.PermitAll;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/subscription/feed")
public class SubscriptionFeedController {
 @Resource private SubscriptionService service;
 @Resource private com.speednet.framework.common.biz.system.tenant.TenantCommonApi tenants;
 @GetMapping("/{tenantId}/{token}") @PermitAll @TenantIgnore @ApiAccessLog(enable=false)
 public ResponseEntity<String> feed(@PathVariable Long tenantId,@PathVariable String token,@RequestParam(defaultValue="base64") String format) {
  try {
   if(!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException();
   tenants.validateTenant(tenantId);
   var result=TenantUtils.execute(tenantId,()->service.feed(token,format));
   return ResponseEntity.ok().contentType("clash".equals(format)?MediaType.parseMediaType("application/yaml"):MediaType.TEXT_PLAIN).header("Cache-Control","no-store")
    .header("subscription-userinfo","upload="+result.upload()+"; download="+result.download()+"; total="+result.total()+"; expire="+result.expiry())
    .header("Content-Disposition","attachment; filename=subscription."+("clash".equals(format)?"yaml":"txt"))
    .header("profile-update-interval","1").body(result.content());
  }catch(RuntimeException e){return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).header("Cache-Control","no-store").body("订阅不可用，请检查有效期或联系管理员");}
 }
}
