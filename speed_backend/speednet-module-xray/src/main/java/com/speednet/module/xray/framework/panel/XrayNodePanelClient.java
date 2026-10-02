package com.speednet.module.xray.framework.panel;
import cn.hutool.json.JSONUtil;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
@Component
public class XrayNodePanelClient {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public Map<String,Object> read(XrayServerDO server) {
        Object value=call(server,"",Map.of());
        if(value instanceof String s) value=JSONUtil.toBean(s,Map.class);
        if(!(value instanceof Map<?,?> map) || !map.containsKey("xraySetting")) throw new IllegalStateException("面板不支持 Xray 配置接口或响应格式不兼容");
        Map<String,Object> result=new LinkedHashMap<>();map.forEach((k,v)->result.put(String.valueOf(k),v));return result;
    }
    public void write(XrayServerDO server,Map<String,Object> config,String testUrl) {
        call(server,"update",Map.of("xraySetting",JSONUtil.toJsonStr(config),"outboundTestUrl",testUrl));
    }
    public Map<String,Object> config(Map<String,Object> response) {
        Object value=response.get("xraySetting");
        if(value instanceof String s)value=JSONUtil.toBean(s,Map.class);
        if(!(value instanceof Map<?,?> map)||!(map.get("outbounds") instanceof List<?>)) throw new IllegalStateException("远端配置缺少 outbounds，停止修改");
        return JSONUtil.toBean(JSONUtil.toJsonStr(map),Map.class);
    }
    private Object call(XrayServerDO server,String path,Map<String,String> form) {
        try {
            if(!Set.of("http","https").contains(server.getPanelScheme())||server.getPanelPort()==null||server.getPanelPort()<1||server.getPanelPort()>65535||server.getPanelToken()==null||server.getPanelToken().isBlank()) throw new IllegalStateException();
            String base=server.getPanelPath()==null?"":server.getPanelPath().replaceAll("^/+|/+$","");
            if(base.contains("..")||base.contains("?")||base.contains("#")||base.contains("\\"))throw new IllegalStateException();
            URI uri=new URI(server.getPanelScheme(),null,server.getHost(),server.getPanelPort(),(base.isEmpty()?"":"/"+base)+"/panel/api/xray/"+path,null,null);
            if(uri.getHost()==null)throw new IllegalStateException();
            String body=form.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
            var req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+server.getPanelToken()).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()/100!=2)throw new IllegalStateException();
            var parsed=JSONUtil.toBean(response.body(),Map.class);
            if(!Boolean.TRUE.equals(parsed.get("success")))throw new IllegalStateException();
            return parsed.get("obj");
        } catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            // Remote messages/configs may contain passwords, so never return them.
            throw new IllegalStateException("Xray 配置 API 调用失败，请检查面板 Token、连接及版本兼容性");
        }
    }
}
