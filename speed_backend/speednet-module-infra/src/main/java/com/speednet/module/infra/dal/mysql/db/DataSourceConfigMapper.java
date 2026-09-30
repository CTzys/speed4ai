package com.speednet.module.infra.dal.mysql.db;

import com.speednet.framework.mybatis.core.mapper.BaseMapperX;
import com.speednet.module.infra.dal.dataobject.db.DataSourceConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源配置 Mapper
 *
 * @author SpeedNet
 */
@Mapper
public interface DataSourceConfigMapper extends BaseMapperX<DataSourceConfigDO> {
}
