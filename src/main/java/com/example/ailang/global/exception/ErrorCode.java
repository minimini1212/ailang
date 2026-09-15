package com.example.ailang.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "존재하지 않는 회원입니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),

    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다."),
    EXPIRED_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    INVALID_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    EMAIL_SENDING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했습니다."),

    // 🔴 인증 메일과 코드 입력에는 상한이 하나도 없었다. 6자리 숫자를 무제한으로
    //    넣어 볼 수 있었고(1,000,000 분의 1 이 아니라 사실상 시간 문제였다),
    //    /api/auth/email/send 는 로그인 없이 부를 수 있어 임의 주소로 메일을
    //    무한히 쏠 수 있었다. 상한 값은 설정으로 뺐다 — application.yml 의 app.mail.*
    EMAIL_SEND_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증 메일을 너무 자주 요청했어요. 잠시 후 다시 시도해 주세요."),
    CODE_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 코드를 여러 번 틀렸어요. 코드를 다시 받아 주세요."),

    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 틀렸습니다."),

    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "리프레시 토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),

    CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 챕터입니다."),
    PROBLEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 조건에 맞는 문제가 없습니다."),
    PROBLEM_NOT_IN_CHAPTER(HttpStatus.BAD_REQUEST, "문제와 챕터가 맞지 않습니다."),
    SELF_JUDGE_REQUIRED(HttpStatus.BAD_REQUEST, "단답형은 채점 결과(selfJudge)를 함께 보내야 합니다."),
    /* 🔴 구글 가입은 학년을 안 받는다 — 기본값으로 접지 않고 학생에게 물어본다 */
    GRADE_REQUIRED(HttpStatus.BAD_REQUEST, "학년을 먼저 설정해 주세요. 마이페이지에서 학년을 고를 수 있어요."),
    ANSWER_NOT_REVEALABLE(HttpStatus.BAD_REQUEST, "이 문제는 정답을 미리 볼 수 없습니다."),

    // AI 서버(FastAPI)에서 넘어오는 실패. 🔴 종류를 뭉개지 않는다 —
    // 학생이 다시 시도하면 되는지, 기다려야 하는지, 우리가 고쳐야 하는지가 다르다.
    // 반대편 분류: ai-server/app/exceptions.py · docs/rules/ai-call-policy.md R3
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "지금은 이용이 몰리고 있어요. 잠시 후 다시 시도해 주세요."),
    AI_BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 응답을 처리하지 못했습니다. 다시 시도해 주세요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 기능을 일시적으로 이용할 수 없습니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 에러");

    private final HttpStatus status;
    private final String message;
}
