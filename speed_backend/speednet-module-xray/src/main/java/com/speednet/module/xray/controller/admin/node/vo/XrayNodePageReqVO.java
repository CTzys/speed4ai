package com.speednet.module.xray.controller.admin.node.vo;
import com.speednet.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true)
public class XrayNodePageReqVO extends PageParam {
    private String keyword;
    private Long regionId;
    private Long cityId;
    private Integer shelfStatus;
    private Integer healthStatus;
}
