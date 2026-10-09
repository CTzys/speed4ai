package com.speednet.module.xray.service.node;
import com.speednet.module.xray.dal.dataobject.node.*;
import com.speednet.module.xray.dal.dataobject.server.XrayServerDO;
import com.speednet.module.xray.dal.mysql.node.*;
import com.speednet.module.xray.service.server.XrayServerService;
import com.speednet.module.xray.framework.panel.XrayNodePanelClient;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class XrayNodeDeploymentTest {
    XrayNodeService service=new XrayNodeService();
    XrayNodeMapper nodes=mock(XrayNodeMapper.class);XrayNodeServerMapper deployments=mock(XrayNodeServerMapper.class);XrayNodeAssignmentMapper assignments=mock(XrayNodeAssignmentMapper.class);
    XrayServerService servers=mock(XrayServerService.class);XrayNodePanelClient panel=mock(XrayNodePanelClient.class);
    XrayNodeDO node=new XrayNodeDO().setId(1L).setHost("example.com").setPort(1080).setAuthType(0).setConfigVersion(1).setShelfStatus(0);
    XrayServerDO server=new XrayServerDO().setId(2L);
    XrayNodeServerDO relation=new XrayNodeServerDO().setId(4L).setNodeId(1L).setServerId(2L).setStatus(0);
    @BeforeEach void setup(){
        node.setTenantId(8L);
        when(panel.config(anyMap())).thenAnswer(call -> { Map<String,Object> r=call.getArgument(0);return cn.hutool.json.JSONUtil.toBean(cn.hutool.json.JSONUtil.toJsonStr(r.get("xraySetting")),Map.class); });
        ReflectionTestUtils.setField(service,"nodes",nodes);ReflectionTestUtils.setField(service,"deployments",deployments);ReflectionTestUtils.setField(service,"assignments",assignments);ReflectionTestUtils.setField(service,"servers",servers);ReflectionTestUtils.setField(service,"panel",panel);
        when(nodes.selectById(1L)).thenReturn(node);when(servers.get(2L)).thenReturn(server);when(deployments.selectOne(any())).thenReturn(relation);when(assignments.counts(1L)).thenReturn(Map.of("activeUserCount",0L));
    }
    @AfterEach void close(){service.close();}
    Map<String,Object> response(boolean includesNode){
        var list=new ArrayList<Object>();list.add(Map.of("tag","direct","protocol","freedom"));if(includesNode)list.add(XrayNodeConfig.outbound(node,"speednet-node-8-1"));
        var config=new HashMap<String,Object>(Map.of("outbounds",list));var response=Map.<String,Object>of("xraySetting",config,"outboundTestUrl","https://example.com/test");
        return response;
    }
    @Test void deploymentRequiresSuccessfulReadBack(){
        when(panel.read(server)).thenReturn(response(false),response(true));
        String message=ReflectionTestUtils.invokeMethod(service,"deploy",1L,2L,"deploy");assertNotNull(message);assertEquals(2,relation.getStatus());assertEquals(1,relation.getAppliedVersion());verify(panel).write(eq(server),anyMap(),eq("https://example.com/test"));
    }
    @Test void missingRemoteConfigurationCannotAppearDeployed(){
        var before=response(false);var after=response(false);when(panel.read(server)).thenReturn(before,after);
        assertThrows(RuntimeException.class,()->ReflectionTestUtils.invokeMethod(service,"deploy",1L,2L,"deploy"));assertEquals(4,relation.getStatus());
    }
    @Test void verifyIsReadOnlyAndUsesCurrentVersion(){
        when(panel.read(server)).thenReturn(response(true));ReflectionTestUtils.invokeMethod(service,"deploy",1L,2L,"verify");assertEquals(2,relation.getStatus());verify(panel,never()).write(any(),anyMap(),anyString());
    }
    @Test void shelfBlocksRemoteRemoval(){
        node.setShelfStatus(1);when(panel.read(server)).thenReturn(response(true));assertThrows(RuntimeException.class,()->ReflectionTestUtils.invokeMethod(service,"deploy",1L,2L,"remove"));verify(panel,never()).write(any(),anyMap(),anyString());
    }
    @Test void inaccessibleServerNeverTouchesRemote(){
        when(servers.get(2L)).thenThrow(new IllegalStateException("missing"));assertThrows(RuntimeException.class,()->ReflectionTestUtils.invokeMethod(service,"deploy",1L,2L,"deploy"));verifyNoInteractions(panel);
    }
}
