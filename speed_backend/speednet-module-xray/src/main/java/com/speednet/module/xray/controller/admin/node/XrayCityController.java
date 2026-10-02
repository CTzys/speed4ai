package com.speednet.module.xray.controller.admin.node;

import com.speednet.framework.common.pojo.CommonResult;
import com.speednet.module.xray.controller.admin.node.vo.XrayCitySaveReqVO;
import com.speednet.module.xray.dal.dataobject.node.XrayCityDO;
import com.speednet.module.xray.service.node.XrayCityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.speednet.framework.common.pojo.CommonResult.success;

@Tag(name="管理后台 - 节点城市")
@RestController
@RequestMapping("/xray/city")
@Validated
public class XrayCityController {
    @Resource private XrayCityService service;
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPermissions('xray:region:query','xray:node:query','xray:node:create','xray:node:update','xray:node:import')")
    public CommonResult<List<XrayCityDO>> list(@RequestParam(required=false) Long regionId) { return success(service.list(regionId)); }
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('xray:region:create')")
    public CommonResult<Long> create(@Valid @RequestBody XrayCitySaveReqVO request) { return success(service.create(request)); }
    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('xray:region:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody XrayCitySaveReqVO request) { service.update(request); return success(true); }
}
