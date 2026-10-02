package com.speednet.module.xray.controller.admin.node.vo;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class XrayRegionSaveReqVO {
    private Long id;
    @NotBlank @Size(max=64) private String name;
    @NotNull @Min(0) @Max(99999) private Integer sort;
    @NotNull @Min(0) @Max(1) private Integer status;
}
