package com.speednet.module.xray.dal.dataobject.node;
import com.baomidou.mybatisplus.annotation.TableName;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import java.time.LocalDateTime;
@TableName("xray_node_server") @Data
public class XrayNodeServerDO extends TenantBaseDO {
    private Long id;
    private Long nodeId;
    private Long serverId;
    private String outboundTag;
    private Integer status;
    private Integer appliedVersion;
    private LocalDateTime lastSyncTime;
    private String lastError;
}
