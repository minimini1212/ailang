package com.example.ailang.domain.user.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

public class InvalidPasswordException extends ApplicationException {
    public InvalidPasswordException() { super(ErrorCode.INVALID_PASSWORD); }
}
