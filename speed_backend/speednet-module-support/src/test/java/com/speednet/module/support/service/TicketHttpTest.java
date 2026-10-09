package com.speednet.module.support.service;

import com.speednet.framework.common.exception.ServiceException;
import com.speednet.framework.common.pojo.CommonResult;
import com.speednet.framework.security.core.util.SecurityFrameworkUtils;
import com.speednet.module.support.controller.app.AppTicketController;
import com.speednet.module.support.controller.admin.AdminTicketController;
import com.speednet.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketHttpTest extends TicketTestFixture {
    MockMvc mvc;
    @BeforeEach void web() throws Exception {
        jdbc.execute("CREATE TABLE custom_session(tenant_id bigint,member_user_id bigint,access_hash varchar(64),revoked boolean,access_expires_at timestamp)");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("http-token".getBytes(StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO custom_session VALUES(1,10,?,0,?)",hash,LocalDateTime.now().plusHours(1));
        var permissions=mock(PermissionApi.class);
        when(permissions.hasAnyPermissions(eq(20L),any(String[].class))).thenReturn(true);
        when(permissions.hasAnyPermissions(eq(20L),eq("support:ticket:manage"))).thenReturn(false);
        var identity=new TicketIdentity(jdbc,permissions);
        var proxy=new ProxyFactory(service);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(jdbc.getDataSource()),new AnnotationTransactionAttributeSource()));
        var transactional=(TicketService)proxy.getProxy();
        mvc=MockMvcBuilders.standaloneSetup(new AppTicketController(transactional,identity),new AdminTicketController(transactional,identity,permissions))
            .setControllerAdvice(new Advice())
            .setCustomHandlerMapping(() -> { var mapping=new RequestMappingHandlerMapping(); mapping.setPathPrefixes(Map.of("/app-api",HandlerTypePredicate.forBasePackage("com.speednet.module.support.controller.app"),"/admin-api",HandlerTypePredicate.forBasePackage("com.speednet.module.support.controller.admin")));return mapping; }).build();
    }
    @AfterEach void security() { SecurityContextHolder.clearContext(); }
    @RestControllerAdvice static class Advice {
        @ExceptionHandler(ServiceException.class) CommonResult<?> error(ServiceException ex) { return CommonResult.error(ex.getCode(),ex.getMessage()); }
    }
    @Test void customerEndpointsEnforceLoginAndSupportHistoryReadAndReplies() throws Exception {
        mvc.perform(get("/app-api/support/tickets")).andExpect(jsonPath("$.code").value(401));
        var response=mvc.perform(post("/app-api/support/tickets").header("Authorization","Bearer http-token").contentType("application/json").content("{\"title\":\"HTTP工单\",\"category\":\"connection\",\"body\":\"连接异常\",\"requestKey\":\"http-create\",\"memberId\":11,\"tenantId\":2}"))
            .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        long id=((Number)com.speednet.framework.common.util.json.JsonUtils.parseObject(response,Map.class).get("data")).longValue();
        mvc.perform(get("/app-api/support/tickets/"+id).header("Authorization","Bearer http-token")).andExpect(jsonPath("$.data.ticket.member_id").value(10)).andExpect(jsonPath("$.data.messages[0].body").value("连接异常"));
        mvc.perform(post("/app-api/support/tickets/"+id+"/read").header("Authorization","Bearer http-token").contentType("application/json").content("{\"lastSeq\":1}")).andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/app-api/support/tickets/"+id+"/reply").header("Authorization","Bearer http-token").contentType("application/json").content("{\"body\":\"客户补充\",\"requestKey\":\"http-reply\",\"internal\":true}")).andExpect(jsonPath("$.code").value(1_014_000_001));
        mvc.perform(get("/app-api/support/tickets/unread").header("Authorization","Bearer http-token")).andExpect(jsonPath("$.data").value(0));
    }
    @Test void screenshotDownloadIsAuthenticatedAndMissingAttachmentRollsBackHttpCreate() throws Exception {
        byte[] png={(byte)137,80,78,71,13,10,26,10,0};
        var response=mvc.perform(multipart("/app-api/support/tickets/attachments").file(new MockMultipartFile("file","test.png","image/png",png)).header("Authorization","Bearer http-token"))
            .andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        var data=(Map<?,?>)com.speednet.framework.common.util.json.JsonUtils.parseObject(response,Map.class).get("data");long id=((Number)data.get("id")).longValue();
        mvc.perform(get("/app-api/support/tickets/attachments/"+id)).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/app-api/support/tickets/attachments/"+id).header("Authorization","Bearer http-token")).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes(png));
        mvc.perform(post("/app-api/support/tickets").header("Authorization","Bearer http-token").contentType("application/json").content("{\"title\":\"失败事务\",\"category\":\"connection\",\"body\":\"测试\",\"requestKey\":\"fail\",\"attachmentIds\":[999]}"))
            .andExpect(jsonPath("$.code").value(1_014_000_001));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket",Integer.class));
    }
    @Test void staffQueriesAndActionsEnforcePermissionsAndOwnership() throws Exception {
        long id=create("staff-test");
        mvc.perform(get("/admin-api/support/tickets")).andExpect(jsonPath("$.code").value(401));
        SecurityFrameworkUtils.setLoginUser(new com.speednet.framework.security.core.LoginUser().setId(20L).setUserType(2).setTenantId(1L),new org.springframework.mock.web.MockHttpServletRequest());
        mvc.perform(post("/admin-api/support/tickets/"+id+"/action").contentType("application/json").content("{\"action\":\"claim\",\"version\":0}"))
            .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/admin-api/support/tickets/"+id)).andExpect(jsonPath("$.data.ticket.assignee_id").value(20));
        SecurityFrameworkUtils.setLoginUser(new com.speednet.framework.security.core.LoginUser().setId(21L).setUserType(2).setTenantId(1L),new org.springframework.mock.web.MockHttpServletRequest());
        mvc.perform(get("/admin-api/support/tickets/"+id)).andExpect(jsonPath("$.code").value(1_014_000_001));
    }
}
