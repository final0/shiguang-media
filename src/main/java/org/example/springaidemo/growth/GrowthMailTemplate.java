package org.example.springaidemo.growth;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 邮件采用内联样式和表格布局；不依赖网站 CSS、脚本或远程图片。 */
@Component
public class GrowthMailTemplate {
    public record Body(String plainText, String html) { }
    private static final Pattern SLOT = Pattern.compile("\\{\\{([a-z]+)}}");
    private static final DateTimeFormatter PLANNED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private final String layout;

    public GrowthMailTemplate() {
        try {
            layout = new ClassPathResource("growth/mail/layout.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read growth mail layout", e);
        }
    }

    public Body reminder(String title, String content, long scheduledMs, boolean delayed) {
        String planned = PLANNED_TIME.format(Instant.ofEpochMilli(scheduledMs));
        String note = content == null ? "" : content;
        String footer = "可在拾光的「待办提醒」修改任务，或在个人资料关闭邮件提醒。";
        String plain = "你设置的待办提醒：" + title + "\n计划时间：" + planned + "（北京时间）\n\n"
                + note + "\n\n" + (delayed ? "本次提醒因服务处理延迟补发。\n" : "") + footer;
        String body = """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" bgcolor="#eaf0e4" style="width:100%%;background-color:#eaf0e4;border:1px solid #dce5d3;border-radius:12px;">
                  <tr><td style="padding:20px;">
                    <p style="margin:0 0 8px;font-size:12px;color:#526b50;line-height:1.6;">约好的时间</p>
                    <p style="margin:0;font-size:23px;font-weight:bold;color:#526b50;line-height:1.5;">%s</p>
                    <p style="margin:6px 0 0;font-size:12px;color:#71866d;line-height:1.6;">北京时间 · UTC+8</p>
                  </td></tr>
                </table>
                <h2 style="margin:26px 0 10px;font-size:20px;font-weight:600;color:#38423a;line-height:1.7;overflow-wrap:anywhere;word-break:break-all;">%s</h2>
                %s
                %s
                """.formatted(escape(planned), lines(title), note.isBlank() ? "" : """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="width:100%%;table-layout:fixed;">
                  <tr><td style="padding:14px 16px;background-color:#f8f7f2;border-left:3px solid #c8d5be;border-radius:0 8px 8px 0;">
                    <p style="margin:0 0 6px;font-size:11px;color:#788271;line-height:1.8;">写给自己的小备注</p>
                    <p style="margin:0;font-size:15px;line-height:1.9;color:#38423a;overflow-wrap:anywhere;word-break:break-all;">%s</p>
                  </td></tr>
                </table>
                """.formatted(lines(note)), delayed ? """
                <p style="margin:18px 0 0;padding:12px 14px;border-radius:8px;background-color:#f3e6d8;color:#8b654b;font-size:12px;line-height:1.8;">这封提醒比计划晚了一些：因服务处理延迟，现为你补发。</p>
                """ : "");
        return new Body(plain, render(Map.of(
                "preheader", escape("你设置的待办提醒已到时间，请查看事项安排。"),
                "label", "一份贴心提醒", "heading", "有件小事，想提醒你",
                "intro", "忙碌的日子里，也为重要的小事留一点时间。这是你在拾光设置的待办提醒。",
                "body", body, "footer", escape(footer))));
    }

    public Body verification(String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("Invalid verification code format");
        String plain = "你的验证码是：" + code + "\n\n5分钟内有效，仅可使用一次。请在发起请求的页面填写。\n如果不是你本人操作，请忽略此邮件。\n拾光 · 成长记录";
        String body = """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" bgcolor="#eaf0e4" style="width:100%%;background-color:#eaf0e4;border:1px solid #dce5d3;border-radius:12px;">
                  <tr><td align="center" style="padding:24px 12px;">
                    <p style="margin:0 0 12px;font-size:12px;line-height:1.6;color:#526b50;">本次邮箱验证码</p>
                    <p style="margin:0;font-family:Consolas,'Courier New',monospace;font-size:34px;font-weight:bold;letter-spacing:5px;line-height:1.5;color:#526b50;white-space:nowrap;">%s</p>
                    <p style="margin:12px 0 0;font-size:12px;line-height:1.8;color:#71866d;">5 分钟内有效 · 仅可使用一次</p>
                  </td></tr>
                </table>
                <p style="margin:20px 0 0;font-size:14px;line-height:1.9;color:#788271;">请返回发起请求的页面填写验证码，完成邮箱验证。请勿将验证码转发或告知他人。</p>
                """.formatted(escape(code));
        return new Body(plain, render(Map.of(
                // 收件箱预览不直接展示验证码。
                "preheader", "你正在验证 QQ 邮箱，请在 5 分钟内完成验证。",
                "label", "邮箱验证", "heading", "让温暖的消息，找到你",
                "intro", "感谢使用拾光。完成邮箱验证，让成长中的小提醒准时与你相遇。",
                "body", body, "footer", "如果不是你本人操作，请忽略此邮件。")));
    }

    private String render(Map<String, String> slots) {
        // 只替换原模板中的占位符一次，用户输入中即使含 {{body}} 也不会被再次解析。
        return SLOT.matcher(layout).replaceAll(match -> Matcher.quoteReplacement(slots.get(match.group(1))));
    }

    private static String escape(String text) {
        return (text == null ? "" : text).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String lines(String text) {
        return escape(text).replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>");
    }
}
