-- 旧 H2 本地版结构参考；当前不再自动执行，不可在 MySQL 中执行。
-- MySQL 8.0/8.4 请使用同目录 schema-mysql.sql；连接配置见 application-mysql.yml。
-- 表名避免 user/comment 等保留字；所有关联操作仍须显式校验家庭。
CREATE TABLE IF NOT EXISTS app_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, phone VARCHAR(64) NOT NULL UNIQUE,
 nickname VARCHAR(40) NOT NULL, password_hash VARCHAR(128) NOT NULL,
 avatar VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 email VARCHAR(100) UNIQUE, email_verified_at TIMESTAMP NULL,
 email_enabled BOOLEAN NOT NULL DEFAULT TRUE, email_version BIGINT NOT NULL DEFAULT 0,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_login_time TIMESTAMP
);
CREATE TABLE IF NOT EXISTS family (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(60) NOT NULL, creator_id BIGINT NOT NULL REFERENCES app_user(id),
 storage_quota BIGINT NOT NULL DEFAULT 5368709120, invite_code VARCHAR(64) UNIQUE,
 invite_expire_time TIMESTAMP, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS family_member (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id), role VARCHAR(16) NOT NULL,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS child (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 name VARCHAR(40) NOT NULL, birthday DATE NOT NULL, gender VARCHAR(16) NOT NULL DEFAULT 'secret',
 color VARCHAR(16) NOT NULL DEFAULT 'sage', is_active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS grow_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 child_id BIGINT NOT NULL REFERENCES child(id), user_id BIGINT NOT NULL REFERENCES app_user(id),
 title VARCHAR(80) NOT NULL, content VARCHAR(4000) NOT NULL, record_type VARCHAR(16) NOT NULL,
 record_time DATE NOT NULL, milestone VARCHAR(60), illustration VARCHAR(24),
 is_deleted BOOLEAN NOT NULL DEFAULT FALSE, delete_time TIMESTAMP, deleted_by BIGINT,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_record_family_date ON grow_record(family_id,child_id,is_deleted,record_time);
CREATE TABLE IF NOT EXISTS media_file (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 record_id BIGINT NOT NULL REFERENCES grow_record(id), oss_key VARCHAR(256) NOT NULL,
 mime_type VARCHAR(40) NOT NULL, size_bytes BIGINT NOT NULL,
 is_deleted BOOLEAN NOT NULL DEFAULT FALSE, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_media_record ON media_file(record_id);
CREATE TABLE IF NOT EXISTS tag (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 name VARCHAR(32) NOT NULL, UNIQUE(family_id,name)
);
CREATE TABLE IF NOT EXISTS record_tag (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, record_id BIGINT NOT NULL REFERENCES grow_record(id),
 tag_id BIGINT NOT NULL REFERENCES tag(id), UNIQUE(record_id,tag_id)
);
CREATE TABLE IF NOT EXISTS record_comment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 record_id BIGINT NOT NULL REFERENCES grow_record(id), user_id BIGINT NOT NULL REFERENCES app_user(id),
 content VARCHAR(500) NOT NULL, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS grow_data (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 child_id BIGINT NOT NULL REFERENCES child(id), height DECIMAL(5,1) NOT NULL,
 weight DECIMAL(5,1) NOT NULL, head DECIMAL(5,1), remark VARCHAR(255),
 measure_time DATE NOT NULL, age_month INT NOT NULL, record_by BIGINT NOT NULL REFERENCES app_user(id)
);
CREATE INDEX IF NOT EXISTS idx_measure_child ON grow_data(family_id,child_id,measure_time);
CREATE TABLE IF NOT EXISTS vaccine (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 child_id BIGINT NOT NULL REFERENCES child(id), vaccine_name VARCHAR(64) NOT NULL,
 dose_no INT NOT NULL, vaccine_date DATE NOT NULL, hospital VARCHAR(128), remark VARCHAR(255)
);
CREATE TABLE IF NOT EXISTS share_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 record_id BIGINT NOT NULL REFERENCES grow_record(id), creator_id BIGINT NOT NULL REFERENCES app_user(id),
 share_token VARCHAR(64) NOT NULL UNIQUE, expire_time TIMESTAMP NOT NULL,
 access_count INT NOT NULL DEFAULT 0, max_access INT NOT NULL DEFAULT 100,
 status INT NOT NULL DEFAULT 1, watermark BOOLEAN NOT NULL DEFAULT TRUE,
 is_downloadable BOOLEAN NOT NULL DEFAULT FALSE, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS notice (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 user_id BIGINT NOT NULL REFERENCES app_user(id), title VARCHAR(100) NOT NULL,
 content VARCHAR(500) NOT NULL, link_id BIGINT, is_read BOOLEAN NOT NULL DEFAULT FALSE,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS operate_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL REFERENCES family(id),
 user_id BIGINT NOT NULL REFERENCES app_user(id), action_type VARCHAR(32) NOT NULL,
 detail VARCHAR(500) NOT NULL, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notice_user ON notice(family_id,user_id,is_read);
CREATE INDEX IF NOT EXISTS idx_log_family ON operate_log(family_id,create_time);
-- 本地存储可靠清理队列：业务提交后清理文件，失败保留任务供重试。
CREATE TABLE IF NOT EXISTS media_cleanup (
 oss_key VARCHAR(256) PRIMARY KEY, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS account_deletion (
 user_id BIGINT PRIMARY KEY, family_id BIGINT NOT NULL, scope VARCHAR(16) NOT NULL,
 requested_ms BIGINT NOT NULL, deadline_ms BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_account_deletion_due ON account_deletion(deadline_ms);
CREATE INDEX IF NOT EXISTS idx_account_deletion_family ON account_deletion(family_id,scope);

-- 邮箱待办提醒及持久化发送队列。
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
);

CREATE TABLE IF NOT EXISTS reminder_delivery (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, reminder_id BIGINT NOT NULL,
 revision BIGINT NOT NULL, scheduled_ms BIGINT NOT NULL, email_version BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'QUEUED', attempts INT NOT NULL DEFAULT 0,
 next_attempt_ms BIGINT NOT NULL, lease_ms BIGINT NULL, claim_token VARCHAR(36) NULL,
 result_code VARCHAR(40) NULL, sent_ms BIGINT NULL,
 UNIQUE KEY uq_reminder_occurrence(reminder_id,revision,scheduled_ms),
 INDEX idx_delivery_due(status,next_attempt_ms),
 CONSTRAINT fk_delivery_reminder FOREIGN KEY(reminder_id) REFERENCES growth_reminder(id)
);
