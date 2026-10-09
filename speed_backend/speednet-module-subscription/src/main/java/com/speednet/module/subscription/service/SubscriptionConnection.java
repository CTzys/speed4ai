package com.speednet.module.subscription.service;

import cn.hutool.json.JSONUtil;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Publishes client-side fields only; server private keys are never serialized. */
public final class SubscriptionConnection {
    private SubscriptionConnection() {}
    @SuppressWarnings("unchecked")
    public static Map<String,Object> object(Object value) {
        if(value instanceof String text)value=JSONUtil.toBean(text,Map.class);
        if(!(value instanceof Map<?,?>))throw new IllegalArgumentException("入站 JSON 配置不兼容");
        return JSONUtil.toBean(JSONUtil.toJsonStr(value),Map.class);
    }
    public static String host(String host) {
        if(host==null||host.isBlank()||!host.equals(host.trim())||host.matches(".*[\\s/@?#\\\\].*"))throw new IllegalArgumentException("连接地址应为域名或 IP，不能包含协议或端口");
        String normalized=host.replaceAll("^\\[|\\]$","");
        try {URI u=new URI("http",null,normalized,443,"/",null,null);if(u.getHost()==null)throw new IllegalArgumentException();}
        catch(Exception e){throw new IllegalArgumentException("连接地址格式错误");}
        return normalized;
    }
    public static void validate(Map<String,Object> inbound) {
        if(!Boolean.TRUE.equals(inbound.get("enable")))throw new IllegalArgumentException("请选择已启用的入站");
        if(!Set.of("vmess","vless","trojan").contains(String.valueOf(inbound.get("protocol"))))throw new IllegalArgumentException("第一版订阅支持 VMess、VLESS、Trojan 入站");
        Map<String,Object> stream=object(inbound.get("streamSettings"));
        String network=String.valueOf(stream.getOrDefault("network","tcp"));
        if(!Set.of("tcp","ws","grpc").contains(network))throw new IllegalArgumentException("第一版订阅支持 TCP、WebSocket、gRPC 传输");
        if(!Set.of("none","tls").contains(String.valueOf(stream.getOrDefault("security","none"))))throw new IllegalArgumentException("该入站的安全类型暂不支持生成订阅，请选择普通或 TLS 入站");
        if("tcp".equals(network)&&stream.get("tcpSettings") instanceof Map<?,?> tcp&&tcp.get("header") instanceof Map<?,?> header&&!"none".equals(header.getOrDefault("type",null)))throw new IllegalArgumentException("暂不支持 TCP HTTP 伪装入站");
        if(!(inbound.get("port") instanceof Number port)||port.intValue()<1||port.intValue()>65535)throw new IllegalArgumentException("入站端口无效");
        object(inbound.get("settings"));
    }
    public static String uri(Map<String,Object> inbound,String host,String credential,String name) {
        validate(inbound);host=host(host);
        Map<String,Object> stream=object(inbound.get("streamSettings"));
        String protocol=String.valueOf(inbound.get("protocol"));String network=String.valueOf(stream.getOrDefault("network","tcp"));String security=String.valueOf(stream.getOrDefault("security","none"));
        String path="", headerHost="", sni="";
        if("ws".equals(network)&&stream.get("wsSettings") instanceof Map<?,?> ws){path=String.valueOf(ws.getOrDefault("path",null));if("null".equals(path))path="";if(ws.get("headers") instanceof Map<?,?> headers&&headers.get("Host")!=null)headerHost=String.valueOf(headers.get("Host"));}
        if("grpc".equals(network)&&stream.get("grpcSettings") instanceof Map<?,?> grpc&&grpc.get("serviceName")!=null)path=String.valueOf(grpc.get("serviceName"));
        if("tls".equals(security)&&stream.get("tlsSettings") instanceof Map<?,?> tls&&tls.get("serverName")!=null)sni=String.valueOf(tls.get("serverName"));
        if("tls".equals(security)&&sni.isBlank())sni=host;
        if("vmess".equals(protocol)) {
            Map<String,Object> link=new LinkedHashMap<>();link.put("v","2");link.put("ps",name);link.put("add",host);link.put("port",String.valueOf(inbound.get("port")));link.put("id",credential);link.put("aid","0");link.put("scy","auto");link.put("net",network);link.put("type","none");link.put("host",headerHost);link.put("path",path);link.put("tls","tls".equals(security)?"tls":"");link.put("sni",sni);
            return "vmess://"+Base64.getEncoder().encodeToString(JSONUtil.toJsonStr(link).getBytes(StandardCharsets.UTF_8));
        }
        List<String> query=new ArrayList<>(List.of("type="+encode(network),"security="+encode(security)));
        if("vless".equals(protocol))query.add("encryption=none");
        if(!sni.isBlank())query.add("sni="+encode(sni));
        if(!headerHost.isBlank())query.add("host="+encode(headerHost));
        if(!path.isBlank())query.add(("grpc".equals(network)?"serviceName=":"path=")+encode(path));
        if("tls".equals(security)&&stream.get("tlsSettings") instanceof Map<?,?> tls&&tls.get("alpn") instanceof List<?> alpn&&!alpn.isEmpty())query.add("alpn="+encode(String.join(",",alpn.stream().map(String::valueOf).toList())));
        return protocol+"://"+encode(credential)+"@"+(host.contains(":")?"["+host+"]":host)+":"+inbound.get("port")+"?"+String.join("&",query)+"#"+encode(name);
    }
    private static String encode(String text){return URLEncoder.encode(text,StandardCharsets.UTF_8).replace("+","%20");}
}
