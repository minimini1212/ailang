package com.example.ailang.domain.user.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/**
 * 학년이 없는 계정이 «학년이 있어야 되는» 기능을 불렀을 때.
 *
 * <p>🔴 <b>구글 가입은 학년을 안 받는다.</b> 그래서 `User.grade` 가 {@code null} 인 계정이
 * 실제로 생기는데, 개념 설명·학년별 문제·진단 테스트·AI 모의문제가 전부
 * {@code user.getGrade().name()} 을 불러 <b>NPE → 500</b> 이 났다.
 *
 * <p>🔴 <b>기본 학년으로 바꿔치기하지 않는다.</b> 「모른다」를 값으로 접으면 그 학생은
 * 엉뚱한 학년의 문제를 받고, 그 결과가 정답률에 쌓여 난이도까지 틀어진다.
 * 500 도 아니다 — 서버가 고장난 것이 아니라 <b>학생이 할 일이 남은 것</b>이다.
 *
 * <p>🎯 그래서 400 으로 «학년을 먼저 정하라»고 말한다. 화면이 그 말을 받아
 * 학년 입력으로 보낼 수 있다.
 */
public class GradeRequiredException extends ApplicationException {
    public GradeRequiredException() {
        super(ErrorCode.GRADE_REQUIRED);
    }
}
