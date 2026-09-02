package com.example.ailang.domain.problem.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/**
 * 요청이 들고 온 문제와 챕터가 서로 맞지 않을 때.
 *
 * <p>🔴 두 개의 id 가 오면 서버가 관계를 증명한 뒤에 쓴다. 확인 없이 쓰면
 * 학생이 «통계를 올릴 챕터» 를 직접 고르는 셈이 된다.
 */
public class ProblemNotInChapterException extends ApplicationException {
    public ProblemNotInChapterException() {
        super(ErrorCode.PROBLEM_NOT_IN_CHAPTER);
    }
}
