package com.speednet.module.subscription.controller.admin.vo;
import lombok.*; import com.speednet.framework.common.pojo.PageParam;
@Data @EqualsAndHashCode(callSuper=true) public class SubscriptionPageReqVO extends PageParam {
 private String keyword; private Long userId; private String orderNo; private Integer status; private Integer syncStatus; private Boolean expiring;
}
