package org.example.springaidemo.growth;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** 新文件只写 MinIO；旧 UUID 文件名只用于兼容读取与清理，不再写入本地目录。 */
@Component
public class GrowthObjectStorage {
    private static final Logger log = LoggerFactory.getLogger(GrowthObjectStorage.class);
    private static final Pattern REMOTE_KEY = Pattern.compile("minio/([a-z0-9][a-z0-9-]{1,61}[a-z0-9])/families/([1-9][0-9]*)/([a-f0-9-]+\\.(jpg|png|mp4))");
    private final MinioClient client;
    private final OkHttpClient httpClient;
    private final String bucket;
    private final boolean autoCreateBucket;
    private final Path legacyRoot;
    private final String endpointLabel;

    public GrowthObjectStorage(@Value("${app.minio.endpoint}") String endpoint,
                               @Value("${app.minio.access-key}") String accessKey,
                               @Value("${app.minio.secret-key}") String secretKey,
                               @Value("${app.minio.bucket}") String bucket,
                               @Value("${app.minio.auto-create-bucket:true}") boolean autoCreateBucket,
                               @Value("${growth.media-directory:./data/growth-media}") String legacyDirectory) {
        if (!bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")) {
            throw new IllegalArgumentException("app.minio.bucket 须为 3–63 位小写字母、数字或连字符，首尾不能是连字符");
        }
        if (accessKey.isBlank() || secretKey.isBlank()) throw new IllegalArgumentException("MinIO 账号和密钥不能为空");
        this.bucket = bucket;
        // 诊断只记录协议、主机、端口，绝不记录地址中的用户信息或查询参数。
        URI address = URI.create(endpoint);
        this.endpointLabel = address.getScheme() + "://" + address.getHost() + (address.getPort() < 0 ? "" : ":" + address.getPort());
        this.autoCreateBucket = autoCreateBucket;
        this.legacyRoot = Path.of(legacyDirectory).toAbsolutePath().normalize();
        this.httpClient = new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS).build();
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).httpClient(httpClient).build();
    }

    @PreDestroy
    public void close() throws Exception {
        try { client.close(); }
        finally { httpClient.dispatcher().executorService().shutdown(); httpClient.connectionPool().evictAll(); }
    }

    public String newKey(long familyId, String extension) {
        // 数据库同时记录 bucket，后续更换默认 bucket 时仍能定位原来的文件。
        return "minio/" + bucket + "/families/" + familyId + "/" + UUID.randomUUID() + "." + extension;
    }

    private record ObjectLocation(String bucket, String object, long familyId) { }

    private ObjectLocation location(String key) {
        var match = REMOTE_KEY.matcher(key);
        if (!match.matches()) throw GrowthException.missing();
        try {
            return new ObjectLocation(match.group(1), "families/" + match.group(2) + "/" + match.group(3), Long.parseLong(match.group(2)));
        } catch (NumberFormatException e) { throw GrowthException.missing(); }
    }

    Long familyId(String key) { return key.startsWith("minio/") ? location(key).familyId() : null; }

    private Path legacyPath(String key) {
        if (!key.matches("[a-f0-9-]+\\.(jpg|png|mp4)")) throw GrowthException.missing();
        Path file = legacyRoot.resolve(key).normalize();
        if (!file.startsWith(legacyRoot)) throw GrowthException.forbidden();
        return file;
    }

    /** 首次上传才访问 MinIO，不把外部服务访问藏在 Bean 构造中。新 bucket 默认私有。 */
    public synchronized void ensureBucket() {
        try {
            if (client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) return;
            if (!autoCreateBucket) throw new GrowthException(HttpStatus.SERVICE_UNAVAILABLE, "MinIO 存储桶尚未创建，请联系管理员");
            try {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            } catch (ErrorResponseException e) {
                // 多个应用实例可能同时创建。仅忽略已属于当前账号的同名桶。
                if (!"BucketAlreadyOwnedByYou".equals(e.errorResponse().code())) throw e;
            }
        } catch (GrowthException e) { throw e; }
        catch (Exception e) { throw failure("初始化存储桶", e); }
    }

    public void put(String key, Path file, String contentType) {
        var target = location(key);
        String uploadId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        long bytes = -1;
        try (var input = Files.newInputStream(file)) {
            bytes = Files.size(file);
            client.putObject(PutObjectArgs.builder().bucket(target.bucket()).object(target.object())
                    .stream(input, bytes, -1).contentType(contentType).build());
            log.info("MinIO upload complete: uploadId={}, endpoint={}, bytes={}, elapsedMs={}",
                    uploadId, endpointLabel, bytes, (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            log.warn("MinIO upload failed: uploadId={}, endpoint={}, bucket={}, bytes={}, contentType={}, elapsedMs={}",
                    uploadId, endpointLabel, target.bucket(), bytes, contentType, (System.nanoTime() - started) / 1_000_000);
            throw failure("上传文件", e, uploadId);
        }
    }

    public long size(String key) {
        try {
            if (!key.startsWith("minio/")) {
                Path file = legacyPath(key);
                if (!Files.isRegularFile(file)) throw GrowthException.missing();
                return Files.size(file);
            }
            var target = location(key);
            return client.statObject(StatObjectArgs.builder().bucket(target.bucket()).object(target.object()).build()).size();
        } catch (GrowthException e) { throw e; }
        catch (Exception e) { throw readFailure(e); }
    }

    /** MinIO 只传输请求的字节范围，视频跳播不必先下载完整对象。调用者必须关闭流。 */
    public InputStream open(String key, long offset, long length) {
        try {
            if (!key.startsWith("minio/")) {
                var stream = Files.newInputStream(legacyPath(key));
                try { stream.skipNBytes(offset); return stream; }
                catch (IOException e) { stream.close(); throw e; }
            }
            var target = location(key);
            return client.getObject(GetObjectArgs.builder().bucket(target.bucket()).object(target.object())
                    .offset(offset).length(length).build());
        } catch (GrowthException e) { throw e; }
        catch (Exception e) { throw readFailure(e); }
    }

    public void delete(String key) {
        try {
            if (!key.startsWith("minio/")) { Files.deleteIfExists(legacyPath(key)); return; }
            var target = location(key);
            client.removeObject(RemoveObjectArgs.builder().bucket(target.bucket()).object(target.object()).build());
        } catch (GrowthException e) { throw e; }
        catch (ErrorResponseException e) {
            if (!java.util.Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket").contains(e.errorResponse().code())) {
                throw failure("清理文件", e);
            }
        }
        catch (Exception e) { throw failure("清理文件", e); }
    }

    private GrowthException readFailure(Exception e) {
        if (e instanceof java.nio.file.NoSuchFileException || (e instanceof ErrorResponseException error
                && java.util.Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket").contains(error.errorResponse().code()))) {
            return GrowthException.missing();
        }
        return failure("读取文件", e);
    }

    private GrowthException failure(String operation, Exception e) {
        return failure(operation, e, UUID.randomUUID().toString());
    }

    private GrowthException failure(String operation, Exception e, String diagnosticId) {
        String reason = reason(e);
        // SDK 的 ErrorResponseException.toString() 可能包含 HTTP 请求信息，不直接打印原异常。
        log.warn("Object storage operation failed: operation={}, diagnosticId={}, endpoint={}, reason={}",
                operation, diagnosticId, endpointLabel, reason, safeStack(e, 0));
        String message = switch (reason) {
            case "CONNECTION_RESET", "CONNECTION_ABORTED", "BROKEN_PIPE", "SOCKET_CLOSED", "SOCKET_ERROR", "UNEXPECTED_EOF" ->
                    "与 MinIO 的连接在传输中断开，请核对服务端同一时刻日志以及代理/网络连接";
            case "SOCKET_PERMISSION_DENIED" -> "本机网络连接被拒绝，请检查防火墙、安全软件或网络策略";
            case "SOCKET_RESOURCE_EXHAUSTED" -> "本机网络资源不足，请检查连接数与系统资源";
            case "CONNECTION_REFUSED" -> "无法连接 MinIO API 端口，请检查服务是否运行、端口映射及防火墙";
            case "NETWORK_UNREACHABLE" -> "无法到达 MinIO 服务器，请检查网络路由或代理";
            case "DNS_FAILED" -> "MinIO 域名解析失败，请检查存储地址与 DNS";
            case "TIMEOUT" -> "等待 MinIO 响应或传输数据超时，请检查网络速度及服务端负载";
            case "TLS_FAILED" -> "MinIO TLS 连接失败，请核对 HTTP/HTTPS 配置和证书";
            case "AccessDenied", "InvalidAccessKeyId", "SignatureDoesNotMatch" -> "MinIO 拒绝存储请求，请检查账号权限、密钥及签名配置";
            case "XMinioStorageFull", "StorageFull" -> "MinIO 存储空间不足，请检查服务端磁盘";
            default -> "MinIO 请求未完成，请根据诊断编号查看服务端日志";
        };
        return new GrowthException(HttpStatus.BAD_GATEWAY, operation + "失败：" + message + "（" + reason + "，诊断编号 " + diagnosticId + "）");
    }

    private String reason(Throwable failure) {
        String fallback = failure.getClass().getSimpleName();
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 16; depth++) {
            if (cause instanceof ErrorResponseException error) return error.errorResponse().code();
            if (cause instanceof javax.net.ssl.SSLException) return "TLS_FAILED";
            if (cause instanceof SocketTimeoutException || cause instanceof java.util.concurrent.TimeoutException) return "TIMEOUT";
            if (cause instanceof UnknownHostException) return "DNS_FAILED";
            if (cause instanceof NoRouteToHostException) return "NETWORK_UNREACHABLE";
            String text = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
            if (cause instanceof ConnectException) return text.contains("timed out") ? "TIMEOUT" : "CONNECTION_REFUSED";
            if (cause instanceof SocketException) {
                if (text.contains("reset")) return "CONNECTION_RESET";
                if (text.contains("aborted")) return "CONNECTION_ABORTED";
                if (text.contains("broken pipe")) return "BROKEN_PIPE";
                if (text.contains("closed")) return "SOCKET_CLOSED";
                if (text.contains("unreachable")) return "NETWORK_UNREACHABLE";
                if (text.contains("timed out")) return "TIMEOUT";
                if (text.contains("permission denied") || text.contains("access permissions")) return "SOCKET_PERMISSION_DENIED";
                if (text.contains("buffer space") || text.contains("too many open files")) return "SOCKET_RESOURCE_EXHAUSTED";
                return "SOCKET_ERROR";
            }
            if (cause instanceof java.io.EOFException) return "UNEXPECTED_EOF";
            if (cause instanceof java.io.InterruptedIOException && text.contains("timeout")) return "TIMEOUT";
            if (cause instanceof IOException && text.contains("unexpected end of stream")) fallback = "UNEXPECTED_EOF";
            if (cause.getCause() == cause) break;
            cause = cause.getCause();
        }
        return fallback;
    }

    private Exception safeStack(Throwable failure, int depth) {
        Exception safe = new Exception(failure.getClass().getName() + " [" + reason(failure) + "]");
        safe.setStackTrace(failure.getStackTrace());
        if (depth < 8 && failure.getCause() != null && failure.getCause() != failure) safe.initCause(safeStack(failure.getCause(), depth + 1));
        return safe;
    }
}
