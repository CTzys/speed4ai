package com.speednet.module.xray.service.inbound;

import com.speednet.framework.common.exception.ServiceException;
import com.speednet.module.xray.controller.admin.inbound.vo.XrayInboundSaveReqVO;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.framework.panel.XrayPanelClient;
import com.speednet.module.xray.service.server.XrayServerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XrayInboundServiceTest {
    XrayInboundService service = new XrayInboundService();
    XrayServerService servers = mock(XrayServerService.class);
    XrayPanelClient client = mock(XrayPanelClient.class);
    XrayServerDO server = new XrayServerDO().setId(3L);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "serverService", servers); ReflectionTestUtils.setField(service, "panelClient", client);
        when(servers.get(3L)).thenReturn(server);
    }
    Map<String, Object> config() {
        return new HashMap<>(Map.of("port", 443, "protocol", "vless", "enable", true,
                "settings", "{\"clients\":[],\"decryption\":\"none\"}", "streamSettings", Map.of("network", "tcp"), "sniffing", Map.of()));
    }
    @Test void updatePreservesCountersTagAndExtraRemoteFields() {
        Map<String, Object> old = config(); old.put("up", 900L); old.put("tag", "existing-tag"); old.put("trafficReset", "never");
        when(client.call(server, "get/8", null)).thenReturn(old);
        var change = config(); change.put("port", 8443); change.put("up", 0); change.put("tag", "injected");
        service.save(new XrayInboundSaveReqVO().setServerId(3L).setId(8L).setConfig(change), true);
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(client).call(eq(server), eq("update/8"), body.capture());
        assertEquals(900L, body.getValue().get("up")); assertEquals("existing-tag", body.getValue().get("tag"));
        assertEquals("never", body.getValue().get("trafficReset")); assertEquals(8443, body.getValue().get("port"));
        assertInstanceOf(String.class, body.getValue().get("streamSettings"));
    }
    @Test void invalidPortOrJsonNeverMutatesRemote() {
        var bad = config(); bad.put("port", 65536);
        assertThrows(ServiceException.class, () -> service.save(new XrayInboundSaveReqVO().setServerId(3L).setConfig(bad), false));
        bad.put("port", 443); bad.put("settings", "[]");
        assertThrows(ServiceException.class, () -> service.save(new XrayInboundSaveReqVO().setServerId(3L).setConfig(bad), false));
        verifyNoInteractions(client);
    }
    @Test void inaccessibleServerNeverCallsRemote() {
        when(servers.get(4L)).thenThrow(new IllegalStateException("not found"));
        assertThrows(IllegalStateException.class, () -> service.delete(4L, 8L)); verifyNoInteractions(client);
    }
}
