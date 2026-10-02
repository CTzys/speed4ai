package com.speednet.module.xray.dal.dataobject.node;

import com.baomidou.mybatisplus.annotation.TableName;
import com.speednet.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;

@TableName("xray_city")
@Data
public class XrayCityDO extends TenantBaseDO {
    private Long id;
    private Long regionId;
    private String name;
    private Integer sort;
    private Integer status;
}
