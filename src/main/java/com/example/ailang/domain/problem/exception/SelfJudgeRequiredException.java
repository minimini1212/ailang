package com.example.ailang.domain.problem.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/**
 * 단답형인데 자가 채점 결과가 안 왔을 때.
 *
 * <p>🔴 예전에는 값이 없으면 «틀림» 으로 기록했다. 프론트가 필드를 빠뜨리면
 * 학생의 정답률이 조용히 깎였다. 「모른다」를 값으로 접지 않는다.
 */
public class SelfJudgeRequiredException extends ApplicationException {
    public SelfJudgeRequiredException() {
        super(ErrorCode.SELF_JUDGE_REQUIRED);
    }
}
