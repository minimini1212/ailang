package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.problem.enums.Difficulty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 난이도 폴백 순서 검사.
 *
 * <p>🔴 이 검사가 지키는 것: <b>학생이 어느 단원을 열어도 문제를 받는다.</b>
 * 실측(2026-09-09)에서 (챕터 8 × 난이도 3) 24칸 중 7칸이 비어 있어,
 * 폴백이 없으면 그 칸에 걸린 학생은 404 를 받는다.
 *
 * <p>📌 Spring 컨텍스트도 DB 도 띄우지 않는다.
 */
class DifficultyFallbackTest {

    @Test
    @DisplayName("첫 번째는 언제나 요청한 난이도다")
    void requestedComesFirst() {
        for (Difficulty d : Difficulty.values()) {
            assertThat(DifficultyFallback.order(d).get(0)).isEqualTo(d);
        }
    }

    @Test
    @DisplayName("세 난이도를 모두, 중복 없이 준다 - 어느 칸이 비어도 결국 찾는다")
    void coversAllDifficulties() {
        for (Difficulty d : Difficulty.values()) {
            List<Difficulty> order = DifficultyFallback.order(d);
            assertThat(order).hasSize(3).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrder(Difficulty.values());
        }
    }

    @Test
    @DisplayName("쉬운 쪽을 먼저 본다 - 못 풀면 그만두기 때문이다")
    void prefersEasierBeforeHarder() {
        assertThat(DifficultyFallback.order(Difficulty.MEDIUM))
                .containsExactly(Difficulty.MEDIUM, Difficulty.LOW, Difficulty.HIGH);
        assertThat(DifficultyFallback.order(Difficulty.HIGH))
                .containsExactly(Difficulty.HIGH, Difficulty.MEDIUM, Difficulty.LOW);
        assertThat(DifficultyFallback.order(Difficulty.LOW))
                .containsExactly(Difficulty.LOW, Difficulty.MEDIUM, Difficulty.HIGH);
    }

    @Test
    @DisplayName("난이도가 null 이면 중(MEDIUM) 부터 - 통계가 없는 학생의 초기값이다")
    void nullFallsBackToMedium() {
        assertThat(DifficultyFallback.order(null))
                .isEqualTo(DifficultyFallback.order(Difficulty.MEDIUM));
    }
}
