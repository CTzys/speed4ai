package com.speednet.module.subscription.service;

import com.speednet.framework.tenant.core.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;
import com.speednet.framework.common.exception.ErrorCode;

@Service
public class SubscriptionAccountService {
    private final JdbcTemplate jdbc;
    public SubscriptionAccountService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String,Object> lock(long user) { return lock(user,false); }
    public Map<String,Object> lock(long user,boolean allowConflict) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        jdbc.update("INSERT INTO subscription_account (tenant_id,user_id) VALUES (?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",tenant,user);
        var a=jdbc.queryForMap("SELECT * FROM subscription_account WHERE tenant_id=? AND user_id=? FOR UPDATE",tenant,user);
        var current=jdbc.queryForList("SELECT id FROM subscription WHERE tenant_id=? AND user_id=? AND deleted=0 AND ended_time IS NULL AND expiry_time>?",tenant,user,java.time.LocalDateTime.now());
        jdbc.update("UPDATE subscription_account SET conflict=? WHERE tenant_id=? AND user_id=?",current.size()>1,tenant,user);
        if(current.size()>1&&!allowConflict)throw exception(new ErrorCode(1_013_000_001,"该用户存在多份有效订阅，请先在后台结束多余订阅"));
        if(current.size()==1&&!java.util.Objects.equals(a.get("current_subscription_id"),current.getFirst().get("id"))) {
            jdbc.update("UPDATE subscription_account SET current_subscription_id=?,version=version+1 WHERE tenant_id=? AND user_id=?",current.getFirst().get("id"),tenant,user);
            a=jdbc.queryForMap("SELECT * FROM subscription_account WHERE tenant_id=? AND user_id=?",tenant,user);
        }
        return a;
    }
    public boolean allows(long user,long subscription) {
        var rows=jdbc.queryForList("SELECT current_subscription_id,conflict FROM subscription_account WHERE tenant_id=? AND user_id=?",TenantContextHolder.getRequiredTenantId(),user);
        if(rows.isEmpty())return true;var a=rows.getFirst();return !(Boolean.TRUE.equals(a.get("conflict"))||a.get("conflict") instanceof Number conflict&&conflict.intValue()!=0)&&a.get("current_subscription_id") instanceof Number n&&n.longValue()==subscription;
    }
    public void changed(long user) { lock(user,true);jdbc.update("UPDATE subscription_account SET version=version+1 WHERE tenant_id=? AND user_id=?",TenantContextHolder.getRequiredTenantId(),user); }
    public void bind(long user,long subscription) {
        jdbc.update("UPDATE subscription_account SET current_subscription_id=?,version=version+1 WHERE tenant_id=? AND user_id=?",subscription,TenantContextHolder.getRequiredTenantId(),user);
    }
}
