package com.speednet.custom.auth;

import com.speednet.custom.auth.AuthDtos.*;
import com.speednet.custom.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/custom-api")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/auth/email-code")
    public ApiResponse<String> sendCode(@Valid @RequestBody EmailRequest request) {
        return ApiResponse.ok(service.sendCode(request.email()));
    }

    @PostMapping("/auth/register")
    public ApiResponse<SessionResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return ApiResponse.ok(service.register(request, http.getRemoteAddr()));
    }

    @PostMapping("/auth/login")
    public ApiResponse<SessionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ApiResponse.ok(service.login(request, http.getRemoteAddr()));
    }

    @PostMapping("/auth/refresh")
    public ApiResponse<SessionResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(service.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Boolean> logout(@RequestHeader("Authorization") String authorization) {
        service.logout(authorization.substring(7));
        return ApiResponse.ok(true);
    }

    @GetMapping("/member/me")
    public ApiResponse<MemberResponse> me(HttpServletRequest request) {
        return ApiResponse.ok(service.me((Long) request.getAttribute(AuthInterceptor.USER_ID)));
    }
}
