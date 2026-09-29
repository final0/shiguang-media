-- 拾光 MySQL 8.0 / 8.4 初始化及旧 H2 脚本误执行后的补齐脚本。
-- 先在客户端选中项目自己的数据库，再以「执行整个脚本」方式执行。
-- 适用：空库，或同一项目 schema.sql 已创建的表；不用于迁移其他系统的同名表。
-- 不删表、不清空数据、不修改已有字段类型。遇错停止，排查后可重新执行。
-- 需要 CREATE/ALTER/INDEX/REFERENCES 及 CREATE ROUTINE/ALTER ROUTINE/EXECUTE 权限。
-- DDL 会隐式提交，不能通过外层事务整体回滚；此脚本尚未连接 MySQL 实测。
-- 不关闭 FOREIGN_KEY_CHECKS：已有孤儿数据应报错，由人工确认后处理。

CREATE TABLE IF NOT EXISTS app_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, phone VARCHAR(64) NOT NULL UNIQUE,
 nickname VARCHAR(40) NOT NULL, password_hash VARCHAR(128) NOT NULL,
 avatar VARCHAR(255), is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
 email VARCHAR(100) NULL, email_verified_at TIMESTAMP NULL DEFAULT NULL,
 email_enabled BOOLEAN NOT NULL DEFAULT TRUE, email_version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uq_growth_user_email(email),
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, last_login_time TIMESTAMP NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS family (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(60) NOT NULL, creator_id BIGINT NOT NULL,
 storage_quota BIGINT NOT NULL DEFAULT 5368709120, invite_code VARCHAR(64) UNIQUE,
 invite_expire_time TIMESTAMP NULL DEFAULT NULL, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS family_member (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL UNIQUE, role VARCHAR(16) NOT NULL,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS child (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 name VARCHAR(40) NOT NULL, birthday DATE NOT NULL, gender VARCHAR(16) NOT NULL DEFAULT 'secret',
 color VARCHAR(16) NOT NULL DEFAULT 'sage', is_active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS grow_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 child_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 title VARCHAR(80) NOT NULL, content VARCHAR(4000) NOT NULL, record_type VARCHAR(16) NOT NULL,
 record_time DATE NOT NULL, milestone VARCHAR(60), illustration VARCHAR(24),
 is_deleted BOOLEAN NOT NULL DEFAULT FALSE, delete_time TIMESTAMP NULL DEFAULT NULL, deleted_by BIGINT,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS media_file (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 record_id BIGINT NOT NULL, oss_key VARCHAR(256) NOT NULL,
 mime_type VARCHAR(40) NOT NULL, size_bytes BIGINT NOT NULL,
 is_deleted BOOLEAN NOT NULL DEFAULT FALSE, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS tag (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 name VARCHAR(32) NOT NULL, UNIQUE(family_id,name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS record_tag (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, record_id BIGINT NOT NULL,
 tag_id BIGINT NOT NULL, UNIQUE(record_id,tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS record_comment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 record_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 content VARCHAR(500) NOT NULL, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS grow_data (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 child_id BIGINT NOT NULL, height DECIMAL(5,1) NOT NULL,
 weight DECIMAL(5,1) NOT NULL, head DECIMAL(5,1), remark VARCHAR(255),
 measure_time DATE NOT NULL, age_month INT NOT NULL, record_by BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS vaccine (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 child_id BIGINT NOT NULL, vaccine_name VARCHAR(64) NOT NULL,
 dose_no INT NOT NULL, vaccine_date DATE NOT NULL, hospital VARCHAR(128), remark VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS share_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 record_id BIGINT NOT NULL, creator_id BIGINT NOT NULL,
 share_token VARCHAR(64) NOT NULL UNIQUE, expire_time TIMESTAMP NOT NULL,
 access_count INT NOT NULL DEFAULT 0, max_access INT NOT NULL DEFAULT 100,
 status INT NOT NULL DEFAULT 1, watermark BOOLEAN NOT NULL DEFAULT TRUE,
 is_downloadable BOOLEAN NOT NULL DEFAULT FALSE, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS notice (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL,
 content VARCHAR(500) NOT NULL, link_id BIGINT, is_read BOOLEAN NOT NULL DEFAULT FALSE,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS operate_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, family_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL, action_type VARCHAR(32) NOT NULL,
 detail VARCHAR(500) NOT NULL, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- create_time 用作可清理时间：永久删除立即处理，未完成上传意图延后处理。
CREATE TABLE IF NOT EXISTS media_cleanup (
 oss_key VARCHAR(256) PRIMARY KEY, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 同名且相同定义则跳过；同名但定义不同则停止，不偷偷替换既有索引。
DROP PROCEDURE IF EXISTS growth_ensure_index_20260928;
DROP PROCEDURE IF EXISTS growth_ensure_fk_20260928;
DELIMITER $$
CREATE PROCEDURE growth_ensure_index_20260928(
 IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255)
)
BEGIN
 DECLARE v_columns TEXT;
 DECLARE v_non_unique INT;
 DECLARE v_bad_parts INT;
 DECLARE v_message VARCHAR(128);
 SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','),
        MIN(NON_UNIQUE),
        COALESCE(SUM(CASE WHEN SUB_PART IS NOT NULL OR INDEX_TYPE <> 'BTREE'
                           OR COLLATION <> 'A' OR IS_VISIBLE <> 'YES' THEN 1 ELSE 0 END),0)
 INTO v_columns,v_non_unique,v_bad_parts
 FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND INDEX_NAME=p_index;
 IF v_columns IS NULL THEN
   SET @growth_schema_ddl=CONCAT('ALTER TABLE `',p_table,'` ADD INDEX `',p_index,'` (`',REPLACE(p_columns,',','`,`'),'`)');
   PREPARE growth_schema_stmt FROM @growth_schema_ddl;
   EXECUTE growth_schema_stmt;
   DEALLOCATE PREPARE growth_schema_stmt;
 ELSEIF v_columns <> p_columns OR v_non_unique <> 1 OR v_bad_parts <> 0 THEN
   SET v_message=CONCAT('Index definition mismatch: ',p_table,'.',p_index);
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_message;
 END IF;
END$$

-- MySQL 8 不执行列内 REFERENCES；用表级 FOREIGN KEY 补齐新建或此前误建的表。
CREATE PROCEDURE growth_ensure_fk_20260928(
 IN p_table VARCHAR(64), IN p_constraint VARCHAR(64), IN p_column VARCHAR(64), IN p_parent VARCHAR(64)
)
BEGIN
 DECLARE v_equivalent INT DEFAULT 0;
 DECLARE v_named INT DEFAULT 0;
 DECLARE v_named_valid INT DEFAULT 0;
 DECLARE v_message VARCHAR(128);
 SELECT COUNT(*) INTO v_named FROM information_schema.TABLE_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME=p_constraint;
 SELECT COUNT(*) INTO v_equivalent FROM (
   SELECT CONSTRAINT_NAME
   FROM information_schema.KEY_COLUMN_USAGE
   WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND REFERENCED_TABLE_NAME IS NOT NULL
   GROUP BY CONSTRAINT_NAME
   HAVING COUNT(*)=1 AND MAX(COLUMN_NAME)=p_column
      AND MAX(REFERENCED_TABLE_SCHEMA)=DATABASE()
      AND MAX(REFERENCED_TABLE_NAME)=p_parent AND MAX(REFERENCED_COLUMN_NAME)='id'
 ) AS matching_fk;
 SELECT COUNT(*) INTO v_named_valid FROM (
   SELECT CONSTRAINT_NAME
   FROM information_schema.KEY_COLUMN_USAGE
   WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=p_table AND CONSTRAINT_NAME=p_constraint
   GROUP BY CONSTRAINT_NAME
   HAVING COUNT(*)=1 AND MAX(COLUMN_NAME)=p_column
      AND MAX(REFERENCED_TABLE_SCHEMA)=DATABASE()
      AND MAX(REFERENCED_TABLE_NAME)=p_parent AND MAX(REFERENCED_COLUMN_NAME)='id'
 ) AS matching_named_fk;
 IF v_named > 0 AND v_named_valid=0 THEN
   SET v_message=CONCAT('Foreign key definition mismatch: ',p_constraint);
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=v_message;
 END IF;
 IF v_equivalent=0 THEN
   SET @growth_schema_ddl=CONCAT('ALTER TABLE `',p_table,'` ADD CONSTRAINT `',p_constraint,
     '` FOREIGN KEY (`',p_column,'`) REFERENCES `',p_parent,'` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT');
   PREPARE growth_schema_stmt FROM @growth_schema_ddl;
   EXECUTE growth_schema_stmt;
   DEALLOCATE PREPARE growth_schema_stmt;
 END IF;
END$$
DELIMITER ;

CALL growth_ensure_index_20260928('grow_record','idx_record_family_date','family_id,child_id,is_deleted,record_time');
CALL growth_ensure_index_20260928('media_file','idx_media_record','record_id');
CALL growth_ensure_index_20260928('grow_data','idx_measure_child','family_id,child_id,measure_time');
CALL growth_ensure_index_20260928('notice','idx_notice_user','family_id,user_id,is_read');
CALL growth_ensure_index_20260928('operate_log','idx_log_family','family_id,create_time');

CALL growth_ensure_fk_20260928('family','fk_growth_family_creator','creator_id','app_user');
CALL growth_ensure_fk_20260928('family_member','fk_growth_member_family','family_id','family');
CALL growth_ensure_fk_20260928('family_member','fk_growth_member_user','user_id','app_user');
CALL growth_ensure_fk_20260928('child','fk_growth_child_family','family_id','family');
CALL growth_ensure_fk_20260928('grow_record','fk_growth_record_family','family_id','family');
CALL growth_ensure_fk_20260928('grow_record','fk_growth_record_child','child_id','child');
CALL growth_ensure_fk_20260928('grow_record','fk_growth_record_user','user_id','app_user');
CALL growth_ensure_fk_20260928('media_file','fk_growth_media_family','family_id','family');
CALL growth_ensure_fk_20260928('media_file','fk_growth_media_record','record_id','grow_record');
CALL growth_ensure_fk_20260928('tag','fk_growth_tag_family','family_id','family');
CALL growth_ensure_fk_20260928('record_tag','fk_growth_rt_record','record_id','grow_record');
CALL growth_ensure_fk_20260928('record_tag','fk_growth_rt_tag','tag_id','tag');
CALL growth_ensure_fk_20260928('record_comment','fk_growth_comment_family','family_id','family');
CALL growth_ensure_fk_20260928('record_comment','fk_growth_comment_record','record_id','grow_record');
CALL growth_ensure_fk_20260928('record_comment','fk_growth_comment_user','user_id','app_user');
CALL growth_ensure_fk_20260928('grow_data','fk_growth_data_family','family_id','family');
CALL growth_ensure_fk_20260928('grow_data','fk_growth_data_child','child_id','child');
CALL growth_ensure_fk_20260928('grow_data','fk_growth_data_user','record_by','app_user');
CALL growth_ensure_fk_20260928('vaccine','fk_growth_vaccine_family','family_id','family');
CALL growth_ensure_fk_20260928('vaccine','fk_growth_vaccine_child','child_id','child');
CALL growth_ensure_fk_20260928('share_record','fk_growth_share_family','family_id','family');
CALL growth_ensure_fk_20260928('share_record','fk_growth_share_record','record_id','grow_record');
CALL growth_ensure_fk_20260928('share_record','fk_growth_share_user','creator_id','app_user');
CALL growth_ensure_fk_20260928('notice','fk_growth_notice_family','family_id','family');
CALL growth_ensure_fk_20260928('notice','fk_growth_notice_user','user_id','app_user');
CALL growth_ensure_fk_20260928('operate_log','fk_growth_log_family','family_id','family');
CALL growth_ensure_fk_20260928('operate_log','fk_growth_log_user','user_id','app_user');

DROP PROCEDURE growth_ensure_index_20260928;
DROP PROCEDURE growth_ensure_fk_20260928;
SET @growth_schema_ddl=NULL;

-- 在当前项目 growth 数据库中整份执行。只新增表，不修改成长记录和媒体。
CREATE TABLE IF NOT EXISTS account_deletion (
 user_id BIGINT PRIMARY KEY,
 family_id BIGINT NOT NULL,
 scope VARCHAR(16) NOT NULL,
 requested_ms BIGINT NOT NULL,
 deadline_ms BIGINT NOT NULL,
 INDEX idx_account_deletion_due(deadline_ms),
 INDEX idx_account_deletion_family(family_id,scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 可重复执行；助手默认关闭，由家庭管理员在页面中开启。
CREATE TABLE IF NOT EXISTS ai_family_settings (
 family_id BIGINT PRIMARY KEY, enabled BOOLEAN NOT NULL DEFAULT FALSE,
 updated_by BIGINT NOT NULL,
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_ai_settings_family FOREIGN KEY(family_id) REFERENCES family(id),
 CONSTRAINT fk_ai_settings_user FOREIGN KEY(updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 只保存使用次数与状态，不保存输入、回答、孩子姓名、测量快照或媒体地址。
CREATE TABLE IF NOT EXISTS ai_assistant_request (
 request_id VARCHAR(36) PRIMARY KEY,
 family_id BIGINT NOT NULL, user_id BIGINT NOT NULL, child_id BIGINT NOT NULL,
 task VARCHAR(24) NOT NULL, status VARCHAR(16) NOT NULL,
 expire_time TIMESTAMP NOT NULL,
 create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_ai_request_user_created(user_id,create_time),
 INDEX idx_ai_request_family_status(family_id,status),
 CONSTRAINT fk_ai_request_family FOREIGN KEY(family_id) REFERENCES family(id),
 CONSTRAINT fk_ai_request_user FOREIGN KEY(user_id) REFERENCES app_user(id),
 CONSTRAINT fk_ai_request_child FOREIGN KEY(child_id) REFERENCES child(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
