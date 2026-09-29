# 拾光：当前服务器的 Kubernetes / Nacos 部署

本目录针对服务器上的 kind 集群 `learning`。使用用户已上传的 JAR，不重新编译 Java 项目。镜像、网段和端口是该服务器的实际配置，迁移到其他集群前需要调整。

2026-09-29 15:49（北京时间）下载功能版本更新完成：Pod `shiguang-68d8b67c46-lwvvt` 两个容器就绪，重启次数均为0，Deployment 可用副本为1。公网首页返回 HTTP 200，服务器首页、`growth.js` 和 `growth.css` 与新 JAR 内容一致。应用日志确认 MySQL 连接池已启动、Nacos 配置加载成功、`mailConfigured=true`，提醒扫描的数据库／应用时间一致。使用用户已编译上传的 JAR 打包镜像，未重新编译 Java、未执行自动化业务测试、未用登录账号下载私人媒体或发送测试邮件。

## 部署位置

| 项目 | 值 |
| --- | --- |
| 上传的 JAR | `/opt/apps/shiguang-media/springai-demo-0.0.1-SNAPSHOT.jar` |
| 部署文件 | `/opt/apps/shiguang-media/deploy` |
| Kubernetes namespace / Deployment / Service | `shiguang` |
| 应用镜像 | `local/shiguang:df54e2b1ba22` |
| JAR SHA-256 | `df54e2b1ba2269a4a637abef3925839f585b1a65757d88841c855358c946f260` |
| Nacos namespace / group / dataId | `public` / `SHIGUANG` / `springai-demo.yaml` |
| Nacos 配置地址（Pod 使用） | `http://192.168.32.1:8848/nacos` |
| MySQL | `192.168.32.1:3306/growth` |
| MinIO API / bucket | `http://192.168.32.1:9000` / `shiguang-media` |
| 网站入口 | 宿主机 `30082` → kind 节点 `192.168.32.2:30082` → Nginx `8081` → Java `8080` |

`resources.yaml` 固定使用本机已导入的镜像（`imagePullPolicy: Never`）。单节点学习服务器采用单副本、`Recreate` 更新策略，避免更新时两个 JVM 同时占用内存。Java 堆上限 256 MiB，容器内存上限 600 MiB；Nginx 上限 48 MiB。

初始化镜像 `local/shiguang-init:20260929` 复用了服务器已缓存的 Nacos 3.2.4 镜像中的 Python，只执行配置拉取脚本，不启动 Nacos。入口镜像 `local/shiguang-nginx:20260929` 来自已拉取的 `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/nginx:1.28-alpine`。三份镜像均已导入当前 kind 节点。

## 配置如何生效

2026-09-29 15:49 更新记录：确认上传 JAR 包含下载 Controller、文件命名实现及前端下载入口后，生成并导入 `local/shiguang:df54e2b1ba22`，Deployment 已切换，服务器及本项目 manifest 同步更新。本次没有修改数据库、Nacos 或 Secret。回退资料在 `/opt/apps/shiguang-media/deploy/releases/20260929-074319/`（UTC），包含 `deployment.before.json`、`resources.before.yaml`、新 JAR 镜像构建目录、`release.json` 和更新后记录。上一个镜像 `local/shiguang:8903e89275fe` 已保留；如需回退，执行 `kubectl -n shiguang set image deployment/shiguang app=local/shiguang:8903e89275fe`，等待就绪后同步还原 manifest 中的镜像标签。

2026-09-29 14:43 更新记录：新 JAR 包含提醒 epoch 时钟修复及 HTML 邮件模板。部署前查询确认邮箱／提醒／账户注销所需表和字段已存在，本次未执行数据库迁移。Nacos 原配置缺少 `spring.mail`，已增量补入 QQ SMTP 465/SSL 配置，密码使用 `${QQ_SMTP_AUTH_CODE:}`；现有 `shiguang-runtime` Secret 已增加该键并保留其余键。发布后回读确认 Nacos 配置一致。服务器及项目的 `resources.yaml` 已同步新镜像标签。

本次备份位于 `/opt/apps/shiguang-media/deploy/releases/20260929-063658/`（目录名为 UTC 时间），包含更新前 Deployment、运行时 Secret、Nacos 配置、manifest，以及新 JAR 的独立镜像构建目录和版本摘要。Secret 备份为仅 root 可读文件，请勿公开。旧镜像 `local/shiguang:6fc743494302` 保留，可通过 `kubectl -n shiguang set image deployment/shiguang app=local/shiguang:6fc743494302` 回退应用；若需回退配置，需另行恢复对应 Nacos／Secret 备份。仅回退 Deployment 不会还原这些配置。

2026-09-29 更新记录：已将用户上传的新 JAR 制作为 `local/shiguang:6fc743494302` 并切换 Deployment。该版本包含公网 HTTP 下 UUID 生成兼容修复。公网首页引用 `assistant.js?v=20260929-http-uuid`，服务器实际返回的首页和助手脚本与新 JAR 内容一致。更新前部署与回退信息保存在服务器 `/opt/apps/shiguang-media/deploy/releases/20260929-023655/`，旧镜像仍保留。未调用千问进行业务验证。

当前 JAR 没有 Nacos SDK。`nacos-config` 初始化容器登录 Nacos、读取配置并校验摘要，写入共享目录 `/runtime/application.yaml`。Java 通过 `SPRING_CONFIG_LOCATION=file:/runtime/application.yaml` 加载它。读取失败会阻止启动，不回退到 JAR 内的旧地址。

Nacos 中保存普通配置和 `${环境变量名}` 引用；实际数据库密码、MinIO 密钥、模型 API Key 存在 `shiguang-runtime` Secret。Nacos 登录信息单独放在 `shiguang-nacos-auth` Secret，仅初始化容器使用。不要把密码填回本目录文件。

修改 Nacos 配置并发布后，在服务器执行：

```bash
kubectl -n shiguang rollout restart deployment/shiguang
kubectl -n shiguang rollout status deployment/shiguang --timeout=180s
```

此方案不支持配置热更新，也不向 Nacos 注册服务。Nacos 配置、Secret、挂载的入口配置发生变化后，都需要重启 Pod。当前会话及 AI 对话上下文在内存中，重启后需要重新登录；MySQL 和 MinIO 数据不受影响。

`prepare_config.py` 是首次导入脚本：从上传的 JAR 提取现有配置，将密钥移入 Secret，再发布到 Nacos。它遇到已有同名 Nacos 配置时会停止，避免覆盖后续修改；不要把它当作日常同步脚本。`application.nacos.yaml` 是服务器上不含明文密钥的初次发布副本，日常配置以 Nacos 为准。

## 查看运行状态

```bash
kubectl -n shiguang get pods,svc
kubectl -n shiguang logs deployment/shiguang -c nacos-config
kubectl -n shiguang logs deployment/shiguang -c app --tail=100
kubectl -n shiguang logs deployment/shiguang -c gateway --tail=50
systemctl status shiguang-web.socket shiguang-web.service
```

入口代理禁止访问未鉴权的 `/api/ai` 调试接口及 `/ai-chat.html`，家庭 AI 助手仍通过 `/api/growth` 使用。代理关闭响应缓冲以支持流式对话，上传请求上限为 201 MiB。业务层对照片、视频和家庭空间的限制继续由现有代码执行。

## 更新 JAR

邮箱与待办提醒版本还需先执行 `src/main/resources/growth/upgrade-email-reminders.sql`，将 [邮件配置片段](mail-nacos-snippet.yaml) 合并进 Nacos，并在现有 `shiguang-runtime` Secret 中增加 `QQ_SMTP_AUTH_CODE`（保留其他键）。当前 `SPRING_CONFIG_LOCATION` 只加载外部配置，修改 JAR 内 YAML 不会自动补齐线上 SMTP 配置。详细步骤见 [邮箱与提醒说明](../../docs/QQ邮箱验证码与待办提醒.md)。

账户注销版本需先执行 `src/main/resources/growth/upgrade-account-deletion.sql`，再部署新 JAR。核对 Nacos 的 `spring.data.redis` 地址和密码，并建议设置 `connect-timeout: 1s`、`timeout: 1s`。Redis 用于多实例清理任务租约，恢复期限保存在 MySQL；升级及验收细节见 [账户注销说明](../../docs/account-deletion.md)。

先备份旧 JAR，再上传新包。使用新镜像标签，避免覆盖旧版本：

```bash
cd /opt/apps/shiguang-media
docker build --pull=false -f deploy/Dockerfile -t local/shiguang:新版本号 .
docker save local/shiguang:新版本号 | docker exec -i learning-control-plane ctr --namespace=k8s.io images import --platform linux/amd64 -
kubectl -n shiguang set image deployment/shiguang app=local/shiguang:新版本号
kubectl -n shiguang rollout status deployment/shiguang --timeout=180s
```

同时更新 `resources.yaml` 的镜像标签，避免以后重新 apply 时回到旧镜像。本机 Docker 的部分镜像为多平台清单，导入当前节点时明确指定 `linux/amd64`，避免缺少其他平台 blob 导致导入失败。

应用版本回退可使用 `kubectl -n shiguang rollout undo deployment/shiguang`；Nacos 配置和 Secret 的修改不包含在 Deployment 历史中，需要分别恢复。

## 访问条件

服务器本机入口为 `http://127.0.0.1:30082/`。公网通过 `http://160.202.231.154:30082/` 访问还取决于云平台端口映射与安全组。宿主机的 `shiguang-web.socket` / `.service` 负责转发到 kind，不改动其他已有服务的端口。

如果公网尚未放行，可在自己的电脑终端执行以下命令，保留窗口后访问 `http://127.0.0.1:18082/`：

```powershell
ssh -N -L 18082:127.0.0.1:30082 -p 49668 root@160.202.231.154
```

当前入口为 HTTP，未配置域名和 TLS。数据库已有业务表，部署没有重新初始化数据库。旧版本曾写在本机磁盘的媒体文件不会自动迁移到服务器；当前 MinIO 文件继续使用现有 bucket。
