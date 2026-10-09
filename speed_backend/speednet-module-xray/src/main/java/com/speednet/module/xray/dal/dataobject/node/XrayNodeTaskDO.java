package com.speednet.module.xray.dal.dataobject.node;
import com.baomidou.mybatisplus.annotation.TableName;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import java.time.LocalDateTime;
@TableName("xray_node_task") @Data
public class XrayNodeTaskDO extends TenantBaseDO {
    private Long id;
    private String batchId;
    private Long nodeId;
    private Long serverId;
    private String action;
    private Integer status;
    private String message;
    private LocalDateTime endTime;
}
