package com.speednet.module.xray.service.install;

import com.speednet.module.xray.dal.dataobject.install.XrayInstallTaskDO;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.dal.mysql.install.XrayInstallLogMapper;
import com.speednet.module.xray.dal.mysql.install.XrayInstallTaskMapper;
import com.speednet.module.xray.dal.mysql.server.XrayServerMapper;
import com.speednet.module.xray.framework.ssh.SshExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class XrayInstallServiceImplTest {
    @Test void installationPassesPortBelowTenThousand() throws Exception {
        service.executeTask(8L);
        var command = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ssh, atLeastOnce()).execute(eq(server), command.capture(), any(), nullable(String.class));
        String installer = command.getAllValues().stream().filter(value -> value.contains("install.sh"))
                .findFirst().orElseThrow();
        var match = java.util.regex.Pattern.compile("XUI_PANEL_PORT=(\\d+)").matcher(installer);
        org.junit.jupiter.api.Assertions.assertTrue(match.find());
        int port = Integer.parseInt(match.group(1));
        org.junit.jupiter.api.Assertions.assertTrue(port >= 1024 && port < 10000);
    }
    private final XrayInstallServiceImpl service = new XrayInstallServiceImpl();
    private final XrayServerMapper servers = mock(XrayServerMapper.class);
    private final XrayInstallTaskMapper tasks = mock(XrayInstallTaskMapper.class);
    private final XrayInstallLogMapper logs = mock(XrayInstallLogMapper.class);
    private final SshExecutor ssh = mock(SshExecutor.class);
    private final XrayServerDO server = new XrayServerDO().setId(3L);
    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(service, "serverMapper", servers); ReflectionTestUtils.setField(service, "taskMapper", tasks);
        ReflectionTestUtils.setField(service, "logMapper", logs); ReflectionTestUtils.setField(service, "sshExecutor", ssh);
        when(tasks.selectById(8L)).thenReturn(new XrayInstallTaskDO().setId(8L).setServerId(3L).setStatus(0));
        when(servers.selectById(3L)).thenReturn(server);
        when(ssh.execute(eq(server), anyString(), any(), nullable(String.class))).thenReturn(new SshExecutor.CommandResult(0, "ok"));
        when(ssh.execute(eq(server), eq("id -u"), any())).thenReturn(new SshExecutor.CommandResult(0, "0"));
    }
    @Test void successfulInstallSavesDetectedSettings() throws Exception {
        when(ssh.execute(eq(server), contains("-show true"), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(0, "Panel is secure with SSL\nport: 2053\nwebBasePath: /secret/\n"));
        service.executeTask(8L);
        verify(servers).updateById(argThat((XrayServerDO value) -> Integer.valueOf(2053).equals(value.getPanelPort())
                && "https".equals(value.getPanelScheme()) && "/secret/".equals(value.getPanelPath()) && value.getPanelToken() == null));
        verify(tasks).updateById(argThat((XrayInstallTaskDO value) -> Integer.valueOf(2).equals(value.getStatus())));
    }
    @Test void discoveryFailureDoesNotFailRunningInstallation() throws Exception {
        when(ssh.execute(eq(server), contains("-show true"), any(), nullable(String.class))).thenReturn(new SshExecutor.CommandResult(1, "error"));
        service.executeTask(8L);
        verify(servers, never()).updateById(argThat((XrayServerDO value) -> value.getPanelPort() != null));
        verify(tasks).updateById(argThat((XrayInstallTaskDO value) -> Integer.valueOf(2).equals(value.getStatus())));
        verify(logs).insert(argThat((com.speednet.module.xray.dal.dataobject.install.XrayInstallLogDO value) -> Integer.valueOf(1).equals(value.getLevel()) && "读取面板连接信息".equals(value.getStep()) && value.getContent().contains("手动配置")));
    }
    @Test void generatesTokenWithoutLoggingPlaintext() throws Exception {
        String token = "test-token-12345678901234567890";
        when(ssh.execute(eq(server), anyString(), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(0, "apiToken: " + token + "\n"));
        when(ssh.execute(eq(server), contains("-getApiToken=true"), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(0, "apiToken: " + token + "\n"));
        service.executeTask(8L);
        verify(servers).updateById(argThat((XrayServerDO value) -> token.equals(value.getPanelToken())));
        verify(logs, never()).insert(argThat((com.speednet.module.xray.dal.dataobject.install.XrayInstallLogDO value) -> value.getContent().contains(token)));
    }
    @Test void preservesExistingTokenWithoutRegeneration() throws Exception {
        server.setPanelToken("existing-token");
        service.executeTask(8L);
        verify(ssh, never()).execute(eq(server), contains("-getApiToken=true"), any(), nullable(String.class));
    }
    @Test void unsupportedTokenCommandKeepsInstallationSuccessful() throws Exception {
        when(ssh.execute(eq(server), contains("-getApiToken=true"), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(1, "unsupported"));
        service.executeTask(8L);
        verify(servers, never()).updateById(argThat((XrayServerDO value) -> value.getPanelToken() != null));
        verify(tasks).updateById(argThat((XrayInstallTaskDO value) -> Integer.valueOf(2).equals(value.getStatus())));
    }

    @Test void manualSyncReadsInstalledServerWithoutCreatingInstallationTask() throws Exception {
        when(ssh.execute(eq(server), contains("install-result.env"), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(0, "Username: test-admin\nPassword: test-password\n"));
        server.setPanelToken("existing-token");
        when(ssh.execute(eq(server), contains("-show true"), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(0, "Panel is secure with SSL\nport: 2053\nwebBasePath: /secret/\n"));
        service.syncPanelConfig(3L);
        verify(servers).updateById(argThat((XrayServerDO value) -> Integer.valueOf(2053).equals(value.getPanelPort())));
        verifyNoInteractions(tasks, logs);
        verify(ssh, never()).execute(eq(server), contains("install.sh"), any(), nullable(String.class));
    }

    @Test void opensDetectedPanelPortDuringInstallation() throws Exception {
        when(ssh.execute(eq(server), contains("-show true"), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(0, "Panel is secure with SSL\nport: 3706\nwebBasePath: /secret/\n"));
        when(ssh.execute(eq(server), contains("ufw allow 3706/tcp"), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(0, "SPEEDNET_FIREWALL_OPENED"));
        service.executeTask(8L);
        verify(ssh).execute(eq(server), contains("ufw allow 3706/tcp"), any(), nullable(String.class));
    }
    @Test void missingActualPortNeverChangesFirewall() throws Exception {
        when(ssh.execute(eq(server), contains("-show true"), any(), nullable(String.class)))
                .thenReturn(new SshExecutor.CommandResult(1, "unknown"));
        service.executeTask(8L);
        verify(ssh, never()).execute(eq(server), contains("ufw allow"), any(), nullable(String.class));
    }

}
