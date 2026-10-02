package com.speednet.module.xray.controller.admin.node;
import com.speednet.framework.common.pojo.*;
import com.speednet.framework.apilog.core.annotation.ApiAccessLog;
import com.speednet.module.xray.controller.admin.node.vo.*;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeTaskDO;
import com.speednet.module.xray.service.node.XrayNodeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.speednet.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - SOCKS5 节点") @RestController @RequestMapping("/xray/node") @Validated
public class XrayNodeController {
    @Resource private XrayNodeService service;
    @Data public static class ImportRequest {@NotBlank @Size(max=200000) private String text;}
    @Data @lombok.EqualsAndHashCode(callSuper=true) public static class ImportCommitRequest extends ImportRequest { @NotNull private Long regionId; @NotNull private Long cityId; }
    @Data public static class BatchRequest {
        @NotEmpty @Size(max=100) private List<@NotNull Long> ids;
        private Long serverId;
        private boolean up;
        private String action;
    }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('xray:node:query')")
    public CommonResult<PageResult<XrayNodeRespVO>> page(@Valid XrayNodePageReqVO req){return success(service.page(req));}
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('xray:node:query')")
    public CommonResult<XrayNodeRespVO> get(@RequestParam Long id){return success(service.get(id));}
    @GetMapping("/detail") @PreAuthorize("@ss.hasPermission('xray:node:query')")
    public CommonResult<Map<String,Object>> detail(@RequestParam Long id){return success(service.detail(id));}
    @GetMapping("/stats") @PreAuthorize("@ss.hasPermission('xray:node:query')")
    public CommonResult<Map<String,Long>> stats(){return success(service.stats());}
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('xray:node:create')") @ApiAccessLog(requestEnable=false)
    public CommonResult<Long> create(@Valid @RequestBody XrayNodeSaveReqVO req){return success(service.create(req));}
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('xray:node:update')") @ApiAccessLog(requestEnable=false)
    public CommonResult<Boolean> update(@Valid @RequestBody XrayNodeSaveReqVO req){service.update(req);return success(true);}
    @PostMapping("/import-preview") @PreAuthorize("@ss.hasPermission('xray:node:import')") @ApiAccessLog(requestEnable=false)
    public CommonResult<List<Map<String,Object>>> preview(@Valid @RequestBody ImportRequest req){return success(service.preview(req.getText()));}
    @PostMapping("/import") @PreAuthorize("@ss.hasPermission('xray:node:import')") @ApiAccessLog(requestEnable=false)
    public CommonResult<List<Map<String,Object>>> importText(@Valid @RequestBody ImportCommitRequest req){return success(service.importText(req.getText(), req.getRegionId(), req.getCityId()));}
    @PostMapping("/shelf") @PreAuthorize("@ss.hasPermission('xray:node:shelf')")
    public CommonResult<List<Map<String,Object>>> shelf(@Valid @RequestBody BatchRequest req){return success(service.shelf(req.getIds(),req.isUp()));}
    @PostMapping("/check") @PreAuthorize("@ss.hasPermission('xray:node:check')")
    public CommonResult<String> check(@Valid @RequestBody BatchRequest req){return success(service.submit(req.getIds(),null,"check"));}
    @PostMapping("/deploy") @PreAuthorize("@ss.hasPermission('xray:node:deploy')")
    public CommonResult<String> deploy(@Valid @RequestBody BatchRequest req){return success(service.submit(req.getIds(),req.getServerId(),"deploy"));}
    @PostMapping("/verify") @PreAuthorize("@ss.hasPermission('xray:node:deploy')")
    public CommonResult<String> verify(@Valid @RequestBody BatchRequest req){return success(service.submit(req.getIds(),req.getServerId(),"verify"));}
    @PostMapping("/remove") @PreAuthorize("@ss.hasPermission('xray:node:deploy')")
    public CommonResult<String> remove(@Valid @RequestBody BatchRequest req){return success(service.submit(req.getIds(),req.getServerId(),"remove"));}
    @GetMapping("/task") @PreAuthorize("@ss.hasPermission('xray:node:query')")
    public CommonResult<List<XrayNodeTaskDO>> task(@RequestParam @Size(max=36) String batchId){return success(service.task(batchId));}
}
