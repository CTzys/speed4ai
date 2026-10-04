package com.speednet.module.subscription.controller.admin.plan;
import com.speednet.module.subscription.service.PackageService;
import com.speednet.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
@RestController @RequestMapping("/subscription/plan")
public class PlanController {
 private final PackageService service;public PlanController(PackageService service){this.service=service;}
 @GetMapping("/list") @PreAuthorize("@ss.hasPermission('subscription:plan:manage')") public CommonResult<?> list(){return CommonResult.success(service.plans(true));}
 @PostMapping("/save") @PreAuthorize("@ss.hasPermission('subscription:plan:manage')") public CommonResult<?> save(@Valid @RequestBody PlanRequest request){return CommonResult.success(service.save(request));}
 @GetMapping("/groups") @PreAuthorize("@ss.hasPermission('subscription:plan:manage')") public CommonResult<?> groups(){return CommonResult.success(service.groups());}
 @PostMapping("/group/save") @PreAuthorize("@ss.hasPermission('subscription:plan:manage')") public CommonResult<?> group(@Valid @RequestBody PlanRequest.NodeGroup request){return CommonResult.success(service.saveGroup(request));}
 @GetMapping("/orders") @PreAuthorize("@ss.hasPermission('subscription:order:manage')") public CommonResult<?> orders(){return CommonResult.success(service.orders(0,true));}
 @PostMapping("/retry") @PreAuthorize("@ss.hasPermission('subscription:order:manage')") public CommonResult<?> retry(@RequestParam long id,@RequestParam long userId){return CommonResult.success(service.refresh(id,userId));}
}
