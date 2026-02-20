package com.example.ailang.domain.auth.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

public class InvalidCodeException extends ApplicationException {
    public InvalidCodeException() { super(ErrorCode.INVALID_CODE); }
}
