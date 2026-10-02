package com.speednet.module.xray.framework.panel;

import com.speednet.framework.common.exception.ServiceException;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class XrayPanelClientTest {
    private XrayServerDO server(int port) {
        return new XrayServerDO().setHost("127.0.0.1").setPanelScheme("http").setPanelPort(port)
                .setPanelPath("/secret/").setPanelToken("test-token");
    }
    @Test void sendsBearerAndBasePathAndMutationBody() throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> method = new AtomicReference<>(), auth = new AtomicReference<>(), body = new AtomicReference<>();
        http.createContext("/secret/panel/api/inbounds/add", exchange -> {
            method.set(exchange.getRequestMethod()); auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] result = "{\"success\":true,\"obj\":{\"id\":7}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, result.length); exchange.getResponseBody().write(result); exchange.close();
        });
        http.start();
        try {
            Object result = new XrayPanelClient().call(server(http.getAddress().getPort()), "add", Map.of("port", 443));
            assertEquals("POST", method.get()); assertEquals("Bearer test-token", auth.get());
            assertTrue(body.get().contains("443")); assertEquals(7, ((Number) ((Map<?, ?>) result).get("id")).intValue());
        } finally { http.stop(0); }
    }
    @Test void refusesRedirectsAndRedactsTokenFromRemoteErrors() throws Exception {
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/secret/panel/api/inbounds/list", exchange -> {
            byte[] result = "{\"success\":false,\"msg\":\"bad test-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, result.length); exchange.getResponseBody().write(result); exchange.close();
        });
        http.createContext("/secret/panel/api/inbounds/get/1", exchange -> {
            exchange.getResponseHeaders().set("Location", "/secret/panel/api/inbounds/list"); exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        http.start();
        try {
            var client = new XrayPanelClient(); var target = server(http.getAddress().getPort());
            var error = assertThrows(ServiceException.class, () -> client.call(target, "list", null));
            assertFalse(error.getMessage().contains("test-token"));
            assertTrue(assertThrows(XrayPanelClient.HttpFailure.class, () -> client.call(target, "get/1", null)).getMessage().contains("302"));
        } finally { http.stop(0); }
    }
    @Test void rejectsMissingCredentialsAndUnsafePaths() {
        var client = new XrayPanelClient(); var target = server(2053);
        target.setPanelToken(""); assertThrows(ServiceException.class, () -> client.endpoint(target, "list"));
        target.setPanelToken("token").setPanelPath("../admin"); assertThrows(ServiceException.class, () -> client.endpoint(target, "list"));
    }
    @Test void supportsV3ClientCreationUpdateAndTrafficAfterLegacy404() throws Exception {
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> created=new AtomicReference<>(),updated=new AtomicReference<>();
        http.createContext("/secret/panel/api/",exchange->{
            String path=exchange.getRequestURI().getPath();String result;
            if(path.contains("/inbounds/")){exchange.sendResponseHeaders(404,-1);exchange.close();return;}
            if(path.endsWith("/clients/add")){created.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));result="{\"success\":true}";}
            else if(path.endsWith("/clients/get/owned"))result="{\"success\":true,\"obj\":{\"client\":{\"id\":42,\"uuid\":\"owned-uuid\",\"email\":\"owned\",\"subId\":\"preserve-sub\",\"comment\":\"keep\",\"allowedIPs\":\"\"},\"inboundIds\":[7]}}";
            else if(path.endsWith("/clients/update/owned")){updated.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));result="{\"success\":true}";}
            else if(path.endsWith("/clients/traffic/owned"))result="{\"success\":true,\"obj\":{\"up\":12,\"down\":34}}";
            else throw new AssertionError(path);
            byte[] bytes=result.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });http.start();
        try {
            var client=new XrayPanelClient();var target=server(http.getAddress().getPort());
            var body=Map.<String,Object>of("id",7,"settings","{\"clients\":[{\"email\":\"owned\",\"id\":\"owned-uuid\",\"enable\":false}]}");
            client.call(target,"addClient",body);
            Map<?,?> add=cn.hutool.json.JSONUtil.toBean(created.get(),Map.class);assertTrue(add.containsKey("client"));assertEquals(7,((Number)((java.util.List<?>)add.get("inboundIds")).getFirst()).intValue());
            client.call(target,"updateClient/owned-uuid",body);
            Map<?,?> update=cn.hutool.json.JSONUtil.toBean(updated.get(),Map.class);assertEquals("owned-uuid",update.get("id"));assertEquals("keep",update.get("comment"));assertEquals("preserve-sub",update.get("subId"));assertFalse(update.containsKey("allowedIPs"));
            var traffic=(Map<?,?>)client.call(target,"getClientTraffics/owned",null);assertEquals(12,((Number)traffic.get("up")).intValue());
        }finally{http.stop(0);}
    }
    @Test void authorizationFailureNeverRetriesOnAnotherApi() throws Exception {
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        http.createContext("/",exchange->{requests.incrementAndGet();exchange.sendResponseHeaders(401,-1);exchange.close();});http.start();
        try {
            var failure=assertThrows(XrayPanelClient.HttpFailure.class,()->new XrayPanelClient().call(server(http.getAddress().getPort()),"addClient",Map.of()));
            assertEquals(401,failure.status);assertEquals(1,requests.get());
        }finally{http.stop(0);}
    }
}
