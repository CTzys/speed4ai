package com.speednet.module.subscription.dal.dataobject;
import com.baomidou.mybatisplus.annotation.*;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import com.speednet.framework.mybatis.core.type.EncryptTypeHandler;
import lombok.*;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="subscription_client",autoResultMap=true)
public class SubscriptionClientDO extends TenantBaseDO {
 private Long id; private Long subscriptionId; private Long nodeId; private Long serverId; private Long inboundId;
 private String protocol; private String email;
 @ToString.Exclude @TableField(typeHandler=EncryptTypeHandler.class) private String credential;
 private String publicHost; private String connectionName;
 @ToString.Exclude @TableField(typeHandler=EncryptTypeHandler.class) private String connectionUri;
 private Boolean released; private Integer syncStatus; private Boolean remoteCreated;
 private Integer nodeVersion; private String lastError; private LocalDateTime assignedTime; private LocalDateTime releasedTime;
 private Long sampleUpload; private Long sampleDownload; private Long usedUpload; private Long usedDownload;
 private LocalDateTime lastTrafficTime;
}
