package com.speednet.module.xray.controller.admin.install;
import com.speednet.framework.common.pojo.*;import com.speednet.module.xray.controller.admin.install.vo.XrayInstallCreateReqVO;import com.speednet.module.xray.dal.dataobject.install.*;import com.speednet.module.xray.service.install.XrayInstallService;import jakarta.annotation.Resource;import jakarta.validation.Valid;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.List;import static com.speednet.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/xray/install") public class XrayInstallController {@Resource private XrayInstallService service;
 @PostMapping("/create") @PreAuthorize("@ss.hasPermission('xray:install:create')") public CommonResult<Long> create(@Valid @RequestBody XrayInstallCreateReqVO v){return success(service.createTask(v));}
 @GetMapping("/page") @PreAuthorize("@ss.hasPermission('xray:install:query')") public CommonResult<PageResult<XrayInstallTaskDO>> page(@Valid PageParam p){return success(service.getPage(p));}
 @GetMapping("/logs") @PreAuthorize("@ss.hasPermission('xray:install:query')") public CommonResult<List<XrayInstallLogDO>> logs(@RequestParam Long taskId){return success(service.getLogs(taskId));}
}
