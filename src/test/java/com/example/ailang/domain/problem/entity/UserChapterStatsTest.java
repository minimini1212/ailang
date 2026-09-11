package com.example.ailang.domain.problem.entity;

import com.example.ailang.domain.problem.enums.Difficulty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 챕터 통계 누적과 난이도 재계산 검사.
 *
 * <p>🎯 이 계산이 이 제품의 고리 전체를 돌린다 — 정답률이 난이도를 정하고, 난이도가 다음
 * 문제를 고른다. 그런데 DB 도 네트워크도 필요 없는 <b>엔티티 메서드</b>이므로 검사를
 * 못 붙일 이유가 없었다.
 *
 * <p>🔴 <b>여기서 재는 것은 「경계」다.</b> 가운데 값(정답률 70%, 30% 같은 것)은 틀리면
 * 금방 보인다. 조용히 틀리는 것은 <b>딱 그 값</b>이다 — 3문제째, 정확히 80%, 정확히 50%.
 *
 * <pre>
 *   총 풀이수  1   2   3 ────────────────→
 *              └───┘   └─ 여기서부터 난이도가 움직인다
 *              아직 모른다 (안 움직인다)
 *
 *   정답률     0 ─────── 50 ─────── 80 ─────── 100
 *              └ 내린다 ┘└─ 유지 ──┘└─ 올린다 ─┘
 *                       ↑           ↑
 *                    50 은 유지    80 은 올린다   ← 양쪽 다 «이상/미만» 이 뒤집히기 쉽다
 * </pre>
 *
 * <p>⚠️ 상태를 손으로 만들 때는 «한 건 내기 직전» 까지 빌더로 세워 두고 {@code recordAnswer}
 * 를 <b>한 번만</b> 부른다. 처음부터 여러 번 부르면 중간마다 재계산이 일어나 경로가 섞여,
 * 정작 재려던 경계를 못 잰다.
 */
@DisplayName("챕터 통계와 난이도 재계산")
class UserChapterStatsTest {

    /** 「한 건 내기 직전」 상태를 만든다. */
    private static UserChapterStats stats(int correctCount, int totalCount, Difficulty difficulty) {
        return UserChapterStats.builder()
                .correctCount(correctCount)
                .totalCount(totalCount)
                .currentDifficulty(difficulty)
                .build();
    }

    @Nested
    @DisplayName("누적은 언제나 일어난다")
    class Accumulates {

        @Test
        @DisplayName("맞으면 정답수와 총수가 함께 오른다")
        void correctRaisesBoth() {
            UserChapterStats s = stats(0, 0, Difficulty.MEDIUM);
            s.recordAnswer(true);
            assertThat(s.getCorrectCount()).isEqualTo(1);
            assertThat(s.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("틀리면 총수만 오른다")
        void wrongRaisesOnlyTotal() {
            UserChapterStats s = stats(0, 0, Difficulty.MEDIUM);
            s.recordAnswer(false);
            assertThat(s.getCorrectCount()).isZero();
            assertThat(s.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("아무것도 안 풀었으면 정답률은 0 이다 — 0으로 나누지 않는다")
        void emptyRateIsZero() {
            assertThat(stats(0, 0, Difficulty.MEDIUM).getCorrectRate()).isZero();
        }

        @Test
        @DisplayName("정답률은 퍼센트(0~100)다 — 비율(0~1)이 아니다")
        void rateIsPercent() {
            // ⚠️ 2026-09-10 에 화면이 이 값에 또 ×100 을 하고 있던 것을 찾았다.
            //    단위를 여기 못 박아 둔다.
            assertThat(stats(1, 2, Difficulty.MEDIUM).getCorrectRate()).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("🔴 3문제 미만은 「못한다」가 아니라 「아직 모른다」")
    class NotEnoughData {

        @Test
        @DisplayName("두 문제를 다 틀려도 난이도는 안 내려간다")
        void twoWrongDoesNotLower() {
            UserChapterStats s = stats(0, 0, Difficulty.MEDIUM);
            s.recordAnswer(false);
            s.recordAnswer(false);
            assertThat(s.getTotalCount()).isEqualTo(2);
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.MEDIUM);
        }

        @Test
        @DisplayName("두 문제를 다 맞혀도 난이도는 안 올라간다")
        void twoCorrectDoesNotRaise() {
            UserChapterStats s = stats(0, 0, Difficulty.MEDIUM);
            s.recordAnswer(true);
            s.recordAnswer(true);
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.MEDIUM);
        }

        @Test
        @DisplayName("🎯 3문제째에 처음 움직인다 — 2에서 3으로 넘어가는 순간")
        void movesOnTheThird() {
            UserChapterStats s = stats(0, 0, Difficulty.MEDIUM);
            s.recordAnswer(true);
            s.recordAnswer(true);
            assertThat(s.getCurrentDifficulty()).as("2문제까지는 멈춰 있다").isEqualTo(Difficulty.MEDIUM);

            s.recordAnswer(true);
            assertThat(s.getCurrentDifficulty()).as("3문제째에 움직인다").isEqualTo(Difficulty.HIGH);
        }
    }

    @Nested
    @DisplayName("🔴 올리고 내리는 «경계» 값")
    class Thresholds {

        @Test
        @DisplayName("정확히 80% 면 올린다 — 「80 이상」이다")
        void exactlyEightyRaises() {
            UserChapterStats s = stats(3, 4, Difficulty.MEDIUM);
            s.recordAnswer(true);                       // 4/5 = 80%
            assertThat(s.getCorrectRate()).isEqualTo(80.0);
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.HIGH);
        }

        @Test
        @DisplayName("80% 에 못 미치면 유지한다")
        void justUnderEightyHolds() {
            UserChapterStats s = stats(3, 4, Difficulty.MEDIUM);
            s.recordAnswer(false);                      // 3/5 = 60%
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.MEDIUM);
        }

        @Test
        @DisplayName("정확히 50% 면 «유지» 한다 — 내리는 것은 「50 미만」이다")
        void exactlyFiftyHolds() {
            UserChapterStats s = stats(2, 3, Difficulty.MEDIUM);
            s.recordAnswer(false);                      // 2/4 = 50%
            assertThat(s.getCorrectRate()).isEqualTo(50.0);
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.MEDIUM);
        }

        @Test
        @DisplayName("50% 미만이면 내린다")
        void underFiftyLowers() {
            UserChapterStats s = stats(1, 3, Difficulty.MEDIUM);
            s.recordAnswer(false);                      // 1/4 = 25%
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.LOW);
        }

        @Test
        @DisplayName("50% 와 80% 사이는 그대로 둔다")
        void middleBandHolds() {
            UserChapterStats s = stats(2, 3, Difficulty.MEDIUM);
            s.recordAnswer(true);                       // 3/4 = 75%
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.MEDIUM);
        }
    }

    @Nested
    @DisplayName("🔴 난이도 축의 양 끝")
    class DifficultyEdges {

        @Test
        @DisplayName("이미 상인데 계속 잘하면 상에 머문다")
        void staysAtTop() {
            UserChapterStats s = stats(4, 4, Difficulty.HIGH);
            s.recordAnswer(true);                       // 5/5 = 100%
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.HIGH);
        }

        @Test
        @DisplayName("이미 하인데 계속 틀리면 하에 머문다 — 더 내려갈 칸이 없다")
        void staysAtBottom() {
            UserChapterStats s = stats(0, 3, Difficulty.LOW);
            s.recordAnswer(false);                      // 0/4 = 0%
            assertThat(s.getCurrentDifficulty()).isEqualTo(Difficulty.LOW);
        }

        @Test
        @DisplayName("한 번에 두 칸 뛰지 않는다 — 0% 여도 상에서 중까지다")
        void movesOneStepAtATime() {
            UserChapterStats s = stats(0, 3, Difficulty.HIGH);
            s.recordAnswer(false);                      // 0/4 = 0%
            assertThat(s.getCurrentDifficulty())
                    .as("0% 라도 상 → 하 로 건너뛰지 않는다")
                    .isEqualTo(Difficulty.MEDIUM);
        }
    }

    @Nested
    @DisplayName("기본값")
    class Defaults {

        @Test
        @DisplayName("새 통계는 0건 · 중(MEDIUM) 에서 시작한다")
        void freshStats() {
            UserChapterStats s = UserChapterStats.builder().build();
            assertThat(s.getCorrectCount()).isZero();
            assertThat(s.getTotalCount()).isZero();
            assertThat(s.getCurrentDifficulty())
                    .as("🔴 모르는 상태의 시작값은 «중» 이다. 하(LOW)로 시작하면 "
                        + "처음 온 학생을 「못한다」고 단정하는 것이 된다")
                    .isEqualTo(Difficulty.MEDIUM);
        }
    }
}
