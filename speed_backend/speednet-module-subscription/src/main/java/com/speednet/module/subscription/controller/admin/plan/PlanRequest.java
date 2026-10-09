package com.speednet.module.subscription.controller.admin.plan;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
import java.util.*;
import java.math.BigDecimal;
import com.speednet.module.subscription.controller.admin.vo.SubscriptionCreateReqVO;
@Data
public class PlanRequest {
 private Long id;
 @NotBlank @Size(max=128) private String name;
 @JsonAlias("content") @JsonSetter(nulls=Nulls.SKIP) @Size(max=4000) private String description="";
 @JsonSetter(nulls=Nulls.SKIP) @Min(1) private int level=1;
 private boolean visible;
 @JsonSetter("show") public void legacyShow(Boolean value){visible=Boolean.TRUE.equals(value);enabled=visible;}
 private boolean enabled;
 @JsonAlias("renew") @JsonSetter(nulls=Nulls.SKIP) private boolean renewEnabled=true;
 @NotNull @Min(0) @Max(9000000000000000L) private Long totalBytes;
 @JsonAlias("group_id") private Long groupId;
 @JsonSetter(nulls=Nulls.SKIP) @Pattern(regexp="none|monthly|interval|monthly_first|monthly_expiry|yearly_first|yearly_expiry") private String resetMode="monthly";
 @JsonSetter(nulls=Nulls.SKIP) @Min(1) @Max(365) private int resetIntervalDays=30;
 @JsonSetter(nulls=Nulls.SKIP) @Pattern(regexp="both|download") private String trafficMode="both";
 @JsonSetter(nulls=Nulls.SKIP) @Min(1) @Max(20) private int nodeLimit=1;
 @JsonAlias("capacity_limit") @JsonSetter(nulls=Nulls.SKIP) @Min(0) @Max(100000000) private int capacityLimit;
 @JsonSetter(nulls=Nulls.SKIP) private int sort;
 @JsonSetter(nulls=Nulls.AS_EMPTY) @Size(max=20) private List<@Valid Price> prices=new ArrayList<>();
 @Valid @JsonSetter(nulls=Nulls.AS_EMPTY) @Size(max=20) private List<SubscriptionCreateReqVO.Assignment> assignments=new ArrayList<>();
 @JsonSetter("transfer_enable") public void transfer(BigDecimal gib){if(gib!=null)totalBytes=gib.multiply(BigDecimal.valueOf(1073741824L)).longValueExact();}
 @JsonSetter("reset_traffic_method") public void resetMethod(Integer v){if(v==null)return;resetMode=switch(v){case 0->"monthly_first";case 1->"monthly_expiry";case 2->"none";case 3->"yearly_first";case 4->"yearly_expiry";default->throw new IllegalArgumentException("流量重置方式必须为0到4");};}
 private void cycle(int months,Integer amount){if(amount==null)return;var p=new Price();p.setMonths(months);p.setPrice(amount);prices.add(p);}
 @JsonSetter("reset_price") public void resetPrice(Integer amount){if(amount==null)return;var p=new Price();p.setKind("reset");p.setPrice(amount);prices.add(p);}
 @JsonSetter("onetime_price") public void onetime(Integer amount){if(amount!=null)throw new IllegalArgumentException("一次性无固定期限套餐尚未开放，请使用周期价格");}
 @JsonSetter("speed_limit") public void speed(Integer limit){if(limit!=null&&limit!=0)throw new IllegalArgumentException("当前节点链路尚不支持套餐限速，请留空或设为0");}
 @JsonSetter("month_price") public void monthly(Integer v){cycle(1,v);}
 @JsonSetter("quarter_price") public void quarterly(Integer v){cycle(3,v);}
 @JsonSetter("half_year_price") public void halfYear(Integer v){cycle(6,v);}
 @JsonSetter("year_price") public void yearly(Integer v){cycle(12,v);}
 @JsonSetter("two_year_price") public void twoYears(Integer v){cycle(24,v);}
 @JsonSetter("three_year_price") public void threeYears(Integer v){cycle(36,v);}
 @Data public static class Price {
  @JsonSetter(nulls=Nulls.SKIP) @Size(max=64) private String name="";
  @JsonSetter(nulls=Nulls.SKIP) @Pattern(regexp="period|traffic|reset") private String kind="period";
  @JsonSetter(nulls=Nulls.SKIP) @Min(1) @Max(36) private int months=1;
  @NotNull @Min(0) @Max(100000000) private Integer price;
  @JsonSetter(nulls=Nulls.SKIP) @Min(0) @Max(9000000000000000L) private long bytes;
  private boolean enabled=true;
 }
 @Data public static class NodeGroup {
  private Long id;
  @NotBlank @Size(max=128) private String name;
  @Valid @JsonSetter(nulls=Nulls.AS_EMPTY) @Size(max=20) private List<SubscriptionCreateReqVO.Assignment> assignments=new ArrayList<>();
 }
}
