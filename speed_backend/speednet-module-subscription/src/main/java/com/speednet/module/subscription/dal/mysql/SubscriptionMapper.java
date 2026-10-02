package com.speednet.module.subscription.dal.mysql;
import com.speednet.framework.mybatis.core.mapper.BaseMapperX;
import com.speednet.module.subscription.dal.dataobject.SubscriptionDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface SubscriptionMapper extends BaseMapperX<SubscriptionDO> { default SubscriptionDO lock(Long id) { return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubscriptionDO>().eq(SubscriptionDO::getId,id).last("FOR UPDATE")); }
 default com.speednet.framework.common.pojo.PageResult<SubscriptionDO> page(com.speednet.module.subscription.controller.admin.vo.SubscriptionPageReqVO req) {
  var q=new com.speednet.framework.mybatis.core.query.LambdaQueryWrapperX<SubscriptionDO>()
   .eqIfPresent(SubscriptionDO::getUserId,req.getUserId()).eqIfPresent(SubscriptionDO::getStatus,req.getStatus())
   .eqIfPresent(SubscriptionDO::getSyncStatus,req.getSyncStatus()).likeIfPresent(SubscriptionDO::getOrderNo,req.getOrderNo());
  if(req.getKeyword()!=null&&!req.getKeyword().isBlank()) q.like(SubscriptionDO::getNumber,req.getKeyword());
  if(Boolean.TRUE.equals(req.getExpiring()))q.gt(SubscriptionDO::getExpiryTime,java.time.LocalDateTime.now()).le(SubscriptionDO::getExpiryTime,java.time.LocalDateTime.now().plusDays(7)).isNull(SubscriptionDO::getEndedTime);
  return selectPage(req,q.orderByDesc(SubscriptionDO::getId));
 } }
