package com.speednet.module.xray.service.install;

import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.dal.mysql.server.XrayServerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XrayInstallCredentialsTest {
    @Test void savesLoginPairWithoutConfusingDatabaseCredentials() {
        var service = new XrayInstallServiceImpl();
        var mapper = mock(XrayServerMapper.class);
        ReflectionTestUtils.setField(service, "serverMapper", mapper);
        var server = new XrayServerDO().setId(7L);
        ReflectionTestUtils.invokeMethod(service, "savePanelCredentials", server,
                "Username: panelAdmin\nPassword: panelSecret\nPort: 2053\nUsername: dbUser\nPassword: dbSecret\n");
        verify(mapper).updateById(argThat((XrayServerDO value) -> value.getId().equals(7L)
                && value.getPanelUsername().equals("panelAdmin") && value.getPanelPassword().equals("panelSecret")));
    }
    @Test void masksAnsiColoredLoginAndTokenOutput() {
        var service = new XrayInstallServiceImpl();
        String output = ReflectionTestUtils.invokeMethod(service, "sanitize",
                "\u001B[32mUsername: admin\u001B[0m\n\u001B[32mPassword: unknown-secret\u001B[0m\nAPI Token: token-secret\n",
                new XrayServerDO());
        assertFalse(output.contains("admin"));
        assertFalse(output.contains("unknown-secret"));
        assertFalse(output.contains("token-secret"));
    }
}
