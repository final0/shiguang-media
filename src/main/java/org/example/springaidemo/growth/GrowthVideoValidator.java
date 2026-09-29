package org.example.springaidemo.growth;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

/** 检查 MP4 容器边界和视频轨道，不根据扩展名或客户端 MIME 信任文件。
 * 不解码视频，不判断编码器是否受浏览器支持；播放失败时需转为 H.264/AAC MP4。
 */
final class GrowthVideoValidator {
    private static final Set<String> BRANDS = Set.of("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "MSNV", "dash");
    private GrowthVideoValidator() { }

    static void validate(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Scan scan = new Scan(file);
            long end = file.length();
            boolean format = false, movie = false, data = false, video = false;
            for (long offset = 0; offset < end;) {
                Box box = scan.box(offset, end);
                switch (box.type()) {
                    case "ftyp" -> {
                        if (format || box.end() - box.payload() < 8 || box.end() - box.payload() > 4096
                                || (box.end() - box.payload()) % 4 != 0) throw invalid();
                        file.seek(box.payload());
                        boolean supported = BRANDS.contains(scan.type());
                        file.skipBytes(4); // minor_version
                        while (file.getFilePointer() < box.end()) supported |= BRANDS.contains(scan.type());
                        if (!supported) throw invalid();
                        format = true;
                    }
                    case "moov" -> { movie = true; video |= scan.videoTrack(box, 0); }
                    case "mdat" -> data |= box.end() > box.payload();
                    default -> { /* free/skip/moof 等容器块只校验边界。 */ }
                }
                offset = box.end();
            }
            if (!format || !movie || !data || !video) throw invalid();
        }
    }
    private static GrowthException invalid() {
        return GrowthException.bad("请选择包含视频轨道的完整 MP4 文件；文件可能损坏或格式不受支持");
    }
    private record Box(String type, long payload, long end) { }
    private static final class Scan {
        private final RandomAccessFile file;
        private int boxes;
        Scan(RandomAccessFile file) { this.file = file; }
        String type() throws IOException {
            byte[] bytes = new byte[4]; file.readFully(bytes);
            return new String(bytes, StandardCharsets.US_ASCII);
        }
        Box box(long offset, long parentEnd) throws IOException {
            if (++boxes > 100_000 || parentEnd - offset < 8) throw invalid();
            file.seek(offset);
            long size = Integer.toUnsignedLong(file.readInt());
            String type = type();
            int header = 8;
            if (size == 1) {
                if (parentEnd - offset < 16) throw invalid();
                size = file.readLong(); header = 16;
            } else if (size == 0) size = parentEnd - offset;
            if (size < header || size > parentEnd - offset) throw invalid();
            return new Box(type, offset + header, offset + size);
        }
        boolean videoTrack(Box parent, int level) throws IOException {
            boolean found = false;
            for (long offset = parent.payload(); offset < parent.end();) {
                Box child = box(offset, parent.end());
                if ((level == 0 && child.type().equals("trak")) || (level == 1 && child.type().equals("mdia"))) {
                    found |= videoTrack(child, level + 1);
                } else if (level == 2 && child.type().equals("hdlr")) {
                    if (child.end() - child.payload() < 12) throw invalid();
                    file.seek(child.payload() + 8); // version/flags + pre_defined，接着是 handler_type。
                    found |= type().equals("vide");
                }
                offset = child.end();
            }
            return found;
        }
    }
}
