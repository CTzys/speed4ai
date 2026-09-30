package com.speednet.module.xray.service.install;

import com.speednet.framework.common.pojo.PageParam;
import com.speednet.framework.common.pojo.PageResult;
import com.speednet.module.xray.controller.admin.install.vo.XrayInstallCreateReqVO;
import com.speednet.module.xray.dal.dataobject.install.XrayInstallLogDO;
import com.speednet.module.xray.dal.dataobject.install.XrayInstallTaskDO;
import java.util.List;

public interface XrayInstallService {
    Long createTask(XrayInstallCreateReqVO reqVO);
    void executeTask(Long taskId);
    PageResult<XrayInstallTaskDO> getPage(PageParam pageParam);
    List<XrayInstallLogDO> getLogs(Long taskId);
}
