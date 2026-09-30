package com.speednet.custom.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record EmailRequest(@NotBlank @Email String email) {}
    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Pattern(regexp = "\\d{6}", message = "验证码必须是六位数字") String code,
                                  @NotBlank @Size(min = 8, max = 72, message = "密码长度应为 8 到 72 位") String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record SessionResponse(String accessToken, String refreshToken, String expiresAt, MemberResponse member) {}
    public record MemberResponse(long id, String email, String nickname, String avatar) {}
}
