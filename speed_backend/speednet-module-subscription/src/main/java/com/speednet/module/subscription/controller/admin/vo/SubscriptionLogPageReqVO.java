package com.speednet.module.subscription.controller.admin.vo;

import com.speednet.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SubscriptionLogPageReqVO extends PageParam {
    @NotNull(message = "订阅 ID 不能为空")
    private Long subscriptionId;
}
