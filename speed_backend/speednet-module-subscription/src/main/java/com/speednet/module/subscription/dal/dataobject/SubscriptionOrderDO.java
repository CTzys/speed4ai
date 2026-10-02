package com.speednet.module.subscription.dal.dataobject;
import com.baomidou.mybatisplus.annotation.*;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import com.speednet.framework.mybatis.core.type.EncryptTypeHandler;
import lombok.*;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName(value="subscription_order",autoResultMap=true)
public class SubscriptionOrderDO extends TenantBaseDO {
 private Long id; private Long subscriptionId; private String orderNo; private String purpose;
}
