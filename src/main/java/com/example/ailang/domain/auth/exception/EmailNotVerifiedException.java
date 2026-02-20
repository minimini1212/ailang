package com.example.ailang.domain.auth.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

public class EmailNotVerifiedException extends ApplicationException {
    public EmailNotVerifiedException() { super(ErrorCode.EMAIL_NOT_VERIFIED); }
}
