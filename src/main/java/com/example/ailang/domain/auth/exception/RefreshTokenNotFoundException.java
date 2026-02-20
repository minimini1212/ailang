package com.example.ailang.domain.auth.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

public class RefreshTokenNotFoundException extends ApplicationException {
    public RefreshTokenNotFoundException() { super(ErrorCode.REFRESH_TOKEN_NOT_FOUND); }
}
