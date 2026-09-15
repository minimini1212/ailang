package com.example.ailang.domain.user.enums;

import com.example.ailang.domain.user.exception.InvalidGradeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 바깥에서 들어온 문자열을 학년으로 바꾸는 자리. <b>순수 함수다</b>.
 *
 * <p>🔴 <b>왜 따로 있나.</b> {@code Grade.valueOf} 를 날것으로 부르면 모르는 값에
 * {@code IllegalArgumentException} 이 나고, 그것을 받는 핸들러가 없어 <b>500 서버 내부
 * 에러</b>로 나간다. 원인은 학생이 보낸 값인데 화면에는 「서버가 고장났다」로 보인다 —
 * 학생은 자기가 할 수 있는 일이 있다는 것을 알 수 없다.
 *
 * <p>🎯 잡는 변형: {@code Grade.from} 을 {@code Grade.valueOf} 로 되돌리는 것,
 * 그리고 모르는 값을 기본값(예: {@code ELEM_3})으로 바꿔치기하는 것.
 * 🔴 후자가 더 나쁘다 — 학생이 <b>엉뚱한 학년의 문제</b>를 받고, 그 결과가 통계에 들어가
 * 다음 문제까지 망친다. 조용히 틀리는 쪽이라 아무도 모른다.
 */
@DisplayName("학년 문자열 해석")
class GradeFromTest {

    @Nested
    @DisplayName("어휘 안의 값")
    class Known {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Grade.class)
        @DisplayName("모든 학년이 자기 이름으로 돌아온다")
        void 모든_학년(Grade grade) {
            assertThat(Grade.from(grade.name())).isEqualTo(grade);
        }

        @Test
        @DisplayName("앞뒤 공백은 봐준다")
        void 공백은_봐준다() {
            assertThat(Grade.from("  MIDDLE_1  ")).isEqualTo(Grade.MIDDLE_1);
        }
    }

    @Nested
    @DisplayName("🔴 어휘 밖의 값은 «거절한다»")
    class Unknown {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"중1", "middle_1", "MIDDLE1", "ELEM_2", "HIGH_2", "1학년", "0"})
        @DisplayName("모르는 값은 기본값으로 접지 않고 거절한다")
        void 모르는_값(String raw) {
            assertThatThrownBy(() -> Grade.from(raw))
                    .isInstanceOf(InvalidGradeException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("없거나 비어 있어도 거절한다 — 「안 보냈다」를 학년으로 읽지 않는다")
        void 비어_있으면(String raw) {
            assertThatThrownBy(() -> Grade.from(raw))
                    .isInstanceOf(InvalidGradeException.class);
        }

        @Test
        @DisplayName("소문자는 «봐주지 않는다» — 어휘는 대문자다")
        void 소문자는_거절한다() {
            // 🎯 여기서 봐주기 시작하면 「어디까지 봐주나」가 계속 늘어난다.
            //    어휘는 DATA_CONTRACT 가 정한 그대로만 받는다.
            assertThatThrownBy(() -> Grade.from("middle_1"))
                    .isInstanceOf(InvalidGradeException.class);
        }
    }
}
