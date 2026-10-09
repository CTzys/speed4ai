package com.speednet.module.subscription.service;
import cn.hutool.json.JSONUtil;
import com.speednet.module.subscription.dal.dataobject.*;
import com.speednet.module.xray.dal.dataobject.node.XrayNodeDO;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.framework.panel.*;
import com.speednet.module.xray.service.node.*;
import com.speednet.module.xray.service.server.XrayServerService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
class SubscriptionGatewayTest {
 SubscriptionGateway gateway=new SubscriptionGateway();XrayNodeService nodes=mock(XrayNodeService.class);XrayPanelClient panel=mock(XrayPanelClient.class);XrayNodePanelClient xray=mock(XrayNodePanelClient.class);XrayServerService servers=mock(XrayServerService.class);
 XrayServerDO server=new XrayServerDO().setId(1L);List<Object> remoteClients;Map<String,Object> config;
 SubscriptionDO s;SubscriptionClientDO c;XrayNodeDO node;
 @BeforeEach void setup(){
  ReflectionTestUtils.setField(gateway,"nodes",nodes);ReflectionTestUtils.setField(gateway,"panel",panel);ReflectionTestUtils.setField(gateway,"xray",xray);ReflectionTestUtils.setField(gateway,"servers",servers);
  s=new SubscriptionDO().setId(1L).setExpiryTime(LocalDateTime.now().plusDays(30)).setUnlimited(false).setTotalBytes(1000L).setUsedUpload(10L).setUsedDownload(20L).setTrafficMode("both");
  c=new SubscriptionClientDO().setId(1L).setNodeId(1L).setServerId(1L).setInboundId(10L).setEmail("sn1s1c1").setCredential("uuid-1").setProtocol("vmess").setPublicHost("edge.example").setConnectionName("节点").setRemoteCreated(false).setNodeVersion(0).setSampleUpload(10L).setSampleDownload(20L);c.setTenantId(1L);
  node=new XrayNodeDO().setId(1L).setHost("exit.example").setPort(1080).setAuthType(0).setConfigVersion(1).setShelfStatus(1);node.setTenantId(1L);
  remoteClients=new ArrayList<>(List.of(new LinkedHashMap<>(Map.of("id","unrelated-uuid","email","other","enable",true))));
  config=new LinkedHashMap<>(Map.of("outbounds",new ArrayList<>(List.of(Map.of("tag","direct","protocol","freedom"),XrayNodeConfig.outbound(node,"speednet-node-1-1"))),"routing",new LinkedHashMap<>(Map.of("rules",new ArrayList<>(List.of(Map.of("type","field","domain",List.of("example.com"),"outboundTag","direct")))))));
  when(servers.get(1L)).thenReturn(server);when(nodes.require(1L)).thenReturn(node);
  when(nodes.withServerLock(anyLong(),any())).thenAnswer(i->((java.util.function.Supplier<?>)i.getArgument(1)).get());
  when(panel.call(eq(server),eq("get/10"),isNull())).thenAnswer(i->Map.of("id",10,"protocol","vmess","port",443,"enable",true,"settings",JSONUtil.toJsonStr(Map.of("clients",remoteClients)),"streamSettings","{\"network\":\"tcp\",\"security\":\"none\"}"));
  when(panel.call(eq(server),anyString(),anyMap())).thenAnswer(i->{String endpoint=i.getArgument(1);if(endpoint.equals("addClient")||endpoint.startsWith("updateClient/")){Map<?,?> body=i.getArgument(2);var settings=JSONUtil.toBean(String.valueOf(body.get("settings")),Map.class);var next=(Map<?,?>)((List<?>)settings.get("clients")).getFirst();remoteClients.removeIf(v->v instanceof Map<?,?> m&&next.get("email").equals(m.get("email")));remoteClients.add(new LinkedHashMap<>(next));}return null;});
  when(xray.read(server)).thenAnswer(i->Map.of("xraySetting",config));when(xray.config(anyMap())).thenAnswer(i->JSONUtil.toBean(JSONUtil.toJsonStr(((Map<?,?>)i.getArgument(0)).get("xraySetting")),Map.class));
  doAnswer(i->{Object payload=i.getArgument(1);config=JSONUtil.toBean(JSONUtil.toJsonStr(payload),Map.class);return null;}).when(xray).write(eq(server),anyMap(),anyString());
 }
 @Test void provisioningAddsOnlyOwnedClientAndUserRoute(){String link=gateway.sync(s,c,true);assertTrue(link.startsWith("vmess://"));assertEquals(2,remoteClients.size());assertTrue(remoteClients.stream().anyMatch(v->v instanceof Map<?,?> m&&"unrelated-uuid".equals(m.get("id"))));var routing=(Map<?,?>)config.get("routing");var rules=(List<?>)routing.get("rules");assertEquals(List.of(c.getEmail()),((Map<?,?>)rules.getFirst()).get("user"));assertEquals(2,rules.size());verify(nodes).prepareSubscriptionNode(1L,1L);}
 @Test void retryReusesUuidAndDoesNotDuplicateClient(){gateway.sync(s,c,true);c.setRemoteCreated(true).setNodeVersion(1);gateway.sync(s,c,true);assertEquals(2,remoteClients.size());verify(panel,times(1)).call(eq(server),eq("addClient"),anyMap());verify(panel).call(eq(server),eq("updateClient/uuid-1"),anyMap());}
 @Test void revocationDisablesOnlyOwnedClientAndPreservesSharedOutbound(){gateway.sync(s,c,true);c.setRemoteCreated(true).setNodeVersion(1);gateway.sync(s,c,false);assertEquals(2,remoteClients.size());assertTrue(remoteClients.stream().anyMatch(v->v instanceof Map<?,?> m&&c.getEmail().equals(m.get("email"))&&Boolean.FALSE.equals(m.get("enable"))));assertEquals(2,((List<?>)config.get("outbounds")).size());assertEquals(1,((List<?>)((Map<?,?>)config.get("routing")).get("rules")).size());}
 @Test void conflictCannotOverwriteSomeoneElsesUuid(){remoteClients.add(Map.of("email",c.getEmail(),"id","foreign-uuid","enable",true));assertThrows(IllegalStateException.class,()->gateway.sync(s,c,true));verify(panel,never()).call(eq(server),eq("updateClient/uuid-1"),anyMap());verifyNoInteractions(xray);}
 @Test void sharedUserRuleIsNotDeleted(){List<Object> rules=new ArrayList<>(List.of(Map.of("user",List.of(c.getEmail(),"other"),"outboundTag","direct")));assertThrows(IllegalStateException.class,()->SubscriptionGateway.removeOwnedRules(rules,c.getEmail()));assertEquals(1,rules.size());}
 @Test void trafficRequiresCountersAndHandlesKnownMissingClient(){when(panel.call(eq(server),eq("getClientTraffics/"+c.getEmail()),isNull())).thenReturn(Map.of("up",12L,"down",34L));assertEquals(new SubscriptionGateway.Traffic(12,34),gateway.traffic(c));when(panel.call(eq(server),eq("getClientTraffics/"+c.getEmail()),isNull())).thenReturn(null);assertEquals(new SubscriptionGateway.Traffic(0,0),gateway.traffic(c));remoteClients.add(Map.of("email",c.getEmail(),"id",c.getCredential()));assertThrows(IllegalStateException.class,()->gateway.traffic(c));}
 @Test void missingClientHttpErrorDoesNotBlockProvisioningRetry(){
  when(panel.call(eq(server),eq("getClientTraffics/"+c.getEmail()),isNull())).thenThrow(new com.speednet.framework.common.exception.ServiceException(500,"HTTP 404"));
  assertEquals(new SubscriptionGateway.Traffic(0,0),gateway.traffic(c));
  gateway.sync(s,c,true);
  assertTrue(remoteClients.stream().anyMatch(v->v instanceof Map<?,?> m&&c.getEmail().equals(m.get("email"))));
  assertThrows(IllegalStateException.class,()->gateway.traffic(c));
 }
 @Test void fallsBackToVerifiedInboundClientCounters(){
  when(panel.call(eq(server),eq("getClientTraffics/"+c.getEmail()),isNull())).thenThrow(new com.speednet.framework.common.exception.ServiceException(500,"HTTP 404"));
  when(panel.call(eq(server),eq("get/10"),isNull())).thenReturn(Map.of("settings",Map.of("clients",List.of(Map.of("email",c.getEmail()))),"clientStats",List.of(Map.of("email",c.getEmail(),"up",12L,"down",34L))));
  assertEquals(new SubscriptionGateway.Traffic(12,34),gateway.traffic(c));
 }
 @Test void initialClientIsCreatedDisabledWithoutAuthorizingRoute(){
  gateway.prepareClient(s,c);
  assertTrue(remoteClients.stream().anyMatch(v->v instanceof Map<?,?> m&&c.getEmail().equals(m.get("email"))&&Boolean.FALSE.equals(m.get("enable"))));
  verify(nodes,never()).prepareSubscriptionNode(anyLong(),anyLong());
  gateway.sync(s,c,true);
  assertEquals(2,remoteClients.size());
  verify(panel,times(1)).call(eq(server),eq("addClient"),anyMap());
 }
}
