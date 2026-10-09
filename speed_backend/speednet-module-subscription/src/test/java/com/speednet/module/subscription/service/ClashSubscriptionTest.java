package com.speednet.module.subscription.service;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClashSubscriptionTest {
    private String link(String protocol,String network,String name) {
        var stream=Map.<String,Object>of("network",network,"security","tls",
            "wsSettings",Map.of("path","/ws?x=1","headers",Map.of("Host","edge.example")),
            "grpcSettings",Map.of("serviceName","test service"),
            "tlsSettings",Map.of("serverName","tls.example","alpn",List.of("h2"),"privateKey","server-secret"));
        return SubscriptionConnection.uri(Map.of("enable",true,"protocol",protocol,"port",443,"settings",Map.of(),"streamSettings",stream),
            "2001:db8::1","user:secret+value",name);
    }
    @Test void convertsSupportedProtocolsAndTransportsWithoutServerSecrets() {
        for(String protocol:List.of("vmess","vless","trojan"))for(String network:List.of("tcp","ws","grpc")) {
            var proxy=ClashSubscription.proxy(link(protocol,network,"节点 A"));
            assertEquals(protocol,proxy.get("type"));assertEquals("2001:db8::1",proxy.get("server"));assertEquals(443,proxy.get("port"));
            assertEquals("user:secret+value",proxy.get(protocol.equals("trojan")?"password":"uuid"));
            assertEquals("tls.example",proxy.get(protocol.equals("trojan")?"sni":"servername"));
            if(network.equals("ws"))assertEquals("/ws?x=1",((Map<?,?>)proxy.get("ws-opts")).get("path"));
            if(network.equals("grpc"))assertEquals("test service",((Map<?,?>)proxy.get("grpc-opts")).get("grpc-service-name"));
            assertFalse(proxy.toString().contains("server-secret"));
        }
    }
    @Test void yamlRoundTripPreservesNamesAndUniqueGroupReferences() {
        var links=List.of(link("vless","ws","DIRECT"),link("vmess","tcp","DIRECT"),link("trojan","grpc","x\nproxies: # 引号 \""));
        String yaml=ClashSubscription.render(links);
        Map<?,?> config=new Yaml().load(yaml);var proxies=(List<Map<String,Object>>)config.get("proxies");
        assertEquals(3,proxies.size());assertEquals(3,proxies.stream().map(p->p.get("name")).distinct().count());
        assertFalse(proxies.stream().anyMatch(p->"DIRECT".equals(p.get("name"))));
        var group=(Map<?,?>)((List<?>)config.get("proxy-groups")).getFirst();var names=(List<?>)group.get("proxies");
        for(var p:proxies)assertTrue(names.contains(p.get("name")));
        assertEquals("MATCH,SpeedNet",((List<?>)config.get("rules")).getLast());assertFalse(yaml.contains("server-secret"));
    }
    @Test void rejectsUnsupportedSecurityAndEmptySubscriptions() {
        assertThrows(IllegalArgumentException.class,()->ClashSubscription.render(List.of()));
        assertThrows(IllegalArgumentException.class,()->ClashSubscription.proxy("vless://id@host:443?security=reality"));
        assertThrows(IllegalArgumentException.class,()->ClashSubscription.proxy("trojan://id@host:443?security=none"));
    }
}
