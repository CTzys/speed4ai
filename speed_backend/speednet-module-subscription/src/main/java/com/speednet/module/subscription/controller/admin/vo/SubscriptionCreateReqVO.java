package com.speednet.module.subscription.controller.admin.vo;
import lombok.Data; import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import java.time.LocalDateTime; import java.util.List;
@Data public class SubscriptionCreateReqVO {
 @NotNull private Long userId;
 @Size(max=64) private String orderNo;
 @NotNull private LocalDateTime startTime; @NotNull private LocalDateTime expiryTime;
 @NotNull private Boolean unlimited;
 @NotNull @Min(0) @Max(9000000000000000L) private Long totalBytes;
 @NotBlank @Pattern(regexp="both|download") private String trafficMode="both";
 @NotBlank @Pattern(regexp="none|monthly|interval|monthly_first|monthly_expiry|yearly_first|yearly_expiry") private String resetMode="none";
 @Min(1) @Max(365) private Integer resetIntervalDays=30;
 @NotNull @Min(1) @Max(20) private Integer nodeLimit=1;
 private Long regionId; private Long cityId;
 @Size(max=500) private String remark;
 @NotEmpty @Size(max=20) private List<@Valid Assignment> assignments;
 @Data public static class Assignment {
  @NotNull private Long nodeId; @NotNull private Long serverId; @NotNull private Long inboundId;
  @NotBlank @Size(max=255) private String publicHost;
 }
}
