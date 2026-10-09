package com.speednet.module.xray.service.node;

import com.speednet.framework.common.exception.ServiceException;
import com.speednet.module.xray.controller.admin.node.vo.*;
import com.speednet.module.xray.dal.dataobject.node.*;
import com.speednet.module.xray.dal.mysql.node.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class XrayCityServiceTest {
    XrayCityService service = new XrayCityService();
    XrayCityMapper mapper = mock(XrayCityMapper.class);
    XrayRegionService regions = mock(XrayRegionService.class);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "regions", regions);
        when(regions.require(1L)).thenReturn(new XrayRegionDO().setId(1L).setName("美国").setStatus(0));
        when(mapper.selectById(10L)).thenReturn(new XrayCityDO().setId(10L).setRegionId(1L).setName("纽约").setStatus(0));
    }
    @Test void enabledCityBelongsToSelectedRegion() {
        assertDoesNotThrow(() -> service.validateSelection(1L, 10L, null));
        assertThrows(ServiceException.class, () -> service.validateSelection(2L, 10L, null));
        assertThrows(ServiceException.class, () -> service.validateSelection(2L, 10L, 10L));
    }
    @Test void nonexistentCityIsRejected() {
        assertThrows(ServiceException.class, () -> service.validateSelection(1L, null, null));
        assertThrows(ServiceException.class, () -> service.validateSelection(1L, 999L, null));
    }
    @Test void disabledCityOnlyPreservesExistingAssociation() {
        when(mapper.selectById(10L)).thenReturn(new XrayCityDO().setId(10L).setRegionId(1L).setName("纽约").setStatus(1));
        assertThrows(ServiceException.class, () -> service.validateSelection(1L, 10L, null));
        assertDoesNotThrow(() -> service.validateSelection(1L, 10L, 10L));
    }
    @Test void disabledParentBlocksNewCitiesButKeepsExisting() {
        when(regions.require(1L)).thenReturn(new XrayRegionDO().setId(1L).setName("美国").setStatus(1));
        assertThrows(ServiceException.class, () -> service.validateSelection(1L, 10L, null));
        assertThrows(ServiceException.class, () -> service.validateSelection(1L, 10L, 11L));
        assertDoesNotThrow(() -> service.validateSelection(1L, 10L, 10L));
    }
    @Test void cityCreateRetainsParentAndRejectsDuplicateName() {
        var request = new XrayCitySaveReqVO().setRegionId(1L).setName("  纽约  ").setSort(0).setStatus(0);
        service.create(request);
        var captured = ArgumentCaptor.forClass(XrayCityDO.class);
        verify(mapper).insert(captured.capture());assertEquals(1L,captured.getValue().getRegionId());assertEquals("纽约",captured.getValue().getName());
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(XrayCityDO.class));
        assertThrows(ServiceException.class, () -> service.create(request));
    }
    @Test void changingCityParentCannotSilentlyChangeNodes() {
        when(regions.require(2L)).thenReturn(new XrayRegionDO().setId(2L).setName("日本").setStatus(0));
        var request = new XrayCitySaveReqVO().setId(10L).setRegionId(2L).setName("纽约").setSort(0).setStatus(0);
        assertThrows(ServiceException.class, () -> service.update(request));verify(mapper,never()).updateById(any(XrayCityDO.class));
    }
    @Test void nodeCreateAndImportRejectMismatchedPairBeforeWriting() {
        var nodes = mock(XrayNodeMapper.class);var nodeService = new XrayNodeService();
        ReflectionTestUtils.setField(nodeService,"nodes",nodes);ReflectionTestUtils.setField(nodeService,"regions",regions);ReflectionTestUtils.setField(nodeService,"cities",service);
        try {
            var request = Socks5NodeParser.parse("socks5://example.com:1080").setRegionId(2L).setCityId(10L);
            assertThrows(ServiceException.class, () -> nodeService.create(request));
            assertThrows(ServiceException.class, () -> nodeService.importText("socks5://example.com:1080",2L,10L));
            verifyNoInteractions(nodes);
        } finally { nodeService.close(); }
    }
    @Test void nodeRequestRequiresCitySelection() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var request = Socks5NodeParser.parse("socks5://example.com:1080").setRegionId(1L);
            assertTrue(factory.getValidator().validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals("cityId")));
            assertTrue(factory.getValidator().validate(request.setCityId(10L)).isEmpty());
        }
    }
}
