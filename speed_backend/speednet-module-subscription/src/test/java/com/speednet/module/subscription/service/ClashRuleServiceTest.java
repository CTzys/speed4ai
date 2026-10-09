package com.speednet.module.subscription.service;

import com.speednet.framework.tenant.core.context.TenantContextHolder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClashRuleServiceTest {
    JdbcTemplate jdbc;ClashRuleService service;
    @BeforeEach void setup()throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:clashrules"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(ds);
        String migration=Files.readString(Path.of("../speednet-server/src/main/resources/db/migration/V10__customer_support_and_clash_rules.sql"));
        migration=migration.substring(migration.indexOf("-- BEGIN V11__subscription_clash_rules.sql\n")+"-- BEGIN V11__subscription_clash_rules.sql\n".length());
        try(var c=ds.getConnection()){ScriptUtils.executeSqlScript(c,new ByteArrayResource(migration.substring(0,migration.indexOf("-- Persist")).getBytes()));}
        jdbc.execute("CREATE TABLE member_user(id BIGINT,tenant_id BIGINT,nickname VARCHAR(100),email VARCHAR(100),deleted BOOLEAN DEFAULT FALSE)");
        jdbc.update("INSERT INTO member_user(id,tenant_id,nickname,email) VALUES(10,1,'客户','customer@test'),(10,2,'其他租户','other@test')");
        TenantContextHolder.setTenantId(1L);service=new ClashRuleService(jdbc);
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void customerOverridesCommonAndDeleteRestoresInheritance(){
        assertEquals(ClashRuleService.DEFAULT_RULES,service.effective(10));
        service.save(0,List.of("DOMAIN-SUFFIX,example.com,DIRECT"));
        assertEquals(List.of("DOMAIN-SUFFIX,example.com,DIRECT","MATCH,SpeedNet"),service.effective(10));
        assertFalse(service.get(10).custom());
        service.save(10,List.of("DOMAIN-SUFFIX,special.com,REJECT","MATCH,DIRECT"));
        service.save(0,List.of("MATCH,SpeedNet"));
        assertEquals(List.of("DOMAIN-SUFFIX,special.com,REJECT","MATCH,DIRECT"),service.effective(10));assertTrue(service.get(10).custom());
        assertEquals(1,service.overrides().size());service.remove(10);assertEquals(List.of("MATCH,SpeedNet"),service.effective(10));
        assertTrue(service.overrides().isEmpty());service.remove(0);assertEquals(ClashRuleService.DEFAULT_RULES,service.effective(10));
    }
    @Test void tenantsAndCustomerValidationAreEnforced(){
        service.save(10,List.of("MATCH,DIRECT"));TenantContextHolder.setTenantId(2L);
        assertEquals(ClashRuleService.DEFAULT_RULES,service.effective(10));assertTrue(service.overrides().isEmpty());
        assertEquals("other@test",service.users("other").getFirst().get("email"));
        assertThrows(RuntimeException.class,()->service.save(99,List.of("MATCH,DIRECT")));
        assertThrows(RuntimeException.class,()->service.remove(-1));
    }
    @Test void rejectsInvalidRulesBeforeWriting(){
        for(var rules:List.of(List.of("MATCH,DIRECT","DOMAIN,x,REJECT"),List.of("DOMAIN,x,unknown"),List.of("RULE-SET,remote,SpeedNet"),
            List.of("IP-CIDR,999.1.1.1/8,DIRECT"),List.of("IP-CIDR6,::1/129,DIRECT"),List.of("DST-PORT,70000,DIRECT"),List.of("DOMAIN,x\nother,DIRECT"))) {
            assertThrows(RuntimeException.class,()->service.save(0,rules),rules.toString());
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM subscription_clash_rule",Integer.class));
    }
    @Test void normalizesRulesAndPreservesOrdering(){
        var rules=ClashRuleService.validate(List.of(" DOMAIN-SUFFIX, example.com , DIRECT ","IP-CIDR6,::1/128,DIRECT,no-resolve","NETWORK,UDP,REJECT"));
        assertEquals("DOMAIN-SUFFIX,example.com,DIRECT",rules.getFirst());assertEquals("MATCH,SpeedNet",rules.getLast());
        String yaml=ClashSubscription.render(List.of("vless://id@example.com:443?security=tls#node"),rules);
        Map<?,?> config=new org.yaml.snakeyaml.Yaml().load(yaml);assertEquals(rules,config.get("rules"));
    }
}
