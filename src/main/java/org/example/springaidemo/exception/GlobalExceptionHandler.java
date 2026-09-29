package org.example.springaidemo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiError> handleAi(AiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct().collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("INVALID_REQUEST", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("INVALID_JSON", "请求体为空、JSON 格式错误或字段类型不正确"));
    }

    public record ApiError(String code, String message) {
    }
}
