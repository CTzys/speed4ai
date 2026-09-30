CREATE TABLE `xray_server` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(128) NOT NULL COMMENT '服务器名称',
  `host` varchar(255) NOT NULL COMMENT '主机或 IP',
  `ssh_port` int NOT NULL DEFAULT 22 COMMENT 'SSH 端口',
  `ssh_username` varchar(64) NOT NULL COMMENT 'SSH 用户名',
  `ssh_auth_type` tinyint NOT NULL DEFAULT 1 COMMENT '认证方式：1 密码，2 私钥',
  `ssh_password` text NULL COMMENT '加密的 SSH 密码',
  `ssh_private_key` longtext NULL COMMENT '加密的 SSH 私钥',
  `ssh_key_passphrase` text NULL COMMENT '加密的私钥口令',
  `panel_scheme` varchar(8) NULL DEFAULT 'https' COMMENT '面板协议',
  `panel_port` int NULL COMMENT '面板端口',
  `panel_path` varchar(255) NULL COMMENT '面板路径',
  `panel_token` text NULL COMMENT '加密的面板 Token',
  `panel_version` varchar(64) NULL COMMENT '面板版本',
  `xray_version` varchar(64) NULL COMMENT 'Xray 版本',
  `install_status` tinyint NOT NULL DEFAULT 0 COMMENT '安装状态：0 未安装，1 安装中，2 成功，3 失败',
  `health_status` tinyint NOT NULL DEFAULT 0 COMMENT '健康状态：0 未知，1 正常，2 服务停止，3 不可达',
  `last_check_time` datetime NULL COMMENT '最后检测时间',
  `last_error` varchar(1000) NULL COMMENT '最后错误',
  `remark` varchar(500) NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), KEY `idx_xray_server_health` (`health_status`)
) ENGINE=InnoDB COMMENT='Xray 服务器';

CREATE TABLE `xray_install_task` (
  `id` bigint NOT NULL AUTO_INCREMENT, `server_id` bigint NOT NULL, `version` varchar(32) NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 等待，1 执行中，2 成功，3 失败',
  `current_step` varchar(128) NULL, `start_time` datetime NULL, `end_time` datetime NULL,
  `error_message` varchar(2000) NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), KEY `idx_xray_install_server` (`server_id`)
) ENGINE=InnoDB COMMENT='Xray 安装任务';

CREATE TABLE `xray_install_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `task_id` bigint NOT NULL, `level` tinyint NOT NULL DEFAULT 0,
  `step` varchar(128) NOT NULL, `content` longtext NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), KEY `idx_xray_log_task` (`task_id`)
) ENGINE=InnoDB COMMENT='Xray 安装日志';

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6000,'Xray 管理','',1,30,0,'/xray','ep:connection',NULL,NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6001,'服务器管理','xray:server:query',2,1,6000,'server','ep:monitor','xray/server/index','XrayServer',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6002,'安装任务','xray:install:query',2,2,6000,'install','ep:setting','xray/install/index','XrayInstall',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6003,'运行状态','xray:server:query',2,3,6000,'status','ep:odometer','xray/status/index','XrayStatus',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');

INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(6010,'新增服务器','xray:server:create',3,1,6001,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6011,'修改服务器','xray:server:update',3,2,6001,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6012,'删除服务器','xray:server:delete',3,3,6001,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6013,'测试 SSH','xray:server:test',3,4,6001,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6014,'状态检测','xray:server:check',3,5,6001,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(6015,'执行安装','xray:install:create',3,1,6002,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');
