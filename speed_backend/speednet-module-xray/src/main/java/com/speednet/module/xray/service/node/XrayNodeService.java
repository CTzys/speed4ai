package com.speednet.module.xray.service.node;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.speednet.framework.common.pojo.PageResult;
import com.speednet.framework.common.util.object.BeanUtils;
import com.speednet.framework.common.exception.ErrorCode;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import com.speednet.framework.tenant.core.util.TenantUtils;
import com.speednet.module.xray.controller.admin.node.vo.*;
import com.speednet.module.xray.dal.dataobject.node.*;
import com.speednet.module.xray.dal.mysql.node.*;
import com.speednet.module.xray.dal.mysql.server.XrayServerMapper;
import com.speednet.module.xray.service.server.XrayServerService;
import com.speednet.module.xray.framework.panel.XrayNodePanelClient;
import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.speednet.framework.common.exception.util.ServiceExceptionUtil.exception;
@Service
public class XrayNodeService {
    private static final ErrorCode INVALID=new ErrorCode(1_012_004_000,"节点操作失败：{}");
    @Resource private XrayNodeMapper nodes;
    @Resource private XrayRegionService regions;
    @Resource private XrayCityService cities;
    @Resource private XrayNodeServerMapper deployments;
    @Resource private XrayNodeCheckLogMapper logs;
    @Resource private XrayNodeTaskMapper tasks;
    @Resource private XrayNodeAssignmentMapper assignments;
    @Resource private XrayServerService servers;
    @Resource private XrayServerMapper serverMapper;
    @Resource private Socks5Probe probe;
    @Resource private XrayNodePanelClient panel;
    // Bounded work queue. Each runnable processes an entire batch, preserving per-server ordering.
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(20),r->{Thread t=new Thread(r,"xray-node-worker");t.setDaemon(true);return t;});
    private final Object[] locks=new Object[64];
    public XrayNodeService(){Arrays.setAll(locks,i->new Object());}
    @PreDestroy public void close(){executor.shutdownNow();}
    private RuntimeException invalid(String text){return exception(INVALID,text);}
    public XrayNodeDO require(Long id){var n=nodes.selectById(id);if(n==null)throw invalid("节点不存在");return n;}
    public Long create(XrayNodeSaveReqVO req){
        normalize(req);
        regions.validateSelection(req.getRegionId(), null);
        cities.validateSelection(req.getRegionId(), req.getCityId(), null);
        var n=BeanUtils.toBean(req,XrayNodeDO.class).setId(null).setIdentityKey(Socks5NodeParser.identity(req)).setShelfStatus(0).setHealthStatus(0).setConfigVersion(1);
        try{nodes.insert(n);}catch(DuplicateKeyException e){throw invalid("相同地址、端口和认证账号的节点已存在，请编辑原节点");}
        return n.getId();
    }
    private void normalize(XrayNodeSaveReqVO req){try{Socks5NodeParser.normalize(req);}catch(IllegalArgumentException e){throw invalid(e.getMessage());}}
    public void update(XrayNodeSaveReqVO req){
        synchronized(nodeLock(req.getId())) {
            var old=require(req.getId());
            if(req.getAuthType()!=null&&req.getAuthType()==1&&(req.getPassword()==null||req.getPassword().isEmpty()))req.setPassword(old.getPassword());
            normalize(req);
            regions.validateSelection(req.getRegionId(), old.getRegionId());
            cities.validateSelection(req.getRegionId(), req.getCityId(), old.getCityId());
            boolean changed=!Objects.equals(old.getHost(),req.getHost())||!Objects.equals(old.getPort(),req.getPort())||!Objects.equals(old.getAuthType(),req.getAuthType())||!Objects.equals(old.getUsername(),req.getUsername())||!Objects.equals(old.getPassword(),req.getPassword());
            var n=BeanUtils.toBean(req,XrayNodeDO.class).setIdentityKey(Socks5NodeParser.identity(req));
            if(changed)n.setConfigVersion(old.getConfigVersion()+1).setHealthStatus(0).setShelfStatus(0).setLastError("连接信息已修改，请确认后重新上架；已部署配置待同步");
            try{nodes.updateById(n);}catch(DuplicateKeyException e){throw invalid("相同节点已存在");}
            if(changed)deployments.update(null,new LambdaUpdateWrapper<XrayNodeServerDO>().eq(XrayNodeServerDO::getNodeId,old.getId()).ne(XrayNodeServerDO::getStatus,5).set(XrayNodeServerDO::getStatus,0));
        }
    }
    public PageResult<XrayNodeRespVO> page(XrayNodePageReqVO req){var p=nodes.selectPage(req);return new PageResult<>(p.getList().stream().map(this::response).toList(),p.getTotal());}
    public XrayNodeRespVO get(Long id){return response(require(id));}
    private XrayNodeRespVO response(XrayNodeDO n){
        var r=BeanUtils.toBean(n,XrayNodeRespVO.class).setRegion(n.getRegionId()==null ? "" : regions.require(n.getRegionId()).getName()).setCity(n.getCityId()==null ? "" : cities.require(n.getCityId()).getName()).setPasswordConfigured(n.getPassword()!=null&&!n.getPassword().isEmpty());
        var ds=deployments.selectList(new LambdaQueryWrapper<XrayNodeServerDO>().eq(XrayNodeServerDO::getNodeId,n.getId()).ne(XrayNodeServerDO::getStatus,5));
        r.setServerCount(ds.size()).setDeployedServerCount(ds.stream().filter(d->d.getStatus()==2&&Objects.equals(d.getAppliedVersion(),n.getConfigVersion())).count());
        var c=assignments.counts(n.getId());
        r.setActiveUserCount(number(c.get("activeUserCount"))).setExpiredUserCount(number(c.get("expiredUserCount")));return r;
    }
    private long number(Object v){return v instanceof Number n?n.longValue():0;}
    public Map<String,Long> stats(){
        return Map.of("total",nodes.selectCount(),"up",nodes.selectCount(new LambdaQueryWrapper<XrayNodeDO>().eq(XrayNodeDO::getShelfStatus,1)),"down",nodes.selectCount(new LambdaQueryWrapper<XrayNodeDO>().eq(XrayNodeDO::getShelfStatus,0)),"healthy",nodes.selectCount(new LambdaQueryWrapper<XrayNodeDO>().eq(XrayNodeDO::getHealthStatus,1)),"unhealthy",nodes.selectCount(new LambdaQueryWrapper<XrayNodeDO>().eq(XrayNodeDO::getHealthStatus,2)));
    }
    public Map<String,Object> detail(Long id){
        var n=require(id);var ds=deployments.selectList(new LambdaQueryWrapper<XrayNodeServerDO>().eq(XrayNodeServerDO::getNodeId,id).orderByDesc(XrayNodeServerDO::getId));
        List<Map<String,Object>> rows=new ArrayList<>();
        for(var d:ds){Map<String,Object> row=new LinkedHashMap<>();row.put("id",d.getId());row.put("serverId",d.getServerId());row.put("outboundTag",d.getOutboundTag());row.put("status",d.getStatus());row.put("appliedVersion",d.getAppliedVersion());row.put("lastSyncTime",d.getLastSyncTime());row.put("lastError",d.getLastError());var s=serverMapper.selectById(d.getServerId());row.put("serverName",s==null?"服务器已删除":s.getName());rows.add(row);}
        var checks=logs.selectList(new LambdaQueryWrapper<XrayNodeCheckLogDO>().eq(XrayNodeCheckLogDO::getNodeId,id).orderByDesc(XrayNodeCheckLogDO::getId).last("LIMIT 50"));
        var operations=tasks.selectList(new LambdaQueryWrapper<XrayNodeTaskDO>().eq(XrayNodeTaskDO::getNodeId,id).orderByDesc(XrayNodeTaskDO::getId).last("LIMIT 50"));
        return Map.of("node",response(n),"servers",rows,"users",assignments.users(id),"checks",checks,"tasks",operations,"subscriptionIntegrated",true);
    }
    public List<Map<String,Object>> preview(String text){
        if(text==null||text.length()>200000)throw invalid("导入文本不能为空且最多 200000 字符");
        String[] lines=text.split("\\R",-1);if(lines.length>500)throw invalid("每次最多导入 500 行");
        List<Map<String,Object>> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;i<lines.length;i++){
            if(lines[i].isBlank())continue;
            Map<String,Object> row=new LinkedHashMap<>();row.put("line",i+1);
            try{
                var n=Socks5NodeParser.parse(lines[i]);String key=Socks5NodeParser.identity(n);
                var existing=nodes.selectOne(new LambdaQueryWrapper<XrayNodeDO>().eq(XrayNodeDO::getIdentityKey,key));
                boolean duplicate=!seen.add(key)||existing!=null;
                row.put("name",n.getName());row.put("host",n.getHost());row.put("port",n.getPort());row.put("username",n.getUsername());row.put("status",duplicate?"duplicate":"valid");row.put("message",duplicate?"节点重复；密码变化请编辑原节点":"可以导入");
            }catch(IllegalArgumentException e){row.put("status","error");row.put("message",e.getMessage());}
            result.add(row);
        }
        return result;
    }
    public List<Map<String,Object>> importText(String text, Long regionId, Long cityId){
        regions.validateSelection(regionId, null);
        cities.validateSelection(regionId, cityId, null);
        var rows=preview(text);String[] lines=text.split("\\R",-1);
        for(var row:rows)if("valid".equals(row.get("status"))){
            try{Long id=create(Socks5NodeParser.parse(lines[(int)row.get("line")-1]).setRegionId(regionId).setCityId(cityId));row.put("id",id);row.put("status","imported");row.put("message","导入成功，默认下架");}
            catch(Exception e){row.put("status","error");row.put("message","导入失败或节点已存在，请刷新检查");}
        }
        return rows;
    }
    public List<Map<String,Object>> shelf(List<Long> ids,boolean up){
        validateIds(ids);List<Map<String,Object>> result=new ArrayList<>();
        for(Long id:new LinkedHashSet<>(ids)){
            try{synchronized(nodeLock(id)){
                require(id);
                // Listing is a business state only. Detection and deployment belong to subscription allocation.
                nodes.updateById(new XrayNodeDO().setId(id).setShelfStatus(up?1:0));
                var task=new XrayNodeTaskDO().setNodeId(id).setAction(up?"上架":"下架").setBatchId(UUID.randomUUID().toString()).setStatus(2).setMessage(up?"允许订阅领用；上架不部署服务器":"停止新领用；已有授权保持").setEndTime(LocalDateTime.now());tasks.insert(task);
                result.add(Map.of("id",id,"success",true,"message",up?"已上架":"已下架"));
            }}catch(Exception e){result.add(Map.of("id",id,"success",false,"message",safe(e)));}
        }return result;
    }
    private void validateIds(List<Long> ids){if(ids==null||ids.isEmpty()||ids.size()>100||ids.stream().anyMatch(Objects::isNull))throw invalid("请选择 1–100 个节点");}
    private Object nodeLock(Long id){return locks[Math.floorMod(Objects.hash("node",id),locks.length)];}
    private Object serverLock(Long id){return locks[Math.floorMod(Objects.hash("server",id),locks.length)];}
    /** Shared server lock for subscription routing and the node deployment worker. */
    public <T> T withServerLock(Long serverId, java.util.function.Supplier<T> work) {
        synchronized(serverLock(serverId)) { return work.get(); }
    }
    public void prepareSubscriptionNode(Long nodeId, Long serverId) {
        check(nodeId);
        deploy(nodeId, serverId, "deploy");
    }
    public synchronized String submit(List<Long> ids,Long serverId,String action){
        validateIds(ids);if(!Set.of("check","deploy","verify","remove").contains(action))throw invalid("操作无效");
        for(Long id:ids)require(id);
        if(!"check".equals(action)){if(serverId==null)throw invalid("请选择服务器");servers.get(serverId);}
        if(executor.getQueue().remainingCapacity()==0)throw invalid("任务队列已满，请稍后重试");
        String batch=UUID.randomUUID().toString();List<Long> taskIds=new ArrayList<>();
        for(Long id:new LinkedHashSet<>(ids)){
            var task=new XrayNodeTaskDO().setBatchId(batch).setNodeId(id).setServerId(serverId).setAction(action).setStatus(0).setMessage("等待执行");tasks.insert(task);taskIds.add(task.getId());
        }
        Long tenant=TenantContextHolder.getRequiredTenantId();
        executor.execute(()->TenantUtils.execute(tenant,()->{
            for(Long taskId:taskIds){
                var t=tasks.selectById(taskId);if(t==null)continue;
                tasks.updateById(new XrayNodeTaskDO().setId(taskId).setStatus(1).setMessage("执行中"));
                try{
                    String message="check".equals(t.getAction())?check(t.getNodeId()):deploy(t.getNodeId(),t.getServerId(),t.getAction());
                    tasks.updateById(new XrayNodeTaskDO().setId(taskId).setStatus(2).setMessage(message).setEndTime(LocalDateTime.now()));
                }catch(Exception e){tasks.updateById(new XrayNodeTaskDO().setId(taskId).setStatus(3).setMessage(safe(e)).setEndTime(LocalDateTime.now()));}
            }
        }));return batch;
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverInterruptedTasks(){
        // This module uses one worker process. A restart cannot safely replay an uncertain remote write.
        var interrupted=TenantUtils.executeIgnore(()->tasks.selectList(new LambdaQueryWrapper<XrayNodeTaskDO>().in(XrayNodeTaskDO::getStatus,0,1)));
        for(var t:interrupted)TenantUtils.execute(t.getTenantId(),()->{
            tasks.updateById(new XrayNodeTaskDO().setId(t.getId()).setStatus(3).setMessage("服务重启中断任务，请核对远端后重试").setEndTime(LocalDateTime.now()));
        });
    }
    public List<XrayNodeTaskDO> task(String batch){return tasks.selectList(new LambdaQueryWrapper<XrayNodeTaskDO>().eq(XrayNodeTaskDO::getBatchId,batch).orderByAsc(XrayNodeTaskDO::getId));}
    private String check(Long id){
        var node=require(id);var r=probe.check(node);
        logs.insert(new XrayNodeCheckLogDO().setNodeId(id).setConfigVersion(node.getConfigVersion()).setStatus(r.success()?1:2).setLatencyMs(r.latencyMs()).setSource("管理后端 · TCP/HTTPS").setMessage(r.message()));
        synchronized(nodeLock(id)){
            if(Objects.equals(require(id).getConfigVersion(),node.getConfigVersion()))
                nodes.updateById(new XrayNodeDO().setId(id).setHealthStatus(r.success()?1:2).setLatencyMs(r.latencyMs()).setLastCheckTime(LocalDateTime.now()).setLastError(r.success()?"":r.message()));
        }
        if(!r.success())throw invalid(r.message());return r.message();
    }
    private String deploy(Long id,Long serverId,String action){
        // All remote changes for a server are serialized in this application instance.
        synchronized(serverLock(serverId)){
            var node=require(id);var server=servers.get(serverId);String tag="speednet-node-"+node.getTenantId()+"-"+id;
            var relation=deployments.selectOne(new LambdaQueryWrapper<XrayNodeServerDO>().eq(XrayNodeServerDO::getNodeId,id).eq(XrayNodeServerDO::getServerId,serverId));
            if(relation==null){relation=new XrayNodeServerDO().setNodeId(id).setServerId(serverId).setOutboundTag(tag).setStatus(0).setAppliedVersion(0);deployments.insert(relation);}
            try{
                relation.setStatus(1).setLastError("");deployments.updateById(relation);
                var response=panel.read(server);var config=panel.config(response);
                boolean remove="remove".equals(action);
                if(remove && (node.getShelfStatus()==1 || number(assignments.counts(id).get("activeUserCount"))>0))throw invalid("请先下架并迁移有效用户后再移除出站");
                if(!"verify".equals(action)){
                    XrayNodeConfig.apply(config,node,tag,remove);
                    panel.write(server,config,String.valueOf(response.getOrDefault("outboundTestUrl","https://www.google.com/generate_204")));
                    config=panel.config(panel.read(server));
                }
                boolean matches=XrayNodeConfig.matches(config,node,tag);
                if(remove){
                    if(((List<?>)config.get("outbounds")).stream().anyMatch(o->o instanceof Map<?,?> m&&tag.equals(m.get("tag"))))throw invalid("远端仍存在出站，请重新核对");
                    relation.setStatus(5).setLastError("");
                }else if(!matches){relation.setStatus(4).setLastError("远端出站缺失或与当前节点配置不一致");throw invalid(relation.getLastError());}
                else{
                    if(!Objects.equals(require(id).getConfigVersion(),node.getConfigVersion()))throw invalid("节点配置已变化，请重新部署");
                    relation.setStatus(2).setAppliedVersion(node.getConfigVersion()).setLastError("");
                }
                relation.setLastSyncTime(LocalDateTime.now());deployments.updateById(relation);
                return remove?"出站已移除":"SOCKS5 出站已配置并回读核对；用户路由由订阅管理维护";
            }catch(Exception e){
                relation.setStatus(relation.getStatus()==4?4:3).setLastError(safe(e)).setLastSyncTime(LocalDateTime.now());deployments.updateById(relation);throw e;
            }
        }
    }
    private String safe(Exception e){
        if(e instanceof com.speednet.framework.common.exception.ServiceException)return e.getMessage();
        if(e instanceof IllegalStateException)return e.getMessage()==null?"操作失败":e.getMessage();
        return "操作失败，请检查连接或刷新后重试";
    }
}
