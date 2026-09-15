package com.example.ailang.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Grade {
    ELEM_3("초등 3학년"),
    ELEM_4("초등 4학년"),
    ELEM_5("초등 5학년"),
    ELEM_6("초등 6학년"),
    MIDDLE_1("중학 1학년"),
    MIDDLE_2("중학 2학년"),
    MIDDLE_3("중학 3학년"),
    HIGH_1("고등 1학년");

    private final String displayName;

    /**
     * 바깥에서 들어온 문자열을 학년으로 바꾼다. <b>모르는 값이면 거절한다.</b>
     *
     * <p>🔴 {@code Grade.valueOf} 를 날것으로 부르지 않는다. 그것이 던지는
     * {@code IllegalArgumentException} 을 받는 핸들러가 없어서, {@code ?grade=중1} 같은
     * 요청이 <b>500 서버 내부 에러</b>로 나갔다 — 학생 입력이 원인인데 우리 잘못처럼 보인다.
     *
     * <p>🎯 순수 함수다. 새 어휘가 생기면 이 한 곳만 본다.
     *
     * @throws com.example.ailang.domain.user.exception.InvalidGradeException 모르는 값일 때
     */
    public static Grade from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new com.example.ailang.domain.user.exception.InvalidGradeException();
        }
        try {
            return Grade.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new com.example.ailang.domain.user.exception.InvalidGradeException();
        }
    }
}
