package org.example.springaidemo.model;

/** SSE 事件：delta 为文本增量，done 为完成，error 为失败。 */
public record AiStreamEvent(String type, String content, String code) {
}
