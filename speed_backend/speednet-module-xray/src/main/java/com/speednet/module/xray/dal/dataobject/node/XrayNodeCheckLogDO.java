package com.speednet.module.xray.dal.dataobject.node;
import com.baomidou.mybatisplus.annotation.TableName;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
@TableName("xray_node_check_log") @Data
public class XrayNodeCheckLogDO extends TenantBaseDO {
    private Long id;
    private Long nodeId;
    private Integer configVersion;
    private Integer status;
    private Integer latencyMs;
    private String source;
    private String message;
}
