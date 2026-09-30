package com.speednet.module.xray.dal.dataobject.install;
import com.baomidou.mybatisplus.annotation.TableName;import com.speednet.framework.tenant.core.db.TenantBaseDO;import lombok.Data;
@TableName("xray_install_log") @Data public class XrayInstallLogDO extends TenantBaseDO {private Long id;private Long taskId;private Integer level;private String step;private String content;}
