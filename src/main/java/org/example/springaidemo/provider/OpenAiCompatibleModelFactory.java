package org.example.springaidemo.provider;

import org.example.springaidemo.config.AiProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/** Spring AI 2.x 将连接信息放在 options 中；baseUrl 是 API 前缀。 */
@Component
public class OpenAiCompatibleModelFactory {

    public ChatModel create(AiProperties.Provider provider, AiProperties properties) {
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(provider.getBaseUrl())
                        .apiKey(provider.getApiKey())
                        .model(provider.getModel())
                        .timeout(properties.getTimeout())
                        .maxRetries(properties.getMaxRetries())
                        .build())
                .build();
    }
}
