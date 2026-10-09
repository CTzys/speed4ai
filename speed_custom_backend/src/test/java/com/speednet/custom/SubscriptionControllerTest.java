package com.speednet.custom;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SubscriptionControllerTest {
    @Test void forwardsSessionWithConfiguredTenantAndPreventsCaching() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var packages = new PackageController("http://backend.test", "1");
        ReflectionTestUtils.setField(packages, "backend", builder.build());
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer customer-token");
        request.addHeader("tenant-id", "999");
        var response = new MockHttpServletResponse();
        server.expect(requestTo("/app-api/subscription-products/info"))
            .andExpect(header("Authorization", "Bearer customer-token"))
            .andExpect(header("tenant-id", "1"))
            .andRespond(withSuccess("{\"code\":0,\"data\":null,\"msg\":\"\"}", MediaType.APPLICATION_JSON));
        assertNotNull(new SubscriptionController(packages).info(request, response));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        server.verify();
    }
    @Test void clashProxyPreservesYamlAndSubscriptionHeaders() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        var packages=new PackageController("http://backend.test","1");ReflectionTestUtils.setField(packages,"backend",builder.build());
        var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer customer-token");request.addHeader("tenant-id","999");
        server.expect(requestTo("/app-api/subscription-products/clash"))
            .andExpect(header("Authorization","Bearer customer-token")).andExpect(header("tenant-id","1"))
            .andRespond(withSuccess("proxies: []\n",MediaType.parseMediaType("application/yaml"))
                .header("subscription-userinfo","upload=0; download=1; total=100; expire=123")
                .header("profile-update-interval","1").header("Content-Disposition","attachment; filename=subscription.yaml"));
        var response=new SubscriptionController(packages).clash(request);
        assertEquals("proxies: []\n",response.getBody());assertEquals("no-store",response.getHeaders().getFirst("Cache-Control"));
        assertEquals("upload=0; download=1; total=100; expire=123",response.getHeaders().getFirst("subscription-userinfo"));server.verify();
    }

}
