package org.example.springaidemo.service;

import com.openai.errors.OpenAIServiceException;
import org.example.springaidemo.config.AiProperties;
import org.example.springaidemo.exception.AiException;
import org.example.springaidemo.model.AiChatRequest;
import org.example.springaidemo.model.AiChatResponse;
import org.example.springaidemo.model.AiStreamEvent;
import org.example.springaidemo.provider.AiProviderRegistry;
import org.example.springaidemo.provider.AiProviderRegistry.RegisteredProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
public class AiChatService {
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private final AiProviderRegistry registry;
    private final AiProperties properties;

    public AiChatService(AiProviderRegistry registry, AiProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    public AiChatResponse chat(AiChatRequest request) {
        PreparedChat prepared = prepare(request);
        try {
            ChatResponse response = prepared.provider().chatClient().prompt(prepared.prompt()).call().chatResponse();
            if (response == null || response.getResult() == null) {
                throw new IllegalStateException("模型未返回回复");
            }
            return new AiChatResponse(prepared.provider().id(), prepared.model(), text(response));
        } catch (RuntimeException exception) {
            throw upstreamError(prepared.provider().id(), exception);
        }
    }

    public Flux<ServerSentEvent<AiStreamEvent>> stream(AiChatRequest request) {
        // 先检查配置和参数，使流建立前的错误仍可以返回正常的 HTTP 400/503。
        PreparedChat prepared = prepare(request);
        return Flux.defer(() -> prepared.provider().chatClient().prompt(prepared.prompt()).stream().chatResponse())
                .timeout(properties.getTimeout())
                .map(AiChatService::text)
                .filter(content -> !content.isEmpty())
                .map(content -> event("delta", content, null))
                .concatWithValues(event("done", "", null))
                .onErrorResume(exception -> {
                    AiException error = upstreamError(prepared.provider().id(), exception);
                    // HTTP 响应已经开始后，使用 error 事件报告失败，不再发送 done。
                    return Flux.just(event("error", error.getMessage(), error.getCode()));
                });
    }

    private PreparedChat prepare(AiChatRequest request) {
        if (!"user".equals(request.messages().get(request.messages().size() - 1).role())) {
            throw new AiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "messages 最后一条必须是 user 消息");
        }
        int totalLength = request.messages().stream().mapToInt(message -> message.content().length()).sum();
        if (totalLength > 64000) {
            throw new AiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "消息总长度不能超过 64000 个字符");
        }
        RegisteredProvider provider = registry.resolve(request.provider());
        String model = StringUtils.hasText(request.model()) ? request.model().trim() : provider.defaultModel();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(properties.getSystemPrompt()));
        request.messages().forEach(message -> messages.add("user".equals(message.role())
                ? new UserMessage(message.content()) : new AssistantMessage(message.content())));
        // 从模型的实际 options 派生，保留厂商具体类型，避免 OpenAiChatOptions 类型转换异常。
        var options = provider.chatModel().getOptions().mutate().model(model).build();
        return new PreparedChat(provider, model, new Prompt(messages, options));
    }

    private static String text(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput().getText() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private static ServerSentEvent<AiStreamEvent> event(String type, String content, String code) {
        return ServerSentEvent.<AiStreamEvent>builder(new AiStreamEvent(type, content, code)).event(type).build();
    }

    private static AiException upstreamError(String provider, Throwable exception) {
        // 异步 SDK 会用 CompletionException 包装 401 等异常，沿 cause 查找真实状态。
        // 限制遍历深度，避免异常 cause 链出现循环时无法退出。
        Throwable cause = exception;
        for (int depth = 0; depth < 32; depth++) {
            if (cause instanceof OpenAIServiceException || cause.getCause() == null
                    || cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        Integer upstreamStatus = cause instanceof OpenAIServiceException serviceError
                ? serviceError.statusCode() : null;
        // 只记录类型和状态，不直接透传厂商异常消息或响应体。
        log.warn("AI 调用失败，provider={}, upstreamStatus={}, exceptionType={}, causeType={}",
                provider, upstreamStatus, exception.getClass().getSimpleName(), cause.getClass().getSimpleName());
        String code = "AI_UPSTREAM_ERROR";
        String message = "模型调用失败，请查看服务端日志中的 causeType 和 upstreamStatus";
        if (upstreamStatus != null) {
            switch (upstreamStatus) {
                case 401 -> {
                    code = "AI_AUTHENTICATION_FAILED";
                    message = "模型服务鉴权失败（上游 HTTP 401），请检查 API Key 是否有效";
                    if ("qwen".equals(provider)) {
                        message += "，并核对 DASHSCOPE_API_KEY/QWEN_API_KEY 及对应 BASE_URL 的实际配置、地域和业务空间；修改后重启应用";
                    }
                }
                case 403 -> {
                    code = "AI_ACCESS_DENIED";
                    message = "模型服务拒绝访问（上游 HTTP 403），请检查账号、业务空间及模型权限";
                }
                case 404 -> {
                    code = "AI_RESOURCE_NOT_FOUND";
                    message = "模型或接口不存在（上游 HTTP 404），请核对模型名称与 base-url";
                }
                case 429 -> {
                    code = "AI_RATE_LIMITED";
                    message = "模型服务限制调用（上游 HTTP 429），请检查调用频率和可用额度";
                }
                case 400, 422 -> {
                    code = "AI_REQUEST_REJECTED";
                    message = "模型服务不接受当前请求（上游 HTTP " + upstreamStatus + "），请检查模型支持的参数及上下文长度";
                }
                case 408, 504 -> {
                    code = "AI_TIMEOUT";
                    message = "模型服务响应超时（上游 HTTP " + upstreamStatus + "），请稍后重试";
                }
                default -> message = "模型服务调用失败（上游 HTTP " + upstreamStatus + "），请稍后重试或检查厂商服务状态";
            }
        } else if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException) {
            code = "AI_TIMEOUT";
            message = "等待模型响应超时，请检查网络或调整 app.ai.timeout";
        }
        // 上游 401 属于服务端模型凭证失败，本应用普通接口仍以 502 返回，避免误导成用户登录失效。
        return new AiException(HttpStatus.BAD_GATEWAY, code, message);
    }

    private record PreparedChat(RegisteredProvider provider, String model, Prompt prompt) {
    }
}
