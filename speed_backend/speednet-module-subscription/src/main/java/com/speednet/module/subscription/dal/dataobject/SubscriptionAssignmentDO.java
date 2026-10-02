package com.speednet.module.subscription.dal.dataobject;
import com.baomidou.mybatisplus.annotation.*;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import com.speednet.framework.mybatis.core.type.EncryptTypeHandler;
import lombok.*;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="xray_node_assignment",autoResultMap=true)
public class SubscriptionAssignmentDO extends TenantBaseDO {
 private Long id; private Long nodeId; private Long serverId; private Long userId; private Long subscriptionId;
 private Long clientId; private LocalDateTime assignedTime; private LocalDateTime expiryTime;
 private Integer subscriptionStatus; private Integer authorizationStatus;
}
