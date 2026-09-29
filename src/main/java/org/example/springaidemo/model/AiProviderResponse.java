package org.example.springaidemo.model;

/** configured 仅表示已填写密钥，不表示已通过真实请求验证。 */
public record AiProviderResponse(String id, String model, boolean enabled,
                                 boolean configured, boolean defaultProvider) {
}
