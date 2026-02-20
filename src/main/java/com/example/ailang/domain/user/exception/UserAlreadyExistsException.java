package com.example.ailang.domain.user.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

public class UserAlreadyExistsException extends ApplicationException {
    public UserAlreadyExistsException() { super(ErrorCode.USER_ALREADY_EXISTS); }
}
