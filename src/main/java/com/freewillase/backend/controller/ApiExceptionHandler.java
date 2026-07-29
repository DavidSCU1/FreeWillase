package com.freewillase.backend.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * 后端统一异常处理器（返回 JSON）。
 *
 * <p>设计目标：</p>
 * <ul>
 *   <li>让前端工作台能稳定拿到 <code>{status, message}</code> 结构，并把 message 直接用于产品提示。</li>
 *   <li>避免将后端堆栈或敏感信息暴露给前端；对上游调用失败统一给出“可理解、可操作”的文案。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        // 登录失败：明确提示账号/密码错误，避免给出“认证失败”这种技术化信息
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UsernameNotFoundException ex) {
        // 用户不存在：直接返回具体 message（通常来自 UserDetailsService）
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        // Bean Validation 校验失败：取第一个字段错误提示（保持返回信息简洁）
        String message = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        // 业务侧主动抛出的可读错误：前端会直接展示 message
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        // 404：对用户统一提示“接口不存在”
        return buildErrorResponse(HttpStatus.NOT_FOUND, "接口不存在");
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClientException(RestClientException ex) {
        // 调用上游服务失败：屏蔽底层网络异常细节，统一转成 502 提示
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, "上游服务请求失败，请稍后重试");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return buildErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大，请控制在 20MB 以内");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException ex) {
        // 文件上传异常：根据 message 是否包含 size 做更具体的提示
        String message = ex.getMessage() != null ? ex.getMessage() : "";
        if (message.toLowerCase().contains("size")) {
            return buildErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大，请控制在 20MB 以内");
        }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "文件上传失败，请重新选择文件后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        // 兜底异常：记录堆栈到服务端日志，返回给前端简洁信息
        log.error("Unhandled exception", ex);
        String message = ex.getMessage() != null ? ex.getMessage() : "服务器内部错误，请稍后再试";
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message) {
        // 统一错误响应结构：status + message + timestamp
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(),
                message,
                LocalDateTime.now()
        ));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String message;
        private LocalDateTime timestamp;
    }
}
