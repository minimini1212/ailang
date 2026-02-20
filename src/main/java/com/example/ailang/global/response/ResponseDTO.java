package com.example.ailang.global.response;

import com.example.ailang.global.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ResponseDTO<T> {

    private final int code;
    private final String message;
    private final T data;

    private ResponseDTO(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static ResponseDTO<Void> ok() {
        return new ResponseDTO<>(HttpStatus.OK.value(), "요청이 성공적으로 처리되었습니다.", null);
    }

    public static ResponseDTO<Void> okWithMessage(String message) {
        return new ResponseDTO<>(HttpStatus.OK.value(), message, null);
    }

    public static <T> ResponseDTO<T> okWithData(T data) {
        return new ResponseDTO<>(HttpStatus.OK.value(), "요청이 성공적으로 처리되었습니다.", data);
    }

    public static <T> ResponseDTO<T> okWithData(T data, String message) {
        return new ResponseDTO<>(HttpStatus.OK.value(), message, data);
    }

    public static ResponseDTO<Void> created() {
        return new ResponseDTO<>(HttpStatus.CREATED.value(), "성공적으로 생성되었습니다.", null);
    }

    public static ResponseDTO<Void> error(ErrorCode errorCode) {
        return new ResponseDTO<>(errorCode.getStatus().value(), errorCode.getMessage(), null);
    }

    public static ResponseDTO<Void> errorWithMessage(HttpStatus status, String message) {
        return new ResponseDTO<>(status.value(), message, null);
    }
}
