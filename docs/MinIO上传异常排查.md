# MinIO 上传异常定位

本次依据用户提供的头像上传日志：`上传文件 (SocketException)`。它表明套接字通信发生异常，但没有具体消息和堆栈，不能确认是连接重置、拒绝连接、超时还是本机网络资源问题，也不能据此判断是账号权限或磁盘容量。

源码中的头像路径为 `GrowthController → GrowthProfile.upload → GrowthObjectStorage.put → MinioClient.putObject`。头像在服务端实际解码、裁切并重编码为512×512 JPEG，上传文件并非原图。当前配置指向 MinIO API 9000 端口。运行时环境变量覆盖、服务器运行状态和故障根因尚未验证。

原代码仅打印异常类名并返回统一错误提示；本次改为：

- `MinIO upload failed`：记录 uploadId、协议/主机/端口、bucket、处理后字节数、contentType、耗时。
- `Object storage operation failed`：使用同一 diagnosticId，记录异常分类和保留原始代码位置的脱敏堆栈。
- 网络异常与 MinIO 权限/容量错误分别提示。未知情况保留异常类型，不能因提示变化认为故障已经修复。
- 不打印用户原文件名、文件内容、存储密钥、签名请求头或 SDK 原始 HTTP 错误对象；异常消息按已知网络特征归类。

重新编译启动后，上传一次头像，保留同一诊断编号的两条失败日志及紧随其后的堆栈。重点查看：

| reason | 后续排查方向 |
| --- | --- |
| CONNECTION_RESET / CONNECTION_ABORTED / BROKEN_PIPE / UNEXPECTED_EOF | 对照同一时间的 MinIO 服务日志、代理日志、容器重启状态和网络连接；此分类本身不确定哪一方断开 |
| CONNECTION_REFUSED | 运行时 endpoint、MinIO进程、API监听端口、Docker端口映射、防火墙 |
| DNS_FAILED / NETWORK_UNREACHABLE | DNS、网络路由或代理设置 |
| TIMEOUT | 对照 elapsedMs 和堆栈区分连接/传输阶段，检查网络速度与服务端负载 |
| SOCKET_PERMISSION_DENIED / SOCKET_RESOURCE_EXHAUSTED | 本机防火墙、安全软件或网络资源 |
| AccessDenied / InvalidAccessKeyId / SignatureDoesNotMatch | MinIO返回的权限、凭证或签名错误 |
| XMinioStorageFull / StorageFull | MinIO服务端磁盘空间 |

上传沿用 SDK 官方的已知大小输入流用法，未改成一次性读入整张图片，未修改凭证、代理或超时，也未添加整文件重复上传。参考：[MinIO Java 8.6.0 putObject 示例和实现](https://github.com/minio/minio-java/blob/8.6.0/api/src/main/java/io/minio/MinioAsyncClient.java)。

本次仅完成源码修改和阅读审查，未编译、未运行测试、未连接 MinIO 或执行真实上传。需新的失败日志或服务端证据才能继续确认根因。
