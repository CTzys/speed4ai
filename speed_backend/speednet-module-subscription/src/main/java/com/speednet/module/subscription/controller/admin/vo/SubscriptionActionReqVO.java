package com.speednet.module.subscription.controller.admin.vo;
import lombok.Data; import jakarta.validation.constraints.*; import java.time.LocalDateTime;
@Data public class SubscriptionActionReqVO {
 @NotNull private Long id;
 @NotBlank @Pattern(regexp="extend|add-traffic|reset-traffic|end|pause|resume|reset-link|remark") private String action;
 @Min(1) @Max(9000000000000000L) private Long bytes;
 @Min(1) @Max(3650) private Integer days;
 private LocalDateTime expiryTime;
 @Size(max=500) private String remark;
 @Size(max=64) private String orderNo;
}
