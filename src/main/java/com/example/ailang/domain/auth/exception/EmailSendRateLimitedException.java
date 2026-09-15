package com.example.ailang.domain.auth.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/** 한 주소로 인증 메일을 정해진 시간 안에 너무 많이 요청했다. */
public class EmailSendRateLimitedException extends ApplicationException {
    public EmailSendRateLimitedException() { super(ErrorCode.EMAIL_SEND_RATE_LIMITED); }
}
