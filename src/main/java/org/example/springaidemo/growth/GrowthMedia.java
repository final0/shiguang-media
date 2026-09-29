package org.example.springaidemo.growth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static org.example.springaidemo.growth.GrowthAuth.Actor;

@Service
public class GrowthMedia {
    private static final Logger log = LoggerFactory.getLogger(GrowthMedia.class);
    private final GrowthStore db;
    private final GrowthService service;
    private final GrowthObjectStorage storage;
    private final GrowthMediaCleanup cleanup;
    private final TransactionTemplate transaction;
    public GrowthMedia(GrowthStore db, GrowthService service, GrowthObjectStorage storage,
                       GrowthMediaCleanup cleanup, PlatformTransactionManager manager) {
        this.db = db; this.service = service; this.storage = storage; this.cleanup = cleanup;
        this.transaction = new TransactionTemplate(manager);
    }

    private record PreparedMedia(Path file, String key, String contentType, long size) { }

    public void upload(Actor a, long recordId, List<MultipartFile> files) {
        service.canEdit(a, service.record(a, recordId, false));
        if (files == null || files.isEmpty() || files.size() > 6) throw GrowthException.bad("每次请选择 1–6 个 JPG、PNG 或 MP4 文件");
        long requestBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (requestBytes > 200L * 1024 * 1024) throw GrowthException.bad("本次照片和视频总大小不能超过 200 MB");
        List<Path> temporary = new ArrayList<>();
        List<PreparedMedia> prepared = new ArrayList<>();
        try {
            // 本地文件仅用于校验容器 / 图片重编码，结束后删除，不作为正式存储。
            for (MultipartFile file : files) {
                String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
                if (name.endsWith(".mp4") || "video/mp4".equalsIgnoreCase(file.getContentType())) {
                    if (file.isEmpty() || file.getSize() > 100L * 1024 * 1024) throw GrowthException.bad("每个 MP4 视频大小须在 0–100 MB 之间");
                    Path target = Files.createTempFile("growth-media-", ".mp4"); temporary.add(target);
                    // 流式复制，不用 getBytes() 把大视频整体读入 Java 堆。
                    try (var stream = file.getInputStream()) { Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING); }
                    GrowthVideoValidator.validate(target);
                    prepared.add(new PreparedMedia(target, storage.newKey(a.familyId(), "mp4"), "video/mp4", Files.size(target)));
                    continue;
                }
                if (file.isEmpty() || file.getSize() > 8 * 1024 * 1024) throw GrowthException.bad("每张图片大小须在 0–8 MB 之间");
                // 读取实际图片格式，拒绝伪装的 HTML/SVG；重编码去掉 EXIF 等原图元数据。
                try (var input = file.getInputStream(); var stream = ImageIO.createImageInputStream(input)) {
                    if (stream == null) throw GrowthException.bad("无法读取图片");
                    var readers = ImageIO.getImageReaders(stream);
                    if (!readers.hasNext()) throw GrowthException.bad("仅支持有效的 JPG、PNG 图片和 MP4 视频");
                    var reader = readers.next();
                    try {
                        reader.setInput(stream);
                        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                        if (!Set.of("jpeg", "jpg", "png").contains(format)) throw GrowthException.bad("仅支持 JPG、PNG 图片和 MP4 视频");
                        if ((long) reader.getWidth(0) * reader.getHeight(0) > 25_000_000) throw GrowthException.bad("图片像素过大，请缩小到 2500 万像素以内");
                        String extension = format.equals("png") ? "png" : "jpg";
                        Path target = Files.createTempFile("growth-media-", "." + extension); temporary.add(target);
                        var bitmap = reader.read(0);
                        try {
                            if (!ImageIO.write(bitmap, extension, target.toFile())) throw GrowthException.bad("图片转换失败");
                        } finally { bitmap.flush(); }
                        prepared.add(new PreparedMedia(target, storage.newKey(a.familyId(), extension),
                                extension.equals("png") ? "image/png" : "image/jpeg", Files.size(target)));
                    } finally { reader.dispose(); }
                }
            }
            List<String> keys = prepared.stream().map(PreparedMedia::key).toList();
            cleanup.stage(keys);
            try {
                transaction.executeWithoutResult(status -> {
                    service.lockFamily(a);
                    service.canEdit(a, service.record(a, recordId, false));
                    if (db.number("SELECT COUNT(*) FROM media_file WHERE record_id=? AND family_id=?", recordId, a.familyId()) + files.size() > 12) throw GrowthException.bad("每条记录最多保存 12 个照片或视频文件");
                    long quota = ((Number) db.one("SELECT storage_quota FROM family WHERE id=?", a.familyId()).get("storageQuota")).longValue();
                    long used = db.number("SELECT COALESCE(SUM(size_bytes),0) FROM media_file WHERE family_id=? AND is_deleted=FALSE", a.familyId());
                    if (used + prepared.stream().mapToLong(PreparedMedia::size).sum() > quota) throw GrowthException.bad("家庭空间容量不足，请先清理照片或视频");
                    for (String key : keys) {
                        if (db.list("SELECT oss_key FROM media_cleanup WHERE oss_key=? FOR UPDATE", key).isEmpty()) {
                            throw GrowthException.bad("上传准备已过期，请重新选择文件");
                        }
                    }
                    storage.ensureBucket();
                    for (PreparedMedia item : prepared) {
                        storage.put(item.key(), item.file(), item.contentType());
                        db.update("INSERT INTO media_file(family_id,record_id,oss_key,mime_type,size_bytes) VALUES(?,?,?,?,?)",
                                a.familyId(), recordId, item.key(), item.contentType(), item.size());
                        // 元数据与清理意图一起提交；回滚时意图保留，重启后仍可清理孤立对象。
                        db.update("DELETE FROM media_cleanup WHERE oss_key=?", item.key());
                    }
                    db.log(a, "upload", "为记录 #" + recordId + " 添加 " + files.size() + " 个照片或视频文件");
                });
            } catch (RuntimeException e) { cleanup.retrySoon(keys); throw e; }
        } catch (IOException e) {
            log.warn("Media preparation failed", e);
            throw new GrowthException(HttpStatus.INTERNAL_SERVER_ERROR, "媒体处理失败，请检查文件是否完整及服务器临时目录空间");
        } finally {
            for (Path file : temporary) {
                try { Files.deleteIfExists(file); }
                catch (IOException e) { log.warn("Cannot remove media temporary file {}", file.getFileName()); }
            }
        }
    }

    public void read(Actor a, long id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        var media = db.one("SELECT m.* FROM media_file m JOIN grow_record r ON r.id=m.record_id JOIN child c ON c.id=r.child_id AND c.family_id=r.family_id WHERE m.id=? AND m.family_id=? AND r.family_id=? AND m.is_deleted=FALSE AND r.is_deleted=FALSE AND c.is_active=TRUE", id, a.familyId(), a.familyId());
        String key = (String) media.get("ossKey");
        long size = storage.size(key);
        long start = 0, end = size - 1;
        boolean partial = false;
        String rangeHeader = request.getHeader("Range");
        // 未提供 ETag/Last-Modified，带 If-Range 时返回完整实体。多范围请求允许退回 200。
        if ("GET".equals(request.getMethod()) && rangeHeader != null && request.getHeader("If-Range") == null) {
            try {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                if (ranges.size() == 1) {
                    start = ranges.get(0).getRangeStart(size); end = ranges.get(0).getRangeEnd(size);
                    if (start < 0 || start >= size || end < start) throw new IllegalArgumentException("Unsatisfiable range");
                    partial = true;
                }
            } catch (IllegalArgumentException e) {
                response.setStatus(416); response.setHeader("Content-Range", "bytes */" + size);
                response.setHeader("Accept-Ranges", "bytes"); response.setContentLengthLong(0); return;
            }
        }
        long length = size == 0 ? 0 : end - start + 1;
        // 先打开对象再设置成功响应头，MinIO 故障时仍能返回正常 JSON 错误。
        try (var input = length == 0 || "HEAD".equals(request.getMethod()) ? null : storage.open(key, start, length)) {
            response.setStatus(partial ? 206 : 200);
            response.setHeader("Accept-Ranges", "bytes"); response.setHeader("Cache-Control", "no-store");
            response.setHeader("Content-Disposition", "inline");
            response.setContentType((String) media.get("mimeType")); response.setContentLengthLong(length);
            if (partial) response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
            if (input == null) return;
            var output = response.getOutputStream();
            byte[] buffer = new byte[64 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) throw new IOException("Media stream ended before Content-Length");
                output.write(buffer, 0, count); remaining -= count;
            }
        }
    }
    /** 访客仅收到缩小且烧录水印的 JPEG，不开放家庭原图端点。 */
    ResponseEntity<Resource> preview(Map<String,Object> media) {
        if (!Set.of("image/jpeg", "image/png").contains(media.get("mimeType"))) {
            throw new GrowthException(HttpStatus.FORBIDDEN, "视频暂时仅对家庭成员开放播放");
        }
        String key = (String) media.get("ossKey");
        try (var input = storage.open(key, 0, storage.size(key))) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) throw GrowthException.missing();
            double scale = Math.min(1.0, 1280.0 / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) (source.getWidth() * scale));
            int height = Math.max(1, (int) (source.getHeight() * scale));
            BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = preview.createGraphics();
            try {
                graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, width, height, null);
                int fontSize = Math.max(13, width / 36);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
                graphics.setColor(new Color(255, 255, 255, 145));
                graphics.rotate(-Math.PI / 9, width / 2.0, height / 2.0);
                for (int y = -height; y < height * 2; y += fontSize * 5) {
                    for (int x = -width; x < width * 2; x += fontSize * 19) graphics.drawString("SHIGUANG · FAMILY MEMORY", x, y);
                }
            } finally { graphics.dispose(); source.flush(); }
            byte[] bytes;
            try (var output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(preview, "jpg", output)) throw new IOException("JPEG encoder unavailable");
                bytes = output.toByteArray();
            } finally { preview.flush(); }
            return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("Cache-Control", "no-store")
                    .header("Content-Disposition", "inline").body(new ByteArrayResource(bytes));
        } catch (IOException e) { throw new GrowthException(HttpStatus.INTERNAL_SERVER_ERROR, "暂时无法生成分享预览"); }
    }
    @Transactional
    public void purgeChild(Actor a, long id) {
        a.requireAdmin(); service.lockFamily(a);
        // 与恢复、上传共用家庭行锁；只能永久清理本家庭仍处于档案回收站中的孩子。
        var child = db.one("SELECT name FROM child WHERE id=? AND family_id=? AND is_active=FALSE FOR UPDATE", id, a.familyId());
        GrowthReminderLifecycle.purge(db,"family_id=? AND child_id=?",a.familyId(),id);
        var keys = db.list("SELECT DISTINCT m.oss_key FROM media_file m JOIN grow_record r ON r.id=m.record_id AND r.family_id=m.family_id WHERE r.family_id=? AND r.child_id=?", a.familyId(), id);
        for (var item : keys) {
            String key = (String) item.get("ossKey");
            if (db.list("SELECT oss_key FROM media_cleanup WHERE oss_key=? FOR UPDATE", key).isEmpty())
                db.update("INSERT INTO media_cleanup(oss_key) VALUES(?)", key);
            else db.update("UPDATE media_cleanup SET create_time=CURRENT_TIMESTAMP WHERE oss_key=?", key);
        }
        // 不过滤记录的 is_deleted：正常记录和原先单独移入记录回收站的内容都要清理。
        String records = "SELECT id FROM grow_record WHERE family_id=? AND child_id=?";
        db.update("DELETE FROM share_record WHERE family_id=? AND record_id IN (" + records + ")", a.familyId(), a.familyId(), id);
        db.update("DELETE FROM record_comment WHERE family_id=? AND record_id IN (" + records + ")", a.familyId(), a.familyId(), id);
        db.update("DELETE FROM record_tag WHERE record_id IN (" + records + ")", a.familyId(), id);
        db.update("DELETE FROM media_file WHERE family_id=? AND record_id IN (" + records + ")", a.familyId(), a.familyId(), id);
        db.update("DELETE FROM notice WHERE family_id=? AND link_id IN (" + records + ")", a.familyId(), a.familyId(), id);
        db.update("DELETE FROM ai_assistant_request WHERE family_id=? AND child_id=?", a.familyId(), id);
        db.update("DELETE FROM grow_data WHERE family_id=? AND child_id=?", a.familyId(), id);
        db.update("DELETE FROM vaccine WHERE family_id=? AND child_id=?", a.familyId(), id);
        db.update("DELETE FROM grow_record WHERE family_id=? AND child_id=?", a.familyId(), id);
        db.update("DELETE FROM child WHERE id=? AND family_id=? AND is_active=FALSE", id, a.familyId());
        // 家庭共享标签字典保留，只删除该孩子记录的标签关联；其他孩子的资料不受影响。
        db.log(a, "delete", "彻底删除孩子档案 #" + id + "（" + child.get("name") + "）及关联资料，照片和视频已加入清理队列");
    }
    @Transactional
    public void purge(Actor a, long id) {
        a.requireAdmin(); service.lockFamily(a); service.record(a, id, true);
        db.update("INSERT INTO media_cleanup(oss_key) SELECT oss_key FROM media_file WHERE record_id=? AND family_id=?", id, a.familyId());
        db.update("DELETE FROM share_record WHERE record_id=? AND family_id=?", id, a.familyId());
        db.update("DELETE FROM record_comment WHERE record_id=? AND family_id=?", id, a.familyId());
        db.update("DELETE FROM record_tag WHERE record_id=?", id);
        db.update("DELETE FROM media_file WHERE record_id=? AND family_id=?", id, a.familyId());
        db.update("DELETE FROM notice WHERE link_id=? AND family_id=?", id, a.familyId());
        db.update("DELETE FROM grow_record WHERE id=? AND family_id=?", id, a.familyId());
        db.log(a, "delete", "彻底删除记录 #" + id + "，文件已加入清理队列");
    }
}
