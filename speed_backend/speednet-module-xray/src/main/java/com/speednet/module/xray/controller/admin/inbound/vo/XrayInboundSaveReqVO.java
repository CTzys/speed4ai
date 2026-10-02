package com.speednet.module.xray.controller.admin.inbound.vo;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Map;

@Data
public class XrayInboundSaveReqVO {
    @NotNull @Positive private Long serverId;
    @Positive private Long id;
    @NotNull private Map<String, Object> config;
}
