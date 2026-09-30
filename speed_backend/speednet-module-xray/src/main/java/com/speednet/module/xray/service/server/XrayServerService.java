package com.speednet.module.xray.service.server;

import com.speednet.framework.common.pojo.PageResult;
import com.speednet.module.xray.controller.admin.server.vo.XrayServerPageReqVO;
import com.speednet.module.xray.controller.admin.server.vo.XrayServerSaveReqVO;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;

public interface XrayServerService {
    Long create(XrayServerSaveReqVO reqVO);
    void update(XrayServerSaveReqVO reqVO);
    void delete(Long id);
    XrayServerDO get(Long id);
    PageResult<XrayServerDO> getPage(XrayServerPageReqVO reqVO);
    void testSsh(Long id);
    XrayServerDO checkHealth(Long id);
}
