package com.example.ailang.domain.problem.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/**
 * 정답을 미리 보여줄 수 없는 문제일 때.
 *
 * <p>🔴 이 창구는 «단답형 자가 채점» 을 위한 것이다. 객관식은 서버가 채점하므로
 * 정답을 미리 줄 이유가 없다.
 */
public class AnswerNotRevealableException extends ApplicationException {
    public AnswerNotRevealableException() {
        super(ErrorCode.ANSWER_NOT_REVEALABLE);
    }
}
