package com.weepwood.nginxdemo.web;

import com.weepwood.nginxdemo.service.InvalidImagePathException;
import com.weepwood.nginxdemo.service.UpstreamImageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidImagePathException.class)
    public ResponseEntity<ApiError> invalidPath(InvalidImagePathException exception) {
        return ResponseEntity.badRequest().body(new ApiError(400, exception.getMessage()));
    }

    @ExceptionHandler(UpstreamImageException.class)
    public ResponseEntity<ApiError> upstream(UpstreamImageException exception) {
        HttpStatus status = exception.getUpstreamStatus() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new ApiError(status.value(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数不正确");
        return ResponseEntity.badRequest().body(new ApiError(400, message));
    }

    public record ApiError(int code, String message) {}
}
