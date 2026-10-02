package com.speednet.module.subscription.service;
import com.speednet.module.subscription.dal.dataobject.SubscriptionDO;
import java.time.LocalDateTime;
import java.security.*; import java.nio.charset.StandardCharsets; import java.util.*;
public final class SubscriptionPolicy {
 private SubscriptionPolicy() {}
 public static final int PENDING=0, ACTIVE=1, PAUSED=2, EXHAUSTED=3, EXPIRED=4, ENDED=5;
 public static int status(SubscriptionDO s,LocalDateTime now) {
  if(s.getEndedTime()!=null)return ENDED;
  if(!s.getExpiryTime().isAfter(now))return EXPIRED;
  if(Boolean.TRUE.equals(s.getPaused()))return PAUSED;
  if(s.getStartTime().isAfter(now))return PENDING;
  if(!Boolean.TRUE.equals(s.getUnlimited())&&used(s)>=s.getTotalBytes())return EXHAUSTED;
  return ACTIVE;
 }
 public static long used(SubscriptionDO s){return "download".equals(s.getTrafficMode())?s.getUsedDownload():Math.addExact(s.getUsedUpload(),s.getUsedDownload());}
 public static long delta(long previous,long current){if(current<0||previous<0)throw new IllegalArgumentException("流量计数异常");return current>=previous?current-previous:current;}
 public static LocalDateTime nextReset(SubscriptionDO s,LocalDateTime from){return switch(s.getResetMode()){case "monthly"->from.plusMonths(1);case "interval"->from.plusDays(s.getResetIntervalDays());default->null;};}
 public static LocalDateTime extend(SubscriptionDO s,Integer days,LocalDateTime expiry,LocalDateTime now){
  if(expiry!=null){if(!expiry.isAfter(s.getExpiryTime())||!expiry.isAfter(now))throw new IllegalArgumentException("新的到期时间必须晚于原到期时间和当前时间");return expiry;}
  if(days==null||days<1)throw new IllegalArgumentException("请填写延长天数或新的到期时间");
  return (s.getExpiryTime().isAfter(now)?s.getExpiryTime():now).plusDays(days);
 }
 public static String token(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 public static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
