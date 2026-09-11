package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.problem.enums.Difficulty;

import java.util.List;

/**
 * 챕터에 그 난이도의 문제가 없을 때 «다음으로 볼 난이도» 순서.
 *
 * <p>🔴 <b>왜 필요한가 — 난이도 축이 단원 축과 거의 같기 때문이다.</b>
 * 2026-09-09 실측에서 (챕터 8개 × 난이도 3종) 24칸 중 <b>7칸이 비어 있었다.</b>
 * <pre>
 *   입체도형     하 0 · 중   0 · 상 168   ← 전부 상
 *   정수와 유리수  하 167 · 중   0 · 상   0   ← 전부 하
 * </pre>
 * 맞춤 문제는 「챕터 + 학생의 현재 난이도」로 문제를 찾는데, 통계가 없는 학생의
 * 초기 난이도가 중(MEDIUM)이다. 그래서 학생이 「입체도형」을 <b>처음 열면</b>
 * 중 0건이라 문제를 못 받고 <b>404</b> 가 났다.
 *
 * <p>⚠️ 이 결함은 챕터를 대단원으로 묶어서 «생긴» 것이 아니다. 예전 331챕터에서는
 * 챕터당 평균 3.5문제라 훨씬 자주 났고, 다만 아무도 세지 않았을 뿐이다.
 *
 * <p>🎯 <b>아래쪽을 먼저 본다.</b> 학생의 수준을 모를 때 어려운 문제를 들이미는 것보다
 * 쉬운 문제를 주는 편이 낫다 — 틀리면 난이도가 내려갈 뿐이지만, 못 풀면 그만둔다.
 *
 * <p>📌 이 클래스는 <b>순수 함수</b>다. DB·Spring 없이 검사할 수 있게 떼어 놓았다.
 */
public final class DifficultyFallback {

    private DifficultyFallback() {
    }

    /**
     * 찾아볼 난이도를 순서대로 준다. 첫 번째는 항상 요청한 난이도다.
     *
     * @param requested 학생의 현재 난이도. {@code null} 이면 중(MEDIUM) 부터 본다.
     */
    public static List<Difficulty> order(Difficulty requested) {
        Difficulty from = requested != null ? requested : Difficulty.MEDIUM;
        return switch (from) {
            case LOW    -> List.of(Difficulty.LOW,    Difficulty.MEDIUM, Difficulty.HIGH);
            case MEDIUM -> List.of(Difficulty.MEDIUM, Difficulty.LOW,    Difficulty.HIGH);
            case HIGH   -> List.of(Difficulty.HIGH,   Difficulty.MEDIUM, Difficulty.LOW);
        };
    }
}
