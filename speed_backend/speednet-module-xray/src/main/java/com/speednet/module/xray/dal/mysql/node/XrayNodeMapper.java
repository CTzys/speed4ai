package com.speednet.module.xray.dal.mysql.node;
import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.mybatis.core.mapper.BaseMapperX;
import com.speednet.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeDO;
import com.speednet.module.xray.controller.admin.node.vo.XrayNodePageReqVO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface XrayNodeMapper extends BaseMapperX<XrayNodeDO> {
    default PageResult<XrayNodeDO> selectPage(XrayNodePageReqVO req) {
        var q = new LambdaQueryWrapperX<XrayNodeDO>()
            .eqIfPresent(XrayNodeDO::getRegionId, req.getRegionId())
            .eqIfPresent(XrayNodeDO::getCityId, req.getCityId())
            .eqIfPresent(XrayNodeDO::getShelfStatus, req.getShelfStatus())
            .eqIfPresent(XrayNodeDO::getHealthStatus, req.getHealthStatus());
        if (req.getKeyword()!=null && !req.getKeyword().isBlank())
            q.and(w -> w.like(XrayNodeDO::getName,req.getKeyword()).or().like(XrayNodeDO::getHost,req.getKeyword()).or().like(XrayNodeDO::getTags,req.getKeyword()));
        return selectPage(req, q.orderByDesc(XrayNodeDO::getId));
    }
}
