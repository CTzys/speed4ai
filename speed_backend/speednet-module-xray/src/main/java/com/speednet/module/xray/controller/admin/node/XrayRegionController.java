package com.speednet.module.xray.controller.admin.node;

import com.speednet.framework.common.pojo.CommonResult;
import com.speednet.module.xray.controller.admin.node.vo.XrayRegionSaveReqVO;
import com.speednet.module.xray.dal.dataobject.node.XrayRegionDO;
import com.speednet.module.xray.service.node.XrayRegionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.speednet.framework.common.pojo.CommonResult.success;

@Tag(name="管理后台 - 节点地区")
@RestController
@RequestMapping("/xray/region")
@Validated
public class XrayRegionController {
    @Resource private XrayRegionService service;
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAnyPermissions('xray:region:query','xray:node:query','xray:node:create','xray:node:update','xray:node:import')")
    public CommonResult<List<XrayRegionDO>> list() { return success(service.list()); }
    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('xray:region:create')")
    public CommonResult<Long> create(@Valid @RequestBody XrayRegionSaveReqVO request) { return success(service.create(request)); }
    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('xray:region:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody XrayRegionSaveReqVO request) { service.update(request); return success(true); }
}
