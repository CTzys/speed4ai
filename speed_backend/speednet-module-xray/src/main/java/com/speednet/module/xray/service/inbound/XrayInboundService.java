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
    @Resource private com.speednet.module.xray.framework.ssh.SshExecutor sshExecutor;
    private static final Set<String> EDITABLE = Set.of("remark", "enable", "listen", "port", "protocol", "total", "expiryTime", "settings", "streamSettings", "sniffing");

    public Object list(Long serverId) {
        Object result = panelClient.call(serverService.get(serverId), "list", null);
        if (!(result instanceof List<?>)) throw exception(PANEL_API_FAILED, "入站列表格式错误");
        return result;
    }
    public Object get(Long serverId, Long id) { return panelClient.call(serverService.get(serverId), "get/" + id, null); }

    public String save(XrayInboundSaveReqVO request, boolean update) {
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
        if (!Boolean.TRUE.equals(config.get("enable"))) return "";
        return openFirewall(server, config);

    }
    private String openFirewall(com.speednet.module.xray.dal.dataobject.server.XrayServerDO server, Map<String, Object> config) {
        int port = ((Number) config.get("port")).intValue();
        try {
            Map<?, ?> settings = JSONUtil.toBean((String) config.get("settings"), Map.class);
            Map<?, ?> stream = JSONUtil.toBean((String) config.get("streamSettings"), Map.class);
            String protocol = (String) config.get("protocol");
            String network = String.valueOf(stream.get("network"));
            Set<String> transports = new LinkedHashSet<>();
            if ("wireguard".equals(protocol) || Set.of("kcp", "mkcp", "quic", "hysteria", "hysteria2").contains(network)) transports.add("udp");
            else if (Set.of("shadowsocks", "dokodemo-door").contains(protocol)) {
                String networks = settings.get("network") == null ? "tcp,udp" : String.valueOf(settings.get("network"));
                for (String item : networks.split(",")) if (Set.of("tcp", "udp").contains(item.trim())) transports.add(item.trim());
            } else {
                transports.add("tcp");
                if ("socks".equals(protocol) && Boolean.TRUE.equals(settings.get("udp"))) transports.add("udp");
            }
            if (transports.isEmpty()) return "入站已保存，但无法识别传输协议，请手动检查防火墙";
            String prefix = "";
            String stdin = null;
            var identity = sshExecutor.execute(server, "id -u", java.time.Duration.ofSeconds(10));
            if (identity.exitCode() != 0 || !"0".equals(identity.output().trim())) {
                var sudo = sshExecutor.execute(server, "sudo -n id -u", java.time.Duration.ofSeconds(10));
                if (sudo.exitCode() == 0 && "0".equals(sudo.output().trim())) prefix = "sudo -n ";
                else if (server.getSshPassword() != null && !server.getSshPassword().isBlank()) {
                    prefix = "sudo -S -p '' "; stdin = server.getSshPassword() + "\n";
                } else throw new IllegalStateException();
            }
            for (String transport : transports) {
                String command = prefix + "sh -c '" + com.speednet.module.xray.framework.panel.XrayFirewall.command(port, transport) + "'";
                var result = sshExecutor.execute(server, command, java.time.Duration.ofSeconds(45), stdin);
                if (result.exitCode() != 0 || !result.output().contains("SPEEDNET_FIREWALL_OPENED"))
                    return "入站已保存，端口 " + port + " 的防火墙未确认自动放行，请检查服务器防火墙及云安全组";
            }
            return "";
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "入站已保存，但端口 " + port + " 自动放行失败，请检查 SSH、root/sudo 权限及防火墙";
        }
    }
    public void delete(Long serverId, Long id) { panelClient.call(serverService.get(serverId), "del/" + id, Map.of()); }
    private void invalid(String message) { throw exception(INBOUND_CONFIG_INVALID, message); }
}
