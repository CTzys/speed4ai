package com.speednet.module.xray.dal.dataobject.install;
import com.baomidou.mybatisplus.annotation.TableName;import com.speednet.framework.tenant.core.db.TenantBaseDO;import lombok.Data;import java.time.LocalDateTime;
@TableName("xray_install_task") @Data public class XrayInstallTaskDO extends TenantBaseDO {private Long id;private Long serverId;private String version;private Integer status;private String currentStep;private LocalDateTime startTime;private LocalDateTime endTime;private String errorMessage;}
