package com.speednet.module.xray.controller.admin.server.vo;
import com.speednet.framework.common.pojo.PageParam;import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) public class XrayServerPageReqVO extends PageParam {private String name;private String host;private Integer installStatus;private Integer healthStatus;}
