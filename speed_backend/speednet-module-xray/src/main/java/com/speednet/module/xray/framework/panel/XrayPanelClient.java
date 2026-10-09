package com.speednet.module.xray.framework.panel;

import cn.hutool.json.JSONUtil;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.speednet.module.xray.enums.ErrorCodeConstants.*;

@Component
public class XrayPanelClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public static class HttpFailure extends IllegalStateException {
        public final int status;
        public HttpFailure(int status) {
            super("面板接口 HTTP " + status + "，请检查接口兼容性、连接与授权");
            this.status=status;
        }
    }
    URI endpoint(XrayServerDO server, String endpoint) {
        return endpoint(server,"inbounds",endpoint);
    }
    private URI endpoint(XrayServerDO server,String group,String endpoint) {
        try {
            String scheme = server.getPanelScheme();
            Integer port = server.getPanelPort();
            String host = server.getHost();
            if (!("http".equals(scheme) || "https".equals(scheme)) || port == null || port < 1 || port > 65535
                    || host == null || host.isBlank() || server.getPanelToken() == null || server.getPanelToken().isBlank()) {
                throw new IllegalArgumentException();
            }
            String path = server.getPanelPath() == null ? "" : server.getPanelPath().trim();
            path = path.replaceAll("^/+|/+$", "");
            if (path.contains("..") || path.contains("?") || path.contains("#") || path.contains("\\")) throw new IllegalArgumentException();
            URI uri = new URI(scheme, null, host, port, (path.isEmpty() ? "" : "/" + path) + "/panel/api/" + group + "/" + endpoint, null, null);
            if (uri.getHost() == null) throw new IllegalArgumentException();
            return uri;
        } catch (Exception e) { throw exception(PANEL_CONFIG_INVALID); }
    }

    public Object call(XrayServerDO server, String endpoint, Map<String, Object> body) {
        try { return request(server,"inbounds",endpoint,body); }
        catch(HttpFailure e) {
            if(e.status!=404)throw e;
            if(endpoint.startsWith("getClientTraffics/"))return request(server,"clients","traffic/"+endpoint.substring("getClientTraffics/".length()),null);
            if("addClient".equals(endpoint)||endpoint.startsWith("updateClient/")) {
                Map<?,?> settings=JSONUtil.toBean(String.valueOf(body.get("settings")),Map.class);
                List<?> entries=(List<?>)settings.get("clients");
                if(entries==null||entries.size()!=1)throw new IllegalArgumentException("新版客户端接口仅允许单客户端操作");
                Map<String,Object> client=new LinkedHashMap<>();((Map<?,?>)entries.getFirst()).forEach((k,v)->client.put(String.valueOf(k),v));
                String email=String.valueOf(client.get("email"));
                if("addClient".equals(endpoint))return request(server,"clients","add",Map.of("client",client,"inboundIds",List.of(body.get("id"))));
                Map<?,?> detail=(Map<?,?>)request(server,"clients","get/"+email,null);
                List<?> ids=(List<?>)detail.get("inboundIds");
                if(ids==null||ids.size()!=1||!String.valueOf(ids.getFirst()).equals(String.valueOf(body.get("id"))))throw new IllegalStateException("客户端关联多个入站或入站不一致，停止覆盖");
                Map<String,Object> full=new LinkedHashMap<>();((Map<?,?>)detail.get("client")).forEach((k,v)->full.put(String.valueOf(k),v));
                // In v3 the record id is numeric; UUID is a separate field in the hydrated record.
                full.keySet().retainAll(Set.of("email","subId","uuid","security","password","flow","reverse","auth","limitIp","limitHwid","totalGB","expiryTime","enable","tgId","group","comment","reset","resetDay","resetMax","trafficReset","trafficResetDay","secret","adTag"));
                Object credential=client.remove("id");
                if(credential!=null) {
                    if(!credential.equals(full.get("uuid")))throw new IllegalStateException("远端客户端 UUID 与本地不一致，停止覆盖");
                    full.put("id",credential);
                }
                if(credential==null&&full.get("uuid") instanceof String uuid&&!uuid.isBlank())full.put("id",uuid);
                full.remove("uuid");
                full.putAll(client);
                return request(server,"clients","update/"+email,full);
            }
            if(endpoint.contains("/delClient/")) {
                String inboundId=endpoint.substring(0,endpoint.indexOf("/delClient/"));
                Map<?,?> in=(Map<?,?>)request(server,"inbounds","get/"+inboundId,null);
                Object settingsRaw=in.get("settings");Map<?,?> settings=settingsRaw instanceof Map<?,?> m?m:JSONUtil.toBean(String.valueOf(settingsRaw),Map.class);
                String credential=endpoint.substring(endpoint.indexOf("/delClient/")+"/delClient/".length());
                for(Object value:(List<?>)settings.get("clients")) {
                    Map<?,?> client=(Map<?,?>)value;
                    if(credential.equals(client.get("id"))||credential.equals(client.get("password")))return request(server,"clients",client.get("email")+"/detach",Map.of("inboundIds",List.of(Long.valueOf(inboundId))));
                }
                throw new IllegalStateException("客户端不存在，停止删除");
            }
            throw e;
        }
    }
    private Object request(XrayServerDO server,String group,String endpoint,Map<String,Object> body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint(server,group,endpoint)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + server.getPanelToken()).header("Accept", "application/json");
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSONUtil.toJsonStr(body)));
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpFailure(response.statusCode());
            }
            // Do not log remote bodies: inbound configurations contain client passwords and private keys.
            Map<?, ?> result = JSONUtil.toBean(response.body(), Map.class);
            if (!Boolean.TRUE.equals(result.get("success"))) {
                String message = String.valueOf(result.getOrDefault("msg", null));
                message = message.replace(server.getPanelToken(), "******");
                throw exception(PANEL_API_FAILED, message.substring(0, Math.min(message.length(), 300)));
            }
            return result.get("obj");
        } catch (HttpFailure e) { throw e; } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(PANEL_API_FAILED, "请求被中断，请刷新确认远端状态");
        } catch (com.speednet.framework.common.exception.ServiceException e) { throw e; }
        catch (Exception e) { throw exception(PANEL_API_FAILED, "请求失败或响应格式错误，请检查连接并刷新确认远端状态"); }
    }
}
