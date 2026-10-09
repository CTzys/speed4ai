package com.speednet.custom;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Uses the existing customer authentication and tenant-scoped subscription service. */
@RestController
public class SubscriptionController {
    private final PackageController packages;

    public SubscriptionController(PackageController packages) { this.packages = packages; }

    @GetMapping("/custom-api/subscription/info")
    public Object info(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return packages.info(request);
    }
    @GetMapping("/custom-api/subscription/nodes")
    public Object nodes(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return packages.forward("nodes", request, null);
    }
    @GetMapping("/custom-api/subscription/clash-link")
    public Object clashLink(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return packages.forward("clash-link", request, null);
    }
    @GetMapping("/custom-api/subscription/clash")
    public org.springframework.http.ResponseEntity<String> clash(HttpServletRequest request) {
        return packages.clash(request);
    }

}
