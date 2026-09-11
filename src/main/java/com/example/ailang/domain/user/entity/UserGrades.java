package com.example.ailang.domain.user.entity;

import com.example.ailang.domain.user.exception.GradeRequiredException;

/**
 * 학년을 «있어야만» 꺼내는 한 곳.
 *
 * <p>🔴 <b>왜 함수 하나로 모으나</b> — {@code user.getGrade().name()} 이 다섯 자리에 흩어져
 * 있었고, 구글 가입 계정은 학년이 {@code null} 이라 <b>그 다섯 곳이 전부 NPE → 500</b> 이었다.
 * 자리마다 따로 막으면 여섯 번째 자리가 생길 때 또 빠진다.
 *
 * <p>📌 순수 함수다 — Spring 없이 검사할 수 있다.
 */
public final class UserGrades {

    private UserGrades() {
    }

    /**
     * 학년 이름을 준다. 없으면 {@link GradeRequiredException}(400).
     *
     * <p>🔴 <b>기본 학년으로 바꿔치기하지 않는다.</b> 「모른다」를 값으로 접으면 그 학생은
     * 엉뚱한 학년의 문제를 받고, 그 결과가 정답률에 쌓여 난이도까지 틀어진다.
     */
    public static String requireName(User user) {
        if (user == null || user.getGrade() == null) {
            throw new GradeRequiredException();
        }
        return user.getGrade().name();
    }
}
