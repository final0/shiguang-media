package org.example.springaidemo.growth;

import org.springframework.http.HttpStatus;

public class GrowthException extends RuntimeException {
    private final HttpStatus status;
    private final java.util.Map<String,Object> details;
    public GrowthException(HttpStatus status, String message) { this(status, message, java.util.Map.of()); }
    public GrowthException(HttpStatus status, String message, java.util.Map<String,Object> details) { super(message); this.status = status; this.details = details; }
    public java.util.Map<String,Object> body() {
        var body = new java.util.LinkedHashMap<String,Object>(details);
        body.put("message", getMessage());
        return body;
    }
    public HttpStatus status() { return status; }
    public static GrowthException bad(String message) { return new GrowthException(HttpStatus.BAD_REQUEST, message); }
    public static GrowthException forbidden() { return new GrowthException(HttpStatus.FORBIDDEN, "没有此操作的权限"); }
    public static GrowthException missing() { return new GrowthException(HttpStatus.NOT_FOUND, "内容不存在或不属于当前家庭"); }
}
