-- 在部署新 JAR 前，手动在当前业务数据库执行。MySQL 8，可重复执行。
-- 不保存手机号/密码，不使用外键，以便家庭删除后仍能处理独立的个人注销申请。
CREATE TABLE IF NOT EXISTS account_deletion (
 user_id BIGINT PRIMARY KEY,
 family_id BIGINT NOT NULL,
 scope VARCHAR(16) NOT NULL,
 requested_ms BIGINT NOT NULL,
 deadline_ms BIGINT NOT NULL,
 INDEX idx_account_deletion_due(deadline_ms),
 INDEX idx_account_deletion_family(family_id,scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
