package com.speednet.module.subscription.controller.admin;

import com.speednet.framework.common.pojo.CommonResult;
import com.speednet.module.subscription.service.ClashRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/subscription/clash-rule")
@PreAuthorize("@ss.hasPermission('subscription:clash-rule:manage')")
public class ClashRuleController {
    private final ClashRuleService service;
    public ClashRuleController(ClashRuleService service){this.service=service;}
    public record SaveRequest(long userId,List<String> rules){}
    @GetMapping("/get") public CommonResult<?> get(@RequestParam(defaultValue="0") long userId){return CommonResult.success(service.get(userId));}
    @PostMapping("/save") public CommonResult<?> save(@RequestBody SaveRequest request){service.save(request.userId(),request.rules());return CommonResult.success(true);}
    @DeleteMapping("/delete") public CommonResult<?> delete(@RequestParam long userId){service.remove(userId);return CommonResult.success(true);}
    @GetMapping("/users") public CommonResult<?> users(@RequestParam(defaultValue="") String keyword){return CommonResult.success(service.users(keyword));}
    @GetMapping("/list") public CommonResult<?> list(){return CommonResult.success(service.overrides());}
}
