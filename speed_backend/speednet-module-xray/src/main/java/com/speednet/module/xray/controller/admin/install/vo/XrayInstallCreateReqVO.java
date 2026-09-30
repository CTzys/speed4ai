package com.speednet.module.xray.controller.admin.install.vo;
import jakarta.validation.constraints.NotNull;import lombok.Data;
@Data public class XrayInstallCreateReqVO {@NotNull private Long serverId;private String version;}
