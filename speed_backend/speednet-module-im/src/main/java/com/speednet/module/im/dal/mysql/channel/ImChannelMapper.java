package com.speednet.module.im.dal.mysql.channel;

import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.mybatis.core.mapper.BaseMapperX;
import com.speednet.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.speednet.module.im.controller.admin.manager.channel.vo.channel.ImChannelPageReqVO;
import com.speednet.module.im.dal.dataobject.channel.ImChannelDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * IM 频道 Mapper
 *
 * @author SpeedNet
 */
@Mapper
public interface ImChannelMapper extends BaseMapperX<ImChannelDO> {

    default ImChannelDO selectByCode(String code) {
        return selectOne(ImChannelDO::getCode, code);
    }

    default List<ImChannelDO> selectListByStatusOrderBySort(Integer status) {
        return selectList(new LambdaQueryWrapperX<ImChannelDO>()
                .eq(ImChannelDO::getStatus, status)
                .orderByAsc(ImChannelDO::getSort));
    }

    default PageResult<ImChannelDO> selectPage(ImChannelPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ImChannelDO>()
                .likeIfPresent(ImChannelDO::getCode, reqVO.getCode())
                .likeIfPresent(ImChannelDO::getName, reqVO.getName())
                .eqIfPresent(ImChannelDO::getStatus, reqVO.getStatus())
                .orderByAsc(ImChannelDO::getSort)
                .orderByDesc(ImChannelDO::getId));
    }

}
