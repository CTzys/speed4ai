package com.speednet.module.xray.dal.mysql.node;
import com.speednet.framework.tenant.core.context.TenantContextHolder;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface XrayNodeAssignmentMapper {
    default List<Map<String,Object>> users(Long nodeId){return userRows(nodeId,TenantContextHolder.getRequiredTenantId());}
    @Select("""
        SELECT a.id, a.user_id AS userId, m.nickname, m.email,
               a.subscription_id AS subscriptionId, s.number AS subscriptionNumber,
               a.server_id AS serverId, a.client_id AS clientId,
               a.assigned_time AS assignedTime, s.expiry_time AS expiryTime,
               a.subscription_status AS subscriptionStatus, a.authorization_status AS authorizationStatus,
               c.released,
               CASE WHEN s.ended_time IS NOT NULL THEN 5 WHEN s.expiry_time<=NOW() THEN 4
                    WHEN s.paused=1 OR m.status<>0 OR m.id IS NULL THEN 2
                    WHEN s.start_time>NOW() THEN 0
                    WHEN s.unlimited=0 AND (CASE WHEN s.traffic_mode='download' THEN s.used_download ELSE s.used_upload+s.used_download END)>=s.total_bytes THEN 3
                    ELSE 1 END AS businessStatus
        FROM xray_node_assignment a
        JOIN subscription s ON s.id=a.subscription_id AND s.tenant_id=a.tenant_id AND s.deleted=0
        LEFT JOIN member_user m ON m.id=a.user_id AND m.tenant_id=a.tenant_id AND m.deleted=0
        LEFT JOIN subscription_client c ON c.id=a.client_id AND c.tenant_id=a.tenant_id AND c.deleted=0
        WHERE a.node_id=#{nodeId} AND a.tenant_id=#{tenantId} AND a.deleted=0 ORDER BY a.id DESC LIMIT 500
        """)
    List<Map<String,Object>> userRows(@Param("nodeId") Long nodeId,@Param("tenantId") Long tenantId);
    default Map<String,Object> counts(Long nodeId){return countRows(nodeId,TenantContextHolder.getRequiredTenantId());}
    @Select("""
        SELECT COUNT(DISTINCT CASE WHEN a.subscription_status=0 AND a.authorization_status=1
                       AND s.ended_time IS NULL AND s.paused=0 AND s.start_time<=NOW() AND s.expiry_time>NOW()
                       AND m.status=0 AND (s.unlimited=1 OR (CASE WHEN s.traffic_mode='download' THEN s.used_download ELSE s.used_upload+s.used_download END)<s.total_bytes)
                       THEN a.user_id END) AS activeUserCount,
               COUNT(DISTINCT CASE WHEN s.expiry_time<=NOW() THEN a.user_id END) AS expiredUserCount
        FROM xray_node_assignment a
        JOIN subscription s ON s.id=a.subscription_id AND s.tenant_id=a.tenant_id AND s.deleted=0
        LEFT JOIN member_user m ON m.id=a.user_id AND m.tenant_id=a.tenant_id AND m.deleted=0
        WHERE a.node_id=#{nodeId} AND a.tenant_id=#{tenantId} AND a.deleted=0
        """)
    Map<String,Object> countRows(@Param("nodeId") Long nodeId,@Param("tenantId") Long tenantId);
}
