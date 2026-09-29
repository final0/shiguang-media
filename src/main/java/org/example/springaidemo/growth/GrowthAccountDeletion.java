package org.example.springaidemo.growth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

/** 数据库保存唯一恢复期限。到期访问立即拒绝，后台事务删除资料并持久化对象清理任务。 */
@Service
public class GrowthAccountDeletion {
    private static final Logger log = LoggerFactory.getLogger(GrowthAccountDeletion.class);
    private static final long WINDOW = 12 * 60 * 60 * 1000L;
    private final GrowthStore db;
    private final TransactionTemplate tx;
    private final StringRedisTemplate redis;
    private volatile long retryRedisAt;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);

    public GrowthAccountDeletion(GrowthStore db, PlatformTransactionManager manager, StringRedisTemplate redis) {
        this.db = db; this.tx = new TransactionTemplate(manager); this.redis = redis;
    }
    public record Apply(@NotBlank @Size(max=72) String password, boolean confirmed, boolean familyConfirmed) { }
    public record Rehome(@NotBlank @Size(max=64) String phone, @NotBlank @Size(max=72) String password,
                         @Size(max=60) String familyName, @Size(max=64) String inviteCode) { }
    private long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    // 使用数据库时钟，多个 Pod 的取消和到期清理使用同一时间基准。
    private long now() { return db.currentTimeMillis(); }
    private void verify(Map<String,Object> user, String password) {
        if (password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !passwords.matches(password, (String) user.get("passwordHash"))) {
            throw new GrowthException(HttpStatus.UNAUTHORIZED, "手机号或原登录密码不正确");
        }
    }
    private Map<String,Object> credentials(String phone, String password) {
        var rows = db.list("SELECT id,phone,password_hash FROM app_user WHERE phone=? AND is_deleted=FALSE", phone);
        if (rows.isEmpty()) throw new GrowthException(HttpStatus.UNAUTHORIZED, "手机号或原登录密码不正确");
        verify(rows.get(0), password); return rows.get(0);
    }
    private Map<String,Object> pending(long user, long family) {
        var rows = db.list("SELECT * FROM account_deletion WHERE user_id=? OR (family_id=? AND scope='FAMILY') ORDER BY deadline_ms LIMIT 1 FOR UPDATE", user, family);
        return rows.isEmpty() ? null : rows.get(0);
    }
    private Map<String,Object> info(Map<String,Object> row, long user) {
        long clock = now(), deadline = number(row, "deadlineMs");
        return Map.of("code", "ACCOUNT_DELETION_PENDING", "deadlineMs", deadline, "serverNowMs", clock,
                "scope", row.get("scope"), "ownRequest", number(row, "userId") == user,
                "canCancel", number(row, "userId") == user && clock < deadline);
    }
    /** 不能使用 Redis 缓存来放行请求，过期及取消状态均以当前数据库记录为准。 */
    public void guard(long user, long family) {
        var row = pending(user, family);
        if (row != null) throw new GrowthException(HttpStatus.CONFLICT,
                number(row, "deadlineMs") <= now() ? "恢复期限已结束，账号或家庭已停用，正在清理资料" : "该账号或家庭已申请注销，是否取消注销？", info(row, user));
    }
    public void guardPhone(String phone) {
        var rows = db.list("SELECT u.id,m.family_id FROM app_user u LEFT JOIN family_member m ON m.user_id=u.id WHERE u.phone=? AND u.is_deleted=FALSE", phone);
        if (!rows.isEmpty()) {
            var row = rows.get(0);
            guard(number(row, "id"), row.get("familyId") == null ? 0 : number(row, "familyId"));
        }
    }
    public Map<String,Object> apply(GrowthAuth.Actor actor, Apply input) {
        return tx.execute(status -> {
            db.one("SELECT id FROM family WHERE id=? FOR UPDATE", actor.familyId());
            var member = db.one("SELECT role FROM family_member WHERE family_id=? AND user_id=? FOR UPDATE", actor.familyId(), actor.id());
            var user = db.one("SELECT phone,password_hash FROM app_user WHERE id=? AND is_deleted=FALSE FOR UPDATE", actor.id());
            verify(user, input.password());
            if (((String) user.get("phone")).startsWith("demo-")) throw GrowthException.bad("示例账号无需注销，请直接退出");
            guard(actor.id(), actor.familyId());
            boolean family = "admin".equals(member.get("role"));
            if (!input.confirmed() || (family && !input.familyConfirmed())) throw GrowthException.bad("请确认注销后果：管理员注销将删除整个家庭及全部资料");
            long clock = now();
            db.update("INSERT INTO account_deletion(user_id,family_id,scope,requested_ms,deadline_ms) VALUES(?,?,?,?,?)",
                    actor.id(), actor.familyId(), family ? "FAMILY" : "PERSONAL", clock, clock + WINDOW);
            GrowthReminderLifecycle.suspend(db,family?"family_id=?":"user_id=?",family?actor.familyId():actor.id());
            db.update("UPDATE ai_assistant_request SET status='CANCELLED',update_time=CURRENT_TIMESTAMP WHERE status='RUNNING' AND "
                    + (family ? "family_id=?" : "user_id=?"), family ? actor.familyId() : actor.id());
            db.log(actor, "account", family ? "申请注销账号和家庭，12小时内可由发起者取消" : "申请注销个人账号，12小时内可取消");
            return info(db.one("SELECT * FROM account_deletion WHERE user_id=?", actor.id()), actor.id());
        });
    }
    public void cancel(GrowthDtos.Login input) {
        var checked = credentials(input.phone(), input.password());
        long userId = number(checked, "id");
        // 在事务外只定位锁；进入后重新读取并验证，避免与到期任务或凭证变更竞态。
        var rows = db.list("SELECT family_id FROM account_deletion WHERE user_id=?", userId);
        if (rows.isEmpty()) throw GrowthException.bad("该账号没有可取消的注销申请；家庭申请只能由发起者取消");
        long family = number(rows.get(0), "familyId");
        tx.executeWithoutResult(status -> {
            db.list("SELECT id FROM family WHERE id=? FOR UPDATE", family);
            var users = db.list("SELECT phone,password_hash FROM app_user WHERE id=? AND is_deleted=FALSE FOR UPDATE", userId);
            if (users.isEmpty() || !input.phone().equals(users.get(0).get("phone"))) throw GrowthException.bad("账号已注销，无法恢复");
            verify(users.get(0), input.password());
            var requests = db.list("SELECT * FROM account_deletion WHERE user_id=? FOR UPDATE", userId);
            if (requests.isEmpty()) throw GrowthException.bad("注销申请已处理，请重新登录");
            if (number(requests.get(0), "familyId") != family) throw GrowthException.bad("注销申请已变化，请重新登录后再试");
            if (now() >= number(requests.get(0), "deadlineMs")) throw GrowthException.bad("12小时恢复期限已结束，不能取消注销");
            // 一个家庭可能有多份个人申请。只能取消本人发起的申请。
            db.update("DELETE FROM account_deletion WHERE user_id=?", userId);
            if (db.number("SELECT COUNT(*) FROM family WHERE id=?", family) > 0)
                db.update("INSERT INTO operate_log(family_id,user_id,action_type,detail) VALUES(?,?,'account','本人验证密码后取消注销申请')", family, userId);
        });
    }
    /** 家庭注销后保留其他成员账号；验证原密码后重新创建或加入家庭。 */
    public long rehome(Rehome input) {
        var checked = credentials(input.phone(), input.password());
        long id = number(checked, "id");
        return tx.execute(status -> {
            boolean joining = input.inviteCode() != null && !input.inviteCode().isBlank();
            long family = 0;
            if (joining) {
                var rows = db.list("SELECT id FROM family WHERE invite_code=? AND invite_expire_time>CURRENT_TIMESTAMP FOR UPDATE", input.inviteCode().trim());
                if (rows.isEmpty()) throw GrowthException.bad("邀请码无效或已过期");
                family = number(rows.get(0), "id"); guard(0, family);
            }
            var user = db.one("SELECT phone,password_hash,nickname FROM app_user WHERE id=? AND is_deleted=FALSE FOR UPDATE", id);
            if (!input.phone().equals(user.get("phone"))) throw GrowthException.bad("账号信息已变更，请重新登录");
            verify(user, input.password()); guard(id, 0);
            if (db.number("SELECT COUNT(*) FROM family_member WHERE user_id=?", id) != 0) throw GrowthException.bad("你已经属于一个家庭，请直接登录");
            if (!joining) family = db.insert("INSERT INTO family(name,creator_id,storage_quota) VALUES(?,?,?)",
                    input.familyName() == null || input.familyName().isBlank() ? user.get("nickname") + "的家" : input.familyName().trim(), id, 5L * 1024 * 1024 * 1024);
            db.update("INSERT INTO family_member(family_id,user_id,role) VALUES(?,?,?)", family, id, joining ? "member" : "admin");
            db.log(new GrowthAuth.Actor(id, family, (String) user.get("nickname"), joining ? "member" : "admin"), "member_change", "重新创建或加入家庭");
            return id;
        });
    }
    private void queue(String key) {
        if (key == null || key.isBlank()) return;
        // 已有的上传意图也提前成为可重试删除任务。
        if (db.list("SELECT oss_key FROM media_cleanup WHERE oss_key=? FOR UPDATE", key).isEmpty())
            db.update("INSERT INTO media_cleanup(oss_key) VALUES(?)", key);
        else db.update("UPDATE media_cleanup SET create_time=CURRENT_TIMESTAMP WHERE oss_key=?", key);
    }
    private void anonymize(long user) {
        GrowthReminderLifecycle.purge(db,"user_id=?",user);
        var row = db.one("SELECT avatar FROM app_user WHERE id=? FOR UPDATE", user);
        queue((String) row.get("avatar"));
        // 保留无个人信息的外键占位，普通成员贡献的共享记录显示“已注销用户”。
        db.update("UPDATE app_user SET phone=?,nickname='已注销用户',password_hash=?,avatar=NULL,email=NULL,email_verified_at=NULL,email_enabled=FALSE,email_version=email_version+1,is_deleted=TRUE,last_login_time=NULL WHERE id=?",
                "deleted-" + UUID.randomUUID(), passwords.encode(UUID.randomUUID().toString()), user);
        db.update("DELETE FROM family_member WHERE user_id=?", user);
        db.update("DELETE FROM notice WHERE user_id=?", user);
        db.update("DELETE FROM ai_assistant_request WHERE user_id=?", user);
        db.update("DELETE FROM share_record WHERE creator_id=?", user);
    }
    private void deleteFamily(long family) {
        GrowthReminderLifecycle.purge(db,"family_id=?",family);
        db.list("SELECT oss_key FROM media_file WHERE family_id=?", family).forEach(row -> queue((String) row.get("ossKey")));
        db.update("DELETE FROM record_tag WHERE record_id IN (SELECT id FROM grow_record WHERE family_id=?)", family);
        // 按外键依赖顺序清理，包括回收站记录、归档孩子及其全部关联数据。
        for (String table : new String[]{"ai_assistant_request", "ai_family_settings", "share_record", "record_comment", "media_file", "notice", "grow_data", "vaccine", "grow_record", "child", "tag", "operate_log", "family_member"})
            db.update("DELETE FROM " + table + " WHERE family_id=?", family);
        db.update("DELETE FROM family WHERE id=?", family);
    }
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void expire() {
        // Redis 仅用于多 Pod 扫描租约；不可用时回退到数据库行锁，不影响恢复和到期语义。
        if (System.currentTimeMillis() >= retryRedisAt) {
            try {
                if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("shiguang:account-deletion:sweep", UUID.randomUUID().toString(), java.time.Duration.ofSeconds(30)))) return;
            } catch (RuntimeException e) {
                retryRedisAt = System.currentTimeMillis() + 60000;
                log.warn("Redis deletion sweep lease unavailable; using database locks for 60 seconds");
            }
        }
        try {
            var due = db.list("SELECT user_id,family_id FROM account_deletion WHERE deadline_ms<=? ORDER BY deadline_ms LIMIT 50", now());
            for (var task : due) {
                long user = number(task, "userId"), family = number(task, "familyId");
                try {
                    tx.executeWithoutResult(status -> {
                        db.list("SELECT id FROM family WHERE id=? FOR UPDATE", family);
                        db.list("SELECT id FROM app_user WHERE id=? FOR UPDATE", user);
                        var rows = db.list("SELECT * FROM account_deletion WHERE user_id=? FOR UPDATE", user);
                        if (rows.isEmpty() || number(rows.get(0), "familyId") != family || number(rows.get(0), "deadlineMs") > now()) return;
                        if ("FAMILY".equals(rows.get(0).get("scope"))) deleteFamily(family);
                        anonymize(user);
                        db.update("DELETE FROM account_deletion WHERE user_id=?", user);
                    });
                } catch (RuntimeException e) { log.error("Account deletion deferred, userId={}; durable request will retry", user, e); }
            }
        } catch (RuntimeException e) { log.warn("Cannot scan account deletion queue; check database and upgrade-account-deletion.sql", e); }
    }
}
