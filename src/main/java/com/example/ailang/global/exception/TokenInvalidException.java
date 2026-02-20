package com.example.ailang.global.exception;

public class TokenInvalidException extends ApplicationException {
    public TokenInvalidException() {
        super(ErrorCode.TOKEN_INVALID);
    }
}
