package com.speednet.module.xray.dal.mysql.server;

import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.mybatis.core.mapper.BaseMapperX;
import com.speednet.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.speednet.module.xray.controller.admin.server.vo.XrayServerPageReqVO;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface XrayServerMapper extends BaseMapperX<XrayServerDO> {
    default PageResult<XrayServerDO> selectPage(XrayServerPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<XrayServerDO>()
                .likeIfPresent(XrayServerDO::getName, reqVO.getName())
                .likeIfPresent(XrayServerDO::getHost, reqVO.getHost())
                .eqIfPresent(XrayServerDO::getInstallStatus, reqVO.getInstallStatus())
                .eqIfPresent(XrayServerDO::getHealthStatus, reqVO.getHealthStatus())
                .orderByDesc(XrayServerDO::getId));
    }
}
