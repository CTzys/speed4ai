package com.speednet.module.subscription.service;

import com.speednet.framework.common.exception.ErrorCode;
import com.speednet.framework.common.util.json.JsonUtils;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class ClashRuleService {
    public static final List<String> DEFAULT_RULES=List.of("IP-CIDR,127.0.0.0/8,DIRECT,no-resolve",
        "IP-CIDR,10.0.0.0/8,DIRECT,no-resolve","IP-CIDR,172.16.0.0/12,DIRECT,no-resolve",
        "IP-CIDR,192.168.0.0/16,DIRECT,no-resolve","MATCH,SpeedNet");
    private final JdbcTemplate jdbc;
    public ClashRuleService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private long tenant(){return TenantContextHolder.getRequiredTenantId();}
    private RuntimeException bad(String message){return exception(new ErrorCode(1_013_000_004,message));}
    public static List<String> validate(List<String> input){
        if(input==null||input.isEmpty()||input.size()>1000)throw new IllegalArgumentException("请填写 1 到 1000 条规则");
        var result=new ArrayList<String>();
        var types=Set.of("DOMAIN","DOMAIN-SUFFIX","DOMAIN-KEYWORD","IP-CIDR","IP-CIDR6","SRC-IP-CIDR","GEOIP","DST-PORT","SRC-PORT","NETWORK","MATCH");
        for(int i=0;i<input.size();i++){
            String line=input.get(i);if(line==null||line.length()>1024||line.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("规则包含非法字符或长度超限");
            var parts=line.trim().split(",",-1);for(int j=0;j<parts.length;j++)parts[j]=parts[j].trim();
            if(!types.contains(parts[0]))throw new IllegalArgumentException("第 "+(i+1)+" 行规则类型不支持");
            boolean match="MATCH".equals(parts[0]);int count=match?2:3;
            if(parts.length!=count&&!(parts.length==4&&Set.of("IP-CIDR","IP-CIDR6","SRC-IP-CIDR","GEOIP").contains(parts[0])&&"no-resolve".equals(parts[3])))throw new IllegalArgumentException("第 "+(i+1)+" 行规则格式不正确");
            if(!Set.of("DIRECT","REJECT","SpeedNet").contains(parts[match?1:2]))throw new IllegalArgumentException("规则策略仅支持 DIRECT、REJECT、SpeedNet");
            if(match&&i!=input.size()-1)throw new IllegalArgumentException("MATCH 规则必须放在最后一行");
            if(!match&&parts[1].isBlank())throw new IllegalArgumentException("规则匹配内容不能为空");
            if(Set.of("SRC-PORT","DST-PORT").contains(parts[0])){try{int port=Integer.parseInt(parts[1]);if(port<1||port>65535)throw new NumberFormatException();}catch(NumberFormatException e){throw new IllegalArgumentException("端口必须为 1 到 65535");}}
            if("NETWORK".equals(parts[0])&&!Set.of("TCP","UDP").contains(parts[1]))throw new IllegalArgumentException("NETWORK 仅支持 TCP、UDP");
            if(Set.of("IP-CIDR","IP-CIDR6","SRC-IP-CIDR").contains(parts[0])){
                var cidr=parts[1].split("/",-1);boolean ipv6=parts[1].contains(":");
                try{
                    if(cidr.length!=2||!cidr[0].matches(ipv6?"[0-9a-fA-F:]+":"[0-9.]+"))throw new IllegalArgumentException();
                    int bits=Integer.parseInt(cidr[1]);if(bits<0||bits>(ipv6?128:32))throw new IllegalArgumentException();
                    if("IP-CIDR6".equals(parts[0])!=ipv6&&!"SRC-IP-CIDR".equals(parts[0]))throw new IllegalArgumentException();
                    if(ipv6){if(!(java.net.InetAddress.getByName(cidr[0]) instanceof java.net.Inet6Address))throw new IllegalArgumentException();}
                    else {var octets=cidr[0].split("\\.",-1);if(octets.length!=4)throw new IllegalArgumentException();for(var octet:octets){int n=Integer.parseInt(octet);if(n<0||n>255)throw new IllegalArgumentException();}}
                }catch(Exception e){throw new IllegalArgumentException("IP 网段格式不正确");}
            }
            result.add(String.join(",",parts));
        }
        if(!result.getLast().startsWith("MATCH,"))result.add("MATCH,SpeedNet");
        return List.copyOf(result);
    }
    private List<String> decode(Object json){return JsonUtils.parseArray(json.toString(),String.class);}
    public record Configuration(long userId,boolean custom,List<String> rules){}
    public Configuration get(long user){checkUser(user);var rows=jdbc.queryForList("SELECT rules_json FROM subscription_clash_rule WHERE tenant_id=? AND user_id=?",tenant(),user);
        return new Configuration(user,!rows.isEmpty(),rows.isEmpty()?effective(0):decode(rows.getFirst().get("rules_json")));}
    public List<String> effective(long user){
        var rows=jdbc.queryForList("SELECT rules_json FROM subscription_clash_rule WHERE tenant_id=? AND user_id IN (0,?) ORDER BY user_id DESC LIMIT 1",tenant(),user);
        return rows.isEmpty()?DEFAULT_RULES:decode(rows.getFirst().get("rules_json"));
    }
    private void checkUser(long user){if(user<0)throw bad("用户 ID 不正确");if(user>0&&jdbc.queryForObject("SELECT COUNT(*) FROM member_user WHERE tenant_id=? AND id=? AND deleted=0",Long.class,tenant(),user)==0)throw bad("客户不存在");}
    public void save(long user,List<String> rules){checkUser(user);List<String> normalized;try{normalized=validate(rules);}catch(IllegalArgumentException e){throw bad(e.getMessage());}
        jdbc.update("INSERT INTO subscription_clash_rule(tenant_id,user_id,rules_json) VALUES(?,?,?) ON DUPLICATE KEY UPDATE rules_json=VALUES(rules_json),update_time=CURRENT_TIMESTAMP",tenant(),user,JsonUtils.toJsonString(normalized));}
    public void remove(long user){checkUser(user);jdbc.update("DELETE FROM subscription_clash_rule WHERE tenant_id=? AND user_id=?",tenant(),user);}
    public List<Map<String,Object>> users(String keyword){String term=keyword==null?"":keyword.trim();if(term.length()>100)throw bad("搜索内容过长");
        return jdbc.queryForList("SELECT id,nickname,email FROM member_user WHERE tenant_id=? AND deleted=0 AND (nickname LIKE ? OR email LIKE ? OR CAST(id AS CHAR)=?) ORDER BY id DESC LIMIT 50",tenant(),"%"+term+"%","%"+term+"%",term);}
    public List<Map<String,Object>> overrides(){return jdbc.queryForList("SELECT r.user_id AS userId,u.nickname,u.email,r.update_time AS updateTime FROM subscription_clash_rule r JOIN member_user u ON u.id=r.user_id AND u.tenant_id=r.tenant_id AND u.deleted=0 WHERE r.tenant_id=? AND r.user_id>0 ORDER BY r.update_time DESC,r.user_id",tenant());}
}
