package org.example.springaidemo.growth;

import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.example.springaidemo.growth.GrowthAuth.Actor;

@Service
public class GrowthSharing {
    private final GrowthStore db;
    private final GrowthService service;
    private final GrowthMedia media;
    public GrowthSharing(GrowthStore db, GrowthService service, GrowthMedia media) { this.db = db; this.service = service; this.media = media; }
    @Transactional
    public Map<String,Object> create(Actor a, long id, GrowthDtos.Share input) {
        service.lockFamily(a);
        service.canEdit(a, service.record(a, id, false));
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        var expiry = LocalDateTime.now().plusHours(input.hours());
        db.update("INSERT INTO share_record(family_id,record_id,creator_id,share_token,expire_time,max_access) VALUES(?,?,?,?,?,?)", a.familyId(), id, a.id(), token, expiry, input.maxAccess());
        db.log(a, "share", "创建记录 #" + id + " 的限时分享");
        return Map.of("url", "/share.html?token=" + token, "expiresAt", expiry.toString());
    }
    @Transactional
    public void revoke(Actor a, long id) {
        a.requireAdmin(); service.lockFamily(a);
        if (db.update("UPDATE share_record SET status=3 WHERE id=? AND family_id=?", id, a.familyId()) == 0) throw GrowthException.missing();
        db.log(a, "share", "撤销分享 #" + id);
    }
    private Map<String,Object> valid(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw GrowthException.missing();
        var rows = db.list("SELECT s.* FROM share_record s JOIN grow_record r ON r.id=s.record_id AND r.family_id=s.family_id JOIN child c ON c.id=r.child_id AND c.family_id=r.family_id WHERE s.share_token=? AND s.status=1 AND s.expire_time>CURRENT_TIMESTAMP AND r.is_deleted=FALSE AND c.is_active=TRUE", token);
        if (rows.isEmpty()) throw GrowthException.bad("这段分享已到期、被撤销或内容已删除");
        var share = rows.get(0);
        if (db.number("SELECT COUNT(*) FROM account_deletion WHERE user_id=? OR (family_id=? AND scope='FAMILY')", share.get("creatorId"), share.get("familyId")) > 0)
            throw GrowthException.bad("账号或家庭正在注销，这段分享已停用");
        return rows.get(0);
    }
    @Transactional
    public Map<String,Object> view(String token, HttpSession session) {
        var share = valid(token);
        // 条件更新原子扣减剩余访问次数，多个访客同时打开也不能突破上限。
        if (db.update("UPDATE share_record SET access_count=access_count+1 WHERE id=? AND status=1 AND access_count<max_access AND expire_time>CURRENT_TIMESTAMP", share.get("id")) == 0) throw GrowthException.bad("分享已达到访问上限");
        session.setAttribute("growthShare:" + token, share.get("id"));
        var entry = db.one("SELECT title,content,record_time,record_type,illustration FROM grow_record WHERE id=? AND family_id=? AND is_deleted=FALSE", share.get("recordId"), share.get("familyId"));
        // 尚未接入视频转码/水印：访客分享仅发布图片预览，不泄露未处理的视频地址。
        entry.put("media", db.list("SELECT id FROM media_file WHERE record_id=? AND family_id=? AND is_deleted=FALSE AND mime_type IN ('image/jpeg','image/png') ORDER BY id", share.get("recordId"), share.get("familyId")));
        entry.put("hiddenVideoCount", db.number("SELECT COUNT(*) FROM media_file WHERE record_id=? AND family_id=? AND is_deleted=FALSE AND mime_type='video/mp4'", share.get("recordId"), share.get("familyId")));
        entry.put("expiresAt", share.get("expireTime"));
        return entry;
    }
    public ResponseEntity<Resource> image(String token, long id, HttpSession session) {
        var share = valid(token);
        if (!Objects.equals(session.getAttribute("growthShare:" + token), share.get("id"))) throw GrowthException.forbidden();
        return media.preview(db.one("SELECT * FROM media_file WHERE id=? AND record_id=? AND family_id=? AND is_deleted=FALSE", id, share.get("recordId"), share.get("familyId")));
    }
}
