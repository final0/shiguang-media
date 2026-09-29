# 拾光 · 儿童成长记录网站

儿童成长记录网站采用奶油白、鼠尾草绿和家庭相册式布局，业务数据保存至 MySQL，新增照片和视频保存至 MinIO。前端直接由 Spring Boot 提供，不需要 Node.js 或独立前端构建。原 Spring AI 功能保留在 `/ai-chat.html`。

1. 在 IDEA 中重新加载 Maven，使用 JDK 17 或以上版本。
2. MySQL `growth` 库若尚未完成建表，在数据库客户端整份执行 [MySQL 专用脚本](src/main/resources/growth/schema-mysql.sql)。旧数据库按已启用功能执行对应升级脚本；本次邮箱与提醒必须执行 [邮箱提醒升级脚本](src/main/resources/growth/upgrade-email-reminders.sql)，无需重复初始化。
3. 将启动工作目录设为项目根目录，运行 `SpringaiDemoApplication`。默认启用 `mysql` profile；如果 IDEA 覆盖了 Active profiles，需包含 `mysql`。
4. 打开 <http://127.0.0.1:8080/>，点击「体验示例空间」，或注册自己的家庭账号。
5. 首次上传自动创建私有 bucket `shiguang-media`，对象路径为 `families/家庭ID/随机文件名`。MySQL 保存媒体归属、类型、大小和对象定位信息；应用重启保留业务数据，登录会话需重新建立。

成长记录功能不需要配置 AI 密钥，需要已配置的 MySQL 和 MinIO 可达。新增 MinIO SDK 依赖由 Maven 在你重新加载项目时下载。

连接信息集中在 [MySQL / MinIO 配置文件](src/main/resources/application-mysql.yml)，保留你填写的 `growth` 数据库名和已有账号信息，环境变量可选覆盖。Redis 配置保留，会话仍使用 HttpSession。MySQL 脚本需要客户端识别 `DELIMITER`，应用不会自动执行；不要向 MySQL 执行旧 H2 `schema.sql`。

原 `data/growth.mv.db` 和 `data/growth-media/` 不会被修改或自动迁移。旧 H2 账号、记录需要单独迁移才能出现在 MySQL；若 MySQL 中已有旧本地媒体元数据，保留原目录即可继续读取。新增上传只写 MinIO，不会因 MinIO 故障退回本地存储。

已编写：登录注册、家庭邀请、多孩档案、时间线与搜索、照片和 MP4 视频上传/播放/单文件下载、日记和里程碑、评论、成长曲线、接种记录、回收站、限时水印图片分享、站内消息、按孩子导出 JSON、容量和操作日志。

下载入口位于记录详情的每张照片（含封面）、图片放大窗口及视频播放器下方。家庭成员可下载有效文件，自动命名为「拾光-记录日期-文件编号」，历史文件也适用，无需改表。实现范围与手动验收项见 [照片和视频下载](docs/照片和视频下载.md)。

**QQ 邮箱与待办提醒**：新注册需要 QQ 邮箱和六位数字验证码，已有用户在个人资料绑定邮箱后可使用「待办提醒」。支持一次性、每天、每周提醒，以及暂停、完成和查看发送记录。Redis 用于验证码与限流，MySQL 保存提醒和投递任务。已加入邮件依赖，本地授权码使用不打入 JAR 的外置配置；Nacos 与 K8s Secret 的部署步骤、接口及验收清单见 [QQ邮箱验证码与待办提醒](docs/QQ邮箱验证码与待办提醒.md)。本次未发送真实邮件。

家庭管理员可在「成长数据」页新增、编辑、删除身高 / 体重 / 头围测量及接种记录。编辑表单回填原值，保存后更新曲线和最新数值；删除需确认且不可恢复。普通成员保留原有新增、查看权限，编辑与删除由后端校验管理员及家庭归属。本功能无需新增 SQL。

**拾光小助手（千问）**：复用当前 `app.ai.providers.qwen` 配置。右下角默认「和拾光聊聊」，支持连续追问、流式回复和新对话；另有记录表单「帮我写一写」、成长数据页「整理测量变化」。旧数据库请先执行 [助手升级脚本](src/main/resources/growth/upgrade-ai-assistant.sql)，新增两张配置/请求状态表；已执行过该脚本的项目，此次聊天改动不需要新 SQL。重新编译启动后，家庭管理员在「空间设置 → 拾光小助手 → 设置与使用」中开启。家人主动确认资料范围后才发送给千问。聊天近期上下文保存在服务端内存，按用户、家庭和孩子隔离，闲置30分钟失效；聊天正文不写入数据库，不自动读取记录和相册。日记草稿仍由用户核对保存。详见 [助手使用说明](docs/拾光AI小助手使用说明.md)。

家庭默认空间为 5 GB（按 1024 进制计算）。已有 512 MB 家庭请在 `growth` 库执行 [配额升级脚本](src/main/resources/growth/upgrade-storage-quota-5gb.sql)，执行后刷新页面；新注册及示例家庭直接使用 5 GB。

视频入口：新建或编辑成长记录时选择 MP4，支持和照片混合上传。图片每张 8 MB、视频每个 100 MB，每次最多 6 个且合计不超过 200 MB，每条记录最多 12 个文件。视频保存至 MinIO，暂不转码，仅家人可见，不包含在访客限时分享中。后端校验权限后按 Range 流式读取，支持拖动进度；分享图片仍由服务端生成水印预览。上传失败和永久删除由数据库清理队列重试清除对象。

**交付状态：源码实现，未编译、未启动、未连接远端服务联调、未运行测试或浏览器验收，由你手动验证。** 接口说明、存储规则和手动验收路径见 [儿童成长网站说明](docs/儿童成长网站说明.md)。

---

# 原 Spring AI 基础框架说明

基于 Java 17、Spring Boot 4.1.1、Spring AI 2.0.0，提供多厂商文本聊天、流式输出和浏览器调试台。Spring AI 版本与 `ai-demo` 项目对齐，便于对比排查。

## 快速开始

1. 在 IDEA 中重新加载 Maven 项目，使用 JDK 17 或以上版本。
2. 在启动配置的 Environment variables 中配置至少一家厂商的密钥，例如 `DASHSCOPE_API_KEY=你的密钥`（兼容旧项目），也支持 `QWEN_API_KEY`。密钥不要写进 Java、YAML 或前端文件。
3. 运行 `org.example.springaidemo.SpringaiDemoApplication`。
4. 浏览器打开 <http://127.0.0.1:8080/ai-chat.html>，选择厂商并发送问题。

也可以自行在 PowerShell 中运行：

```powershell
$env:DASHSCOPE_API_KEY = '你的密钥'
$env:AI_DEFAULT_PROVIDER = 'qwen'
.\mvnw.cmd spring-boot:run
```

不填写密钥时仍可启动、打开页面和查看厂商列表，调用未配置的厂商会返回 HTTP 503。环境变量改变后需要重启进程。项目不自动读取 `.env` 文件。

## 配置

配置文件为 `src/main/resources/application.yml`。

| 厂商标识 | 密钥环境变量 | 地址环境变量 | 模型环境变量 | 示例默认模型 |
| --- | --- | --- | --- | --- |
| qwen | DASHSCOPE_API_KEY（兼容 QWEN_API_KEY） | DASHSCOPE_BASE_URL（兼容 QWEN_BASE_URL） | QWEN_MODEL | qwen3.8-max |
| deepseek（百炼） | 复用 qwen 的密钥 | 复用 qwen 的地址 | DEEPSEEK_MODEL | deepseek-v4-pro-0813 |
| openai | OPENAI_API_KEY | OPENAI_BASE_URL | OPENAI_MODEL | gpt-4o-mini |

- `AI_DEFAULT_PROVIDER`：请求省略 provider 时使用的厂商，默认 qwen；不会自动切换到其他厂商。
- `SERVER_PORT`：默认 8080；`SERVER_ADDRESS` 默认 127.0.0.1，仅供本机访问。
- `app.ai.system-prompt`：服务端统一系统提示词。
- `app.ai.timeout`：SDK 请求超时和流式无事件等待超时，默认 120 秒；MVC 异步请求超时为 180 秒。若调大前者，也应同步调整后者。
- `app.ai.max-retries`：SDK 重试次数，默认 0。
- 每家厂商独立配置 `enabled / base-url / api-key / model`。

通义变量优先级：`DASHSCOPE_API_KEY` → `QWEN_API_KEY` → YAML 默认值，地址同理为 `DASHSCOPE_BASE_URL` → `QWEN_BASE_URL` → YAML 默认地址。前一个变量未定义时才使用后一个；已定义但为空不会回退。若两个名称都配置了，建议只保留一个，避免旧值覆盖预期配置。

`provider=deepseek` 现表示阿里云百炼托管的 DeepSeek，通过配置引用复用 qwen 的实际地址和密钥，不使用 `DEEPSEEK_API_KEY` 或 `DEEPSEEK_BASE_URL`。只需配置百炼凭证，并确认业务空间有相应 DeepSeek 模型权限。百炼密钥不能直接用于 `https://api.deepseek.com`；如以后需要接入 DeepSeek 官网，应另外新增厂商配置并使用其独立密钥。

base-url 必须是 API 前缀，不要包含 `/chat/completions`。默认通义地址为北京地域旧域名，官方仍支持；也可根据业务空间和地域设置新的专属域名。模型名称为可修改的示例，需确认当前账号开通了相应模型权限。

本项目手动创建每家厂商的 `ChatModel` 和 `ChatClient`，没有引入单厂商自动配置 starter，因此配置入口是 `app.ai`，不是 `spring.ai.openai`。这里只实现 OpenAI 兼容协议；其他原生协议需要增加对应适配器。

## 接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/ai/providers` | 查询厂商配置状态，不返回密钥或地址；configured 不代表连接已验证 |
| POST | `/api/ai/chat` | 普通 JSON 回复 |
| POST | `/api/ai/chat/stream` | SSE 流式回复 |

普通和流式聊天使用相同请求体：

```json
{
  "provider": "qwen",
  "model": "qwen-plus",
  "messages": [
    {"role": "user", "content": "什么是 Spring AI？"},
    {"role": "assistant", "content": "Spring AI 是用于开发 AI 应用的框架。"},
    {"role": "user", "content": "结合 Java 解释 ChatClient 的作用"}
  ]
}
```

provider、model 可以省略或留空，分别使用服务端默认厂商和该厂商的默认模型。messages 必填，按时间顺序传入，只支持 user/assistant，最后一条必须是 user。最多 40 条，每条不超过 16000 个字符，总计不超过 64000 个字符。字符限制不等同于模型的 token 上限。

返回示例：

```json
{"provider":"qwen","model":"qwen-plus","content":"ChatClient 用于……"}
```

响应中的 model 表示本次请求选用的模型名称。SSE 事件格式如下，每个事件末尾有一个空行：

```text
event:delta
data:{"type":"delta","content":"你好","code":null}

event:done
data:{"type":"done","content":"","code":null}

```

发生上游错误时会发送 `event:error`，data 中 content 为错误说明，随后结束，不发送 done。code 会区分 `AI_AUTHENTICATION_FAILED`（上游 401）、`AI_ACCESS_DENIED`（403）、`AI_RESOURCE_NOT_FOUND`（404）、`AI_RATE_LIMITED`（429）、`AI_REQUEST_REJECTED`（400/422）、`AI_TIMEOUT` 和兜底 `AI_UPSTREAM_ERROR`。连接建立前的参数或配置问题返回 HTTP 400/503 和 JSON；普通请求上游失败返回 HTTP 502。客户端须检查 HTTP 状态，也须处理流中的 error 事件与未收到 done 的断流。

如果通义返回 `401: Incorrect API key provided`，先核对当前进程实际使用的密钥与地址是否和可用项目一致，再核对百炼地域和业务空间。当前默认地址使用北京地域。IDEA 启动配置中的 `DASHSCOPE_API_KEY` 或 `QWEN_API_KEY` 会覆盖 YAML 占位符的默认值；只修改 YAML 默认值可能仍然使用旧环境变量。更新实际生效的配置后重启应用。日志记录 `upstreamStatus` 和解包后的 `causeType`，不输出密钥。

本项目已针对与 `ai-demo` 的差异对齐 Spring AI 2.0.0，并兼容旧项目的 `DASHSCOPE_*` 变量名。源码和构建输出中的密钥默认值一致，并不证明两个 IDEA 启动进程最终读取的密钥相同。尚未执行真实请求验证，不能仅凭版本差异认定 2.0.1 存在鉴权缺陷。

后续提供的 `ai-demo` 日志也出现了 qwen/deepseek 鉴权失败，因此旧项目不能作为当前已成功调用的基准。已按实际需求将 DeepSeek 改为百炼路由；qwen 本身的鉴权问题仍需核对实际生效的密钥和业务空间。

浏览器使用 `fetch + ReadableStream` 调用 POST SSE；原生 `EventSource` 无法直接发送此 POST 请求。前端按纯文本渲染回复，支持取消生成；仅成功的完整对话进入后续上下文。

## 代码分层与扩展

```text
controller/AiChatController              HTTP 和参数校验
service/AiChatService                    上下文组装、普通/流式调用、错误映射
provider/AiProviderRegistry              厂商路由、ChatModel/ChatClient 复用
provider/OpenAiCompatibleModelFactory    OpenAI 兼容协议模型构造
config/AiProperties                     外部配置绑定
model/                                  请求/响应 DTO
exception/                              统一错误响应
```

调用链：Controller → Service → Registry → ChatClient → ChatModel → 厂商 API。

增加一个 OpenAI 兼容厂商，只需要在 `app.ai.providers` 下新增配置。请求级模型切换通过实际模型 options 的 `mutate()` 派生，保留具体选项类型。后续可在 ChatClient 层接入 Advisor、工具调用和 RAG。

原 AI 模块仍为基础框架：AI 对话没有服务端持久化、RAG/向量库、认证和限流，页面历史只保存在内存中。成长网站使用独立的家庭鉴权与 MySQL 数据库；它不会自动为 `/api/ai/**` 添加访问控制。对外部署前需要统一处理这两部分接口的认证和限流。

## 手动验证建议

账户注销新功能的业务规则、数据库升级、Redis/Nacos 配置和验收步骤见 [账户注销与恢复期](docs/account-deletion.md)。上线新 JAR 前需要执行 `growth/upgrade-account-deletion.sql`。

本次仅完成源码阅读审查，未执行编译、测试、应用启动、浏览器验证或真实模型调用。

1. 未设置任何密钥时启动，访问首页和厂商列表；调用聊天应返回 503。
2. 配置一个真实密钥并重启，分别尝试普通/流式聊天及多轮追问。
3. 使用未知厂商、空 messages、非法 role、null 消息或非 user 结尾，确认返回 400。
4. 使用错误密钥或无权限的模型，确认普通请求 502、流式请求 error 事件，前端恢复可操作状态。
5. 生成过程中停止、清空、切换厂商，确认失败或中止的内容不进入后续上下文。

## 参考资料

- [Spring AI 版本兼容与依赖管理](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI 2.x OpenAiChatModel API](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/openai/OpenAiChatModel.html)
- [通义千问 OpenAI 兼容接口](https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions)
- [阿里云百炼 DeepSeek 接口](https://help.aliyun.com/zh/model-studio/deepseek-api)
