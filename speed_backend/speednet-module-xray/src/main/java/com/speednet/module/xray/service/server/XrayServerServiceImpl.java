package com.speednet.module.xray.service.server;

import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.common.util.object.BeanUtils;
import com.speednet.module.xray.controller.admin.server.vo.XrayServerPageReqVO;
import com.speednet.module.xray.controller.admin.server.vo.XrayServerSaveReqVO;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.dal.mysql.server.XrayServerMapper;
import com.speednet.module.xray.framework.ssh.SshExecutor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.speednet.module.xray.enums.ErrorCodeConstants.SERVER_NOT_EXISTS;
import static com.speednet.module.xray.enums.ErrorCodeConstants.SSH_CONNECT_FAILED;

@Service
@Validated
public class XrayServerServiceImpl implements XrayServerService {
    @Resource private XrayServerMapper serverMapper;
    @Resource private SshExecutor sshExecutor;

    @Override public Long create(XrayServerSaveReqVO reqVO) {
        XrayServerDO server = BeanUtils.toBean(reqVO, XrayServerDO.class);
        server.setInstallStatus(0).setHealthStatus(0);
        serverMapper.insert(server);
        return server.getId();
    }

    @Override public void update(XrayServerSaveReqVO reqVO) {
        XrayServerDO old = validateExists(reqVO.getId());
        XrayServerDO update = BeanUtils.toBean(reqVO, XrayServerDO.class);
        // Blank secrets mean "keep existing"; secrets are never returned to the browser.
        if (update.getSshPassword() == null || update.getSshPassword().isBlank()) update.setSshPassword(old.getSshPassword());
        if (update.getSshPrivateKey() == null || update.getSshPrivateKey().isBlank()) update.setSshPrivateKey(old.getSshPrivateKey());
        if (update.getSshKeyPassphrase() == null || update.getSshKeyPassphrase().isBlank()) update.setSshKeyPassphrase(old.getSshKeyPassphrase());
        if (update.getPanelToken() == null || update.getPanelToken().isBlank()) update.setPanelToken(old.getPanelToken());
        serverMapper.updateById(update);
    }

    @Override public void delete(Long id) { validateExists(id); serverMapper.deleteById(id); }
    @Override public XrayServerDO get(Long id) { return validateExists(id); }
    @Override public PageResult<XrayServerDO> getPage(XrayServerPageReqVO reqVO) { return serverMapper.selectPage(reqVO); }

    @Override public void testSsh(Long id) {
        try { sshExecutor.test(validateExists(id)); }
        catch (Exception e) { throw exception(SSH_CONNECT_FAILED, safeMessage(e)); }
    }

    @Override public XrayServerDO checkHealth(Long id) {
        XrayServerDO server = validateExists(id);
        XrayServerDO update = new XrayServerDO().setId(id).setLastCheckTime(LocalDateTime.now());
        try {
            SshExecutor.CommandResult result = sshExecutor.execute(server,
                    "systemctl is-active --quiet x-ui && printf RUNNING || printf STOPPED", Duration.ofSeconds(15));
            boolean running = result.exitCode() == 0 && result.output().contains("RUNNING");
            update.setHealthStatus(running ? 1 : 2).setLastError(running ? null : "x-ui 服务未运行");
        } catch (Exception e) {
            update.setHealthStatus(3).setLastError(safeMessage(e));
        }
        serverMapper.updateById(update);
        return validateExists(id);
    }

    private XrayServerDO validateExists(Long id) {
        XrayServerDO server = serverMapper.selectById(id);
        if (server == null) throw exception(SERVER_NOT_EXISTS);
        return server;
    }
    private String safeMessage(Exception e) {
        String value = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
