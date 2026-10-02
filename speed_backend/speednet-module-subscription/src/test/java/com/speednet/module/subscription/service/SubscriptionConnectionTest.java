package com.speednet.module.subscription.service;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class SubscriptionConnectionTest {
 Map<String,Object> inbound(String protocol,Map<String,Object> stream){return Map.of("enable",true,"protocol",protocol,"port",443,"settings","{\"clients\":[]}","streamSettings",stream);}
 @Test void vmessPublishesStableCredentialsAndNoPrivateServerSettings(){String secret="server-private-material";var stream=Map.<String,Object>of("network","ws","security","tls","wsSettings",Map.of("path","/ws","headers",Map.of("Host","edge.example")),"tlsSettings",Map.of("serverName","edge.example","certificates",List.of(Map.of("key",secret))));String link=SubscriptionConnection.uri(inbound("vmess",stream),"edge.example","uuid-123","美国-纽约");String decoded=new String(Base64.getDecoder().decode(link.substring(8)),StandardCharsets.UTF_8);var data=JSONUtil.toBean(decoded,Map.class);assertEquals("uuid-123",data.get("id"));assertEquals("/ws",data.get("path"));assertEquals("tls",data.get("tls"));assertFalse(decoded.contains(secret));}
 @Test void vlessSupportsIpv6GrpcAndTls(){String link=SubscriptionConnection.uri(inbound("vless",Map.of("network","grpc","security","tls","grpcSettings",Map.of("serviceName","test service"))),"2001:db8::1","uuid","节点 A");assertTrue(link.startsWith("vless://uuid@[2001:db8::1]:443?"));assertTrue(link.contains("serviceName=test%20service"));assertTrue(link.contains("encryption=none"));}
 @Test void rejectsUnsupportedTransportSecurityAndDisabledInbound(){assertThrows(IllegalArgumentException.class,()->SubscriptionConnection.validate(inbound("vmess",Map.of("network","tcp","security","reality"))));assertThrows(IllegalArgumentException.class,()->SubscriptionConnection.validate(inbound("vmess",Map.of("network","kcp","security","none"))));var disabled=new HashMap<>(inbound("vmess",Map.of("network","tcp")));disabled.put("enable",false);assertThrows(IllegalArgumentException.class,()->SubscriptionConnection.validate(disabled));}
 @Test void rejectsUrlAndCredentialsAsPublicHost(){for(String value:List.of("https://example.com","example.com:443","u@host","host/path","host?x=1","host\\x"," host"))assertThrows(IllegalArgumentException.class,()->SubscriptionConnection.host(value),value);assertEquals("example.com",SubscriptionConnection.host("example.com"));assertEquals("2001:db8::1",SubscriptionConnection.host("[2001:db8::1]"));}
}
