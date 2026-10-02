package com.speednet.module.subscription.controller.admin;
import com.speednet.framework.common.pojo.*;
import com.speednet.framework.apilog.core.annotation.ApiAccessLog;
import com.speednet.module.subscription.controller.admin.vo.*;
import com.speednet.module.subscription.service.SubscriptionService;
import com.speednet.module.xray.dal.mysql.node.XrayNodeMapper;
import com.speednet.module.xray.service.inbound.XrayInboundService;
import com.speednet.module.xray.service.node.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeDO;
import jakarta.annotation.Resource;import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import static com.speednet.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/subscription") @Validated @Tag(name="管理后台 - 订阅管理")
public class SubscriptionController {
 @Resource private SubscriptionService service;
 @Resource private XrayNodeMapper nodeMapper;
 @Resource private com.speednet.module.xray.dal.mysql.server.XrayServerMapper serverMapper;
 @Resource private XrayInboundService inbounds;
 @Resource private XrayRegionService regions;
 @Resource private XrayCityService cities;
 @GetMapping("/page") @PreAuthorize("@ss.hasPermission('subscription:query')")
 public CommonResult<PageResult<Map<String,Object>>> page(@Valid SubscriptionPageReqVO req){return success(service.page(req));}
 @GetMapping("/detail") @PreAuthorize("@ss.hasPermission('subscription:query')")
 public CommonResult<Map<String,Object>> detail(@RequestParam Long id){return success(service.detail(id));}
 @GetMapping("/log-page") @PreAuthorize("@ss.hasPermission('subscription:query')")
 public CommonResult<PageResult<com.speednet.module.subscription.dal.dataobject.SubscriptionLogDO>> logPage(@Valid SubscriptionLogPageReqVO req){return success(service.logPage(req));}
 @PostMapping("/create") @PreAuthorize("@ss.hasPermission('subscription:create')") @ApiAccessLog(requestEnable=false)
 public CommonResult<Long> create(@Valid @RequestBody SubscriptionCreateReqVO req){return success(service.create(req));}
 @PostMapping("/action") @PreAuthorize("@ss.hasPermission('subscription:' + (#req.action == 'resume' ? 'pause' : #req.action))")
 public CommonResult<Boolean> action(@Valid @RequestBody SubscriptionActionReqVO req){service.action(req);return success(true);}
 @PostMapping("/sync") @PreAuthorize("@ss.hasPermission('subscription:sync')")
 public CommonResult<Boolean> sync(@RequestParam Long id){return success(service.enqueue(id));}
 @GetMapping("/link") @PreAuthorize("@ss.hasPermission('subscription:credentials')") @ApiAccessLog(requestEnable=false,responseEnable=false)
 public CommonResult<Map<String,String>> link(@RequestParam Long id){return success(service.link(id));}
 @GetMapping("/client-credentials") @PreAuthorize("@ss.hasPermission('subscription:credentials')") @ApiAccessLog(enable=false)
 public CommonResult<Map<String,String>> credentials(@RequestParam Long id,@RequestParam Long clientId){return success(service.credentials(id,clientId));}
 @PostMapping("/reset-client") @PreAuthorize("@ss.hasPermission('subscription:reset-client')") @ApiAccessLog(requestEnable=false,responseEnable=false)
 public CommonResult<Boolean> resetClient(@RequestParam Long id,@RequestParam Long clientId){service.resetCredential(id,clientId);return success(true);}
 @GetMapping("/users") @PreAuthorize("@ss.hasPermission('subscription:query')")
 public CommonResult<List<Map<String,Object>>> users(@RequestParam(required=false) @Size(max=128) String keyword){return success(service.userOptions(keyword));}
 @GetMapping("/options") @PreAuthorize("@ss.hasPermission('subscription:query')")
 public CommonResult<Map<String,Object>> options(){
  var ns=nodeMapper.selectList(new LambdaQueryWrapper<XrayNodeDO>().eq(XrayNodeDO::getShelfStatus,1).orderByDesc(XrayNodeDO::getId)).stream().map(n->{var r=new LinkedHashMap<String,Object>();r.put("id",n.getId());r.put("name",n.getName());r.put("regionId",n.getRegionId());r.put("cityId",n.getCityId());r.put("healthStatus",n.getHealthStatus());return r;}).toList();
  var ss=serverMapper.selectList().stream().filter(s->s.getPanelToken()!=null&&!s.getPanelToken().isBlank()&&s.getPanelPort()!=null).map(s->Map.of("id",s.getId(),"name",s.getName(),"host",s.getHost())).toList();
  return success(Map.of("nodes",ns,"servers",ss,"regions",regions.list(),"cities",cities.list(null)));
 }
 @GetMapping("/inbounds") @PreAuthorize("@ss.hasPermission('subscription:query')") @ApiAccessLog(responseEnable=false)
 public CommonResult<List<Map<String,Object>>> inbounds(@RequestParam Long serverId){
  Object raw=inbounds.list(serverId);List<Map<String,Object>> result=new ArrayList<>();
  for(Object value:(List<?>)raw)if(value instanceof Map<?,?> m&&Boolean.TRUE.equals(m.get("enable"))&&Set.of("vmess","vless","trojan").contains(String.valueOf(m.get("protocol")))) {
   Map<String,Object> row=new LinkedHashMap<>();for(String field:List.of("id","remark","protocol","port"))row.put(field,m.get(field));result.add(row);
  }return success(result);
 }
 @Data public static class AssignReq { @NotNull private Long id; @Valid @NotNull private SubscriptionCreateReqVO.Assignment assignment; }
 @PostMapping("/assign") @PreAuthorize("@ss.hasPermission('subscription:assign')")
 public CommonResult<Boolean> assign(@Valid @RequestBody AssignReq req){service.assign(req.getId(),req.getAssignment());return success(true);}
 @PostMapping("/release") @PreAuthorize("@ss.hasPermission('subscription:assign')")
 public CommonResult<Boolean> release(@RequestParam Long id,@RequestParam Long clientId){service.release(id,clientId);return success(true);}
}
