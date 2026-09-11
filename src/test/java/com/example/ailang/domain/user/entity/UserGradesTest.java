package com.example.ailang.domain.user.entity;

import com.example.ailang.domain.user.enums.Grade;
import com.example.ailang.domain.user.exception.GradeRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 학년이 없는 계정을 어떻게 다루나.
 *
 * <p>🔴 이 검사가 막는 것: <b>구글 가입 계정은 학년이 없다.</b> 그 계정이 개념 설명이나
 * 학년별 문제를 부르면 예전에는 <b>NPE → 500</b> 이었다 — 서버가 고장난 것처럼 보이는데
 * 실은 학생이 할 일이 남은 것이다.
 *
 * <p>🔴 그렇다고 기본 학년으로 접어도 안 된다. 엉뚱한 학년의 문제를 받고, 그 결과가
 * 정답률에 쌓여 <b>난이도까지 틀어진다.</b>
 *
 * <p>📌 Spring 컨텍스트도 DB 도 띄우지 않는다.
 */
class UserGradesTest {

    @Test
    @DisplayName("학년이 있으면 그 이름을 준다")
    void returnsName() {
        User user = User.builder().grade(Grade.MIDDLE_1).build();
        assertThat(UserGrades.requireName(user)).isEqualTo("MIDDLE_1");
    }

    @Test
    @DisplayName("🔴 학년이 없으면 던진다 — 기본값으로 바꿔치기하지 않는다")
    void throwsWhenGradeMissing() {
        User user = User.builder().grade(null).build();
        assertThatThrownBy(() -> UserGrades.requireName(user))
                .isInstanceOf(GradeRequiredException.class);
    }

    @Test
    @DisplayName("🧭 유저 자체가 없어도 NPE 가 아니라 같은 예외다")
    void throwsWhenUserNull() {
        assertThatThrownBy(() -> UserGrades.requireName(null))
                .isInstanceOf(GradeRequiredException.class);
    }

    @Test
    @DisplayName("🔴 400 이다 — 500 이 아니다. 서버 고장이 아니라 학생이 할 일이 남은 것이다")
    void isBadRequestNotServerError() {
        User user = User.builder().grade(null).build();
        assertThatThrownBy(() -> UserGrades.requireName(user))
                .isInstanceOf(GradeRequiredException.class)
                .satisfies(e -> {
                    GradeRequiredException g = (GradeRequiredException) e;
                    assertThat(g.getErrorCode().getStatus().value()).isEqualTo(400);
                    assertThat(g.getErrorCode().getMessage()).contains("학년");
                });
    }
}
