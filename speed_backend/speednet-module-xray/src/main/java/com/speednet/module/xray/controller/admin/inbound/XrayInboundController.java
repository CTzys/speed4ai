package com.speednet.module.xray.controller.admin.inbound;

import com.speednet.framework.common.pojo.CommonResult;
import com.speednet.module.xray.controller.admin.inbound.vo.XrayInboundSaveReqVO;
import com.speednet.module.xray.service.inbound.XrayInboundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.speednet.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Xray 入站")
@RestController
@RequestMapping("/xray/inbound")
@Validated
public class XrayInboundController {
    @Resource private XrayInboundService service;
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('xray:inbound:query')")
    public CommonResult<Object> list(@RequestParam @Positive Long serverId) { return success(service.list(serverId)); }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('xray:inbound:query')")
    public CommonResult<Object> get(@RequestParam @Positive Long serverId, @RequestParam @Positive Long id) { return success(service.get(serverId, id)); }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('xray:inbound:create')")
    public CommonResult<String> create(@Valid @RequestBody XrayInboundSaveReqVO request) { return success(service.save(request, false)); }
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('xray:inbound:update')")
    public CommonResult<String> update(@Valid @RequestBody XrayInboundSaveReqVO request) { return success(service.save(request, true)); }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('xray:inbound:delete')")
    public CommonResult<Boolean> delete(@RequestParam @Positive Long serverId, @RequestParam @Positive Long id) { service.delete(serverId, id); return success(true); }
}
