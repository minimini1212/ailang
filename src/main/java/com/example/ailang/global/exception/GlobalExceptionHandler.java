package com.example.ailang.global.exception;

import com.example.ailang.global.response.ResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ResponseDTO<Void>> handleApplicationException(ApplicationException e) {
        log.warn("ApplicationException: {}", e.getMessage());
        return ResponseEntity.status(e.getErrorCode().getStatus()).body(ResponseDTO.error(e.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDTO<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ResponseDTO.errorWithMessage(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ResponseDTO<Void>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(ResponseDTO.errorWithMessage(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseDTO<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ResponseDTO.errorWithMessage(HttpStatus.BAD_REQUEST, "요청 본문 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseDTO<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ResponseDTO.errorWithMessage(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메소드입니다."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseDTO<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(ResponseDTO.errorWithMessage(HttpStatus.BAD_REQUEST, "필수 파라미터가 누락되었습니다: " + e.getParameterName()));
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ResponseDTO<Void>> handleRedisFailure(RedisConnectionFailureException e) {
        log.error("Redis connection failure", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ResponseDTO.errorWithMessage(HttpStatus.SERVICE_UNAVAILABLE, "서비스가 일시적으로 불가합니다."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ResponseDTO<Void>> handleDataAccess(DataAccessException e) {
        log.error("DataAccessException", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseDTO.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ResponseDTO<Void>> handleRuntimeException(RuntimeException e) {
        log.error("Unhandled RuntimeException", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseDTO.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
