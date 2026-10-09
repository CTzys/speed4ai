package com.speednet.module.support.controller.app;

import com.speednet.module.support.service.*;
import com.speednet.framework.common.pojo.CommonResult;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.speednet.module.support.service.TicketPolicy.require;
import static com.speednet.module.support.service.TicketRequests.*;

@RestController
@RequestMapping("/support/tickets")
@PermitAll
public class AppTicketController {
    private final TicketService service;
    private final TicketIdentity identity;
    public AppTicketController(TicketService service, TicketIdentity identity) { this.service = service; this.identity = identity; }
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping public CommonResult<?> page(HttpServletRequest r, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size, @RequestParam(required=false) String status, @RequestParam(required=false) String category, @RequestParam(required=false) String keyword) {
        return CommonResult.success(service.page(identity.member(r), page, size, status, category, null, keyword, null, null));
    }
    @GetMapping("/unread") public CommonResult<?> unread(HttpServletRequest r) { return CommonResult.success(service.unread(identity.member(r))); }
    @com.speednet.framework.apilog.core.annotation.ApiAccessLog(requestEnable=false)
    @PostMapping public CommonResult<?> create(HttpServletRequest r, @RequestBody Create body) { return CommonResult.success(service.create(identity.member(r), body)); }
    @GetMapping("/{id}") public CommonResult<?> detail(HttpServletRequest r, @PathVariable long id, @RequestParam(defaultValue="0") long beforeSeq) { return CommonResult.success(service.detail(id, identity.member(r), beforeSeq)); }
    @com.speednet.framework.apilog.core.annotation.ApiAccessLog(requestEnable=false)
    @PostMapping("/{id}/reply") public CommonResult<?> reply(HttpServletRequest r, @PathVariable long id, @RequestBody Reply body) { service.reply(id, identity.member(r), body); return CommonResult.success(true); }
    @PostMapping("/{id}/action") public CommonResult<?> action(HttpServletRequest r, @PathVariable long id, @RequestBody Action body) {
        require(java.util.Set.of("close", "reopen").contains(body.action() == null ? "" : body.action()), "客户操作无效");
        service.action(id, identity.member(r), body); return CommonResult.success(true);
    }
    @PostMapping("/{id}/read") public CommonResult<?> read(HttpServletRequest r, @PathVariable long id, @RequestBody Read body) { service.read(id, identity.member(r), body.lastSeq()); return CommonResult.success(true); }
    @PostMapping("/attachments") public CommonResult<?> upload(HttpServletRequest r, @RequestParam MultipartFile file) throws java.io.IOException { return CommonResult.success(service.upload(identity.member(r), file)); }
    @DeleteMapping("/attachments/{id}") public CommonResult<?> removeAttachment(HttpServletRequest r, @PathVariable long id) { service.removeAttachment(id, identity.member(r)); return CommonResult.success(true); }
    @GetMapping("/attachments/{id}") public org.springframework.http.ResponseEntity<byte[]> attachment(HttpServletRequest r, @PathVariable long id) { return TicketIdentity.image(service.attachment(id, identity.member(r))); }
}
