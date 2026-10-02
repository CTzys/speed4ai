package com.speednet.module.xray.service.node;
import com.speednet.framework.common.exception.ServiceException;
import com.speednet.module.xray.controller.admin.node.vo.XrayNodeSaveReqVO;
import com.speednet.module.xray.dal.dataobject.node.*;
import com.speednet.module.xray.dal.mysql.node.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class XrayNodeServiceTest {
    XrayNodeService service=new XrayNodeService();XrayCityService cities=mock(XrayCityService.class);XrayRegionService regions=mock(XrayRegionService.class);XrayNodeMapper nodes=mock(XrayNodeMapper.class);XrayNodeServerMapper deployments=mock(XrayNodeServerMapper.class);XrayNodeTaskMapper tasks=mock(XrayNodeTaskMapper.class);
    @BeforeEach void setup(){
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "node-tests"), XrayNodeServerDO.class);
ReflectionTestUtils.setField(service,"cities",cities);ReflectionTestUtils.setField(service,"regions",regions);ReflectionTestUtils.setField(service,"nodes",nodes);ReflectionTestUtils.setField(service,"deployments",deployments);ReflectionTestUtils.setField(service,"tasks",tasks);}
    @AfterEach void close(){service.close();}
    XrayNodeDO old(){return new XrayNodeDO().setId(1L).setRegionId(1L).setCityId(10L).setName("node").setHost("example.com").setPort(1080).setAuthType(1).setUsername("u").setPassword("secret").setConfigVersion(1).setHealthStatus(1).setShelfStatus(1).setLastCheckTime(LocalDateTime.now());}
    @Test void importsPartialValidLinesAndRedactsCredentials(){
        when(nodes.selectOne(any())).thenReturn(null);
        var rows=service.preview("socks5://u:secret@example.com:1080#one\n\nsocks5://u:new@example.com:1080#two\nhttp://user:VERYSECRET@bad:80");
        assertEquals(3,rows.size());assertEquals("valid",rows.get(0).get("status"));assertEquals("duplicate",rows.get(1).get("status"));assertEquals("error",rows.get(2).get("status"));assertEquals(4,rows.get(2).get("line"));
        assertFalse(rows.toString().contains("secret"));assertFalse(rows.toString().contains("VERYSECRET"));
        service.importText("socks5://example.com:1080\ninvalid",1L,10L);verify(nodes,times(1)).insert(any(XrayNodeDO.class));
    }
    @Test void editKeepsPasswordAndOnlyInvalidatesWhenConnectionChanges(){
        when(nodes.selectById(1L)).thenReturn(old());
        var req=new XrayNodeSaveReqVO().setId(1L).setRegionId(1L).setCityId(10L).setName("new name").setHost("example.com").setPort(1080).setAuthType(1).setUsername("u").setPassword("");
        service.update(req);var captured=ArgumentCaptor.forClass(XrayNodeDO.class);verify(nodes).updateById(captured.capture());assertEquals("secret",captured.getValue().getPassword());assertNull(captured.getValue().getConfigVersion());verifyNoInteractions(deployments);
        reset(nodes);when(nodes.selectById(1L)).thenReturn(old());service.update(req.setPassword("new password"));verify(nodes).updateById(captured.capture());assertEquals(2,captured.getValue().getConfigVersion());assertEquals(0,captured.getValue().getShelfStatus());assertEquals(0,captured.getValue().getHealthStatus());verify(deployments).update(isNull(),any());
    }
    @Test void shelfAcceptsUncheckedUnhealthyAndStaleNodesWithoutDeployment(){
        when(nodes.selectById(1L)).thenReturn(old().setHealthStatus(0).setLastCheckTime(null));
        when(nodes.selectById(2L)).thenReturn(old().setId(2L).setHealthStatus(2));
        when(nodes.selectById(3L)).thenReturn(old().setId(3L).setLastCheckTime(LocalDateTime.now().minusDays(2)));
        var rows=service.shelf(List.of(1L,2L,3L),true);
        assertTrue(rows.stream().allMatch(row->Boolean.TRUE.equals(row.get("success"))));
        verify(nodes,times(3)).updateById(any(XrayNodeDO.class));
        verifyNoInteractions(deployments);
    }
    @Test void batchShelfHandlesMissingNodesIndependentlyAndNeverChangesRemote(){
        var servers=mock(com.speednet.module.xray.service.server.XrayServerService.class);
        var panel=mock(com.speednet.module.xray.framework.panel.XrayNodePanelClient.class);
        var probe=mock(Socks5Probe.class);
        ReflectionTestUtils.setField(service,"servers",servers);ReflectionTestUtils.setField(service,"panel",panel);ReflectionTestUtils.setField(service,"probe",probe);
        when(nodes.selectById(1L)).thenReturn(old());
        var rows=service.shelf(List.of(1L,99L),true);
        assertEquals(true,rows.get(0).get("success"));assertEquals(false,rows.get(1).get("success"));
        assertEquals(true,service.shelf(List.of(1L),false).getFirst().get("success"));
        verifyNoInteractions(deployments,servers,panel,probe);
    }
    @Test void inaccessibleNodeCannotCreateBackgroundWork(){assertThrows(ServiceException.class,()->service.submit(List.of(99L),null,"check"));verifyNoInteractions(tasks);}
}
