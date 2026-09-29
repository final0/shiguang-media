package org.example.springaidemo.provider;

import org.example.springaidemo.config.AiProperties;
import org.example.springaidemo.exception.AiException;
import org.example.springaidemo.model.AiProviderResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 每个已配置的厂商复用一个模型和 ChatClient，不在每次请求时创建连接。 */
@Component
public class AiProviderRegistry {
    private final AiProperties properties;
    private final Map<String, RegisteredProvider> providers = new LinkedHashMap<>();

    public AiProviderRegistry(AiProperties properties, OpenAiCompatibleModelFactory factory) {
        this.properties = properties;
        if (properties.getTimeout().isNegative() || properties.getTimeout().isZero()) {
            throw new IllegalStateException("app.ai.timeout 必须大于 0");
        }
        if (!properties.getProviders().containsKey(properties.getDefaultProvider())) {
            throw new IllegalStateException("app.ai.default-provider 必须指向已声明的厂商");
        }
        properties.getProviders().forEach((id, config) -> {
            validateUrl(id, config.getBaseUrl());
            if (config.isEnabled() && StringUtils.hasText(config.getApiKey())) {
                ChatModel model = factory.create(config, properties);
                providers.put(id, new RegisteredProvider(id, config.getModel(), model, ChatClient.create(model)));
            }
        });
    }

    public RegisteredProvider resolve(String requestedProvider) {
        String id = StringUtils.hasText(requestedProvider)
                ? requestedProvider.trim() : properties.getDefaultProvider();
        AiProperties.Provider config = properties.getProviders().get(id);
        if (config == null) {
            throw new AiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PROVIDER", "未定义的模型厂商");
        }
        RegisteredProvider provider = providers.get(id);
        if (provider == null) {
            throw new AiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE",
                    "该厂商未启用或未配置 API Key，请修改服务端配置后重启");
        }
        return provider;
    }

    public List<AiProviderResponse> list() {
        return properties.getProviders().entrySet().stream()
                .map(entry -> new AiProviderResponse(entry.getKey(), entry.getValue().getModel(),
                        entry.getValue().isEnabled(), StringUtils.hasText(entry.getValue().getApiKey()),
                        entry.getKey().equals(properties.getDefaultProvider())))
                .toList();
    }

    private static void validateUrl(String id, String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPath().replaceAll("/+$", "").endsWith("/chat/completions")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.ai.providers." + id
                    + ".base-url 必须是有效的 HTTP(S) API 前缀，不能包含 /chat/completions、凭证或查询参数");
        }
    }

    public record RegisteredProvider(String id, String defaultModel, ChatModel chatModel, ChatClient chatClient) {
    }
}
