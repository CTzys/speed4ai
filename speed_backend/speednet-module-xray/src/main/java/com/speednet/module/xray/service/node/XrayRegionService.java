package com.speednet.module.xray.service.node;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.speednet.framework.common.exception.ErrorCode;
import com.speednet.framework.common.util.object.BeanUtils;
import com.speednet.module.xray.controller.admin.node.vo.XrayRegionSaveReqVO;
import com.speednet.module.xray.dal.dataobject.node.XrayRegionDO;
import com.speednet.module.xray.dal.mysql.node.XrayRegionMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class XrayRegionService {
    private static final ErrorCode INVALID = new ErrorCode(1_012_004_001, "地区操作失败：{}");
    @Resource private XrayRegionMapper mapper;

    public List<XrayRegionDO> list() {
        return mapper.selectList(new LambdaQueryWrapper<XrayRegionDO>()
                .orderByAsc(XrayRegionDO::getSort).orderByAsc(XrayRegionDO::getId));
    }

    public XrayRegionDO require(Long id) {
        XrayRegionDO region = id == null ? null : mapper.selectById(id);
        if (region == null) throw exception(INVALID, "请选择已创建的地区");
        return region;
    }

    public void validateSelection(Long id, Long currentId) {
        XrayRegionDO region = require(id);
        if (region.getStatus() != 0 && !Objects.equals(id, currentId))
            throw exception(INVALID, "该地区已停用，请选择启用的地区");
    }

    public Long create(XrayRegionSaveReqVO request) {
        XrayRegionDO region = BeanUtils.toBean(request, XrayRegionDO.class).setId(null).setName(request.getName().trim());
        try { mapper.insert(region); }
        catch (DuplicateKeyException e) { throw exception(INVALID, "地区名称已存在"); }
        return region.getId();
    }

    public void update(XrayRegionSaveReqVO request) {
        require(request.getId());
        XrayRegionDO region = BeanUtils.toBean(request, XrayRegionDO.class).setName(request.getName().trim());
        try { mapper.updateById(region); }
        catch (DuplicateKeyException e) { throw exception(INVALID, "地区名称已存在"); }
    }
}
