package org.example.springaidemo.growth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.example.springaidemo.growth.GrowthAuth.Actor;

/** 账号修改只能作用于登录者；头像重编码后进入现有私有 MinIO 桶。 */
@Service
public class GrowthProfile {
    private static final Logger log = LoggerFactory.getLogger(GrowthProfile.class);
    private final GrowthStore db;
    private final GrowthObjectStorage storage;
    private final GrowthMediaCleanup cleanup;
    private final TransactionTemplate transaction;
    private final GrowthAccountDeletion deletion;
    public GrowthProfile(GrowthStore db, GrowthObjectStorage storage, GrowthMediaCleanup cleanup, PlatformTransactionManager manager, GrowthAccountDeletion deletion) {
        this.db = db; this.storage = storage; this.cleanup = cleanup; this.transaction = new TransactionTemplate(manager);
        this.deletion = deletion;
    }

    public Map<String,Object> get(Actor a) {
        var user = db.one("SELECT id,phone,nickname,avatar,create_time FROM app_user WHERE id=? AND is_deleted=FALSE", a.id());
        user.put("hasAvatar", user.remove("avatar") instanceof String key && !key.isBlank());
        boolean demo = ((String) user.get("phone")).startsWith("demo-");
        user.put("demo", demo);
        if (demo) user.put("phone", "");
        return user;
    }
    private Map<String,Object> lockUser(Actor a) {
        db.one("SELECT id FROM family WHERE id=? FOR UPDATE", a.familyId());
        deletion.guard(a.id(), a.familyId());
        return db.one("SELECT u.avatar FROM app_user u JOIN family_member m ON m.user_id=u.id WHERE u.id=? AND u.is_deleted=FALSE AND m.family_id=? FOR UPDATE", a.id(), a.familyId());
    }
    @Transactional
    public void update(Actor a, GrowthDtos.Profile input) {
        lockUser(a);
        db.update("UPDATE app_user SET nickname=? WHERE id=?", input.nickname().trim(), a.id());
        db.log(a, "account", "修改本人昵称");
    }
    private void queueOld(Object old) {
        if (old instanceof String key && !key.isBlank()) {
            db.update("INSERT INTO media_cleanup(oss_key,create_time) SELECT ?,CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM media_cleanup WHERE oss_key=?)", key, key);
        }
    }
    @Transactional
    public void removeAvatar(Actor a) {
        var user = lockUser(a);
        db.update("UPDATE app_user SET avatar=NULL WHERE id=?", a.id());
        queueOld(user.get("avatar"));
        db.log(a, "account", "恢复本人默认头像");
    }
    public void upload(Actor a, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 2L * 1024 * 1024) throw GrowthException.bad("头像请选择不超过 2 MB 的 JPG 或 PNG 图片");
        Path temporary = null;
        String key = storage.newKey(a.familyId(), "jpg");
        boolean staged = false;
        try {
            temporary = Files.createTempFile("growth-avatar-", ".jpg");
            prepare(file, temporary);
            cleanup.stage(List.of(key)); staged = true;
            storage.ensureBucket();
            storage.put(key, temporary, "image/jpeg");
            // 网络上传结束后才开启短事务；旧头像和失败上传都使用已有持久化清理队列。
            transaction.executeWithoutResult(status -> {
                var user = lockUser(a);
                if (db.list("SELECT oss_key FROM media_cleanup WHERE oss_key=? FOR UPDATE", key).isEmpty()) throw GrowthException.bad("头像上传准备已过期，请重试");
                db.update("UPDATE app_user SET avatar=? WHERE id=?", key, a.id());
                queueOld(user.get("avatar"));
                db.update("DELETE FROM media_cleanup WHERE oss_key=?", key);
                db.log(a, "account", "更新本人头像");
            });
        } catch (IOException e) {
            if (staged) cleanup.retrySoon(List.of(key));
            throw GrowthException.bad("图片处理失败，请选择完整的 JPG 或 PNG 图片");
        } catch (RuntimeException e) {
            if (staged) cleanup.retrySoon(List.of(key));
            throw e;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException e) { log.warn("Cannot remove avatar temporary file"); }
        }
    }
    private void prepare(MultipartFile file, Path output) throws IOException {
        try (var input = file.getInputStream(); var stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw GrowthException.bad("无法读取头像图片");
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw GrowthException.bad("仅支持有效的 JPG 或 PNG 图片");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                if (!Set.of("jpeg", "jpg", "png").contains(reader.getFormatName().toLowerCase(Locale.ROOT))) throw GrowthException.bad("仅支持 JPG 或 PNG 图片");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 8_000_000) throw GrowthException.bad("头像像素过大，请缩小到 800 万像素以内");
                BufferedImage original = reader.read(0), square = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
                var graphics = square.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 512, 512);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int side = Math.min(width, height), x = (width - side) / 2, y = (height - side) / 2;
                    graphics.drawImage(original, 0, 0, 512, 512, x, y, x + side, y + side, null);
                    if (!ImageIO.write(square, "jpg", output.toFile())) throw GrowthException.bad("头像转换失败");
                } finally { graphics.dispose(); original.flush(); square.flush(); }
            } finally { reader.dispose(); }
        }
    }
    public ResponseEntity<byte[]> avatar(Actor a, long userId) {
        var user = db.one("SELECT u.avatar FROM app_user u JOIN family_member m ON m.user_id=u.id WHERE u.id=? AND m.family_id=? AND u.is_deleted=FALSE", userId, a.familyId());
        if (!(user.get("avatar") instanceof String key) || key.isBlank()) throw GrowthException.missing();
        long size = storage.size(key);
        if (size < 1 || size > 2L * 1024 * 1024) throw GrowthException.bad("头像文件无效，请重新上传");
        try (var input = storage.open(key, 0, size)) {
            byte[] data = input.readNBytes((int) size);
            if (data.length != size) throw GrowthException.bad("头像读取不完整，请稍后重试");
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Cache-Control", "no-store").body(data);
        } catch (IOException e) { throw GrowthException.bad("暂时无法读取头像，请稍后重试"); }
    }
}
