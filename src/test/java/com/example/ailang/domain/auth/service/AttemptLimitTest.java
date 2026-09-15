package com.example.ailang.domain.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴 이 검사가 잡는 변형: {@code count > max} 를 {@code count >= max} 로 바꾸는 것.
 *
 * <p>세는 값이 «올린 뒤» 의 값이라 첫 요청이 이미 1 이다. 부등호를 한 칸 옮기면 5회 허용이
 * 조용히 4회가 되는데, 응답은 똑같이 「너무 자주 요청했어요」라서 아무도 눈치채지 못한다.
 */
@DisplayName("허용 횟수 판정")
class AttemptLimitTest {

    @Nested
    @DisplayName("🔴 허용 횟수의 «경계»")
    class Boundary {

        @Test
        @DisplayName("상한이 5면 5번째 요청까지는 통과한다")
        void 상한까지는_통과한다() {
            AttemptLimit limit = new AttemptLimit(5);

            assertThat(limit.isExceeded(1)).isFalse();
            assertThat(limit.isExceeded(4)).isFalse();
            assertThat(limit.isExceeded(5)).isFalse();
        }

        @Test
        @DisplayName("6번째부터 거부한다")
        void 상한을_넘으면_거부한다() {
            AttemptLimit limit = new AttemptLimit(5);

            assertThat(limit.isExceeded(6)).isTrue();
            assertThat(limit.isExceeded(100)).isTrue();
        }

        @Test
        @DisplayName("상한이 1이면 첫 요청만 통과한다")
        void 상한이_1일_때() {
            AttemptLimit limit = new AttemptLimit(1);

            assertThat(limit.isExceeded(1)).isFalse();
            assertThat(limit.isExceeded(2)).isTrue();
        }
    }

    @Nested
    @DisplayName("🔴 설정 실수를 「무제한」으로 접지 않는다")
    class InvalidConfig {

        @Test
        @DisplayName("0이나 음수는 뜰 때 거부한다 — 조용히 통과시키면 상한이 없는 것과 같다")
        void 상한이_1보다_작으면_거부한다() {
            assertThatThrownBy(() -> new AttemptLimit(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AttemptLimit(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
