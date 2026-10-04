package com.speednet.module.subscription.dal.dataobject;
import com.baomidou.mybatisplus.annotation.*;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import com.speednet.framework.mybatis.core.type.EncryptTypeHandler;
import lombok.*;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="subscription",autoResultMap=true)
public class SubscriptionDO extends TenantBaseDO {
 private Long id; private Long planId; private Long baseTotalBytes; private Long extraUsedBytes;
 private String number; private Long userId; private String source; private String orderNo;
 private LocalDateTime startTime; private LocalDateTime expiryTime; private LocalDateTime endedTime;
 private Boolean paused; private Integer status; private Integer syncStatus; private String lastError;
 private Boolean unlimited; private Long totalBytes; private Long usedUpload; private Long usedDownload;
 private Long lifetimeUpload; private Long lifetimeDownload; private String trafficMode;
 private String resetMode; private Integer resetIntervalDays; private LocalDateTime nextResetTime;
 private Integer nodeLimit; private Long regionId; private Long cityId; private String remark;
 @ToString.Exclude @TableField(typeHandler=EncryptTypeHandler.class) private String token;
 @ToString.Exclude private String tokenHash;
 private LocalDateTime lastTrafficTime; private LocalDateTime lastSyncTime;
}
