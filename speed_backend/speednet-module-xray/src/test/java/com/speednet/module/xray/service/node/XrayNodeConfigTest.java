package com.speednet.module.xray.service.node;
import cn.hutool.json.JSONUtil;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeDO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class XrayNodeConfigTest {
    XrayNodeDO node(){return new XrayNodeDO().setHost("example.com").setPort(1080).setAuthType(1).setUsername("u").setPassword("p");}
    @Test void preservesOtherOutboundsAndRoutingAndIsIdempotent(){
        var direct=Map.of("tag","direct","protocol","freedom");var routing=Map.of("rules",List.of(Map.of("outboundTag","direct")));
        Map<String,Object> config=new LinkedHashMap<>(Map.of("outbounds",new ArrayList<>(List.of(direct)),"routing",routing,"dns",Map.of("servers",List.of("1.1.1.1"))));
        XrayNodeConfig.apply(config,node(),"managed",false);XrayNodeConfig.apply(config,node(),"managed",false);
        assertEquals(2,((List<?>)config.get("outbounds")).size());assertEquals(direct,((List<?>)config.get("outbounds")).getFirst());assertEquals(routing,config.get("routing"));
        assertTrue(XrayNodeConfig.matches(JSONUtil.toBean(JSONUtil.toJsonStr(config),Map.class),node(),"managed"));
        assertFalse(XrayNodeConfig.matches(config,node().setPassword("changed"),"managed"));
        XrayNodeConfig.apply(config,node(),"managed",true);assertEquals(List.of(direct),config.get("outbounds"));
    }
    @Test void removalRejectsRoutingAndBalancerReferences(){
        var config=new HashMap<String,Object>(Map.of("outbounds",List.of(XrayNodeConfig.outbound(node(),"managed")),"routing",Map.of("rules",List.of(Map.of("outboundTag","managed")))));
        assertThrows(IllegalStateException.class,()->XrayNodeConfig.apply(config,node(),"managed",true));
        config.put("routing",Map.of("balancers",List.of(Map.of("selector",List.of("man")))));
        assertThrows(IllegalStateException.class,()->XrayNodeConfig.apply(config,node(),"managed",true));
    }
}
