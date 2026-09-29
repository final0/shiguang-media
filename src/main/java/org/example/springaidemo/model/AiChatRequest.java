package org.example.springaidemo.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** messages 包含历史消息和本次问题；服务端统一添加 system 消息。 */
public record AiChatRequest(
        @Size(max = 64) String provider,
        @Size(max = 128) String model,
        @NotEmpty @Size(max = 40) List<@NotNull @Valid ChatMessage> messages) {

    public record ChatMessage(
            @NotBlank @Pattern(regexp = "user|assistant", message = "role 仅支持 user 或 assistant") String role,
            @NotBlank @Size(max = 16000) String content) {
    }
}
