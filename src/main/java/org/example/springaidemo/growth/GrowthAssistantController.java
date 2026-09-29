package org.example.springaidemo.growth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/growth/assistant")
public class GrowthAssistantController {
    private final GrowthAuth auth;
    private final GrowthAssistantService assistant;
    private final GrowthAssistantStore store;

    public GrowthAssistantController(GrowthAuth auth, GrowthAssistantService assistant, GrowthAssistantStore store) {
        this.auth = auth; this.assistant = assistant; this.store = store;
    }

    public record Settings(@NotNull Boolean enabled) { }
    public record Ask(@NotBlank @Pattern(regexp = "[a-f0-9-]{36}") String requestId,
                      @Positive long childId, @NotBlank @Pattern(regexp = "chat|diary|measurements") String task,
                      @NotBlank @Size(max = 4000) String question,
                      @NotNull @PastOrPresent LocalDate from, @NotNull @PastOrPresent LocalDate to,
                      @NotBlank @Pattern(regexp = "gentle|simple|playful") String style,
                      @AssertTrue(message = "请先确认本次资料将发送给千问") boolean consent,
                      @Pattern(regexp = "[a-f0-9-]{36}") String conversationId,
                      @Min(0) @Max(100000) int turn) { }

    @GetMapping("/settings")
    public Map<String,Object> settings(HttpServletRequest request) {
        return assistant.settings(auth.actor(request.getSession(false)));
    }

    @PutMapping("/settings")
    public Map<String,Object> settings(@Valid @RequestBody Settings input, HttpServletRequest request) {
        var actor = auth.actor(request.getSession(false));
        store.settings(actor, input.enabled());
        return assistant.settings(actor);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<GrowthAssistantService.Event>> stream(@Valid @RequestBody Ask input, HttpServletRequest request) {
        var session = request.getSession(false);
        var actor = auth.actor(session);
        return assistant.stream(actor, input, () -> {
            var current = auth.actor(session);
            if (current.id() != actor.id() || current.familyId() != actor.familyId()) throw GrowthException.forbidden();
        });
    }

    @PostMapping("/requests/{requestId}/cancel")
    public void cancel(@PathVariable String requestId, HttpServletRequest request) {
        store.cancel(auth.actor(request.getSession(false)), requestId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public void clear(@PathVariable String conversationId, HttpServletRequest request) {
        assistant.clear(auth.actor(request.getSession(false)), conversationId);
    }
}
