package com.example.ailang.domain.auth.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/**
 * 인증 코드를 정해진 횟수보다 많이 틀렸다.
 *
 * <p>🔴 「틀렸다」({@link InvalidCodeException}) 와 다른 상태다. 틀린 것은 다시 넣어 보면
 * 되지만 이쪽은 코드가 이미 버려졌으므로 <b>다시 받아야</b> 한다. 둘을 같은 응답으로
 * 뭉개면 학생이 같은 코드를 계속 넣으며 왜 안 되는지 모른다.
 */
public class CodeAttemptsExceededException extends ApplicationException {
    public CodeAttemptsExceededException() { super(ErrorCode.CODE_ATTEMPTS_EXCEEDED); }
}
