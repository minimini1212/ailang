package com.example.ailang.domain.user.dto.request;

import com.example.ailang.domain.user.enums.Grade;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학년 설정·변경 요청.
 *
 * <p>🔴 <b>학년이 비면 «모른다»이지 «기본 학년»이 아니다.</b> 값이 없으면 400 으로 되돌려
 * 보낸다 — 여기서 기본값을 채우면 그 학생은 엉뚱한 학년의 문제를 받고, 그 결과가
 * 정답률에 쌓여 난이도까지 틀어진다. (같은 규율: {@link com.example.ailang.domain.user.entity.UserGrades})
 *
 * <p>🧭 <b>없는 학년 문자열도 400 이다.</b> Jackson 이 {@link Grade} 로 못 바꾸면
 * {@code HttpMessageNotReadableException} 이 나고, 전역 핸들러가 400 으로 받는다.
 * 문자열을 손으로 비교하지 않는 이유다 — 어휘의 정본은 enum 한 곳이다.
 */
@Getter
@NoArgsConstructor
public class UpdateGradeRequest {

    @NotNull(message = "학년을 선택해주세요.")
    private Grade grade;
}
