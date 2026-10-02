package com.speednet.module.xray.dal.dataobject.node;
import com.baomidou.mybatisplus.annotation.*;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import com.speednet.framework.mybatis.core.type.EncryptTypeHandler;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;
@TableName(value="xray_node", autoResultMap=true) @Data
public class XrayNodeDO extends TenantBaseDO {
    private Long id;
    private String name;
    private String host;
    private Integer port;
    private Integer authType;
    private String username;
    @ToString.Exclude @TableField(typeHandler=EncryptTypeHandler.class) private String password;
    private String identityKey;
    private Long regionId;
    private Long cityId;
    private String tags;
    private String remark;
    private Integer shelfStatus;
    private Integer healthStatus;
    private Integer latencyMs;
    private String lastError;
    private LocalDateTime lastCheckTime;
    private Integer configVersion;
}
