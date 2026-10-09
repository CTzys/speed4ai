package com.speednet.module.xray.service.node;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeDO;
import java.util.*;
public final class XrayNodeConfig {
    private XrayNodeConfig() {}
    public static Map<String,Object> outbound(XrayNodeDO node,String tag) {
        Map<String,Object> server=new LinkedHashMap<>(Map.of("address",node.getHost(),"port",node.getPort()));
        if(node.getAuthType()==1)server.put("users",List.of(Map.of("user",node.getUsername(),"pass",node.getPassword())));
        return new LinkedHashMap<>(Map.of("tag",tag,"protocol","socks","settings",Map.of("servers",List.of(server))));
    }
    @SuppressWarnings("unchecked")
    public static void apply(Map<String,Object> config,XrayNodeDO node,String tag,boolean remove) {
        if(!(config.get("outbounds") instanceof List<?> list))throw new IllegalStateException("配置缺少出站列表");
        if(remove && config.get("routing") instanceof Map<?,?> routing && routing.get("rules") instanceof List<?> rules)
            for(Object rule:rules)if(rule instanceof Map<?,?> map && tag.equals(map.get("outboundTag")))throw new IllegalStateException("该出站仍被路由引用，请先迁移用户或移除路由");
        if(remove && config.get("routing") instanceof Map<?,?> routing && routing.get("balancers") instanceof List<?> balancers)
            for(Object balancer:balancers)if(balancer instanceof Map<?,?> map && map.get("selector") instanceof List<?> selectors)
                for(Object selector:selectors)if(selector instanceof String s && tag.startsWith(s))throw new IllegalStateException("该出站仍被负载均衡引用，请先移除引用");
        if(list.isEmpty())throw new IllegalStateException("远端没有默认出站，请先配置服务器默认出站");
        List<Object> copy=new ArrayList<>(list);
        int index=-1;
        for(int i=0;i<copy.size();i++)if(copy.get(i) instanceof Map<?,?> map && tag.equals(map.get("tag"))){
            if(index>=0)throw new IllegalStateException("远端存在重复出站标识，请先修复配置");
            if(!"socks".equals(map.get("protocol")))throw new IllegalStateException("出站标识与非 SOCKS5 配置冲突，停止修改");
            index=i;
        }
        if(remove){
            if(index==0)throw new IllegalStateException("该节点是默认出站，请先调整默认出站");
            if(index>=0)copy.remove(index);
        }else if(index>=0){
            Map<String,Object> replacement=new LinkedHashMap<>();
            ((Map<?,?>)copy.get(index)).forEach((k,v)->replacement.put(String.valueOf(k),v));
            replacement.putAll(outbound(node,tag));copy.set(index,replacement);
        }else copy.add(outbound(node,tag));
        config.put("outbounds",copy);
    }
    public static boolean matches(Map<String,Object> config,XrayNodeDO node,String tag) {
        if(!(config.get("outbounds") instanceof List<?> list))return false;
        var expected=outbound(node,tag);
        for(Object item:list)if(item instanceof Map<?,?> map && tag.equals(map.get("tag"))) {
            // Compare only managed fields; other panel-added fields are preserved separately.
            return expected.get("protocol").equals(map.get("protocol"))&&expected.get("settings").equals(map.get("settings"));
        }
        return false;
    }
}
