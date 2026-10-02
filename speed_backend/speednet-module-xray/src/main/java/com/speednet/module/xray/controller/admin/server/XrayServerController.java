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
    @Resource private com.speednet.module.xray.service.install.XrayInstallService installService;

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

    @PostMapping("/start") @Operation(summary = "启动 x-ui 服务")
    @PreAuthorize("@ss.hasPermission('xray:server:start')")
    public CommonResult<XrayServerRespVO> start(@RequestParam Long id) { return success(toResp(service.start(id))); }
    @PostMapping("/stop") @Operation(summary = "停止 x-ui 服务")
    @PreAuthorize("@ss.hasPermission('xray:server:stop')")
    public CommonResult<XrayServerRespVO> stop(@RequestParam Long id) { return success(toResp(service.stop(id))); }
    @PostMapping("/test-panel") @Operation(summary = "测试面板 API 连接和 Token")
    @PreAuthorize("@ss.hasPermission('xray:server:test')")
    public CommonResult<Boolean> testPanel(@RequestParam Long id) { service.testPanel(id); return success(true); }

    @PostMapping("/sync-panel-config") @Operation(summary = "从已安装服务器读取面板信息并配置 Token")
    @PreAuthorize("@ss.hasPermission('xray:server:update')")
    public CommonResult<XrayServerRespVO> syncPanelConfig(@RequestParam Long id) {
        installService.syncPanelConfig(id);
        return success(toResp(service.get(id)));
    }

    @GetMapping("/panel-credentials") @Operation(summary = "查看已保存的面板登录账号密码")
    @PreAuthorize("@ss.hasPermission('xray:server:update')")
    public CommonResult<XrayPanelCredentialsRespVO> panelCredentials(@RequestParam Long id,
            jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        XrayServerDO server = service.get(id);
        XrayPanelCredentialsRespVO credentials = new XrayPanelCredentialsRespVO();
        credentials.setUsername(server.getPanelUsername());
        credentials.setPassword(server.getPanelPassword());
        return success(credentials);
    }

    private XrayServerRespVO toResp(XrayServerDO server) { return fillSecretFlags(BeanUtils.toBean(server, XrayServerRespVO.class), server); }
    private XrayServerRespVO fillSecretFlags(XrayServerRespVO resp, XrayServerDO server) {
        resp.setSshCredentialConfigured(hasText(server.getSshPassword()) || hasText(server.getSshPrivateKey()));
        resp.setPanelCredentialConfigured(hasText(server.getPanelUsername()) && hasText(server.getPanelPassword()));
        resp.setPanelTokenConfigured(hasText(server.getPanelToken()));
        return resp;
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
