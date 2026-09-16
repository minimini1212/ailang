package com.example.ailang.domain.problem.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정답 표기 정규화 검사.
 *
 * <p>🎯 이 레포에서 <b>인프라 없이 도는 첫 검사</b>다. 기존 검사 하나는
 * {@code @SpringBootTest} 라 Oracle·Redis 가 떠 있어야만 돌았다.
 *
 * <p>🔴 아래 「지우면 안 되는 것」 묶음은 <b>옛 코드에서 실제로 빨간불이던 것</b>이다.
 * 2026-09-01 에 실제 정답 데이터로 재현한 충돌을 그대로 옮겼다.
 */
@DisplayName("정답 정규화")
class AnswerNormalizerTest {

    @Nested
    @DisplayName("같은 답의 표기 차이는 흡수한다")
    class AbsorbsNotation {

        @Test
        @DisplayName("원문자와 숫자는 같다")
        void circledNumber() {
            assertThat(AnswerNormalizer.matches("①", "1")).isTrue();
            assertThat(AnswerNormalizer.matches("⑤", "5")).isTrue();
            assertThat(AnswerNormalizer.matches("⑵", "2")).isTrue();
        }

        @Test
        @DisplayName("달러 기호와 공백은 무시한다")
        void dollarsAndSpaces() {
            assertThat(AnswerNormalizer.matches("$2$", "2")).isTrue();
            assertThat(AnswerNormalizer.matches("$ 2 $", "2")).isTrue();
            assertThat(AnswerNormalizer.matches("$ ④ $", "4")).isTrue();
        }

        @Test
        @DisplayName("분수는 나눗셈 표기와 같다")
        void fraction() {
            assertThat(AnswerNormalizer.matches("$\\frac{3}{4}$", "3/4")).isTrue();
        }

        @Test
        @DisplayName("곱셈 기호는 명령어든 기호든 같다")
        void times() {
            assertThat(AnswerNormalizer.matches("$2 \\times 3$", "2×3")).isTrue();
        }

        @Test
        @DisplayName("대소문자는 무시한다")
        void caseInsensitive() {
            assertThat(AnswerNormalizer.matches("x", "X")).isTrue();
        }
    }

    @Nested
    @DisplayName("🔴 뜻이 다른 답을 같게 만들지 않는다")
    class DoesNotCollide {

        @Test
        @DisplayName("루트2 와 2 는 다른 수다")
        void sqrtIsNotItsRadicand() {
            assertThat(AnswerNormalizer.matches("$\\sqrt{2}$", "2")).isFalse();
        }

        @Test
        @DisplayName("2루트2 와 루트2/2 는 서로 역수다")
        void reciprocalsDiffer() {
            assertThat(AnswerNormalizer.matches("2\\sqrt{2}", "$\\frac{\\sqrt{2}}{2}$")).isFalse();
        }

        @Test
        @DisplayName("8 과 8파이 는 다르다")
        void piMatters() {
            assertThat(AnswerNormalizer.matches("$ 8 \\pi $", "8")).isFalse();
        }

        @Test
        @DisplayName("파이 와 알파 는 다르다")
        void differentGreekLetters() {
            assertThat(AnswerNormalizer.matches("$\\pi$", "$\\alpha$")).isFalse();
        }

        @Test
        @DisplayName("각도 표기가 붙으면 다른 답이다")
        void degreeMatters() {
            assertThat(AnswerNormalizer.matches("$65^{\\circ}$", "65")).isFalse();
        }
    }

    @Nested
    @DisplayName("🔴 비교할 수 없으면 정답이 아니다")
    class NotComparable {

        @Test
        @DisplayName("달러 한 글자는 아무 답과도 같지 않다")
        void loneDollarIsNotAnAnswer() {
            // 예전에는 양쪽이 빈 문자열이 되어 «정답» 이었다
            assertThat(AnswerNormalizer.matches("$\\pi$", "$")).isFalse();
            assertThat(AnswerNormalizer.matches("$", "$")).isFalse();
        }

        @Test
        @DisplayName("빈 값은 비교 대상이 아니다")
        void blank() {
            assertThat(AnswerNormalizer.isComparable(AnswerNormalizer.normalize("$"))).isFalse();
            assertThat(AnswerNormalizer.isComparable(AnswerNormalizer.normalize("  "))).isFalse();
            assertThat(AnswerNormalizer.isComparable(AnswerNormalizer.normalize(null))).isFalse();
        }

        @Test
        @DisplayName("null 은 터지지 않는다")
        void nullSafe() {
            assertThat(AnswerNormalizer.matches(null, "1")).isFalse();
            assertThat(AnswerNormalizer.matches("1", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("복수정답")
    class MultipleAnswers {

        @Test
        @DisplayName("구분자 공백은 무시한다")
        void spacingBetween() {
            assertThat(AnswerNormalizer.matches("①, ③", "①,③")).isTrue();
            assertThat(AnswerNormalizer.matches("①, ③", "1,3")).isTrue();
        }

        @Test
        @DisplayName("⚠️ 순서가 다르면 지금은 다른 답이다")
        void orderMattersToday() {
            // 원본 자료에 복수정답이 6건 있다. 순서까지 맞아야 하는 것이 맞는지는
            // 아직 정하지 않았다 — 지금 동작을 못 박아 둔다.
            assertThat(AnswerNormalizer.matches("①, ③", "3,1")).isFalse();
        }
    }

    @Nested
    @DisplayName("지수·첨자·연산기호 — 🔴 여기가 아직 안 덮여 있었다")
    class ScriptsAndOperators {

        @Test
        @DisplayName("지수의 중괄호는 있으나 없으나 같다")
        void exponent() {
            // 막는 것: ^{n} → ^n 규칙이 사라지면 「x^2」라고 쓴 학생이 전부 오답이 된다.
            assertThat(AnswerNormalizer.matches("$x^{2}$", "x^2")).isTrue();
            assertThat(AnswerNormalizer.matches("$2^{10}$", "2^10")).isTrue();
        }

        @Test
        @DisplayName("아래첨자의 중괄호도 마찬가지다")
        void subscript() {
            assertThat(AnswerNormalizer.matches("$a_{1}$", "a_1")).isTrue();
        }

        @Test
        @DisplayName("🔴 지수는 «값» 이다 — 2^3 과 2^2 는 다른 답이다")
        void exponentValueMatters() {
            // 막는 것: 중괄호를 지우는 규칙이 지수 «내용» 까지 지우게 바뀌는 경우.
            //         그러면 서로 다른 답이 같은 문자열이 된다.
            assertThat(AnswerNormalizer.matches("$2^{3}$", "2^2")).isFalse();
            assertThat(AnswerNormalizer.matches("$x^{2}$", "x")).isFalse();
        }

        @Test
        @DisplayName("나눗셈·가운뎃점·플러스마이너스도 기호와 같다")
        void otherOperators() {
            assertThat(AnswerNormalizer.matches("$6 \\div 2$", "6÷2")).isTrue();
            assertThat(AnswerNormalizer.matches("$2 \\cdot 3$", "2·3")).isTrue();
            assertThat(AnswerNormalizer.matches("$\\pm 5$", "±5")).isTrue();
        }

        @Test
        @DisplayName("🔴 ± 5 와 5 는 다른 답이다")
        void plusMinusIsNotPlain() {
            // 막는 것: 「뜻이 확실한 명령어만 바꾼다」가 「모르는 명령어는 지운다」로
            //         되돌아가는 경우. 그러면 ±5 가 5 와 같아진다.
            assertThat(AnswerNormalizer.matches("$\\pm 5$", "5")).isFalse();
        }

        @Test
        @DisplayName("괄호형 원문자도 숫자와 같다")
        void parenthesizedCircledNumbers() {
            assertThat(AnswerNormalizer.matches("⑴", "1")).isTrue();
            assertThat(AnswerNormalizer.matches("⑸", "5")).isTrue();
        }

        @Test
        @DisplayName("분수와 지수가 섞여도 자리를 지킨다")
        void fractionWithExponent() {
            assertThat(AnswerNormalizer.matches("$\\frac{1}{2^{3}}$", "1/2^3")).isTrue();
        }
    }

    /**
     * 🔴 객관식 정답 칸의 <b>자료 잡음</b>.
     *
     * <p>2026-09-16 에 DB 의 객관식 500건을 전부 꺼내 세었다. 정답 칸이 보기 번호가
     * 아닌 것이 <b>70건</b>이었고 — 그 문제들은 <b>학생이 무엇을 골라도 오답</b>이었다 —
     * 그중 아래 두 모양 <b>14건</b>은 사람 판단 없이 걷어낼 수 있다.
     *
     * <p>⚠️ 종전 기록은 이것을 「14건」으로 적어 뒀는데 그건 <b>자릿수로 갈랐기 때문</b>이다
     * (「12자 초과 14건」). 12자 이하여도 보기 번호가 아니면 똑같이 못 맞힌다.
     */
    @Nested
    @DisplayName("🔴 객관식 정답 칸의 자료 잡음을 걷어낸다")
    class StripsChoiceNoise {

        @Test
        @DisplayName("「(해답)③」 은 ③ 이다 — DB 에 7건 있다")
        void solutionPrefix() {
            assertThat(AnswerNormalizer.matchesChoice("(해답)③", "3")).isTrue();
            assertThat(AnswerNormalizer.matchesChoice("(해답)①", "1")).isTrue();
        }

        @Test
        @DisplayName("괄호로 싼 「(5)」 는 보기 5번이다 — DB 에 7건 있다")
        void parenthesizedChoice() {
            // 본문 보기가 ①~⑤ 다섯 개인 것을 확인하고 넣은 규칙이다.
            // 477/500 이 5지선다고, 이 7건도 전부 5지선다였다.
            assertThat(AnswerNormalizer.matchesChoice("(5)", "5")).isTrue();
            assertThat(AnswerNormalizer.matchesChoice("(3)", "3")).isTrue();
        }

        @Test
        @DisplayName("🎯 잡음이 없던 답은 그대로 맞는다")
        void plainAnswersStillMatch() {
            // 430건이 이쪽이다. 규칙을 넓히면서 이 길을 망가뜨리면 안 된다.
            assertThat(AnswerNormalizer.matchesChoice("①", "1")).isTrue();
            assertThat(AnswerNormalizer.matchesChoice("$ ④ $", "4")).isTrue();
        }
    }

    /**
     * 🔴 <b>걷어내면 안 되는 것.</b> 이 묶음이 「넓히다가 틀리게 맞히는」 쪽을 막는다.
     *
     * <p>이 파일 맨 위 묶음(「지우면 안 되는 것」)과 같은 취지다 —
     * <b>못 맞히는 쪽이 틀리게 맞히는 쪽보다 낫다.</b>
     */
    @Nested
    @DisplayName("🔴 괄호를 «아무 데서나» 벗기지 않는다")
    class DoesNotOverStrip {

        @Test
        @DisplayName("수식 «안» 의 괄호는 그대로 둔다")
        void keepsParenthesesInsideExpressions() {
            // 정답 칸에 풀이가 통째로 들어온 건이 12건 있다. 거기서 괄호를 벗기면
            // 🔴 다른 수가 된다. 「통째로 (N) 일 때만」 벗기는 이유다.
            assertThat(AnswerNormalizer.matchesChoice("$a-\\left(-\\frac{1}{3}\\right)$", "1"))
                    .isFalse();
        }

        @Test
        @DisplayName("보기 번호가 아닌 괄호 숫자는 안 벗긴다")
        void onlyChoiceRange() {
            // (7) 은 보기 번호일 수 없다 — 보기는 다섯 개까지다.
            assertThat(AnswerNormalizer.matchesChoice("(7)", "7")).isFalse();
            assertThat(AnswerNormalizer.matchesChoice("(12)", "12")).isFalse();
        }

        @Test
        @DisplayName("🔴 복수정답은 «여전히» 안 맞는다 — 고친 척하면 안 된다")
        void multipleAnswersStillUnmatched() {
            // DB 에 36건 있다. 이건 자료 잡음이 아니라 «문제의 성격» 이라
            // 화면이 둘을 고를 수 있어야 하고, 채점도 집합 비교여야 한다.
            // 🎯 여기서 ①,③ 을 1 로 뭉개면 「①만 골라도 정답」이 되어 더 나쁘다.
            assertThat(AnswerNormalizer.matchesChoice("①, ③", "1")).isFalse();
            assertThat(AnswerNormalizer.matchesChoice("①, ③", "3")).isFalse();
        }

        @Test
        @DisplayName("🎯 「해답」 뒤에 보기 번호 «하나» 가 아니면 «안 걷어낸다»")
        void prefixOnlyBeforeSingleChoiceNumber() {
            // 🔴 DB 의 7건은 «전부» 「(해답)」 + 원문자 하나였다. 그 밖의 모양은
            //    본 적이 없으므로 규칙을 넓히지 않는다 — 못 맞히는 쪽이 틀리게
            //    맞히는 쪽보다 낫다(이 파일 맨 위 묶음과 같은 취지).
            assertThat(AnswerNormalizer.matchesChoice("(해답)12", "12")).isFalse();
            assertThat(AnswerNormalizer.matchesChoice("(해답)12", "1")).isFalse();
        }
    }
}
