package org.example.springaidemo.controller;

import jakarta.validation.Valid;
import org.example.springaidemo.model.AiChatRequest;
import org.example.springaidemo.model.AiChatResponse;
import org.example.springaidemo.model.AiProviderResponse;
import org.example.springaidemo.model.AiStreamEvent;
import org.example.springaidemo.provider.AiProviderRegistry;
import org.example.springaidemo.service.AiChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {
    private final AiChatService service;
    private final AiProviderRegistry registry;

    public AiChatController(AiChatService service, AiProviderRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @GetMapping("/providers")
    public List<AiProviderResponse> providers() {
        return registry.list();
    }

    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return service.chat(request);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AiStreamEvent>> stream(@Valid @RequestBody AiChatRequest request) {
        return service.stream(request);
    }
}
