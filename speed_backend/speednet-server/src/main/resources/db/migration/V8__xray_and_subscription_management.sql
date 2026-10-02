-- Consolidated migrations V8-V16. Requires migrations V1-V7.

-- ===== V8__xray_inbound_management.sql =====
-- Inbounds are stored by the remote 3x-ui panel; only menu permissions are local.
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6004,'入站管理','xray:inbound:query',2,4,6000,'inbound','ep:connection','xray/inbound/index','XrayInbound',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6020,'新增入站','xray:inbound:create',3,1,6004,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6021,'修改入站','xray:inbound:update',3,2,6004,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6022,'删除入站','xray:inbound:delete',3,3,6004,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');

-- ===== V9__xray_service_control.sql =====
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6016,'启动服务','xray:server:start',3,6,6001,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6017,'停止服务','xray:server:stop',3,7,6001,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');

-- ===== V10__xray_panel_credentials.sql =====
ALTER TABLE `xray_server`
  ADD COLUMN `panel_username` text DEFAULT NULL COMMENT '面板登录用户名（加密）',
  ADD COLUMN `panel_password` text DEFAULT NULL COMMENT '面板登录密码（加密）';

-- ===== V11__socks5_node_management.sql =====
-- External SOCKS5 resources. All rows are tenant scoped; credentials use EncryptTypeHandler.
CREATE TABLE `xray_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL, `host` varchar(255) NOT NULL, `port` int NOT NULL,
  `auth_type` tinyint NOT NULL DEFAULT 0, `username` varchar(255) NOT NULL DEFAULT '', `password` text NULL,
  `identity_key` char(64) NOT NULL COMMENT 'SHA256(normalized host, port, username)',
  `region` varchar(64) NULL, `tags` varchar(255) NULL, `remark` varchar(500) NULL,
  `shelf_status` tinyint NOT NULL DEFAULT 0 COMMENT '0 下架 1 上架',
  `health_status` tinyint NOT NULL DEFAULT 0 COMMENT '0 未检测 1 正常 2 异常',
  `latency_ms` int NULL, `last_check_time` datetime NULL, `last_error` varchar(1000) NULL,
  `config_version` int NOT NULL DEFAULT 1,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_node_identity` (`tenant_id`,`identity_key`),
  KEY `idx_node_shelf_health` (`tenant_id`,`shelf_status`,`health_status`)
) ENGINE=InnoDB COMMENT='外部 SOCKS5 节点';
CREATE TABLE `xray_node_server` (
  `id` bigint NOT NULL AUTO_INCREMENT, `node_id` bigint NOT NULL, `server_id` bigint NOT NULL,
  `outbound_tag` varchar(128) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 待同步 1 同步中 2 配置已核对 3 失败 4 远端缺失或不一致 5 已移除',
  `applied_version` int NOT NULL DEFAULT 0, `last_sync_time` datetime NULL, `last_error` varchar(1000) NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_node_server` (`tenant_id`,`node_id`,`server_id`)
) ENGINE=InnoDB COMMENT='节点服务器出站部署记录';
-- Reserved for the subscription module. This release does not fabricate assignments or expose a writer.
CREATE TABLE `xray_node_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT, `node_id` bigint NOT NULL, `server_id` bigint NOT NULL,
  `user_id` bigint NOT NULL, `subscription_id` bigint NOT NULL,
  `assigned_time` datetime NOT NULL, `expiry_time` datetime NOT NULL,
  `subscription_status` tinyint NOT NULL DEFAULT 0 COMMENT '0 有效 1 取消',
  `authorization_status` tinyint NOT NULL DEFAULT 0 COMMENT '0 待开通 1 已生效 2 待撤销 3 已撤销 4 撤销失败',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_node_assignment` (`tenant_id`,`node_id`,`server_id`,`subscription_id`),
  KEY `idx_node_assignment_expiry` (`tenant_id`,`node_id`,`expiry_time`)
) ENGINE=InnoDB COMMENT='订阅节点领用关系（订阅模块接入后维护）';
CREATE TABLE `xray_node_check_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `node_id` bigint NOT NULL, `config_version` int NOT NULL,
  `status` tinyint NOT NULL, `latency_ms` int NULL, `source` varchar(64) NOT NULL, `message` varchar(1000) NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_node_check` (`tenant_id`,`node_id`,`id`)
) ENGINE=InnoDB COMMENT='节点检测记录';
CREATE TABLE `xray_node_task` (
  `id` bigint NOT NULL AUTO_INCREMENT, `batch_id` varchar(36) NOT NULL,
  `node_id` bigint NOT NULL, `server_id` bigint NULL, `action` varchar(32) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 等待 1 执行中 2 成功 3 失败',
  `message` varchar(1000) NULL, `end_time` datetime NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_node_task_batch` (`tenant_id`,`batch_id`), KEY `idx_node_task_node` (`tenant_id`,`node_id`,`id`)
) ENGINE=InnoDB COMMENT='节点操作任务及结果';

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6005,'节点管理','xray:node:query',2,5,6000,'node','ep:connection','xray/node/index','XrayNode',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6040,'新增节点','xray:node:create',3,1,6005,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6041,'编辑节点','xray:node:update',3,2,6005,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6042,'导入节点','xray:node:import',3,3,6005,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6043,'节点上下架','xray:node:shelf',3,4,6005,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6044,'检测节点','xray:node:check',3,5,6005,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6045,'部署节点','xray:node:deploy',3,6,6005,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');

-- ===== V12__node_region_management.sql =====
-- Controlled regions replace free-form node input. Preserve existing nonblank values per tenant.
CREATE TABLE `xray_region` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 启用 1 停用',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_region_name` (`tenant_id`,`name`)
) ENGINE=InnoDB COMMENT='节点地区';

INSERT INTO `xray_region` (`name`,`tenant_id`)
SELECT DISTINCT TRIM(`region`), `tenant_id` FROM `xray_node`
WHERE `deleted`=b'0' AND `region` IS NOT NULL AND TRIM(`region`)<>'';
ALTER TABLE `xray_node` ADD COLUMN `region_id` bigint NULL COMMENT '地区编号',
  ADD KEY `idx_node_region` (`tenant_id`,`region_id`);
UPDATE `xray_node` n JOIN `xray_region` r ON r.tenant_id=n.tenant_id AND r.name=TRIM(n.region)
SET n.region_id=r.id WHERE n.deleted=b'0';
-- The legacy region column remains for migration compatibility; application writes use region_id only.
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6006,'地区管理','xray:region:query',2,6,6000,'region','ep:location','xray/region/index','XrayRegion',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6050,'新增地区','xray:region:create',3,1,6006,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6051,'修改地区','xray:region:update',3,2,6006,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');

-- ===== V13__node_city_management.sql =====
-- Region -> city hierarchy. Existing nodes retain their region and await explicit city selection.
CREATE TABLE `xray_city` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `region_id` bigint NOT NULL,
  `name` varchar(64) NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 启用 1 停用',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_city_name` (`tenant_id`,`region_id`,`name`)
) ENGINE=InnoDB COMMENT='节点城市';
ALTER TABLE `xray_node` ADD COLUMN `city_id` bigint NULL COMMENT '城市编号',
  ADD KEY `idx_node_city` (`tenant_id`,`region_id`,`city_id`);
UPDATE `system_menu` SET `name`='地区与城市管理' WHERE `id`=6006;
UPDATE `system_menu` SET `name`='新增地区/城市' WHERE `id`=6050;
UPDATE `system_menu` SET `name`='修改地区/城市' WHERE `id`=6051;

-- ===== V14__subscription_management.sql =====
-- Independent business subscription module. Credentials and subscription tokens are encrypted.
CREATE TABLE `subscription` (
 `id` bigint NOT NULL AUTO_INCREMENT, `number` varchar(64) NOT NULL, `name` varchar(128) NOT NULL,
 `user_id` bigint NOT NULL, `source` varchar(32) NOT NULL, `order_no` varchar(64) NULL,
 `start_time` datetime NOT NULL, `expiry_time` datetime NOT NULL, `ended_time` datetime NULL,
 `paused` bit(1) NOT NULL DEFAULT b'0', `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 待生效 1 生效 2 暂停 3 流量耗尽 4 到期 5 结束',
 `sync_status` tinyint NOT NULL DEFAULT 0 COMMENT '0 待同步 1 同步中 2 已核对 3 失败', `last_error` varchar(1000) NOT NULL DEFAULT '',
 `unlimited` bit(1) NOT NULL DEFAULT b'0', `total_bytes` bigint NOT NULL DEFAULT 0,
 `used_upload` bigint NOT NULL DEFAULT 0, `used_download` bigint NOT NULL DEFAULT 0,
 `lifetime_upload` bigint NOT NULL DEFAULT 0, `lifetime_download` bigint NOT NULL DEFAULT 0,
 `traffic_mode` varchar(16) NOT NULL DEFAULT 'both', `reset_mode` varchar(16) NOT NULL DEFAULT 'none',
 `reset_interval_days` int NOT NULL DEFAULT 30, `next_reset_time` datetime NULL,
 `node_limit` int NOT NULL DEFAULT 1, `region_id` bigint NULL, `city_id` bigint NULL, `remark` varchar(500) NULL,
 `token` text NOT NULL, `token_hash` char(64) NOT NULL,
 `last_traffic_time` datetime NULL, `last_sync_time` datetime NULL,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), UNIQUE KEY `uk_subscription_number` (`tenant_id`,`number`),
 UNIQUE KEY `uk_subscription_token` (`tenant_id`,`token_hash`),
 KEY `idx_subscription_user` (`tenant_id`,`user_id`), KEY `idx_subscription_expiry` (`status`,`expiry_time`)
) ENGINE=InnoDB COMMENT='用户订阅授权';
CREATE TABLE `subscription_client` (
 `id` bigint NOT NULL AUTO_INCREMENT, `subscription_id` bigint NOT NULL,
 `node_id` bigint NOT NULL, `server_id` bigint NOT NULL, `inbound_id` bigint NOT NULL,
 `protocol` varchar(16) NOT NULL, `email` varchar(128) NOT NULL, `credential` text NOT NULL,
 `public_host` varchar(255) NOT NULL, `connection_name` varchar(255) NOT NULL, `connection_uri` text NULL,
 `released` bit(1) NOT NULL DEFAULT b'0', `sync_status` tinyint NOT NULL DEFAULT 0,
 `remote_created` bit(1) NOT NULL DEFAULT b'0', `node_version` int NOT NULL DEFAULT 0,
 `last_error` varchar(1000) NOT NULL DEFAULT '', `assigned_time` datetime NOT NULL, `released_time` datetime NULL,
 `sample_upload` bigint NOT NULL DEFAULT 0, `sample_download` bigint NOT NULL DEFAULT 0,
 `used_upload` bigint NOT NULL DEFAULT 0, `used_download` bigint NOT NULL DEFAULT 0,
 `last_traffic_time` datetime NULL,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), UNIQUE KEY `uk_subscription_client_email` (`tenant_id`,`email`),
 KEY `idx_subscription_client` (`tenant_id`,`subscription_id`)
) ENGINE=InnoDB COMMENT='订阅客户端和节点分配记录';
CREATE TABLE `subscription_order` (
 `id` bigint NOT NULL AUTO_INCREMENT, `subscription_id` bigint NOT NULL,
 `order_no` varchar(64) NOT NULL, `purpose` varchar(32) NOT NULL,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), KEY `idx_subscription_order` (`tenant_id`,`subscription_id`,`order_no`)
) ENGINE=InnoDB COMMENT='订阅来源、续费和流量订单关联';
CREATE TABLE `subscription_log` (
 `id` bigint NOT NULL AUTO_INCREMENT, `subscription_id` bigint NOT NULL,
 `action` varchar(32) NOT NULL, `message` varchar(1000) NOT NULL,
 `upload_bytes` bigint NOT NULL DEFAULT 0, `download_bytes` bigint NOT NULL DEFAULT 0, `success` tinyint NOT NULL DEFAULT 1,
 `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
 PRIMARY KEY (`id`), KEY `idx_subscription_log` (`tenant_id`,`subscription_id`,`id`)
) ENGINE=InnoDB COMMENT='订阅操作及流量周期记录';
ALTER TABLE `xray_node_assignment` ADD COLUMN `client_id` bigint NULL;
CREATE INDEX `idx_assignment_subscription` ON `xray_node_assignment` (`tenant_id`,`subscription_id`);

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6100,'订阅管理','',1,31,0,'/subscription','ep:tickets','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6101,'订阅列表','subscription:query',2,1,6100,'list','ep:list','subscription/list/index','SubscriptionList',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6110,'新建订阅','subscription:create',3,1,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6111,'延长订阅','subscription:extend',3,2,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6112,'增加流量','subscription:add-traffic',3,3,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6113,'重置流量','subscription:reset-traffic',3,4,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6114,'结束订阅','subscription:end',3,5,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6115,'暂停恢复','subscription:pause',3,6,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6116,'配置同步','subscription:sync',3,7,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6117,'查看订阅链接','subscription:credentials',3,8,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6118,'重置订阅链接','subscription:reset-link',3,9,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6119,'编辑备注','subscription:remark',3,10,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6121,'重置客户端认证','subscription:reset-client',3,12,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6120,'节点分配','subscription:assign',3,11,6101,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');

-- ===== V15__subscription_name_optional.sql =====
-- Subscription names are no longer used. Keep historical values for upgrade compatibility.
-- Inserts omit this legacy column and use the default instead of requiring a name.
ALTER TABLE `subscription` ALTER COLUMN `name` SET DEFAULT '';

-- ===== V16__subscription_quartz_job.sql =====
-- The managed job replaces SubscriptionService's @Scheduled trigger.
-- Do not alter an existing administrator-created configuration or reset its paused state.
INSERT INTO `infra_job` (`name`,`status`,`handler_name`,`handler_param`,`cron_expression`,
    `retry_count`,`retry_interval`,`monitor_timeout`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '订阅扫描',1,'subscriptionScanJob','','0 * * * * ?',0,0,0,'1',NOW(),'1',NOW(),b'0'
WHERE NOT EXISTS (SELECT 1 FROM `infra_job` WHERE `handler_name`='subscriptionScanJob' AND `deleted`=b'0');
