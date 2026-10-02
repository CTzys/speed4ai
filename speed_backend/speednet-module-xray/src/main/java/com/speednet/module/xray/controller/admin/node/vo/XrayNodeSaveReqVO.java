package com.speednet.module.xray.controller.admin.node.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class XrayNodeSaveReqVO {
    private Long id;
    @NotBlank @Size(max=128) private String name;
    @NotBlank @Size(max=255) private String host;
    @NotNull @Min(1) @Max(65535) private Integer port;
    @NotNull @Min(0) @Max(1) private Integer authType;
    @Size(max=255) private String username;
    @Size(max=255) private String password;
    @NotNull private Long regionId;
    @NotNull private Long cityId;
    @Size(max=255) private String tags;
    @Size(max=500) private String remark;
}
