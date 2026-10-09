package com.speednet.module.xray.framework.panel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XrayPanelCredentialsTest {
    @Test void capturesLoginBeforeDatabaseCredentialsWithAnsiAndCrLf() {
        var credentials = XrayPanelCredentials.parse("\u001B[32mUsername:    panelAdmin\u001B[0m\r\n"
                + "\u001B[32mPassword:    panelSecret\u001B[0m\r\n"
                + "Port: 2053\r\nPostgreSQL Credentials\r\nUsername: dbUser\r\nPassword: dbSecret\r\n");
        assertEquals("panelAdmin", credentials.username());
        assertEquals("panelSecret", credentials.password());
    }
    @Test void rejectsOutputThatDoesNotProvidePlaintextCredentials() {
        assertThrows(IllegalArgumentException.class, () -> XrayPanelCredentials.parse(
                "Username, Password, and WebBasePath are properly set.\n"));
        assertThrows(IllegalArgumentException.class, () -> XrayPanelCredentials.parse("Username: admin\n"));
    }
}
