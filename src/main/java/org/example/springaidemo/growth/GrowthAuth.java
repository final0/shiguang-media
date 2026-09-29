package org.example.springaidemo.growth;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class GrowthAuth {
    // 使用 long，5 GiB 已超过 int 上限；显式写入以兼容尚未升级默认值的旧表。
    private static final long DEFAULT_STORAGE_QUOTA = 5L * 1024 * 1024 * 1024;
    private final GrowthStore db;
    private final GrowthAccountDeletion deletion;
    private final GrowthEmailVerification codes;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    public GrowthAuth(GrowthStore db, GrowthAccountDeletion deletion, GrowthEmailVerification codes) { this.db = db; this.deletion = deletion; this.codes=codes; }
    public record Actor(long id, long familyId, String nickname, String role) {
        public boolean admin() { return "admin".equals(role); }
        public void requireAdmin() { if (!admin()) throw GrowthException.forbidden(); }
    }
    public record Authenticated(long id, String credentialStamp) { }
    private String stamp(String phone, String passwordHash) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest((phone + "\n" + passwordHash).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Authenticated authenticated(long id) {
        var user = db.one("SELECT phone,password_hash FROM app_user WHERE id=? AND is_deleted=FALSE", id);
        return new Authenticated(id, stamp((String) user.get("phone"), (String) user.get("passwordHash")));
    }
    public Actor actor(HttpSession session) {
        try { return sessionActor(session); }
        catch (IllegalStateException e) { throw new GrowthException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"); }
    }
    private Actor sessionActor(HttpSession session) {
        Object userId = session == null ? null : session.getAttribute("growthUser");
        if (!(userId instanceof Long)) throw new GrowthException(HttpStatus.UNAUTHORIZED, "请先登录你的家庭空间");
        var rows = db.list("SELECT u.id,u.nickname,u.phone,u.password_hash,m.family_id,m.role FROM app_user u LEFT JOIN family_member m ON m.user_id=u.id WHERE u.id=? AND u.is_deleted=FALSE", userId);
        if (rows.isEmpty()) throw new GrowthException(HttpStatus.UNAUTHORIZED, "登录已失效或你已被移出家庭，请重新登录");
        var row = rows.get(0);
        if (!stamp((String) row.get("phone"), (String) row.get("passwordHash")).equals(session.getAttribute("growthCredentialStamp"))) {
            session.invalidate();
            throw new GrowthException(HttpStatus.UNAUTHORIZED, "账号信息已更新，请重新登录");
        }
        long id = ((Number) row.get("id")).longValue();
        deletion.guard(id, row.get("familyId") == null ? 0 : ((Number) row.get("familyId")).longValue());
        if (row.get("familyId") == null) throw familyRequired((String) row.get("phone"));
        return new Actor(id, ((Number) row.get("familyId")).longValue(), (String) row.get("nickname"), (String) row.get("role"));
    }
    private GrowthException familyRequired(String phone) {
        return new GrowthException(HttpStatus.CONFLICT, "个人账号仍然保留，请重新创建或加入家庭", java.util.Map.of("code", "FAMILY_REQUIRED", "phone", phone));
    }
    public Authenticated login(GrowthDtos.Login input) {
        var rows = db.list("SELECT id,phone,password_hash FROM app_user WHERE phone=? AND is_deleted=FALSE", input.phone());
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72 || rows.isEmpty() ||
                !passwords.matches(input.password(), (String) rows.get(0).get("passwordHash"))) {
            throw new GrowthException(HttpStatus.UNAUTHORIZED, "手机号或密码不正确");
        }
        long id = ((Number) rows.get(0).get("id")).longValue();
        deletion.guardPhone(input.phone());
        if (db.number("SELECT COUNT(*) FROM family_member WHERE user_id=?", id) == 0) throw familyRequired(input.phone());
        db.update("UPDATE app_user SET last_login_time=CURRENT_TIMESTAMP WHERE id=?", id);
        // 使用刚才通过密码验证的版本，不能在验证后再读取新密码版本签发会话。
        return new Authenticated(id, stamp((String) rows.get(0).get("phone"), (String) rows.get(0).get("passwordHash")));
    }
    @Transactional
    public void changePhone(Actor a, GrowthDtos.Phone input) {
        var user = lockCredentials(a, input.currentPassword());
        if (input.phone().equals(user.get("phone"))) throw GrowthException.bad("新手机号与当前手机号相同");
        if (db.number("SELECT COUNT(*) FROM app_user WHERE phone=? AND id<>?", input.phone(), a.id()) > 0) throw GrowthException.bad("这个手机号已被使用");
        // 重新加盐但保持密码不变；即使以后改回旧手机号，旧会话也不能重新生效。
        try { db.update("UPDATE app_user SET phone=?,password_hash=? WHERE id=?", input.phone(), passwords.encode(input.currentPassword()), a.id()); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw GrowthException.bad("这个手机号已被使用"); }
        db.log(a, "account", "修改本人登录手机号");
    }
    @Transactional
    public void changePassword(Actor a, GrowthDtos.Password input) {
        var user = lockCredentials(a, input.currentPassword());
        if (!input.newPassword().equals(input.confirmPassword())) throw GrowthException.bad("两次新密码不一致");
        if (input.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) throw GrowthException.bad("密码 UTF-8 长度不能超过 72 字节");
        if (passwords.matches(input.newPassword(), (String) user.get("passwordHash"))) throw GrowthException.bad("新密码不能与当前密码相同");
        db.update("UPDATE app_user SET password_hash=? WHERE id=?", passwords.encode(input.newPassword()), a.id());
        db.log(a, "account", "修改本人登录密码");
    }
    private java.util.Map<String,Object> lockCredentials(Actor a, String currentPassword) {
        db.one("SELECT id FROM family WHERE id=? FOR UPDATE", a.familyId());
        deletion.guard(a.id(), a.familyId());
        var user = db.one("SELECT u.phone,u.password_hash FROM app_user u JOIN family_member m ON m.user_id=u.id WHERE u.id=? AND u.is_deleted=FALSE AND m.family_id=? FOR UPDATE", a.id(), a.familyId());
        if (((String) user.get("phone")).startsWith("demo-")) throw GrowthException.bad("示例账号不支持修改登录信息，请注册自己的账号");
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72 || !passwords.matches(currentPassword, (String) user.get("passwordHash"))) throw GrowthException.bad("当前密码不正确");
        return user;
    }
    @Transactional
    public long register(GrowthDtos.Register input, String emailContext) {
        if (input.phone() == null) throw GrowthException.bad("请输入手机号");
        deletion.guardPhone(input.phone());
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw GrowthException.bad("密码 UTF-8 长度不能超过 72 字节");
        if (db.number("SELECT COUNT(*) FROM app_user WHERE phone=?", input.phone()) > 0) throw GrowthException.bad("这个手机号已经注册");
        boolean joining = input.inviteCode() != null && !input.inviteCode().isBlank();
        long familyId = 0;
        if (joining) {
            var rows = db.list("SELECT id FROM family WHERE invite_code=? AND invite_expire_time>CURRENT_TIMESTAMP FOR UPDATE", input.inviteCode().trim());
            if (rows.isEmpty()) throw GrowthException.bad("邀请码无效或已过期");
            familyId = ((Number) rows.get(0).get("id")).longValue();
            deletion.guard(0, familyId);
        }
        String email=codes.normalize(input.email());
        if(db.number("SELECT COUNT(*) FROM app_user WHERE email=?",email)>0)throw GrowthException.bad("该邮箱已绑定账号");
        codes.consume(email,input.emailCode(),emailContext);
        long id;
        try { id = db.insert("INSERT INTO app_user(phone,nickname,password_hash,email,email_verified_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)", input.phone(), input.nickname().trim(), passwords.encode(input.password()),email); }
        catch(org.springframework.dao.DuplicateKeyException e){throw GrowthException.bad("手机号或邮箱已注册，请使用其他账号重新获取验证码");}
        if (!joining) familyId = db.insert("INSERT INTO family(name,creator_id,storage_quota) VALUES(?,?,?)", input.familyName() == null || input.familyName().isBlank() ? input.nickname().trim() + "的家" : input.familyName().trim(), id, DEFAULT_STORAGE_QUOTA);
        db.update("INSERT INTO family_member(family_id,user_id,role) VALUES(?,?,?)", familyId, id, joining ? "member" : "admin");
        db.log(new Actor(id, familyId, input.nickname(), joining ? "member" : "admin"), "member_change", joining ? "通过邀请加入家庭" : "创建家庭空间");
        return id;
    }
    @Transactional
    public long demo() {
        String unique = UUID.randomUUID().toString();
        long id = db.insert("INSERT INTO app_user(phone,nickname,password_hash) VALUES(?,?,?)", "demo-" + unique, "小禾妈妈", passwords.encode(unique));
        long family = db.insert("INSERT INTO family(name,creator_id,storage_quota) VALUES(?,?,?)", "小禾的幸福小家", id, DEFAULT_STORAGE_QUOTA);
        db.update("INSERT INTO family_member(family_id,user_id,role) VALUES(?,?,'admin')", family, id);
        LocalDate today = LocalDate.now();
        long child = db.insert("INSERT INTO child(family_id,name,birthday,gender) VALUES(?,?,?,'girl')", family, "小禾", today.minusYears(3).minusMonths(2));
        String[][] entries = {
            {"把秋天装进口袋里", "今天和小禾一起去公园，她认真地收集了每一片好看的叶子。\n“妈妈，这片像一只小蝴蝶！”\n原来幸福，就是陪你慢慢发现这个世界。", "diary", "park", "户外时光"},
            {"第一次，自己骑出好远", "从需要扶着车把，到回头开心地说“我会啦”。\n小小的勇敢，也值得被郑重收藏。", "milestone", "bike", "第一次"},
            {"厨房里的小小面包师", "面粉沾上了鼻尖，快乐藏在饼干里。\n今天的作品有点歪，但每一块都有爱的形状。", "diary", "baking", "亲子日常"},
            {"睡前，再讲一个故事吧", "靠在一起读了最喜欢的绘本。晚安，我的小小星球，愿你的梦里也有柔软的云。", "diary", "night", "亲子日常"},
            {"三岁快乐，我的宝贝", "又陪你走过了一个春夏秋冬。\n愿你永远拥有好奇心，勇敢、自在地长大。", "milestone", "birthday", "生日"},
            {"去看海的那一天", "第一次听见海浪的声音，小禾追着泡沫跑了好久。收藏一整个蓝色的下午。", "diary", "sea", "旅行"},
            {"去年的今天，你还小小的", "小手牵着我的手，走过家门口的花园。你一天一天长大，我们一点一点珍藏。", "diary", "park", "亲子日常"}
        };
        int[] days = {0, 2, 5, 8, 61, 90, 365};
        for (int i = 0; i < entries.length; i++) {
            var entry = entries[i];
            LocalDate date = i == 6 ? today.minusYears(1) : today.minusDays(days[i]);
            long record = db.insert("INSERT INTO grow_record(family_id,child_id,user_id,title,content,record_type,record_time,illustration,milestone) VALUES(?,?,?,?,?,?,?,?,?)", family, child, id, entry[0], entry[1], entry[2], date, entry[3], "milestone".equals(entry[2]) ? entry[0] : null);
            db.ensureTag(family, entry[4]);
            db.update("INSERT INTO record_tag(record_id,tag_id) SELECT ?,id FROM tag WHERE family_id=? AND name=?", record, family, entry[4]);
        }
        for (int i = 0; i < 6; i++) {
            LocalDate measured = today.minusMonths(5 - i);
            db.update("INSERT INTO grow_data(family_id,child_id,height,weight,measure_time,age_month,record_by,remark) VALUES(?,?,?,?,?,?,?,?)", family, child, 90 + i * 1.2, 12.5 + i * 0.3, measured, 33 + i, id, "示例测量数据");
        }
        db.update("INSERT INTO notice(family_id,user_id,title,content) VALUES(?,?,?,?)", family, id, "欢迎来到拾光", "这是独立的示例空间。图画、名字和记录均为演示内容；退出后请注册自己的家庭。示例会话到期后无法再次登录。" );
        db.log(new Actor(id, family, "小禾妈妈", "admin"), "setting", "创建独立示例空间");
        return id;
    }
}
