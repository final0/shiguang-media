-- MySQL 8：先在业务数据库执行整份脚本，再部署新 JAR。可重复执行。
DELIMITER $$
DROP PROCEDURE IF EXISTS growth_email_upgrade_20260929$$
CREATE PROCEDURE growth_email_upgrade_20260929()
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='email') THEN
  ALTER TABLE app_user ADD COLUMN email VARCHAR(100) NULL;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='email_verified_at') THEN
  ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMP NULL DEFAULT NULL;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='email_enabled') THEN
  ALTER TABLE app_user ADD COLUMN email_enabled BOOLEAN NOT NULL DEFAULT TRUE;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND COLUMN_NAME='email_version') THEN
  ALTER TABLE app_user ADD COLUMN email_version BIGINT NOT NULL DEFAULT 0;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_user' AND INDEX_NAME='uq_growth_user_email') THEN
  ALTER TABLE app_user ADD UNIQUE INDEX uq_growth_user_email(email);
 END IF;
END$$
CALL growth_email_upgrade_20260929()$$
DROP PROCEDURE growth_email_upgrade_20260929$$
DELIMITER ;

CREATE TABLE IF NOT EXISTS growth_reminder (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 family_id BIGINT NOT NULL, user_id BIGINT NOT NULL, child_id BIGINT NULL,
 title VARCHAR(80) NOT NULL, content VARCHAR(1000) NOT NULL DEFAULT '',
 cadence VARCHAR(12) NOT NULL, start_ms BIGINT NOT NULL, next_ms BIGINT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', revision BIGINT NOT NULL DEFAULT 1,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_reminder_due(state,next_ms), INDEX idx_reminder_owner(family_id,user_id),
 CONSTRAINT fk_reminder_family FOREIGN KEY(family_id) REFERENCES family(id),
 CONSTRAINT fk_reminder_user FOREIGN KEY(user_id) REFERENCES app_user(id),
 CONSTRAINT fk_reminder_child FOREIGN KEY(child_id) REFERENCES child(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reminder_delivery (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, reminder_id BIGINT NOT NULL,
 revision BIGINT NOT NULL, scheduled_ms BIGINT NOT NULL, email_version BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'QUEUED', attempts INT NOT NULL DEFAULT 0,
 next_attempt_ms BIGINT NOT NULL, lease_ms BIGINT NULL, claim_token VARCHAR(36) NULL,
 result_code VARCHAR(40) NULL, sent_ms BIGINT NULL,
 UNIQUE KEY uq_reminder_occurrence(reminder_id,revision,scheduled_ms),
 INDEX idx_delivery_due(status,next_attempt_ms),
 CONSTRAINT fk_delivery_reminder FOREIGN KEY(reminder_id) REFERENCES growth_reminder(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
