package com.speednet.module.xray.service.server;

import com.speednet.framework.common.exception.ServiceException;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.dal.mysql.server.XrayServerMapper;
import com.speednet.module.xray.framework.panel.XrayPanelClient;
import com.speednet.module.xray.framework.ssh.SshExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XrayServerServiceImplTest {
    XrayServerServiceImpl service = new XrayServerServiceImpl();
    XrayServerMapper mapper = mock(XrayServerMapper.class);
    SshExecutor ssh = mock(SshExecutor.class);
    XrayPanelClient panel = mock(XrayPanelClient.class);
    XrayServerDO server = new XrayServerDO().setId(3L).setInstallStatus(2);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "serverMapper", mapper);
        ReflectionTestUtils.setField(service, "sshExecutor", ssh);
        ReflectionTestUtils.setField(service, "panelClient", panel);
        when(mapper.selectById(3L)).thenReturn(server);
    }
    @Test void rootStartVerifiesActiveBeforeRecordingSuccess() throws Exception {
        when(ssh.execute(eq(server), eq("id -u"), any())).thenReturn(new SshExecutor.CommandResult(0, "0\n"));
        when(ssh.execute(eq(server), contains("systemctl start x-ui"), any(), isNull())).thenReturn(new SshExecutor.CommandResult(0, "active\n"));
        service.start(3L);
        verify(mapper).updateById(argThat((XrayServerDO value) -> Integer.valueOf(1).equals(value.getHealthStatus())));
    }
    @Test void nonRootStopUsesSudoAndVerifiesInactive() throws Exception {
        when(ssh.execute(eq(server), eq("id -u"), any())).thenReturn(new SshExecutor.CommandResult(0, "1000"));
        when(ssh.execute(eq(server), eq("sudo -n id -u"), any())).thenReturn(new SshExecutor.CommandResult(0, "0"));
        when(ssh.execute(eq(server), startsWith("sudo -n sh -c 'systemctl stop x-ui"), any(), isNull())).thenReturn(new SshExecutor.CommandResult(0, "inactive\n"));
        service.stop(3L);
        verify(mapper).updateById(argThat((XrayServerDO value) -> Integer.valueOf(2).equals(value.getHealthStatus())));
    }
    @Test void failedStartDoesNotRecordSuccess() throws Exception {
        when(ssh.execute(eq(server), eq("id -u"), any())).thenReturn(new SshExecutor.CommandResult(0, "0"));
        when(ssh.execute(eq(server), contains("systemctl start x-ui"), any(), isNull())).thenReturn(new SshExecutor.CommandResult(1, "failed"));
        assertThrows(ServiceException.class, () -> service.start(3L));
        verify(mapper, never()).updateById(any(XrayServerDO.class));
    }
    @Test void installInProgressBlocksServiceControl() {
        server.setInstallStatus(1);
        assertThrows(ServiceException.class, () -> service.stop(3L));
        verifyNoInteractions(ssh);
    }
    @Test void panelConnectionTestCallsAuthenticatedReadApi() {
        service.testPanel(3L);
        verify(panel).call(server, "list", null);
    }
}
