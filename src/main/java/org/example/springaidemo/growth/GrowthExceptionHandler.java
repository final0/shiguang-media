package org.example.springaidemo.growth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {GrowthController.class, GrowthAssistantController.class, GrowthReminderController.class})
public class GrowthExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GrowthExceptionHandler.class);
    @ExceptionHandler(GrowthException.class)
    ResponseEntity<?> handle(GrowthException e) { return ResponseEntity.status(e.status()).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(e.body()); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> upload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(java.util.Map.of("message", "每张图片不能超过 8 MB，每个视频不能超过 100 MB，每次最多 25 个文件且合计不能超过 500 MB"));
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<?> database(org.springframework.dao.DataAccessException e) {
        log.error("Growth database operation failed", e);
        return ResponseEntity.internalServerError().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(java.util.Map.of("message", "数据操作失败，请确认数据库连接及功能升级脚本已执行"));
    }
}
