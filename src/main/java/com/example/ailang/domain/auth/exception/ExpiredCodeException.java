package com.example.ailang.domain.auth.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

public class ExpiredCodeException extends ApplicationException {
    public ExpiredCodeException() { super(ErrorCode.EXPIRED_CODE); }
}
