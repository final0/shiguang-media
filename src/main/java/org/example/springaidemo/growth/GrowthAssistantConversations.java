package org.example.springaidemo.growth;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.springaidemo.growth.GrowthAuth.Actor;

/** 只在服务端内存保留近期完整对话，不接受客户端伪造的 assistant/system 历史。 */
@Service
public class GrowthAssistantConversations {
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final int MAX_SESSIONS = 256, MAX_TURNS = 12, MAX_CHARS = 24000;
    private final Map<Key, Conversation> conversations = new HashMap<>();

    record Key(long userId, long familyId, long childId, String id) { }
    record Turn(String question, String answer) { }
    public record Snapshot(Key key, long version, List<Message> messages, boolean shortened) { }
    private static final class Conversation {
        final List<Turn> turns = new ArrayList<>();
        long version;
        boolean shortened;
        Instant touched = Instant.now();
    }

    @Scheduled(fixedDelay = 60000)
    public synchronized void discardExpired() {
        Instant now = Instant.now();
        conversations.values().removeIf(c -> c.touched.plus(TTL).isBefore(now));
    }

    public synchronized Snapshot begin(Actor actor, GrowthAssistantController.Ask input) {
        Instant now = Instant.now();
        discardExpired();
        Key key = new Key(actor.id(), actor.familyId(), input.childId(), input.conversationId());
        Conversation conversation = conversations.get(key);
        if (conversation == null) {
            if (input.turn() != 0) throw GrowthException.bad("对话已过期或服务已重启，请点击“新对话”重新开始");
            if (conversations.size() >= MAX_SESSIONS) throw GrowthException.bad("当前聊天人数较多，请稍后重试");
            conversation = new Conversation();
            conversations.put(key, conversation);
        }
        if (conversation.version != input.turn()) throw GrowthException.bad("上一轮对话状态未同步，请点击“新对话”重新开始");
        conversation.touched = now;
        List<Message> messages = new ArrayList<>();
        for (Turn turn : conversation.turns) {
            messages.add(new UserMessage(turn.question()));
            messages.add(new AssistantMessage(turn.answer()));
        }
        return new Snapshot(key, conversation.version, List.copyOf(messages), conversation.shortened);
    }

    public synchronized void complete(Snapshot snapshot, String question, String answer) {
        Conversation conversation = conversations.get(snapshot.key());
        // 新对话/关闭面板可能与最后一个模型片段交错，已清除的会话不得重新创建。
        if (conversation == null || conversation.version != snapshot.version()) throw GrowthException.bad("对话已清除，请重新开始");
        conversation.turns.add(new Turn(question, answer));
        int chars = conversation.turns.stream().mapToInt(t -> t.question().length() + t.answer().length()).sum();
        while (conversation.turns.size() > MAX_TURNS || chars > MAX_CHARS) {
            Turn removed = conversation.turns.remove(0);
            chars -= removed.question().length() + removed.answer().length();
            conversation.shortened = true;
        }
        conversation.version++;
        conversation.touched = Instant.now();
    }

    public synchronized void clear(Actor actor, String id) {
        conversations.keySet().removeIf(k -> k.userId() == actor.id() && k.familyId() == actor.familyId() && k.id().equals(id));
    }
}
