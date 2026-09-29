package org.example.springaidemo.growth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.example.springaidemo.growth.GrowthAuth.Actor;

@Service
public class GrowthService {
    private final GrowthStore db;
    private final GrowthAccountDeletion deletion;
    public GrowthService(GrowthStore db, GrowthAccountDeletion deletion) { this.db = db; this.deletion = deletion; }
    // 所有资源先验证家庭；管理员权限也不能跨家庭。
    Map<String,Object> child(Actor a, long id) { return db.one("SELECT * FROM child WHERE id=? AND family_id=? AND is_active=TRUE", id, a.familyId()); }
    Map<String,Object> record(Actor a, long id, boolean deleted) {
        return db.one("SELECT r.* FROM grow_record r JOIN child c ON c.id=r.child_id AND c.family_id=r.family_id WHERE r.id=? AND r.family_id=? AND r.is_deleted=? AND c.is_active=TRUE", id, a.familyId(), deleted);
    }
    void canEdit(Actor a, Map<String,Object> record) {
        if (!a.admin() && ((Number) record.get("userId")).longValue() != a.id()) throw GrowthException.forbidden();
    }
    void lockFamily(Actor a) {
        db.one("SELECT id FROM family WHERE id=? FOR UPDATE", a.familyId());
        deletion.guard(a.id(), a.familyId());
        // 角色变更、移出成员与写入串行；锁定后重新读取，不能沿用等待前的管理员身份。
        var rows = db.list("SELECT role FROM family_member WHERE family_id=? AND user_id=? FOR UPDATE", a.familyId(), a.id());
        if (rows.isEmpty() || !Objects.equals(rows.get(0).get("role"), a.role())) throw GrowthException.forbidden();
    }
    public Map<String,Object> snapshot(Actor a) {
        Map<String,Object> result = new LinkedHashMap<>();
        var user = db.one("SELECT id,nickname,(avatar IS NOT NULL AND avatar<>'') AS has_avatar FROM app_user WHERE id=?", a.id());
        user.put("role", a.role()); user.put("familyId", a.familyId());
        result.put("user", user);
        result.put("demo", db.number("SELECT COUNT(*) FROM app_user WHERE id=? AND phone LIKE 'demo-%'", a.id()) > 0);
        var family = db.one("SELECT id,name,creator_id,storage_quota,create_time FROM family WHERE id=?", a.familyId());
        family.put("storageUsed", db.number("SELECT COALESCE(SUM(size_bytes),0) FROM media_file WHERE family_id=? AND is_deleted=FALSE", a.familyId()));
        family.put("trashBytes", db.number("SELECT COALESCE(SUM(size_bytes),0) FROM media_file WHERE family_id=? AND is_deleted=TRUE", a.familyId()));
        result.put("family", family);
        result.put("children", db.list("SELECT * FROM child WHERE family_id=? AND is_active=TRUE ORDER BY id", a.familyId()));
        result.put("archivedChildren", a.admin() ? db.list("SELECT * FROM child WHERE family_id=? AND is_active=FALSE ORDER BY id DESC", a.familyId()) : List.of());
        result.put("members", db.list("SELECT m.id,m.user_id,m.role,u.nickname,(u.avatar IS NOT NULL AND u.avatar<>'') AS has_avatar,m.create_time FROM family_member m JOIN app_user u ON u.id=m.user_id WHERE m.family_id=? ORDER BY m.id", a.familyId()));
        var records = db.list("SELECT r.*,u.nickname AS author FROM grow_record r JOIN app_user u ON u.id=r.user_id JOIN child c ON c.id=r.child_id AND c.family_id=r.family_id WHERE r.family_id=? AND r.is_deleted=FALSE AND c.is_active=TRUE ORDER BY r.record_time DESC,r.id DESC", a.familyId());
        // 批量加载关联内容，避免时间线逐条查询产生 N+1。
        var media = db.list("SELECT id,record_id,mime_type,size_bytes FROM media_file WHERE family_id=? AND is_deleted=FALSE ORDER BY id", a.familyId());
        var tags = db.list("SELECT rt.record_id,t.name FROM record_tag rt JOIN tag t ON t.id=rt.tag_id WHERE t.family_id=?", a.familyId());
        var comments = db.list("SELECT c.id,c.user_id,c.record_id,c.content,c.create_time,u.nickname FROM record_comment c JOIN app_user u ON u.id=c.user_id WHERE c.family_id=? ORDER BY c.create_time", a.familyId());
        records.forEach(r -> {
            long id = ((Number) r.get("id")).longValue();
            r.put("media", media.stream().filter(m -> ((Number) m.get("recordId")).longValue() == id).toList());
            r.put("tags", tags.stream().filter(t -> ((Number) t.get("recordId")).longValue() == id).map(t -> t.get("name")).toList());
            r.put("comments", comments.stream().filter(c -> ((Number) c.get("recordId")).longValue() == id).toList());
        });
        result.put("records", records);
        result.put("measurements", db.list("SELECT d.* FROM grow_data d JOIN child c ON c.id=d.child_id AND c.family_id=d.family_id WHERE d.family_id=? AND c.is_active=TRUE ORDER BY d.measure_time,d.id", a.familyId()));
        result.put("vaccines", db.list("SELECT v.* FROM vaccine v JOIN child c ON c.id=v.child_id AND c.family_id=v.family_id WHERE v.family_id=? AND c.is_active=TRUE ORDER BY v.vaccine_date DESC,v.id DESC", a.familyId()));
        result.put("notices", db.list("SELECT n.* FROM notice n WHERE n.family_id=? AND n.user_id=? AND (n.link_id IS NULL OR EXISTS (SELECT 1 FROM grow_record r JOIN child c ON c.id=r.child_id AND c.family_id=r.family_id WHERE r.id=n.link_id AND r.family_id=n.family_id AND c.is_active=TRUE)) ORDER BY n.create_time DESC LIMIT 100", a.familyId(), a.id()));
        result.put("trash", a.admin() ? db.list("SELECT r.*,u.nickname AS author FROM grow_record r JOIN app_user u ON u.id=r.user_id JOIN child c ON c.id=r.child_id AND c.family_id=r.family_id WHERE r.family_id=? AND r.is_deleted=TRUE AND c.is_active=TRUE ORDER BY r.delete_time DESC", a.familyId()) : List.of());
        result.put("logs", a.admin() ? db.list("SELECT l.*,u.nickname FROM operate_log l JOIN app_user u ON u.id=l.user_id WHERE l.family_id=? ORDER BY l.id DESC LIMIT 100", a.familyId()) : List.of());
        result.put("shares", a.admin() ? db.list("SELECT s.id,s.record_id,s.share_token,s.expire_time,s.access_count,s.max_access,s.status,r.title FROM share_record s JOIN grow_record r ON r.id=s.record_id AND r.family_id=s.family_id JOIN child c ON c.id=r.child_id AND c.family_id=r.family_id WHERE s.family_id=? AND c.is_active=TRUE ORDER BY s.id DESC", a.familyId()) : List.of());
        return result;
    }
    @Transactional
    public long addChild(Actor a, GrowthDtos.Child input) {
        a.requireAdmin(); lockFamily(a);
        long id = db.insert("INSERT INTO child(family_id,name,birthday,gender) VALUES(?,?,?,?)", a.familyId(), input.name().trim(), input.birthday(), input.gender() == null ? "secret" : input.gender());
        db.log(a, "update", "添加孩子档案：" + input.name());
        return id;
    }
    @Transactional
    public void archiveChild(Actor a, long id) {
        a.requireAdmin(); lockFamily(a);
        var child = child(a, id);
        // 只切换档案可见状态，保留各条记录原本的删除状态，不向对象清理队列写入。
        db.update("UPDATE child SET is_active=FALSE WHERE id=? AND family_id=?", id, a.familyId());
        GrowthReminderLifecycle.suspend(db,"family_id=? AND child_id=?",a.familyId(),id);
        db.update("UPDATE share_record SET status=3 WHERE family_id=? AND record_id IN (SELECT id FROM grow_record WHERE family_id=? AND child_id=?)", a.familyId(), a.familyId(), id);
        db.update("UPDATE ai_assistant_request SET status='CANCELLED',update_time=CURRENT_TIMESTAMP WHERE family_id=? AND child_id=? AND status='RUNNING'", a.familyId(), id);
        db.log(a, "delete", "将孩子档案 #" + id + "（" + child.get("name") + "）移入档案回收站，保留关联资料并撤销分享");
    }
    @Transactional
    public void restoreChild(Actor a, long id) {
        a.requireAdmin(); lockFamily(a);
        var child = db.one("SELECT name FROM child WHERE id=? AND family_id=? AND is_active=FALSE", id, a.familyId());
        db.update("UPDATE child SET is_active=TRUE WHERE id=? AND family_id=?", id, a.familyId());
        db.log(a, "restore", "恢复孩子档案 #" + id + "（" + child.get("name") + "）及关联资料，原分享保持失效");
    }
    private void recordDate(Actor a, long childId, LocalDate date) {
        LocalDate birthday = LocalDate.parse(child(a, childId).get("birthday").toString());
        if (date.isBefore(birthday)) throw GrowthException.bad("记录日期不能早于孩子生日");
    }
    private void tags(Actor a, long id, List<String> names) {
        db.update("DELETE FROM record_tag WHERE record_id=?", id);
        for (String name : names.stream().map(String::trim).distinct().toList()) {
            db.ensureTag(a.familyId(), name);
            db.update("INSERT INTO record_tag(record_id,tag_id) SELECT ?,id FROM tag WHERE family_id=? AND name=?", id, a.familyId(), name);
        }
    }
    @Transactional
    public long saveRecord(Actor a, Long id, GrowthDtos.Entry input) {
        lockFamily(a);
        recordDate(a, input.childId(), input.recordTime());
        if (id == null) {
            id = db.insert("INSERT INTO grow_record(family_id,child_id,user_id,title,content,record_type,record_time,milestone) VALUES(?,?,?,?,?,?,?,?)", a.familyId(), input.childId(), a.id(), input.title().trim(), input.content().trim(), input.recordType(), input.recordTime(), input.milestone());
            db.log(a, "upload", "发布成长记录：" + input.title());
        } else {
            canEdit(a, record(a, id, false));
            db.update("UPDATE grow_record SET child_id=?,title=?,content=?,record_type=?,record_time=?,milestone=? WHERE id=? AND family_id=?", input.childId(), input.title().trim(), input.content().trim(), input.recordType(), input.recordTime(), input.milestone(), id, a.familyId());
            db.log(a, "update", "编辑成长记录：" + input.title());
        }
        tags(a, id, input.tags());
        return id;
    }
    @Transactional
    public void delete(Actor a, long id) {
        lockFamily(a);
        canEdit(a, record(a, id, false));
        db.update("UPDATE grow_record SET is_deleted=TRUE,delete_time=CURRENT_TIMESTAMP,deleted_by=? WHERE id=? AND family_id=?", a.id(), id, a.familyId());
        db.update("UPDATE media_file SET is_deleted=TRUE WHERE record_id=? AND family_id=?", id, a.familyId());
        db.update("UPDATE share_record SET status=3 WHERE record_id=? AND family_id=?", id, a.familyId());
        db.log(a, "delete", "将记录 #" + id + " 移入回收站，并撤销分享");
    }
    @Transactional
    public void restore(Actor a, long id) {
        a.requireAdmin(); lockFamily(a); record(a, id, true);
        long current = db.number("SELECT COALESCE(SUM(size_bytes),0) FROM media_file WHERE family_id=? AND is_deleted=FALSE", a.familyId());
        long restored = db.number("SELECT COALESCE(SUM(size_bytes),0) FROM media_file WHERE family_id=? AND record_id=?", a.familyId(), id);
        long quota = db.number("SELECT storage_quota FROM family WHERE id=?", a.familyId());
        if (current + restored > quota) throw GrowthException.bad("恢复后的照片和视频将超过空间配额，请先释放空间");
        db.update("UPDATE grow_record SET is_deleted=FALSE,delete_time=NULL,deleted_by=NULL WHERE id=? AND family_id=?", id, a.familyId());
        db.update("UPDATE media_file SET is_deleted=FALSE WHERE record_id=? AND family_id=?", id, a.familyId());
        db.log(a, "restore", "恢复记录 #" + id + "（原分享保持失效）");
    }
    @Transactional
    public void comment(Actor a, long id, GrowthDtos.Comment input) {
        lockFamily(a); var entry = record(a, id, false);
        db.update("INSERT INTO record_comment(family_id,record_id,user_id,content) VALUES(?,?,?,?)", a.familyId(), id, a.id(), input.content().trim());
        long author = ((Number) entry.get("userId")).longValue();
        if (author != a.id()) db.update("INSERT INTO notice(family_id,user_id,title,content,link_id) VALUES(?,?,?,?,?)", a.familyId(), author, a.nickname() + " 留下了评论", input.content().trim(), id);
    }
    @Transactional
    public void saveMeasurement(Actor a, Long id, GrowthDtos.Measurement input) {
        if (id != null) a.requireAdmin();
        lockFamily(a);
        // 先检查原记录归属，再校验目标孩子；管理员也不能修改其他家庭的数据。
        if (id != null) child(a, ((Number) db.one("SELECT child_id FROM grow_data WHERE id=? AND family_id=?", id, a.familyId()).get("childId")).longValue());
        recordDate(a, input.childId(), input.measureTime());
        var birthday = LocalDate.parse(child(a, input.childId()).get("birthday").toString());
        int months = (int) ChronoUnit.MONTHS.between(birthday, input.measureTime());
        if (id == null) {
            id = db.insert("INSERT INTO grow_data(family_id,child_id,height,weight,head,remark,measure_time,age_month,record_by) VALUES(?,?,?,?,?,?,?,?,?)", a.familyId(), input.childId(), input.height(), input.weight(), input.head(), input.remark(), input.measureTime(), months, a.id());
            db.log(a, "update", "录入成长测量 #" + id + "：" + input.measureTime());
        } else {
            // 日期或孩子变更时重新计算月龄，原始录入人 record_by 保留，修改人由日志记录。
            db.update("UPDATE grow_data SET child_id=?,height=?,weight=?,head=?,remark=?,measure_time=?,age_month=? WHERE id=? AND family_id=?", input.childId(), input.height(), input.weight(), input.head(), input.remark(), input.measureTime(), months, id, a.familyId());
            db.log(a, "update", "编辑成长测量 #" + id + "：" + input.measureTime());
        }
    }
    @Transactional
    public void deleteMeasurement(Actor a, long id) {
        a.requireAdmin(); lockFamily(a);
        var measurement = db.one("SELECT child_id,measure_time FROM grow_data WHERE id=? AND family_id=?", id, a.familyId());
        child(a, ((Number) measurement.get("childId")).longValue());
        db.update("DELETE FROM grow_data WHERE id=? AND family_id=?", id, a.familyId());
        db.log(a, "delete", "删除孩子 #" + measurement.get("childId") + " 的成长测量 #" + id + "：" + measurement.get("measureTime"));
    }
    @Transactional
    public void saveVaccine(Actor a, Long id, GrowthDtos.Vaccine input) {
        if (id != null) a.requireAdmin();
        lockFamily(a);
        if (id != null) child(a, ((Number) db.one("SELECT child_id FROM vaccine WHERE id=? AND family_id=?", id, a.familyId()).get("childId")).longValue());
        recordDate(a, input.childId(), input.vaccineDate());
        if (id == null) {
            id = db.insert("INSERT INTO vaccine(family_id,child_id,vaccine_name,dose_no,vaccine_date,hospital,remark) VALUES(?,?,?,?,?,?,?)", a.familyId(), input.childId(), input.vaccineName().trim(), input.doseNo(), input.vaccineDate(), input.hospital(), input.remark());
            db.log(a, "update", "录入接种记录 #" + id + "：" + input.vaccineName());
        } else {
            db.update("UPDATE vaccine SET child_id=?,vaccine_name=?,dose_no=?,vaccine_date=?,hospital=?,remark=? WHERE id=? AND family_id=?", input.childId(), input.vaccineName().trim(), input.doseNo(), input.vaccineDate(), input.hospital(), input.remark(), id, a.familyId());
            db.log(a, "update", "编辑接种记录 #" + id + "：" + input.vaccineName());
        }
    }
    @Transactional
    public void deleteVaccine(Actor a, long id) {
        a.requireAdmin(); lockFamily(a);
        var vaccine = db.one("SELECT child_id,vaccine_name,vaccine_date FROM vaccine WHERE id=? AND family_id=?", id, a.familyId());
        child(a, ((Number) vaccine.get("childId")).longValue());
        db.update("DELETE FROM vaccine WHERE id=? AND family_id=?", id, a.familyId());
        db.log(a, "delete", "删除孩子 #" + vaccine.get("childId") + " 的接种记录 #" + id + "：" + vaccine.get("vaccineName") + "（" + vaccine.get("vaccineDate") + "）");
    }
    @Transactional
    public Map<String,Object> invite(Actor a) {
        a.requireAdmin(); lockFamily(a);
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        var expiry = java.time.LocalDateTime.now().plusDays(7);
        db.update("UPDATE family SET invite_code=?,invite_expire_time=? WHERE id=?", code, expiry, a.familyId());
        db.log(a, "member_change", "生成新的家庭邀请码，7 天有效，旧码立即失效");
        return Map.of("code", code, "expiresAt", expiry.toString());
    }
    @Transactional
    public void removeMember(Actor a, long id) {
        a.requireAdmin(); lockFamily(a);
        var member = managedMember(a, id);
        ensureAnotherAdmin(a, member);
        db.update("DELETE FROM family_member WHERE id=? AND family_id=?", id, a.familyId());
        GrowthReminderLifecycle.purge(db,"family_id=? AND user_id=?",a.familyId(),member.get("userId"));
        db.update("UPDATE ai_assistant_request SET status='CANCELLED',update_time=CURRENT_TIMESTAMP WHERE family_id=? AND user_id=? AND status='RUNNING'", a.familyId(), member.get("userId"));
        db.update("UPDATE share_record SET status=3 WHERE family_id=? AND creator_id=?", a.familyId(), member.get("userId"));
        db.log(a, "member_change", "移除家庭成员 #" + id + "（" + member.get("nickname") + "），保留其成长记录并撤销其分享");
    }
    private Map<String,Object> managedMember(Actor a, long id) {
        var member = db.one("SELECT m.*,u.nickname FROM family_member m JOIN app_user u ON u.id=m.user_id WHERE m.id=? AND m.family_id=?", id, a.familyId());
        long userId = ((Number) member.get("userId")).longValue();
        if (userId == db.number("SELECT creator_id FROM family WHERE id=?", a.familyId())) throw GrowthException.bad("不能移除家庭创建者或取消其管理员权限");
        if (userId == a.id()) throw GrowthException.bad("不能移出自己或修改自己的管理员权限");
        return member;
    }
    private void ensureAnotherAdmin(Actor a, Map<String,Object> member) {
        if ("admin".equals(member.get("role")) && db.number("SELECT COUNT(*) FROM family_member WHERE family_id=? AND role='admin'", a.familyId()) <= 1) throw GrowthException.bad("家庭至少需要保留一位管理员");
    }
    @Transactional
    public void memberRole(Actor a, long id, GrowthDtos.MemberRole input) {
        a.requireAdmin(); lockFamily(a);
        var member = managedMember(a, id);
        if (db.number("SELECT COUNT(*) FROM account_deletion WHERE user_id=?", member.get("userId")) > 0) throw GrowthException.bad("该成员正在注销账号，请先由本人取消注销，再调整权限");
        if (Objects.equals(input.role(), member.get("role"))) return;
        if ("member".equals(input.role())) ensureAnotherAdmin(a, member);
        db.update("UPDATE family_member SET role=? WHERE id=? AND family_id=?", input.role(), id, a.familyId());
        String action = "admin".equals(input.role()) ? "授予管理员权限" : "取消管理员权限";
        db.log(a, "member_change", action + "：" + member.get("nickname") + "（成员 #" + id + "）");
        db.update("INSERT INTO notice(family_id,user_id,title,content) VALUES(?,?,?,?)", a.familyId(), member.get("userId"), "家庭权限已更新", a.nickname() + "已为你" + action + "，刷新页面后查看最新功能。");
    }
    @Transactional
    public void settings(Actor a, GrowthDtos.Settings input) {
        a.requireAdmin(); lockFamily(a); db.update("UPDATE family SET name=? WHERE id=?", input.name().trim(), a.familyId());
        db.log(a, "setting", "修改空间名称为：" + input.name());
    }
    public void readNotices(Actor a) { db.update("UPDATE notice SET is_read=TRUE WHERE family_id=? AND user_id=?", a.familyId(), a.id()); }
    @Transactional
    public Map<String,Object> export(Actor a, long childId) {
        a.requireAdmin(); lockFamily(a); child(a, childId);
        var data = snapshot(a);
        Map<String,Object> export = new LinkedHashMap<>();
        export.put("format", "shiguang-child-v1"); export.put("child", child(a, childId));
        for (String key : List.of("records", "measurements", "vaccines")) {
            @SuppressWarnings("unchecked") var rows = (List<Map<String,Object>>) data.get(key);
            export.put(key, rows.stream().filter(r -> ((Number) r.get("childId")).longValue() == childId).toList());
        }
        export.put("note", "仅导出结构化数据及照片/视频编号，不包含媒体二进制。照片和视频仍需登录原家庭查看。");
        db.log(a, "export", "导出孩子 #" + childId + " 的结构化记录");
        return export;
    }
}
