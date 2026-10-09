package com.speednet.module.xray.controller.admin.node.vo;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class XrayNodeRespVO {
    private Long id;
    private String name;
    private String host;
    private Integer port;
    private Integer authType;
    private String username;
    private boolean passwordConfigured;
    private Long regionId;
    private String region;
    private Long cityId;
    private String city;
    private String tags;
    private String remark;
    private Integer shelfStatus;
    private Integer healthStatus;
    private Integer latencyMs;
    private String lastError;
    private LocalDateTime lastCheckTime;
    private LocalDateTime createTime;
    private Integer configVersion;
    private long deployedServerCount;
    private long serverCount;
    private long activeUserCount;
    private long expiredUserCount;
}
