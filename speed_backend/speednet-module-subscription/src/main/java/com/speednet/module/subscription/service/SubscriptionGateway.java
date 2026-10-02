package com.speednet.module.subscription.service;

import cn.hutool.json.JSONUtil;
import com.speednet.module.xray.service.node.XrayNodeConfig;
import com.speednet.module.subscription.dal.dataobject.*;
import com.speednet.module.xray.service.node.*;
import com.speednet.module.xray.service.server.XrayServerService;
import com.speednet.module.xray.framework.panel.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.time.ZoneId;
import java.util.*;

@Component
public class SubscriptionGateway {
    @Resource private XrayNodeService nodes;
    @Resource private XrayServerService servers;
    @Resource private XrayPanelClient panel;
    @Resource private XrayNodePanelClient xray;

    public Map<String,Object> inbound(Long serverId,Long inboundId) {
        Object result=panel.call(servers.get(serverId),"get/"+inboundId,null);
        return SubscriptionConnection.object(result);
    }
    public record Traffic(long upload,long download) {}
    public Traffic traffic(SubscriptionClientDO c) {
        return nodes.withServerLock(c.getServerId(),()->{
            Object raw;
            try {
                raw=panel.call(servers.get(c.getServerId()),"getClientTraffics/"+c.getEmail(),null);
            } catch(com.speednet.framework.common.exception.ServiceException | XrayPanelClient.HttpFailure e) {
                // Some panels return HTTP 404 for a client that has never been created.
                // Verify the inbound before treating the missing client as zero usage.
                raw=null;
            }
            if(raw==null) {
                var in=inbound(c.getServerId(),c.getInboundId());
                var settings=SubscriptionConnection.object(in.get("settings"));
                if(!(settings.get("clients") instanceof List<?> list))throw new IllegalStateException("入站客户端列表缺失，无法确认流量");
                if(list.stream().noneMatch(v->v instanceof Map<?,?> m&&c.getEmail().equals(m.get("email"))))return new Traffic(0,0);
                if(in.get("clientStats") instanceof List<?> stats) {
                    raw=stats.stream().filter(v->v instanceof Map<?,?> m&&c.getEmail().equals(m.get("email"))).findFirst().orElse(null);
                }
            }
            if(!(raw instanceof Map<?,?> result)||!(result.get("up") instanceof Number up)||!(result.get("down") instanceof Number down)||up.longValue()<0||down.longValue()<0)throw new IllegalStateException("客户端流量响应缺失，请检查面板统计配置");
            return new Traffic(up.longValue(),down.longValue());
        });
    }
    public void prepareClient(SubscriptionDO s,SubscriptionClientDO c) {
        sync(s,c,false,true);
    }
    public String sync(SubscriptionDO s,SubscriptionClientDO c,boolean enabled) {
        return sync(s,c,enabled,false);
    }
    private String sync(SubscriptionDO s,SubscriptionClientDO c,boolean enabled,boolean createDisabled) {
        return nodes.withServerLock(c.getServerId(),()->{
            var server=servers.get(c.getServerId());
            Map<String,Object> in=inbound(c.getServerId(),c.getInboundId());
            if(!c.getProtocol().equals(in.get("protocol")))throw new IllegalStateException("入站协议已经变化，请重新分配客户端");
            Map<String,Object> settings=SubscriptionConnection.object(in.get("settings"));
            if(!(settings.get("clients") instanceof List<?> existing))throw new IllegalStateException("入站缺少 clients 列表");
            List<Map<String,Object>> owned=new ArrayList<>();
            for(Object value:existing) {
                var client=SubscriptionConnection.object(value);
                String key="trojan".equals(c.getProtocol())?"password":"id";
                if(c.getEmail().equals(client.get("email"))) {
                    if(!c.getCredential().equals(client.get(key)))throw new IllegalStateException("远端客户端认证与本地不一致，停止覆盖");
                    owned.add(client);
                } else if(c.getCredential().equals(client.get(key)))throw new IllegalStateException("远端认证凭据被其他客户端使用，停止覆盖");
            }
            if(owned.size()>1)throw new IllegalStateException("远端存在重复客户端，请先修复");
            String uri="";
            if(enabled) {
                SubscriptionConnection.validate(in);
                uri=SubscriptionConnection.uri(in,c.getPublicHost(),c.getCredential(),c.getConnectionName());
                var node=nodes.require(c.getNodeId());
                if(c.getNodeVersion()==0 && node.getShelfStatus()!=1)throw new IllegalStateException("节点已下架，请重新选择上架节点");
                if(!Boolean.TRUE.equals(c.getRemoteCreated())||!Objects.equals(c.getNodeVersion(),node.getConfigVersion()))nodes.prepareSubscriptionNode(c.getNodeId(),c.getServerId());
                configureRoute(c,true); // Route must exist before enabling a client to avoid default-egress leakage.
            }
            if(enabled||createDisabled||!owned.isEmpty()) {
                Map<String,Object> client=owned.isEmpty()?new LinkedHashMap<>():new LinkedHashMap<>(owned.getFirst());
                client.put("email",c.getEmail());client.put("trojan".equals(c.getProtocol())?"password":"id",c.getCredential());
                if("vmess".equals(c.getProtocol()))client.put("alterId",0);
                client.put("enable",enabled);client.put("expiryTime",s.getExpiryTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                client.put("limitIp",0);client.put("flow","");client.put("reset",0);
                // The aggregate quota is enforced by our periodic reconciliation. Panel ceilings add a per-client guard.
                long remaining=Boolean.TRUE.equals(s.getUnlimited())?0:Math.max(0,s.getTotalBytes()-SubscriptionPolicy.used(s));
                long already=Math.addExact(c.getSampleUpload(),c.getSampleDownload());
                client.put("totalGB",Boolean.TRUE.equals(s.getUnlimited())||"download".equals(s.getTrafficMode())?0:Math.addExact(already,Math.max(remaining,1)));
                panel.call(server,owned.isEmpty()?"addClient":"updateClient/"+c.getCredential(),Map.of("id",c.getInboundId(),"settings",JSONUtil.toJsonStr(Map.of("clients",List.of(client)))));
                var read=inbound(c.getServerId(),c.getInboundId());
                var readSettings=SubscriptionConnection.object(read.get("settings"));
                if(!(readSettings.get("clients") instanceof List<?> list)||list.stream().noneMatch(v->v instanceof Map<?,?> m&&c.getEmail().equals(m.get("email"))&&c.getCredential().equals(m.get("trojan".equals(c.getProtocol())?"password":"id"))&&Boolean.valueOf(enabled).equals(m.get("enable"))))throw new IllegalStateException("客户端配置回读不一致，请重新同步");
            }
            if(!enabled)configureRoute(c,false);
            return uri;
        });
    }
    public void removeClient(SubscriptionClientDO c) {
        nodes.withServerLock(c.getServerId(),()->{
            var server=servers.get(c.getServerId());
            var settings=SubscriptionConnection.object(inbound(c.getServerId(),c.getInboundId()).get("settings"));
            if(!(settings.get("clients") instanceof List<?> list))throw new IllegalStateException("入站客户端列表缺失");
            String key="trojan".equals(c.getProtocol())?"password":"id";
            for(Object value:list)if(value instanceof Map<?,?> m&&c.getEmail().equals(m.get("email"))&&!c.getCredential().equals(m.get(key)))throw new IllegalStateException("远端认证与本地不一致，停止删除");
            boolean found=list.stream().anyMatch(v->v instanceof Map<?,?> m&&c.getEmail().equals(m.get("email")));
            if(found) {
                panel.call(server,c.getInboundId()+"/delClient/"+c.getCredential(),Map.of());
                var read=SubscriptionConnection.object(inbound(c.getServerId(),c.getInboundId()).get("settings"));
                if(!(read.get("clients") instanceof List<?> clients)||clients.stream().anyMatch(v->v instanceof Map<?,?> m&&c.getEmail().equals(m.get("email"))))throw new IllegalStateException("客户端删除回读失败");
            }
            return null;
        });
    }
    @SuppressWarnings("unchecked")
    private void configureRoute(SubscriptionClientDO c,boolean enable) {
        var server=servers.get(c.getServerId());
        var wrapper=xray.read(server);var config=xray.config(wrapper);
        Map<String,Object> routing=config.get("routing") instanceof Map<?,?> m?SubscriptionConnection.object(m):new LinkedHashMap<>();
        Object raw=routing.get("rules");
        if(raw!=null&&!(raw instanceof List<?>))throw new IllegalStateException("远端路由规则格式不兼容");
        List<Object> rules=new ArrayList<>(raw==null?List.of():(List<?>)raw);
        String before=JSONUtil.toJsonStr(rules);
        removeOwnedRules(rules,c.getEmail());
        String tag="speednet-node-"+c.getTenantId()+"-"+c.getNodeId();
        if(enable&&!XrayNodeConfig.matches(config,nodes.require(c.getNodeId()),tag))throw new IllegalStateException("SOCKS5 出口配置缺失或已被修改，请先重新核对节点部署");
        Map<String,Object> expected=Map.of("type","field","user",List.of(c.getEmail()),"outboundTag",tag);
        if(enable)rules.addFirst(expected); // Explicit user routing precedes broad catch-all rules.
        if(before.equals(JSONUtil.toJsonStr(rules)))return;
        routing.put("rules",rules);config.put("routing",routing);
        xray.write(server,config,String.valueOf(wrapper.getOrDefault("outboundTestUrl","https://www.google.com/generate_204")));
        var read=xray.config(xray.read(server));
        if(!(read.get("routing") instanceof Map<?,?> r)||!(r.get("rules") instanceof List<?> readRules))throw new IllegalStateException("路由回读失败");
        boolean found=readRules.stream().anyMatch(v->v instanceof Map<?,?> m&&List.of(c.getEmail()).equals(m.get("user"))&&tag.equals(m.get("outboundTag")));
        if(found!=enable)throw new IllegalStateException("用户出口路由回读不一致");
    }
    static void removeOwnedRules(List<Object> rules,String email) {
        for(Object value:rules)if(value instanceof Map<?,?> m&&m.get("user") instanceof List<?> users&&users.contains(email)&&users.size()!=1)throw new IllegalStateException("该客户端被共享路由引用，停止修改");
        rules.removeIf(value->value instanceof Map<?,?> m&&List.of(email).equals(m.get("user")));
    }
}
