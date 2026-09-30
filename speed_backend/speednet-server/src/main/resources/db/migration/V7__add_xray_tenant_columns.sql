-- V6 has already been applied in some installations. Add the columns that
-- the project's tenant interceptor expects without altering V6's checksum.
ALTER TABLE `xray_server`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  ADD KEY `idx_xray_server_tenant` (`tenant_id`);

ALTER TABLE `xray_install_task`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  ADD KEY `idx_xray_install_tenant` (`tenant_id`);

ALTER TABLE `xray_install_log`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  ADD KEY `idx_xray_log_tenant` (`tenant_id`);
