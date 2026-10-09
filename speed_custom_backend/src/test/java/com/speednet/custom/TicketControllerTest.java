package com.speednet.custom;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TicketControllerTest {
    TicketController controller;
    MockRestServiceServer server;
    MockHttpServletRequest request;
    @BeforeEach void setup() {
        var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();
        controller=new TicketController("http://backend.test/","1");
        ReflectionTestUtils.setField(controller,"backend",builder.build());
        request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer customer-token");request.addHeader("tenant-id","999");
    }
    @AfterEach void verify() { server.verify(); }
    @Test void searchParametersAreEncodedOnceAndTenantComesFromConfiguration() {
        server.expect(requestTo("http://backend.test/app-api/support/tickets?page=1&size=20&status=&category=&keyword=%E8%BF%9E%E6%8E%A5%20%E9%97%AE%E9%A2%98"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("tenant-id","1")).andExpect(header("Authorization","Bearer customer-token"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"list\":[],\"total\":0}}",org.springframework.http.MediaType.APPLICATION_JSON));
        assertNotNull(controller.page(request,1,20,"","","连接 问题"));
    }
    @Test void mutationsStayOnFixedSupportPaths() {
        server.expect(requestTo("http://backend.test/app-api/support/tickets/12/reply"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json("{\"body\":\"客户补充\",\"requestKey\":\"retry-key\"}"))
                .andRespond(withSuccess("{\"code\":0,\"data\":true}",org.springframework.http.MediaType.APPLICATION_JSON));
        assertNotNull(controller.action(request,12,"reply",Map.of("body","客户补充","requestKey","retry-key")));
    }
    @Test void imageResponsePreservesPrivateHeadersAndType() {
        byte[] png={(byte)137,80,78,71,13,10,26,10};
        server.expect(requestTo("http://backend.test/app-api/support/tickets/attachments/1")).andRespond(withSuccess(png,org.springframework.http.MediaType.IMAGE_PNG));
        var response=controller.attachment(request,1);assertEquals("no-store",response.getHeaders().getFirst("Cache-Control"));assertArrayEquals(png,response.getBody());
    }
    @Test void oversizedUploadIsRejectedBeforeContactingBackend() {
        assertThrows(com.speednet.custom.common.ApiException.class,() -> controller.upload(request,new MockMultipartFile("file","big.png","image/png",new byte[5*1024*1024+1])));
    }
}
