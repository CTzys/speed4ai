package com.speednet.module.member.controller.app.auth.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AppAuthEmailRegisterReqVO extends AppAuthEmailReqVO {

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
    private String code;
}
