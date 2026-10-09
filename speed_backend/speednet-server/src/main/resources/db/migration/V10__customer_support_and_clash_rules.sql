-- Consolidated customer support and Clash routing rules, in original migration order.

-- BEGIN V10__support_tickets.sql
-- Dedicated private screenshot storage: no publicly accessible infra-file URL.
CREATE TABLE support_ticket (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, member_id bigint NOT NULL,
 number varchar(40) NOT NULL, title varchar(120) NOT NULL, category varchar(24) NOT NULL,
 priority varchar(16) NOT NULL DEFAULT 'normal', status varchar(24) NOT NULL DEFAULT 'pending',
 assignee_id bigint NULL, order_id bigint NULL, subscription_id bigint NULL, snapshot_json text NOT NULL,
 request_key varchar(64) NOT NULL, version int NOT NULL DEFAULT 0, message_seq bigint NOT NULL DEFAULT 0,
 last_activity_at datetime(3) NOT NULL, closed_at datetime(3) NULL, close_reason varchar(500) NOT NULL DEFAULT '',
 create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), update_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_number(number), UNIQUE KEY uk_request(tenant_id,member_id,request_key),
 KEY idx_member(tenant_id,member_id,update_time,id), KEY idx_status(tenant_id,status,update_time,id),
 KEY idx_assignee(tenant_id,assignee_id,status,update_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE support_ticket_message (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, ticket_id bigint NOT NULL,
 seq bigint NOT NULL, sender_type varchar(16) NOT NULL, sender_id bigint NOT NULL,
 visibility varchar(16) NOT NULL DEFAULT 'public', body text NOT NULL, request_key varchar(64) NOT NULL,
 create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_seq(tenant_id,ticket_id,seq),
 UNIQUE KEY uk_message_request(tenant_id,ticket_id,sender_type,sender_id,request_key),
 KEY idx_message(tenant_id,ticket_id,visibility,seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE support_ticket_attachment (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, ticket_id bigint NOT NULL,
 message_id bigint NULL, uploader_type varchar(16) NOT NULL, uploader_id bigint NOT NULL,
 name varchar(200) NOT NULL, content_type varchar(32) NOT NULL, size int NOT NULL, content mediumblob NOT NULL,
 create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_attachment_message(tenant_id,ticket_id,message_id), KEY idx_staging(tenant_id,uploader_type,uploader_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE support_ticket_event (
 id bigint PRIMARY KEY AUTO_INCREMENT, tenant_id bigint NOT NULL, ticket_id bigint NOT NULL,
 actor_type varchar(16) NOT NULL, actor_id bigint NOT NULL, action varchar(24) NOT NULL,
 detail varchar(1000) NOT NULL, create_time datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_event(tenant_id,ticket_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE support_ticket_read (
 tenant_id bigint NOT NULL, ticket_id bigint NOT NULL, reader_type varchar(16) NOT NULL,
 reader_id bigint NOT NULL, last_seq bigint NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,ticket_id,reader_type,reader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
VALUES ('工单管理','support:ticket:query',2,35,0,'/support','ep:chat-dot-round','support/ticket/index','SupportTickets',0,1,1,1,'1','1',0);
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '回复工单','support:ticket:reply',3,1,id,'','','',0,1,1,1,'1','1',0 FROM system_menu WHERE component_name='SupportTickets' AND deleted=0;
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '分配工单','support:ticket:assign',3,2,id,'','','',0,1,1,1,'1','1',0 FROM system_menu WHERE component_name='SupportTickets' AND deleted=0;
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '关闭工单','support:ticket:close',3,3,id,'','','',0,1,1,1,'1','1',0 FROM system_menu WHERE component_name='SupportTickets' AND deleted=0;
INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT '工单主管','support:ticket:manage',3,4,id,'','','',0,1,1,1,'1','1',0 FROM system_menu WHERE component_name='SupportTickets' AND deleted=0;
-- END V10__support_tickets.sql

-- BEGIN V11__subscription_clash_rules.sql
-- user_id=0 is the tenant-wide common rule; positive IDs are complete customer overrides.
CREATE TABLE subscription_clash_rule (
 tenant_id bigint NOT NULL, user_id bigint NOT NULL DEFAULT 0,
 rules_json mediumtext NOT NULL,
 create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (tenant_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Persist the existing defaults for all current tenants. New tenants use the same defaults until saved.
INSERT INTO subscription_clash_rule(tenant_id,user_id,rules_json)
SELECT id,0,'["IP-CIDR,127.0.0.0/8,DIRECT,no-resolve","IP-CIDR,10.0.0.0/8,DIRECT,no-resolve","IP-CIDR,172.16.0.0/12,DIRECT,no-resolve","IP-CIDR,192.168.0.0/16,DIRECT,no-resolve","MATCH,SpeedNet"]'
FROM system_tenant WHERE deleted=0;
INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted)
SELECT 'Clash 规则','subscription:clash-rule:manage',2,6,id,'clash-rule','ep:setting','subscription/clash-rule/index','SubscriptionClashRule',0,1,1,1,'1','1',0
FROM system_menu WHERE parent_id=0 AND path='/subscription' AND deleted=0;
-- END V11__subscription_clash_rules.sql
