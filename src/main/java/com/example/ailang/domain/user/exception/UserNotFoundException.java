package com.example.ailang.domain.user.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

public class UserNotFoundException extends ApplicationException {
    public UserNotFoundException() { super(ErrorCode.USER_NOT_FOUND); }
}
