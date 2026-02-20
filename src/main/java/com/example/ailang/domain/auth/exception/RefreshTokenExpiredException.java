package com.example.ailang.domain.auth.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

public class RefreshTokenExpiredException extends ApplicationException {
    public RefreshTokenExpiredException() { super(ErrorCode.REFRESH_TOKEN_EXPIRED); }
}
