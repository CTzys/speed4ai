CREATE TABLE custom_email_code (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  email VARCHAR(128) NOT NULL,
  code_hash VARCHAR(100) NOT NULL,
  expires_at DATETIME NOT NULL,
  last_sent_at DATETIME NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  consumed BIT(1) NOT NULL DEFAULT b'0',
  UNIQUE KEY uk_custom_email_code_tenant_email (tenant_id, email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE custom_session (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  member_user_id BIGINT NOT NULL,
  access_hash CHAR(64) NOT NULL,
  refresh_hash CHAR(64) NOT NULL,
  access_expires_at DATETIME NOT NULL,
  refresh_expires_at DATETIME NOT NULL,
  revoked BIT(1) NOT NULL DEFAULT b'0',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_custom_session_access (access_hash),
  UNIQUE KEY uk_custom_session_refresh (refresh_hash),
  KEY idx_custom_session_user (tenant_id, member_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
