package com.example.ailang.global.exception;

public class TokenExpiredException extends ApplicationException {
    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED);
    }
}
