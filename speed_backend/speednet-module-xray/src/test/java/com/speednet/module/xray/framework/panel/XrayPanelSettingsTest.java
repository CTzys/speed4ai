package com.speednet.module.xray.framework.panel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XrayPanelSettingsTest {
    @Test void parsesHttpsAndBasePath() {
        assertEquals(new XrayPanelSettings("https", 2053, "/secret/"), XrayPanelSettings.parse(
                "Panel is secure with SSL\nport: 2053\nwebBasePath: /secret/\n"));
    }
    @Test void parsesHttpAndRootPathWithWindowsNewlines() {
        assertEquals(new XrayPanelSettings("http", 54321, "/"), XrayPanelSettings.parse(
                "Warning: Panel is not secure with SSL\r\nport: 54321\r\nwebBasePath: /\r\n"));
    }
    @Test void refusesIncompleteOrInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> XrayPanelSettings.parse("port: 2053\nwebBasePath: /secret/\n"));
        assertThrows(IllegalArgumentException.class, () -> XrayPanelSettings.parse("Panel is secure with SSL\nport: 99999\nwebBasePath: /\n"));
        assertThrows(IllegalArgumentException.class, () -> XrayPanelSettings.parse("Panel is secure with SSL\nport: 2053\nwebBasePath: /../\n"));
    }
}
