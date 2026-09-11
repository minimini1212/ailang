package com.example.ailang.domain.problem.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/**
 * 문제를 찾을 수 없을 때 발생하는 예외
 * - 주로 해당 챕터/난이도에 등록된 문제가 없을 때 발생
 */
public class ProblemNotFoundException extends ApplicationException {
    public ProblemNotFoundException() {
        super(ErrorCode.PROBLEM_NOT_FOUND);
    }
}
