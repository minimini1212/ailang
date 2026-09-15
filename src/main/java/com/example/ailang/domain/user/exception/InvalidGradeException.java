package com.example.ailang.domain.user.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/**
 * 어휘 밖의 학년 문자열이 들어왔다.
 *
 * <p>🔴 <b>「모른다」를 기본값으로 접지 않는다.</b> 모르는 학년을 초3 으로 치면 학생은
 * 엉뚱한 학년의 문제를 받고, 그 풀이 결과가 그대로 통계에 들어가 다음 문제까지 망친다.
 * 거절하는 편이 낫다.
 */
public class InvalidGradeException extends ApplicationException {
    public InvalidGradeException() { super(ErrorCode.INVALID_GRADE); }
}
