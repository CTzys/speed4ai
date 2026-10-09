package com.speednet.module.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.speednet.framework.common.exception.ErrorCode;
import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.common.util.object.BeanUtils;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.framework.tenant.core.util.TenantUtils;
import com.speednet.module.member.api.user.MemberUserApi;
import com.speednet.module.member.dal.dataobject.user.MemberUserDO;
import com.speednet.module.member.dal.mysql.user.MemberUserMapper;
import com.speednet.module.subscription.controller.admin.vo.*;
import com.speednet.module.subscription.dal.dataobject.*;
import com.speednet.module.subscription.dal.mysql.*;
import com.speednet.module.xray.service.node.*;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class SubscriptionService {
    private static final ErrorCode INVALID=new ErrorCode(1_013_000_000,"订阅操作失败：{}");
    @Resource private SubscriptionAccountService accounts;
    @Resource private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Resource private SubscriptionMapper subscriptions;
    @Resource private SubscriptionClientMapper clients;
    @Resource private SubscriptionAssignmentMapper assignments;
    @Resource private SubscriptionLogMapper logs;
    @Resource private SubscriptionOrderMapper orders;
    @Resource private MemberUserApi users;
    @Resource private com.speednet.framework.common.biz.system.tenant.TenantCommonApi tenants;
    @Resource private MemberUserMapper userMapper;
    @Resource private XrayNodeService nodes;
    @Resource private XrayRegionService regions;
    @Resource private XrayCityService cities;
    @Resource private SubscriptionGateway gateway;
    @Resource private PlatformTransactionManager transactionManager;
    private final Set<String> queued=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(100),r->{var t=new Thread(r,"subscription-worker");t.setDaemon(true);return t;});
    @PreDestroy public void close(){executor.shutdownNow();}
    private RuntimeException invalid(String text){return exception(INVALID,text);}
    public SubscriptionDO require(Long id){var s=subscriptions.selectById(id);if(s==null)throw invalid("订阅不存在");return s;}
    private SubscriptionDO lock(Long id){var s=subscriptions.lock(id);if(s==null)throw invalid("订阅不存在");return s;}
    private <T>T transaction(java.util.function.Supplier<T> work){return new TransactionTemplate(transactionManager).execute(status->work.get());}
    private void enabledUser(Long id){var u=users.getUser(id);if(u==null||!Integer.valueOf(0).equals(u.getStatus()))throw invalid("请选择存在且启用的会员用户");}
    private int effectiveStatus(SubscriptionDO s) {
        int result=SubscriptionPolicy.status(s,LocalDateTime.now());
        if(result==SubscriptionPolicy.ACTIVE){
            if(!accounts.allows(s.getUserId(),s.getId()))return SubscriptionPolicy.PAUSED;
            try {tenants.validateTenant(TenantContextHolder.getRequiredTenantId());}catch(RuntimeException e){return SubscriptionPolicy.PAUSED;}
            var u=users.getUser(s.getUserId());if(u==null||!Integer.valueOf(0).equals(u.getStatus()))return SubscriptionPolicy.PAUSED;}
        return result;
    }
    public Long create(SubscriptionCreateReqVO req) {
        enabledUser(req.getUserId());
        if(!req.getExpiryTime().isAfter(req.getStartTime())||!req.getExpiryTime().isAfter(LocalDateTime.now()))throw invalid("到期时间应晚于开始时间和当前时间");
        if(!req.getUnlimited()&&req.getTotalBytes()<0)throw invalid("有限流量订阅的额度不能为负");
        if(req.getAssignments().size()>req.getNodeLimit())throw invalid("分配数量超过节点数量上限");
        if("interval".equals(req.getResetMode())&&req.getResetIntervalDays()==null)throw invalid("请填写流量重置间隔天数");
        if(req.getRegionId()!=null)regions.validateSelection(req.getRegionId(),null);
        if(req.getCityId()!=null){if(req.getRegionId()==null)throw invalid("选择城市时必须选择地区");cities.validateSelection(req.getRegionId(),req.getCityId(),null);}
        Set<Long> nodeIds=new HashSet<>();List<SubscriptionClientDO> prepared=new ArrayList<>();
        for(var a:req.getAssignments()) {
            if(!nodeIds.add(a.getNodeId()))throw invalid("同一订阅不能重复分配同一出口节点");
            prepared.add(prepare(a,req.getRegionId(),req.getCityId()));
        }
        Long id=transaction(()->{
            accounts.lock(req.getUserId());
            var previous=subscriptions.selectList(new LambdaQueryWrapper<SubscriptionDO>().eq(SubscriptionDO::getUserId,req.getUserId()));
            if(previous.stream().anyMatch(old->old.getEndedTime()==null&&old.getExpiryTime().isAfter(LocalDateTime.now())))throw invalid("该用户已有有效订阅，请续订或升级当前订阅");
            var s=BeanUtils.toBean(req,SubscriptionDO.class).setNumber("SN"+UUID.randomUUID().toString().replace("-","")).setSource("admin")
                .setOrderNo(blank(req.getOrderNo())).setPaused(false).setSyncStatus(0).setLastError("")
                .setUsedUpload(0L).setUsedDownload(0L).setLifetimeUpload(0L).setLifetimeDownload(0L);
            String token=SubscriptionPolicy.token();s.setToken(token).setTokenHash(SubscriptionPolicy.hash(token));
            s.setStatus(SubscriptionPolicy.status(s,LocalDateTime.now())).setNextResetTime(SubscriptionPolicy.nextReset(s,s.getStartTime()));
            subscriptions.insert(s);
            accounts.bind(req.getUserId(),s.getId());
            for(var c:prepared)insertClient(s,c);
            if(s.getOrderNo()!=null)order(s.getId(),s.getOrderNo(),"create");
            log(s.getId(),"create","后台创建订阅，等待生效并配置服务器",0,0,true);
            return s.getId();
        });
        enqueue(id);return id;
    }
    private SubscriptionClientDO prepare(SubscriptionCreateReqVO.Assignment a,Long regionId,Long cityId) {
        var n=nodes.require(a.getNodeId());regions.validateSelection(n.getRegionId(),null);cities.validateSelection(n.getRegionId(),n.getCityId(),null);if(n.getShelfStatus()!=1)throw invalid("只能领用已上架的出口节点");
        if(regionId!=null&&!regionId.equals(n.getRegionId())||cityId!=null&&!cityId.equals(n.getCityId()))throw invalid("节点不在订阅允许的地区城市内");
        var in=gateway.inbound(a.getServerId(),a.getInboundId());
        try{SubscriptionConnection.validate(in);SubscriptionConnection.host(a.getPublicHost());}catch(IllegalArgumentException e){throw invalid(e.getMessage());}
        return new SubscriptionClientDO().setNodeId(a.getNodeId()).setServerId(a.getServerId()).setInboundId(a.getInboundId()).setPublicHost(a.getPublicHost())
            .setProtocol(String.valueOf(in.get("protocol"))).setConnectionName(n.getName()).setCredential("trojan".equals(in.get("protocol"))?SubscriptionPolicy.token():UUID.randomUUID().toString())
            .setReleased(false).setSyncStatus(0).setRemoteCreated(false).setNodeVersion(0).setLastError("").setAssignedTime(LocalDateTime.now())
            .setSampleUpload(0L).setSampleDownload(0L).setUsedUpload(0L).setUsedDownload(0L);
    }
    private void insertClient(SubscriptionDO s,SubscriptionClientDO c) {
        c.setSubscriptionId(s.getId()).setEmail("pending-"+UUID.randomUUID());clients.insert(c);
        c.setEmail("sn"+TenantContextHolder.getRequiredTenantId()+"s"+s.getId()+"c"+c.getId());clients.updateById(c);
        var a=new SubscriptionAssignmentDO().setNodeId(c.getNodeId()).setServerId(c.getServerId()).setUserId(s.getUserId()).setSubscriptionId(s.getId());
        a.setClientId(c.getId()).setAssignedTime(c.getAssignedTime()).setExpiryTime(s.getExpiryTime()).setSubscriptionStatus(1).setAuthorizationStatus(0);
        assignments.insert(a);
    }
    public void action(SubscriptionActionReqVO req) {
        transaction(()->{
            accounts.lock(require(req.getId()).getUserId(),"end".equals(req.getAction()));
            var s=lock(req.getId());
            if(s.getEndedTime()!=null&&!Set.of("reset-link","remark").contains(req.getAction()))throw invalid("已结束订阅不能重新启用，请创建新订阅");
            String message;
            switch(req.getAction()) {
                case "extend" -> {if(subscriptions.selectList(new LambdaQueryWrapper<SubscriptionDO>().eq(SubscriptionDO::getUserId,s.getUserId())).stream().anyMatch(other->!other.getId().equals(s.getId())&&other.getEndedTime()==null&&other.getExpiryTime().isAfter(LocalDateTime.now())))throw invalid("该用户已有其他有效订阅，不能延长此订阅");expirePacks(s);try{s.setExpiryTime(SubscriptionPolicy.extend(s,req.getDays(),req.getExpiryTime(),LocalDateTime.now()));}catch(IllegalArgumentException e){throw invalid(e.getMessage());}message="到期时间延长至 "+s.getExpiryTime();}
                case "add-traffic" -> {if(Boolean.TRUE.equals(s.getUnlimited()))throw invalid("无限流量订阅无需增加额度");if(req.getBytes()==null)throw invalid("请填写增加的流量");long next=Math.addExact(s.getTotalBytes(),req.getBytes());if(next>9000000000000000L)throw invalid("流量额度超出范围");s.setTotalBytes(next);if(s.getBaseTotalBytes()!=null)s.setBaseTotalBytes(Math.addExact(s.getBaseTotalBytes(),req.getBytes()));message="增加流量 "+req.getBytes()+" 字节";}
                case "reset-traffic" -> {if(!collect(s))throw invalid("流量采集失败，请先恢复服务器连接再重置，避免旧流量计入新周期");reset(s,"reset-traffic");message="已重置当前周期流量，历史使用量保留";}
                case "end" -> {s.setEndedTime(LocalDateTime.now());message="订阅已结束，等待撤销服务器客户端";}
                case "pause" -> {s.setPaused(true);message="暂停订阅，不顺延到期时间";}
                case "resume" -> {enabledUser(s.getUserId());if(!s.getExpiryTime().isAfter(LocalDateTime.now()))throw invalid("请先延长已到期订阅");if(!s.getUnlimited()&&SubscriptionPolicy.used(s)>=s.getTotalBytes())throw invalid("请先增加流量或重置流量");s.setPaused(false);message="恢复订阅，等待配置服务器";}
                case "reset-link" -> {String token=SubscriptionPolicy.token();s.setToken(token).setTokenHash(SubscriptionPolicy.hash(token));message="已重置订阅链接，旧链接立即失效，客户端 UUID 保持";}
                case "remark" -> {s.setRemark(req.getRemark()==null?"":req.getRemark());message="修改订阅备注";}
                default -> throw invalid("无效操作");
            }
            if(req.getRemark()!=null&&!"remark".equals(req.getAction()))message+="；原因："+req.getRemark();
            if(req.getOrderNo()!=null&&!req.getOrderNo().isBlank()&&Set.of("extend","add-traffic").contains(req.getAction()))order(s.getId(),req.getOrderNo().trim(),req.getAction());
            s.setStatus(effectiveStatus(s));
            if(!Set.of("reset-link","remark").contains(req.getAction()))s.setSyncStatus(0);
            subscriptions.updateById(s);
            if(!Set.of("reset-link","remark").contains(req.getAction()))accounts.changed(s.getUserId());
            assignments.update(null,new LambdaUpdateWrapper<SubscriptionAssignmentDO>().eq(SubscriptionAssignmentDO::getSubscriptionId,s.getId()).set(SubscriptionAssignmentDO::getExpiryTime,s.getExpiryTime()).set(SubscriptionAssignmentDO::getSubscriptionStatus,s.getStatus()==1?0:1));
            log(s.getId(),req.getAction(),message,0,0,true);return null;
        });
        if(!Set.of("reset-link","remark").contains(req.getAction()))enqueue(req.getId());
    }
    public void assign(Long id,SubscriptionCreateReqVO.Assignment req) {
        var old=require(id);var prepared=prepare(req,old.getRegionId(),old.getCityId());
        transaction(()->{
            accounts.lock(old.getUserId());var s=lock(id);if(s.getEndedTime()!=null)throw invalid("已结束订阅不能分配节点");
            var list=clientList(id);
            if(list.stream().noneMatch(c->!c.getReleased()&&c.getNodeId().equals(req.getNodeId())) && list.stream().filter(c->!c.getReleased()).map(SubscriptionClientDO::getNodeId).distinct().count()>=s.getNodeLimit())throw invalid("已达到节点数量上限，请先释放原节点");
            if(list.stream().anyMatch(c->!c.getReleased()&&c.getNodeId().equals(req.getNodeId())&&c.getServerId().equals(req.getServerId())&&c.getInboundId().equals(req.getInboundId())))throw invalid("该出口节点已关联此服务器入站，请勿重复添加");
            insertClient(s,prepared);accounts.changed(s.getUserId());s.setSyncStatus(0);subscriptions.updateById(s);log(id,"assign","新增节点分配 "+req.getNodeId(),0,0,true);return null;
        });enqueue(id);
    }
    public void release(Long id,Long clientId) {
        transaction(()->{
            accounts.lock(require(id).getUserId(),true);var s=lock(id);var c=clients.selectById(clientId);if(c==null||!id.equals(c.getSubscriptionId()))throw invalid("客户端不属于该订阅");
            if(!c.getReleased()){accounts.changed(s.getUserId());c.setReleased(true).setReleasedTime(LocalDateTime.now()).setSyncStatus(0);clients.updateById(c);s.setSyncStatus(0);subscriptions.updateById(s);log(id,"release","释放节点 "+c.getNodeId()+"，等待撤销客户端",0,0,true);}return null;
        });enqueue(id);
    }
    public Map<String,String> credentials(Long id,Long clientId) {
        require(id);var c=clients.selectById(clientId);
        if(c==null||!id.equals(c.getSubscriptionId()))throw invalid("客户端不属于该订阅");
        return Map.of("protocol",c.getProtocol(),"credential",c.getCredential(),"connectionUri",c.getConnectionUri()==null?"":c.getConnectionUri());
    }
    public void resetCredential(Long id,Long clientId) {
        try {transaction(()->{
            var s=lock(id);var c=clients.selectById(clientId);
            if(c==null||!id.equals(c.getSubscriptionId())||c.getReleased())throw invalid("请选择当前订阅已分配的客户端");
            if(s.getEndedTime()!=null)throw invalid("已结束订阅不能重置客户端");
            gateway.sync(s,c,false);
            if(!collect(s))throw invalid("流量采集失败，暂不能重置客户端认证");
            gateway.removeClient(c);
            c.setCredential("trojan".equals(c.getProtocol())?SubscriptionPolicy.token():UUID.randomUUID().toString())
                .setConnectionUri("").setRemoteCreated(false).setSampleUpload(0L).setSampleDownload(0L).setSyncStatus(0);
            clients.updateById(c);s.setSyncStatus(0);subscriptions.updateById(s);
            updateAssignment(s,c,false,0);
            log(id,"reset-client","客户端 "+clientId+" 的旧认证已撤销，新认证等待配置",0,0,true);return null;
        });}finally{enqueue(id);}
    }
    private List<SubscriptionClientDO> clientList(Long id){return clients.selectList(new LambdaQueryWrapper<SubscriptionClientDO>().eq(SubscriptionClientDO::getSubscriptionId,id).orderByAsc(SubscriptionClientDO::getId));}
    /** Saves incremental samples before computing shared subscription quota. */
    private boolean collect(SubscriptionDO s) {
        boolean complete=true;long upload=0,download=0;
        for(var c:clientList(s.getId())) {
            if(!c.getRemoteCreated()||c.getReleased()&&c.getSyncStatus()==2)continue;
            try {
                var t=gateway.traffic(c);long du=SubscriptionPolicy.delta(c.getSampleUpload(),t.upload()),dd=SubscriptionPolicy.delta(c.getSampleDownload(),t.download());
                c.setSampleUpload(t.upload()).setSampleDownload(t.download()).setUsedUpload(Math.addExact(c.getUsedUpload(),du)).setUsedDownload(Math.addExact(c.getUsedDownload(),dd)).setLastTrafficTime(LocalDateTime.now());clients.updateById(c);
                upload=Math.addExact(upload,du);download=Math.addExact(download,dd);
            } catch(Exception e){complete=false;}
        }
        if(s.getBaseTotalBytes()!=null) {
            long increment="download".equals(s.getTrafficMode())?download:Math.addExact(upload,download);
            long extra=Math.max(0,Math.addExact(SubscriptionPolicy.used(s),increment)-s.getBaseTotalBytes())-Math.max(0,SubscriptionPolicy.used(s)-s.getBaseTotalBytes());
            long consumed=0;
            for(var pack:jdbc.queryForList("SELECT id,remaining_bytes FROM subscription_traffic_pack WHERE tenant_id=? AND subscription_id=? AND remaining_bytes>0 ORDER BY id FOR UPDATE",s.getTenantId(),s.getId())) {
                long take=Math.min(extra,((Number)pack.get("remaining_bytes")).longValue());
                jdbc.update("UPDATE subscription_traffic_pack SET remaining_bytes=remaining_bytes-? WHERE tenant_id=? AND id=?",take,s.getTenantId(),pack.get("id"));extra-=take;consumed+=take;
                if(extra==0)break;
            }
            s.setExtraUsedBytes((s.getExtraUsedBytes()==null?0:s.getExtraUsedBytes())+consumed);
        }
        s.setUsedUpload(Math.addExact(s.getUsedUpload(),upload)).setUsedDownload(Math.addExact(s.getUsedDownload(),download))
            .setLifetimeUpload(Math.addExact(s.getLifetimeUpload(),upload)).setLifetimeDownload(Math.addExact(s.getLifetimeDownload(),download));
        if(complete)s.setLastTrafficTime(LocalDateTime.now());
        if(upload!=0||download!=0)log(s.getId(),"traffic","采集客户端增量流量",upload,download,true);
        return complete;
    }
    private void reset(SubscriptionDO s,String action) {
        log(s.getId(),"cycle-end","流量周期结束，重置前使用量",s.getUsedUpload(),s.getUsedDownload(),true);
        if(s.getBaseTotalBytes()!=null) {
            s.setTotalBytes(Math.addExact(s.getBaseTotalBytes(),packRemaining(s))).setExtraUsedBytes(0L);
        }
        s.setUsedUpload(0L).setUsedDownload(0L);
        clients.update(null,new LambdaUpdateWrapper<SubscriptionClientDO>().eq(SubscriptionClientDO::getSubscriptionId,s.getId()).set(SubscriptionClientDO::getUsedUpload,0L).set(SubscriptionClientDO::getUsedDownload,0L));
        LocalDateTime next=s.getNextResetTime();
        if(next!=null) {
            while(next!=null&&!next.isAfter(LocalDateTime.now()))next=SubscriptionPolicy.nextReset(s,next);
            s.setNextResetTime(next);
        }
    }
    public long packRemaining(SubscriptionDO s) {
        Long value=jdbc.queryForObject("SELECT COALESCE(SUM(remaining_bytes),0) FROM subscription_traffic_pack WHERE tenant_id=? AND subscription_id=?",Long.class,s.getTenantId(),s.getId());return value==null?0:value;
    }
    private void expirePacks(SubscriptionDO s) {
        if(s.getBaseTotalBytes()!=null&&!s.getExpiryTime().isAfter(LocalDateTime.now())) {
            jdbc.update("UPDATE subscription_traffic_pack SET remaining_bytes=0 WHERE tenant_id=? AND subscription_id=?",s.getTenantId(),s.getId());
            s.setTotalBytes(s.getBaseTotalBytes()).setExtraUsedBytes(0L);
        }
    }
    /** Caller holds the unique user account lock and runs in the same database transaction. */
    public void commercialChange(long id,Long plan,long base,String mode,int interval,String traffic,int limit,LocalDateTime expiry,boolean resetCycle,List<SubscriptionCreateReqVO.Assignment> target) {
        var s=lock(id);
        boolean ruleChanged=!Objects.equals(s.getResetMode(),mode)||s.getResetIntervalDays()!=interval;
        if(s.getEndedTime()!=null||!s.getExpiryTime().isAfter(LocalDateTime.now()))throw invalid("订阅已过期或结束，不能续费，请购买新订阅");
        if(!collect(s))throw invalid("流量采集失败，暂不能变更订阅，请稍后重试");
        expirePacks(s);
        if(resetCycle)reset(s,"purchase");
        s.setPlanId(plan).setBaseTotalBytes(base).setTrafficMode(traffic).setResetMode(mode).setResetIntervalDays(interval).setNodeLimit(limit).setExpiryTime(expiry);
        s.setTotalBytes(Math.addExact(base,Math.addExact(packRemaining(s),s.getExtraUsedBytes()==null?0:s.getExtraUsedBytes())));
        if(resetCycle)s.setStartTime(LocalDateTime.now());
        if(resetCycle||ruleChanged||"none".equals(mode))s.setNextResetTime(SubscriptionPolicy.nextReset(s,LocalDateTime.now()));
        if(target!=null) {
            var old=clientList(id);
            for(var c:old)if(!c.getReleased()&&target.stream().noneMatch(a->a.getNodeId().equals(c.getNodeId())&&a.getServerId().equals(c.getServerId())&&a.getInboundId().equals(c.getInboundId())&&a.getPublicHost().equals(c.getPublicHost()))) {c.setReleased(true).setReleasedTime(LocalDateTime.now());clients.updateById(c);}
            for(var a:target)if(old.stream().noneMatch(c->!c.getReleased()&&c.getNodeId().equals(a.getNodeId())&&c.getServerId().equals(a.getServerId())&&c.getInboundId().equals(a.getInboundId())&&c.getPublicHost().equals(a.getPublicHost())))insertClient(s,prepare(a,null,null));
        }
        s.setStatus(effectiveStatus(s)).setSyncStatus(0);subscriptions.updateById(s);
        log(id,"purchase","套餐订单变更权益，等待服务器核对",0,0,true);
    }
    public void commercialReset(long id){var s=require(id);if(s.getEndedTime()!=null||!s.getExpiryTime().isAfter(LocalDateTime.now()))throw invalid("当前订阅已到期");var req=new SubscriptionActionReqVO();req.setId(id);req.setAction("reset-traffic");action(req);}
    public void commercialTraffic(long id,long purchase,long bytes) {
        var s=lock(id);if(s.getEndedTime()!=null||!s.getExpiryTime().isAfter(LocalDateTime.now()))throw invalid("当前订阅已到期");
        if(!collect(s))throw invalid("流量采集失败，暂不能加购");
        jdbc.update("INSERT INTO subscription_traffic_pack (tenant_id,subscription_id,purchase_id,total_bytes,remaining_bytes) VALUES (?,?,?,?,?)",s.getTenantId(),id,purchase,bytes,bytes);
        s.setTotalBytes(Math.addExact(s.getTotalBytes(),bytes)).setSyncStatus(0).setStatus(effectiveStatus(s));subscriptions.updateById(s);
        log(id,"add-traffic","订单补充流量 "+bytes+" 字节",0,0,true);
    }
    public boolean enqueue(Long id) {
        require(id);Long tenant=TenantContextHolder.getRequiredTenantId();
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void afterCommit(){enqueueCommitted(id,tenant);}
            });return true;
        }
        return enqueueCommitted(id,tenant);
    }
    private boolean enqueueCommitted(Long id,Long tenant) {
        String key=tenant+":"+id;
        if(!queued.add(key))return true;
        try {executor.execute(()->{try{TenantUtils.execute(tenant,()->reconcile(id));}finally{queued.remove(key);}});return true;}
        catch(RejectedExecutionException e){queued.remove(key);return false;}
    }
    public void reconcile(Long id) {
        try {transaction(()->{
            var s=lock(id);
            List<String> failures=new ArrayList<>();
            // Create new clients disabled before reading their counters. Enable only after routing is ready.
            if(effectiveStatus(s)==SubscriptionPolicy.ACTIVE) {
                for(var c:clientList(id)) {
                    if(c.getReleased()||c.getNodeVersion()!=0)continue;
                    try {
                        clients.updateById(new SubscriptionClientDO().setId(c.getId()).setRemoteCreated(true));
                        gateway.prepareClient(s,c);
                        c.setRemoteCreated(true);clients.updateById(c);
                    } catch(Exception e) {
                        c.setRemoteCreated(true).setSyncStatus(3).setLastError(safe(e));clients.updateById(c);
                        failures.add("客户端 "+c.getId()+" 创建失败："+safe(e));
                    }
                }
            }
            boolean trafficComplete=collect(s);
            if(trafficComplete&&s.getEndedTime()==null&&s.getNextResetTime()!=null&&!s.getNextResetTime().isAfter(LocalDateTime.now())&&!s.getStartTime().isAfter(LocalDateTime.now())&&s.getExpiryTime().isAfter(LocalDateTime.now()))reset(s,"auto-reset");
            int previousSync=s.getSyncStatus();
            int status=effectiveStatus(s);s.setStatus(status).setSyncStatus(1);subscriptions.updateById(s);
            boolean enable=status==SubscriptionPolicy.ACTIVE&&trafficComplete&&failures.isEmpty();
            if(!trafficComplete)failures.add("流量采集失败，客户端暂时禁用，恢复连接后重试");
            for(var c:clientList(id)) {
                boolean wanted=enable&&!c.getReleased();
                try {
                    if(wanted) { // Track uncertain writes so expiry reconciliation also revokes a client after an interrupted add.
                        clients.updateById(new SubscriptionClientDO().setId(c.getId()).setRemoteCreated(true));
                    }
                    String uri;
                    if(status==SubscriptionPolicy.PENDING&&!c.getRemoteCreated())uri="";
                    else if(c.getReleased()&&c.getSyncStatus()==2)uri="";
                    else uri=gateway.sync(s,c,wanted);
                    c.setConnectionUri(uri).setSyncStatus(2).setLastError("");
                    if(wanted)c.setRemoteCreated(true).setNodeVersion(nodes.require(c.getNodeId()).getConfigVersion());
                    clients.updateById(c);
                    updateAssignment(s,c,wanted,2);
                } catch(Exception e) {
                    String message=safe(e);c.setRemoteCreated(c.getRemoteCreated()||wanted).setSyncStatus(3).setLastError(message);clients.updateById(c);updateAssignment(s,c,wanted,3);
                    failures.add("客户端 "+c.getId()+"："+message);
                }
            }
            if(!enable&&trafficComplete) {
                // Capture the final bytes after the client has actually been disabled.
                if(!collect(s))failures.add("授权撤销后最终流量采集失败，将继续重试");
            }
            String error=String.join("；",failures);if(error.length()>1000)error=error.substring(0,1000);
            boolean changed=!Objects.equals(s.getLastError(),error)||previousSync!=2;
            s.setSyncStatus(failures.isEmpty()?2:3).setLastError(error).setLastSyncTime(LocalDateTime.now());subscriptions.updateById(s);
            if(changed)log(id,"sync",failures.isEmpty()?"服务器客户端和出口路由已回读核对":error,0,0,failures.isEmpty());
            return null;
        });}catch(Exception e){
            // The durable desired state survives a restart; uncertain remote writes are reconciled again.
            subscriptions.updateById(new SubscriptionDO().setId(id).setSyncStatus(3).setLastError("同步任务失败，请检查配置并重试").setLastSyncTime(LocalDateTime.now()));
        }
    }
    private void updateAssignment(SubscriptionDO s,SubscriptionClientDO c,boolean wanted,int state) {
        assignments.update(null,new LambdaUpdateWrapper<SubscriptionAssignmentDO>().eq(SubscriptionAssignmentDO::getClientId,c.getId())
            .set(SubscriptionAssignmentDO::getExpiryTime,s.getExpiryTime()).set(SubscriptionAssignmentDO::getSubscriptionStatus,s.getStatus()==1&&!c.getReleased()?0:1)
            .set(SubscriptionAssignmentDO::getAuthorizationStatus,wanted?(state==2?1:0):(state==0?0:state==2?3:4)));
    }
    public record ScanResult(int selected, int accepted, int deferred, int failed) {
        public String summary() {
            return "扫描 " + selected + " 份订阅，接受或已在队列 " + accepted
                + " 份，队列满延期 " + deferred + " 份，提交失败 " + failed
                + " 份；仅表示扫描提交结果，具体同步结果请查看订阅详情";
        }
    }
    /** Called by the managed Quartz job; immediate business actions still enqueue directly. */
    public ScanResult scan() {
        var due=TenantUtils.executeIgnore(()->subscriptions.selectList(new LambdaQueryWrapper<SubscriptionDO>()
            .and(w->w.in(SubscriptionDO::getStatus,0,1,2,3).or().ne(SubscriptionDO::getSyncStatus,2)).orderByAsc(SubscriptionDO::getLastSyncTime).last("LIMIT 100")));
        int accepted=0, deferred=0, failed=0;
        for(var s:due) {
            try {
                boolean result=TenantUtils.execute(s.getTenantId(),()->enqueue(s.getId()));
                if(result)accepted++;else deferred++;
            } catch(RuntimeException e) {failed++;}
        }
        return new ScanResult(due.size(),accepted,deferred,failed);
    }
    public PageResult<Map<String,Object>> page(SubscriptionPageReqVO req) {
        var p=subscriptions.page(req);return new PageResult<>(p.getList().stream().map(this::response).toList(),p.getTotal());
    }
    private Map<String,Object> response(SubscriptionDO s) {
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("id",s.getId());r.put("number",s.getNumber());r.put("userId",s.getUserId());r.put("source",s.getSource());r.put("orderNo",s.getOrderNo());
        var u=users.getUser(s.getUserId());r.put("userName",u==null?"用户已删除":u.getNickname());r.put("userEmail",u==null?null:u.getEmail());
        r.put("startTime",s.getStartTime());r.put("expiryTime",s.getExpiryTime());r.put("endedTime",s.getEndedTime());r.put("status",effectiveStatus(s));r.put("syncStatus",s.getSyncStatus());r.put("lastError",s.getLastError());
        r.put("unlimited",s.getUnlimited());r.put("totalBytes",s.getTotalBytes());r.put("usedUpload",s.getUsedUpload());r.put("usedDownload",s.getUsedDownload());r.put("usedBytes",SubscriptionPolicy.used(s));r.put("remainingBytes",s.getUnlimited()?null:Math.max(0,s.getTotalBytes()-SubscriptionPolicy.used(s)));
        r.put("lifetimeUpload",s.getLifetimeUpload());r.put("lifetimeDownload",s.getLifetimeDownload());r.put("trafficMode",s.getTrafficMode());r.put("resetMode",s.getResetMode());r.put("resetIntervalDays",s.getResetIntervalDays());r.put("nextResetTime",s.getNextResetTime());r.put("nodeLimit",s.getNodeLimit());r.put("regionId",s.getRegionId());r.put("cityId",s.getCityId());r.put("remark",s.getRemark());r.put("createTime",s.getCreateTime());r.put("lastTrafficTime",s.getLastTrafficTime());r.put("lastSyncTime",s.getLastSyncTime());
        r.put("nodeCount",clientList(s.getId()).stream().filter(c->!c.getReleased()).map(SubscriptionClientDO::getNodeId).distinct().count());
        r.put("clientCount",clients.selectCount(new LambdaQueryWrapper<SubscriptionClientDO>().eq(SubscriptionClientDO::getSubscriptionId,s.getId()).eq(SubscriptionClientDO::getReleased,false)));
        return r;
    }
    public Map<String,Object> detail(Long id) {
        var rows=clientList(id).stream().map(c->{Map<String,Object> r=new LinkedHashMap<>();r.put("id",c.getId());r.put("nodeId",c.getNodeId());r.put("nodeName",c.getConnectionName());r.put("serverId",c.getServerId());r.put("inboundId",c.getInboundId());r.put("protocol",c.getProtocol());r.put("email",c.getEmail());r.put("publicHost",c.getPublicHost());r.put("released",c.getReleased());r.put("syncStatus",c.getSyncStatus());r.put("remoteCreated",c.getRemoteCreated());r.put("lastError",c.getLastError());r.put("usedUpload",c.getUsedUpload());r.put("usedDownload",c.getUsedDownload());r.put("lastTrafficTime",c.getLastTrafficTime());r.put("assignedTime",c.getAssignedTime());r.put("releasedTime",c.getReleasedTime());return r;}).toList();
        return Map.of("subscription",response(require(id)),"clients",rows,"orders",orders.selectList(new LambdaQueryWrapper<SubscriptionOrderDO>().eq(SubscriptionOrderDO::getSubscriptionId,id).orderByDesc(SubscriptionOrderDO::getId)));
    }
    public PageResult<SubscriptionLogDO> logPage(SubscriptionLogPageReqVO req) {
        require(req.getSubscriptionId());
        return logs.selectPage(req, new LambdaQueryWrapper<SubscriptionLogDO>()
                .eq(SubscriptionLogDO::getSubscriptionId, req.getSubscriptionId())
                .orderByDesc(SubscriptionLogDO::getId));
    }
    public List<Map<String,Object>> userOptions(String keyword) {
        var q=new LambdaQueryWrapper<MemberUserDO>().eq(MemberUserDO::getStatus,0);
        if(keyword!=null&&!keyword.isBlank())q.and(w->w.like(MemberUserDO::getEmail,keyword).or().like(MemberUserDO::getNickname,keyword));
        return userMapper.selectList(q.orderByDesc(MemberUserDO::getId).last("LIMIT 50")).stream().map(u->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",u.getId());row.put("nickname",u.getNickname());row.put("email",u.getEmail());return row;}).toList();
    }
    public Map<String,String> link(Long id){var s=require(id);return Map.of("path","/app-api/subscription/feed/"+s.getTenantId()+"/"+s.getToken());}
    public record Feed(String content,long upload,long download,long total,long expiry) {}
    @jakarta.annotation.Resource private ClashRuleService clashRules;
    public Feed feed(String token) { return feed(token,"base64"); }
    public Feed feed(String token,String format) {
        if(!Set.of("base64","clash").contains(format))throw invalid("订阅格式不支持");
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw invalid("订阅链接无效");
        var s=subscriptions.selectOne(new LambdaQueryWrapper<SubscriptionDO>().eq(SubscriptionDO::getTokenHash,SubscriptionPolicy.hash(token)));
        if(s==null||effectiveStatus(s)!=SubscriptionPolicy.ACTIVE)throw invalid("订阅不存在、未生效或已停用");
        if(s.getSyncStatus()!=2)throw invalid("订阅配置尚未成功，请联系管理员");
        var list=clientList(s.getId()).stream().filter(c->!c.getReleased()&&c.getSyncStatus()==2&&c.getConnectionUri()!=null&&!c.getConnectionUri().isBlank()).map(SubscriptionClientDO::getConnectionUri).toList();
        if(list.isEmpty())throw invalid("订阅暂无可用节点");
        return new Feed("clash".equals(format)?ClashSubscription.render(list,clashRules.effective(s.getUserId())):Base64.getEncoder().encodeToString(String.join("\n",list).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"download".equals(s.getTrafficMode())?0:s.getUsedUpload(),s.getUsedDownload(),s.getUnlimited()?0:s.getTotalBytes(),s.getExpiryTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
    }
    public Feed clash(Long id) { return feed(require(id).getToken(),"clash"); }
    public List<Map<String,Object>> nodes(Long id) {
        // The same entitlement and synchronization checks apply to node metadata and configuration downloads.
        clash(id);
        return clientList(id).stream().filter(c->!c.getReleased()&&c.getSyncStatus()==2&&c.getConnectionUri()!=null&&!c.getConnectionUri().isBlank()).map(c->{
            var proxy=ClashSubscription.proxy(c.getConnectionUri());
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",c.getNodeId());row.put("name",c.getConnectionName());
            row.put("protocol",c.getProtocol());row.put("server",proxy.get("server"));row.put("port",proxy.get("port"));return row;
        }).toList();
    }
    private void order(Long id,String no,String purpose){orders.insert(new SubscriptionOrderDO().setSubscriptionId(id).setOrderNo(no).setPurpose(purpose));}
    private void log(Long id,String action,String message,long up,long down,boolean ok){logs.insert(new SubscriptionLogDO().setSubscriptionId(id).setAction(action).setMessage(message).setUploadBytes(up).setDownloadBytes(down).setSuccess(ok?1:0));}
    private String blank(String text){return text==null||text.isBlank()?null:text.trim();}
    private String safe(Exception e){return e instanceof IllegalStateException||e instanceof IllegalArgumentException?e.getMessage():"面板操作失败，请检查连接、Token 与版本兼容性";}
}
