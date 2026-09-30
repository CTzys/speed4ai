package com.speednet.module.xray.dal.mysql.install;

import com.speednet.framework.common.pojo.PageParam;
import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.mybatis.core.mapper.BaseMapperX;
import com.speednet.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.speednet.module.xray.dal.dataobject.install.XrayInstallTaskDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface XrayInstallTaskMapper extends BaseMapperX<XrayInstallTaskDO> {
    default XrayInstallTaskDO selectRunningByServerId(Long serverId) {
        return selectOne(new LambdaQueryWrapperX<XrayInstallTaskDO>()
                .eq(XrayInstallTaskDO::getServerId, serverId)
                .in(XrayInstallTaskDO::getStatus, 0, 1));
    }
    default PageResult<XrayInstallTaskDO> selectPage(PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<XrayInstallTaskDO>()
                .orderByDesc(XrayInstallTaskDO::getId));
    }
}
