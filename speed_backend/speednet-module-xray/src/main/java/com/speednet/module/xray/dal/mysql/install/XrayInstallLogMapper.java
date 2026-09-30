package com.speednet.module.xray.dal.mysql.install;

import com.speednet.framework.mybatis.core.mapper.BaseMapperX;
import com.speednet.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.speednet.module.xray.dal.dataobject.install.XrayInstallLogDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface XrayInstallLogMapper extends BaseMapperX<XrayInstallLogDO> {
    default List<XrayInstallLogDO> selectByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<XrayInstallLogDO>()
                .eq(XrayInstallLogDO::getTaskId, taskId).orderByAsc(XrayInstallLogDO::getId));
    }
}
