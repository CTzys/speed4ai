package com.speednet.module.xray.service.node;

import com.speednet.framework.common.exception.ErrorCode;
import com.speednet.framework.common.util.object.BeanUtils;
import com.speednet.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.speednet.module.xray.controller.admin.node.vo.XrayCitySaveReqVO;
import com.speednet.module.xray.dal.dataobject.node.XrayCityDO;
import com.speednet.module.xray.dal.mysql.node.XrayCityMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class XrayCityService {
    private static final ErrorCode INVALID = new ErrorCode(1_012_004_002, "城市操作失败：{}");
    @Resource private XrayCityMapper mapper;
    @Resource private XrayRegionService regions;

    public List<XrayCityDO> list(Long regionId) {
        if (regionId != null) regions.require(regionId);
        return mapper.selectList(new LambdaQueryWrapperX<XrayCityDO>()
                .eqIfPresent(XrayCityDO::getRegionId, regionId)
                .orderByAsc(XrayCityDO::getSort).orderByAsc(XrayCityDO::getId));
    }

    public XrayCityDO require(Long id) {
        XrayCityDO city = id == null ? null : mapper.selectById(id);
        if (city == null) throw exception(INVALID, "请选择已创建的城市");
        return city;
    }

    public void validateSelection(Long regionId, Long cityId, Long currentCityId) {
        XrayCityDO city = require(cityId);
        if (!Objects.equals(city.getRegionId(), regionId))
            throw exception(INVALID, "所选城市不属于当前地区");
        if (city.getStatus() != 0 && !Objects.equals(cityId, currentCityId))
            throw exception(INVALID, "该城市已停用，请选择启用的城市");
        if (regions.require(regionId).getStatus() != 0 && !Objects.equals(cityId, currentCityId))
            throw exception(INVALID, "该地区已停用，不能选择新的城市");
    }

    public Long create(XrayCitySaveReqVO request) {
        regions.require(request.getRegionId());
        XrayCityDO city = BeanUtils.toBean(request, XrayCityDO.class).setId(null).setName(request.getName().trim());
        try { mapper.insert(city); }
        catch (DuplicateKeyException e) { throw exception(INVALID, "该地区的城市名称已存在"); }
        return city.getId();
    }

    public void update(XrayCitySaveReqVO request) {
        XrayCityDO old = require(request.getId());
        regions.require(request.getRegionId());
        if (!Objects.equals(old.getRegionId(), request.getRegionId()))
            throw exception(INVALID, "城市所属地区不能修改，请在目标地区新增城市");
        XrayCityDO city = BeanUtils.toBean(request, XrayCityDO.class).setName(request.getName().trim());
        try { mapper.updateById(city); }
        catch (DuplicateKeyException e) { throw exception(INVALID, "该地区的城市名称已存在"); }
    }
}
