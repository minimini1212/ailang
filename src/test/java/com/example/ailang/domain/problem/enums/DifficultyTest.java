package com.example.ailang.domain.problem.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 난이도 한 칸 이동 검사.
 *
 * <p>🎯 여기서 재는 것은 <b>양 끝</b>이다. 가운데 이동(하↔중↔상)은 틀리면 바로 보이지만,
 * 끝에서 한 칸 더 가려 할 때의 동작은 조용히 틀린다 — 예외를 던지거나, 한 바퀴 돌아
 * 상(HIGH)에서 하(LOW)가 되거나, null 이 되는 식이다.
 *
 * <p>🔴 <b>이 검사가 막는 것.</b> 끝을 «유지» 로 처리한 자리가 사라지면
 * (예: {@code case HIGH -> HIGH} 를 지우거나 순환으로 바꾸면) 아래 두 검사가 빨간불이 된다.
 * 잘 푸는 학생이 갑자기 가장 쉬운 문제를 받게 되는 종류의 고장이라, 겪어도 원인을
 * 난이도 계산에서 찾지 않는다.
 */
@DisplayName("난이도 한 칸 이동")
class DifficultyTest {

    @Nested
    @DisplayName("가운데에서는 한 칸씩 움직인다")
    class Middle {

        @Test
        @DisplayName("올리면 하 → 중 → 상")
        void upgradeStepsUp() {
            assertThat(Difficulty.LOW.upgrade()).isEqualTo(Difficulty.MEDIUM);
            assertThat(Difficulty.MEDIUM.upgrade()).isEqualTo(Difficulty.HIGH);
        }

        @Test
        @DisplayName("내리면 상 → 중 → 하")
        void downgradeStepsDown() {
            assertThat(Difficulty.HIGH.downgrade()).isEqualTo(Difficulty.MEDIUM);
            assertThat(Difficulty.MEDIUM.downgrade()).isEqualTo(Difficulty.LOW);
        }
    }

    @Nested
    @DisplayName("🔴 끝에서 더 가려 하면 제자리에 머문다")
    class Boundary {

        @Test
        @DisplayName("상에서 더 올려도 상이다 — 한 바퀴 돌지 않는다")
        void ceilingHolds() {
            assertThat(Difficulty.HIGH.upgrade()).isEqualTo(Difficulty.HIGH);
        }

        @Test
        @DisplayName("하에서 더 내려도 하다 — 한 바퀴 돌지 않는다")
        void floorHolds() {
            assertThat(Difficulty.LOW.downgrade()).isEqualTo(Difficulty.LOW);
        }

        @Test
        @DisplayName("끝에서 여러 번 눌러도 값이 변하지 않는다")
        void repeatedlyPushingTheEdgeIsStable() {
            Difficulty top = Difficulty.HIGH;
            Difficulty bottom = Difficulty.LOW;
            for (int i = 0; i < 5; i++) {
                top = top.upgrade();
                bottom = bottom.downgrade();
            }
            assertThat(top).isEqualTo(Difficulty.HIGH);
            assertThat(bottom).isEqualTo(Difficulty.LOW);
        }
    }

    @Nested
    @DisplayName("올린 뒤 내리면 제자리로 돌아온다 — 끝이 아닐 때만")
    class RoundTrip {

        @Test
        @DisplayName("중에서 올렸다 내리면 중이다")
        void middleRoundTrip() {
            assertThat(Difficulty.MEDIUM.upgrade().downgrade()).isEqualTo(Difficulty.MEDIUM);
        }

        @Test
        @DisplayName("⚠️ 상에서 올렸다 내리면 «중» 이다 — 제자리가 아니다")
        void topRoundTripDoesNotReturn() {
            // 끝에서의 「유지」는 되돌릴 수 없는 동작이다. 의도된 것이고, 여기 못 박아 둔다.
            assertThat(Difficulty.HIGH.upgrade().downgrade()).isEqualTo(Difficulty.MEDIUM);
        }
    }
}
