package com.speednet.module.xray.service.inbound;

import cn.hutool.json.JSONUtil;
import com.speednet.module.xray.controller.admin.inbound.vo.XrayInboundSaveReqVO;
import com.speednet.module.xray.framework.panel.XrayPanelClient;
import com.speednet.module.xray.service.server.XrayServerService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.speednet.module.xray.enums.ErrorCodeConstants.*;

@Service
public class XrayInboundService {
    @Resource private XrayServerService serverService;
    @Resource private XrayPanelClient panelClient;
    private static final Set<String> EDITABLE = Set.of("remark", "enable", "listen", "port", "protocol", "total", "expiryTime", "settings", "streamSettings", "sniffing");

    public Object list(Long serverId) {
        Object result = panelClient.call(serverService.get(serverId), "list", null);
        if (!(result instanceof List<?>)) throw exception(PANEL_API_FAILED, "入站列表格式错误");
        return result;
    }
    public Object get(Long serverId, Long id) { return panelClient.call(serverService.get(serverId), "get/" + id, null); }

    public void save(XrayInboundSaveReqVO request, boolean update) {
        var server = serverService.get(request.getServerId()); // Tenant-scoped lookup before accessing the remote panel.
        Map<String, Object> config = new LinkedHashMap<>();
        if (update) {
            if (request.getId() == null) throw exception(INBOUND_CONFIG_INVALID, "缺少入站 ID");
            Object old = panelClient.call(server, "get/" + request.getId(), null);
            if (!(old instanceof Map<?, ?> existing)) throw exception(INBOUND_CONFIG_INVALID, "入站不存在");
            existing.forEach((key, value) -> config.put(String.valueOf(key), value));
        }
        request.getConfig().forEach((key, value) -> { if (EDITABLE.contains(key)) config.put(key, value); });
        if (!(config.get("protocol") instanceof String protocol) || protocol.isBlank()) invalid("协议不能为空");
        if (!(config.get("port") instanceof Number port) || port.doubleValue() != port.intValue() || port.intValue() < 1 || port.intValue() > 65535) invalid("端口应为 1–65535 的整数");
        if (!(config.get("enable") instanceof Boolean)) invalid("启用状态不能为空");
        for (String field : List.of("total", "expiryTime")) {
            config.putIfAbsent(field, 0L);
            if (!(config.get(field) instanceof Number value) || value.doubleValue() < 0 || value.doubleValue() != value.longValue()) invalid(field + " 应为非负整数");
        }
        for (String field : List.of("settings", "streamSettings", "sniffing")) {
            Object value = config.get(field);
            try {
                if (value instanceof String text) value = JSONUtil.toBean(text, Map.class);
                if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException();
                // JSON strings work with both legacy and current 3x-ui versions.
                config.put(field, JSONUtil.toJsonStr(value));
            } catch (Exception e) { invalid(field + " 必须是 JSON 对象"); }
        }
        if (update) config.put("id", request.getId());
        else { config.put("up", 0); config.put("down", 0); }
        panelClient.call(server, update ? "update/" + request.getId() : "add", config);
    }
    public void delete(Long serverId, Long id) { panelClient.call(serverService.get(serverId), "del/" + id, Map.of()); }
    private void invalid(String message) { throw exception(INBOUND_CONFIG_INVALID, message); }
}
