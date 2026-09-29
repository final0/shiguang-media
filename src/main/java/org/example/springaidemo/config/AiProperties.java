package org.example.springaidemo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 厂商配置仅保存在服务端，不允许客户端传入密钥或请求地址。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    @NotBlank
    private String defaultProvider = "qwen";

    @NotBlank
    private String systemPrompt = "你是一个专业、友好的中文助手。";

    @NotNull
    private Duration timeout = Duration.ofSeconds(120);

    @Min(0)
    private int maxRetries;

    @NotEmpty
    private Map<@NotBlank String, @NotNull @Valid Provider> providers = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Provider {
        private boolean enabled = true;
        @NotBlank
        private String baseUrl;
        // 不使用 @Data，避免自动生成包含 API Key 的 toString。
        private String apiKey;
        @NotBlank
        private String model;
    }
}
