package org.example.springaidemo.growth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class GrowthMail {
    private final JavaMailSender sender;
    private final String from;
    private final String password;
    private final GrowthMailTemplate templates;
    public GrowthMail(ObjectProvider<JavaMailSender> sender, GrowthMailTemplate templates, @Value("${spring.mail.username:}") String from,
                      @Value("${spring.mail.password:}") String password) {
        this.sender = sender.getIfAvailable(); this.from = from; this.password = password;
        this.templates = templates;
    }
    public boolean configured() { return sender != null && !from.isBlank() && !password.isBlank(); }
    public void sendReminder(String to, String title, String content, long scheduledMs, boolean delayed) {
        send(to, "【拾光提醒】" + title, templates.reminder(title, content, scheduledMs, delayed));
    }
    public void sendVerification(String to, String code) {
        send(to, "【拾光】邮箱验证码", templates.verification(code));
    }
    private void send(String to, String subject, GrowthMailTemplate.Body body) {
        if (!configured()) throw new GrowthException(HttpStatus.SERVICE_UNAVAILABLE, "邮件服务尚未配置，请联系管理员");
        sender.send(message -> {
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "拾光 · 成长记录");
            helper.setTo(to);
            helper.setSubject(subject.replace('\r', ' ').replace('\n', ' '));
            // 同一邮件包含纯文本和 HTML 两种正文，由收件客户端选择适合的版本。
            helper.setText(body.plainText(), body.html());
        });
    }
}
