package org.example.springaidemo.growth;

import jakarta.validation.constraints.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class GrowthEmailAccount {
    public record Code(@NotBlank @Size(max=100) String email, @Pattern(regexp="1[0-9]{10}") String phone) { }
    public record Bind(@NotBlank @Size(max=100) String email, @Pattern(regexp="[0-9]{6}") @NotBlank String code,
                       @NotBlank @Size(max=72) String currentPassword) { }
    public record Preference(boolean enabled) { }
    private final GrowthStore db;
    private final GrowthService service;
    private final GrowthEmailVerification codes;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    public GrowthEmailAccount(GrowthStore db, GrowthService service, GrowthEmailVerification codes) { this.db=db;this.service=service;this.codes=codes; }
    public Map<String,Object> get(GrowthAuth.Actor a) {
        return db.one("SELECT email,email_verified_at,email_enabled,email_version FROM app_user WHERE id=? AND is_deleted=FALSE",a.id());
    }
    public void available(String email) {
        if(db.number("SELECT COUNT(*) FROM app_user WHERE email=?",email)>0) throw GrowthException.bad("该邮箱已绑定账号；如正在注销，请使用原手机号登录处理");
    }
    private Map<String,Object> user(GrowthAuth.Actor a) {
        service.lockFamily(a);
        var row=db.one("SELECT phone,password_hash FROM app_user WHERE id=? AND is_deleted=FALSE FOR UPDATE",a.id());
        if(row.get("phone").toString().startsWith("demo-"))throw GrowthException.bad("示例账号不支持邮件功能，请注册正式账号");
        return row;
    }
    @Transactional
    public void bind(GrowthAuth.Actor a, Bind input, String context) {
        var row=user(a);
        if(input.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || !passwords.matches(input.currentPassword(),row.get("passwordHash").toString()))throw GrowthException.bad("当前登录密码不正确");
        String email=codes.normalize(input.email());available(email);
        codes.consume(email,input.code(),context);
        try { db.update("UPDATE app_user SET email=?,email_verified_at=CURRENT_TIMESTAMP,email_version=email_version+1 WHERE id=?",email,a.id()); }
        catch(org.springframework.dao.DuplicateKeyException e){throw GrowthException.bad("该邮箱已绑定其他账号，请重新获取验证码后填写其他邮箱");}
        cancelQueued(a);
        db.log(a,"account","绑定或更换已验证邮箱");
    }
    @Transactional
    public void preference(GrowthAuth.Actor a, boolean enabled) {
        user(a);
        db.update("UPDATE app_user SET email_enabled=?,email_version=email_version+1 WHERE id=?",enabled,a.id());
        cancelQueued(a);
        db.log(a,"account",enabled?"开启邮件提醒":"关闭邮件提醒");
    }
    private void cancelQueued(GrowthAuth.Actor a) {
        db.update("UPDATE reminder_delivery SET status='CANCELLED',result_code='EMAIL_CHANGED' WHERE status IN ('QUEUED','SENDING') AND reminder_id IN (SELECT id FROM growth_reminder WHERE user_id=?)",a.id());
        // 原来已排队的一次性提醒不自动补发到新邮箱，用户可重新编辑时间安排。
        db.update("UPDATE growth_reminder SET state='PAUSED',revision=revision+1 WHERE user_id=? AND state='ACTIVE' AND next_ms IS NULL",a.id());
    }
}
