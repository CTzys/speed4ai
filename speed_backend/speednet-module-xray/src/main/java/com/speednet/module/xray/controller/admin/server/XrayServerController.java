package com.speednet.module.xray.controller.admin.server;

import com.speednet.framework.common.pojo.CommonResult;
import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.common.util.object.BeanUtils;
import com.speednet.module.xray.controller.admin.server.vo.*;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.service.server.XrayServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.speednet.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Xray 服务器")
@RestController
@RequestMapping("/xray/server")
@Validated
public class XrayServerController {
    @Resource private XrayServerService service;

    @PostMapping("/create") @Operation(summary = "新增服务器")
    @PreAuthorize("@ss.hasPermission('xray:server:create')")
    public CommonResult<Long> create(@Valid @RequestBody XrayServerSaveReqVO reqVO) { return success(service.create(reqVO)); }
    @PutMapping("/update") @Operation(summary = "修改服务器")
    @PreAuthorize("@ss.hasPermission('xray:server:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody XrayServerSaveReqVO reqVO) { service.update(reqVO); return success(true); }
    @DeleteMapping("/delete") @Operation(summary = "删除服务器")
    @PreAuthorize("@ss.hasPermission('xray:server:delete')")
    public CommonResult<Boolean> delete(@RequestParam Long id) { service.delete(id); return success(true); }
    @GetMapping("/get") @Operation(summary = "服务器详情")
    @PreAuthorize("@ss.hasPermission('xray:server:query')")
    public CommonResult<XrayServerRespVO> get(@RequestParam Long id) { return success(toResp(service.get(id))); }
    @GetMapping("/page") @Operation(summary = "服务器分页")
    @PreAuthorize("@ss.hasPermission('xray:server:query')")
    public CommonResult<PageResult<XrayServerRespVO>> page(@Valid XrayServerPageReqVO reqVO) {
        PageResult<XrayServerDO> page = service.getPage(reqVO);
        return success(new PageResult<>(page.getList().stream().map(this::toResp).toList(), page.getTotal()));
    }
    @PostMapping("/test-ssh") @Operation(summary = "测试 SSH")
    @PreAuthorize("@ss.hasPermission('xray:server:test')")
    public CommonResult<Boolean> testSsh(@RequestParam Long id) { service.testSsh(id); return success(true); }
    @PostMapping("/check-health") @Operation(summary = "检测运行状态")
    @PreAuthorize("@ss.hasPermission('xray:server:check')")
    public CommonResult<XrayServerRespVO> check(@RequestParam Long id) { return success(toResp(service.checkHealth(id))); }

    private XrayServerRespVO toResp(XrayServerDO server) { return fillSecretFlags(BeanUtils.toBean(server, XrayServerRespVO.class), server); }
    private XrayServerRespVO fillSecretFlags(XrayServerRespVO resp, XrayServerDO server) {
        resp.setSshCredentialConfigured(hasText(server.getSshPassword()) || hasText(server.getSshPrivateKey()));
        resp.setPanelTokenConfigured(hasText(server.getPanelToken()));
        return resp;
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
