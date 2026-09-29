package org.example.springaidemo.growth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import static org.example.springaidemo.growth.GrowthAuth.Actor;

/** 仅保存家庭开关及请求状态/次数，不保存问题、回答或儿童资料快照。 */
@Service
public class GrowthAssistantStore {
    private final GrowthStore db;
    private final GrowthService service;
    private final int dailyLimit;
    public GrowthAssistantStore(GrowthStore db, GrowthService service, @Value("${growth.ai.daily-request-limit:40}") int dailyLimit) {
        if (dailyLimit < 1) throw new IllegalArgumentException("growth.ai.daily-request-limit 必须大于 0");
        this.db = db; this.service = service; this.dailyLimit = dailyLimit;
    }
    public int dailyLimit() { return dailyLimit; }
    public boolean enabled(Actor a) {
        return db.number("SELECT COUNT(*) FROM ai_family_settings WHERE family_id=? AND enabled=TRUE", a.familyId()) > 0;
    }
    @Transactional
    public void settings(Actor a, boolean enabled) {
        a.requireAdmin();
        service.lockFamily(a);
        if (db.number("SELECT COUNT(*) FROM ai_family_settings WHERE family_id=?", a.familyId()) == 0) {
            db.update("INSERT INTO ai_family_settings(family_id,enabled,updated_by) VALUES(?,?,?)", a.familyId(), enabled, a.id());
        } else {
            db.update("UPDATE ai_family_settings SET enabled=?,updated_by=?,update_time=CURRENT_TIMESTAMP WHERE family_id=?", enabled, a.id(), a.familyId());
        }
        if (!enabled) db.update("UPDATE ai_assistant_request SET status='CANCELLED',update_time=CURRENT_TIMESTAMP WHERE family_id=? AND status='RUNNING'", a.familyId());
        db.log(a, "setting", (enabled ? "开启" : "关闭") + "千问成长助手");
    }
    @Transactional
    public void reserve(Actor a, GrowthAssistantController.Ask input) {
        // 与开关操作统一按家庭、用户顺序加锁，仅用于短事务登记；模型调用在事务外。
        service.lockFamily(a);
        service.child(a, input.childId());
        db.one("SELECT id FROM app_user WHERE id=? FOR UPDATE", a.id());
        if (!enabled(a)) throw GrowthException.bad("请先由家庭管理员开启拾光小助手");
        if (db.number("SELECT COUNT(*) FROM ai_assistant_request WHERE request_id=?", input.requestId()) > 0) {
            throw new GrowthException(HttpStatus.CONFLICT, "这次请求已经提交，请勿重复发送");
        }
        if (db.number("SELECT COUNT(*) FROM ai_assistant_request WHERE user_id=? AND status='RUNNING' AND expire_time>CURRENT_TIMESTAMP", a.id()) > 0) {
            throw new GrowthException(HttpStatus.TOO_MANY_REQUESTS, "已有回答正在生成，请先停止或等待完成");
        }
        if (db.number("SELECT COUNT(*) FROM ai_assistant_request WHERE user_id=? AND create_time>=?", a.id(), LocalDate.now().atStartOfDay()) >= dailyLimit) {
            throw new GrowthException(HttpStatus.TOO_MANY_REQUESTS, "今天已达到 " + dailyLimit + " 次使用上限，请明天再来");
        }
        db.update("INSERT INTO ai_assistant_request(request_id,family_id,user_id,child_id,task,status,expire_time) VALUES(?,?,?,?,?,'RUNNING',?)",
                input.requestId(), a.familyId(), a.id(), input.childId(), input.task(), Timestamp.from(Instant.now().plusSeconds(300)));
    }
    public void check(Actor a, String requestId) {
        if (!enabled(a)) throw GrowthException.bad("家庭管理员已关闭拾光小助手");
        // COMPLETE 到最后一个 SSE 事件发出之间仍可能被周期复核，不应误判为取消。
        if (db.number("SELECT COUNT(*) FROM ai_assistant_request WHERE request_id=? AND user_id=? AND family_id=? AND status IN ('RUNNING','COMPLETE') AND expire_time>CURRENT_TIMESTAMP", requestId, a.id(), a.familyId()) == 0) {
            throw GrowthException.bad("生成已停止或请求已过期，请重新发送");
        }
    }
    public int finish(Actor a, String requestId, String status) {
        return db.update("UPDATE ai_assistant_request SET status=?,update_time=CURRENT_TIMESTAMP WHERE request_id=? AND family_id=? AND user_id=? AND status='RUNNING'", status, requestId, a.familyId(), a.id());
    }
    public void cancel(Actor a, String requestId) { finish(a, requestId, "CANCELLED"); }
}
