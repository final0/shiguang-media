-- 在当前项目 growth 数据库中整份执行。只新增表，不修改成长记录和媒体。
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
