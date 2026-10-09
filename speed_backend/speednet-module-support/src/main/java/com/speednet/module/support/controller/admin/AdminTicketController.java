package com.speednet.module.support.controller.admin;

import com.speednet.module.support.service.*;
import com.speednet.framework.common.pojo.CommonResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.speednet.module.support.service.TicketRequests.*;

@RestController
@RequestMapping("/support/tickets")
@PreAuthorize("@ss.hasPermission('support:ticket:query')")
public class AdminTicketController {
    private final TicketService service;
    private final TicketIdentity identity;
    private final com.speednet.module.system.api.permission.PermissionApi permissions;
    public AdminTicketController(TicketService service, TicketIdentity identity, com.speednet.module.system.api.permission.PermissionApi permissions) { this.service = service; this.identity = identity; this.permissions = permissions; }
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    private Actor actor(String permission) {
        var actor = identity.staff();
        TicketPolicy.require(permissions.hasAnyPermissions(actor.id(), "support:ticket:query") && (permission == null || permissions.hasAnyPermissions(actor.id(), permission)), "没有工单操作权限");
        return actor;
    }
    @GetMapping public CommonResult<?> page(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(required=false) String status, @RequestParam(required=false) String category, @RequestParam(required=false) String view, @RequestParam(required=false) String keyword, @RequestParam(required=false) Long memberId, @RequestParam(required=false) Long assigneeId) {
        return CommonResult.success(service.page(actor(null), page, size, status, category, view, keyword, memberId, assigneeId));
    }
    @GetMapping("/staff") public CommonResult<?> staff() { return CommonResult.success(service.staff(actor("support:ticket:assign"))); }
    @GetMapping("/{id}") public CommonResult<?> detail(@PathVariable long id, @RequestParam(defaultValue="0") long beforeSeq) { return CommonResult.success(service.detail(id, actor(null), beforeSeq)); }
    @GetMapping("/{id}/events") public CommonResult<?> events(@PathVariable long id, @RequestParam(defaultValue="0") long beforeId) { return CommonResult.success(service.events(id, actor(null), beforeId)); }
    @com.speednet.framework.apilog.core.annotation.ApiAccessLog(requestEnable=false)
    @PostMapping("/{id}/reply") public CommonResult<?> reply(@PathVariable long id, @RequestBody Reply body) { service.reply(id, actor("support:ticket:reply"), body); return CommonResult.success(true); }
    @PostMapping("/{id}/action") public CommonResult<?> action(@PathVariable long id, @RequestBody Action body) {
        String permission = switch (body.action() == null ? "" : body.action()) {
            case "claim", "assign" -> "support:ticket:assign";
            case "priority" -> "support:ticket:manage";
            case "close" -> "support:ticket:close";
            case "reopen" -> "support:ticket:reply";
            default -> throw new IllegalArgumentException("未知工单操作");
        };
        service.action(id, actor(permission), body); return CommonResult.success(true);
    }
    @PostMapping("/{id}/read") public CommonResult<?> read(@PathVariable long id, @RequestBody Read body) { service.read(id, actor(null), body.lastSeq()); return CommonResult.success(true); }
    @PostMapping("/attachments") public CommonResult<?> upload(@RequestParam MultipartFile file) throws java.io.IOException { return CommonResult.success(service.upload(actor("support:ticket:reply"), file)); }
    @DeleteMapping("/attachments/{id}") public CommonResult<?> removeAttachment(@PathVariable long id) { service.removeAttachment(id, actor("support:ticket:reply")); return CommonResult.success(true); }
    @GetMapping("/attachments/{id}") public org.springframework.http.ResponseEntity<byte[]> attachment(@PathVariable long id) { return TicketIdentity.image(service.attachment(id, actor(null))); }
}
