package com.speednet.custom.auth;

import com.speednet.custom.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String USER_ID = "customMemberUserId";
    private final AuthService authService;
    public AuthInterceptor(AuthService authService) { this.authService = authService; }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        request.setAttribute(USER_ID, authService.authenticate(authorization.substring(7)));
        return true;
    }
}
