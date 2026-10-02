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

class XrayRegionServiceTest {
    XrayRegionService regions = new XrayRegionService();
    XrayRegionMapper mapper = mock(XrayRegionMapper.class);
    @BeforeEach void setup() { ReflectionTestUtils.setField(regions, "mapper", mapper); }

    @Test void selectionMustExistInCurrentTenant() {
        assertThrows(ServiceException.class, () -> regions.validateSelection(null, null));
        assertThrows(ServiceException.class, () -> regions.validateSelection(99L, null));
    }
    @Test void disabledRegionAllowsExistingAssociationOnly() {
        when(mapper.selectById(1L)).thenReturn(new XrayRegionDO().setId(1L).setName("香港").setStatus(1));
        assertThrows(ServiceException.class, () -> regions.validateSelection(1L, null));
        assertThrows(ServiceException.class, () -> regions.validateSelection(1L, 2L));
        assertDoesNotThrow(() -> regions.validateSelection(1L, 1L));
        when(mapper.selectById(2L)).thenReturn(new XrayRegionDO().setId(2L).setName("日本").setStatus(0));
        assertDoesNotThrow(() -> regions.validateSelection(2L, 1L));
    }
    @Test void createTrimsNameAndRejectsDuplicates() {
        var req = new XrayRegionSaveReqVO().setName("  香港  ").setSort(10).setStatus(0);
        regions.create(req);
        var captured = ArgumentCaptor.forClass(XrayRegionDO.class);
        verify(mapper).insert(captured.capture()); assertEquals("香港", captured.getValue().getName());
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(XrayRegionDO.class));
        assertThrows(ServiceException.class, () -> regions.create(req));
    }
    @Test void arbitraryMissingRegionCannotCreateOrImportNode() {
        var service = new XrayNodeService();
        var nodes = mock(XrayNodeMapper.class);
        ReflectionTestUtils.setField(service, "nodes", nodes);
        ReflectionTestUtils.setField(service, "regions", regions);
        try {
            var req = Socks5NodeParser.parse("socks5://example.com:1080").setRegionId(99L);
            assertThrows(ServiceException.class, () -> service.create(req));
            assertThrows(ServiceException.class, () -> service.importText("socks5://example.com:1080", 99L, 10L));
            verifyNoInteractions(nodes);
        } finally { service.close(); }
    }
    @Test void requestValidationRequiresRegionId() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var request = Socks5NodeParser.parse("socks5://example.com:1080");
            assertTrue(factory.getValidator().validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().equals("regionId")));
            assertTrue(factory.getValidator().validate(request.setRegionId(1L).setCityId(10L)).isEmpty());
        }
    }
}
