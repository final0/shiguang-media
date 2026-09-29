package org.example.springaidemo.growth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 持久化上传意图与删除任务，进程重启后仍能重试 MinIO 文件清理。 */
@Service
public class GrowthMediaCleanup {
    private static final Logger log = LoggerFactory.getLogger(GrowthMediaCleanup.class);
    private final GrowthStore db;
    private final GrowthObjectStorage storage;
    private final TransactionTemplate transaction;

    public GrowthMediaCleanup(GrowthStore db, GrowthObjectStorage storage, PlatformTransactionManager manager) {
        this.db = db; this.storage = storage;
        this.transaction = new TransactionTemplate(manager);
    }

    /** 在业务事务开始前提交意图，避免占用家庭行锁时再借第二个数据库连接。 */
    public void stage(List<String> keys) {
        var retryAt = Timestamp.from(Instant.now().plusSeconds(3600));
        transaction.executeWithoutResult(status -> keys.forEach(key ->
                db.update("INSERT INTO media_cleanup(oss_key,create_time) VALUES(?,?)", key, retryAt)));
    }

    /** 正常失败无需等待一小时；异常退出的未完成上传则由过期意图兜底。 */
    public void retrySoon(List<String> keys) {
        try {
            transaction.executeWithoutResult(status -> keys.forEach(key ->
                    db.update("UPDATE media_cleanup SET create_time=CURRENT_TIMESTAMP WHERE oss_key=?", key)));
        } catch (RuntimeException e) { log.warn("Cannot expedite pending media cleanup; persisted tasks will retry later"); }
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void cleanup() {
        List<java.util.Map<String, Object>> tasks;
        try {
            tasks = db.list("SELECT oss_key FROM media_cleanup WHERE create_time<=CURRENT_TIMESTAMP ORDER BY create_time LIMIT 100");
        } catch (RuntimeException e) { log.warn("Cannot read media cleanup queue; will retry"); return; }
        for (var task : tasks) {
            String key = (String) task.get("ossKey");
            try {
                transaction.executeWithoutResult(status -> {
                    Long familyId = storage.familyId(key);
                    // 与上传、永久删除使用同一把家庭行锁，不能清理仍在上传事务中的对象。
                    if (familyId != null) db.list("SELECT id FROM family WHERE id=? FOR UPDATE", familyId);
                    if (db.list("SELECT oss_key FROM media_cleanup WHERE oss_key=? AND create_time<=CURRENT_TIMESTAMP FOR UPDATE", key).isEmpty()) return;
                    // 兼容重试/提交结果不明确的情况：仍被任何记录引用的文件绝不能删除。
                    if (db.number("SELECT COUNT(*) FROM media_file WHERE oss_key=?", key) == 0
                            && db.number("SELECT COUNT(*) FROM app_user WHERE avatar=?", key) == 0) storage.delete(key);
                    db.update("DELETE FROM media_cleanup WHERE oss_key=?", key);
                });
            } catch (RuntimeException e) { log.warn("Media cleanup deferred for {}", key); }
        }
    }
}
