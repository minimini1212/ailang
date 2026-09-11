package com.example.ailang.domain.problem.service;

/**
 * 정답 비교를 위한 표기 정규화.
 *
 * <p>같은 답이 여러 표기로 저장돼 있어서 필요하다 — 적재된 기출 정답에
 * {@code ①}, {@code $2$}, {@code $ 2 $}, {@code $\frac{3}{4}$} 가 섞여 있다.
 *
 * <p>🔴 <b>모르는 표기는 지우지 않는다.</b> 예전에는 아는 명령어를 바꾼 뒤 남은
 * {@code \word} 를 전부 지웠는데, 그러면 <b>서로 다른 답이 같은 문자열이 된다.</b>
 * 2026-09-01 에 실제 정답 데이터로 재현한 결과:
 * <pre>
 *   $\sqrt{2}$   → 2        ← 2 와 같아진다.  다른 수다
 *   2\sqrt{2}    → 22       ┐ 서로 역수인데
 *   $\frac{\sqrt{2}}{2}$ → 22  ┘ 같은 답이 된다
 *   $\pi$        → (빈 문자열)  ← "$" 한 글자만 내도 정답이 된다
 * </pre>
 * 지우는 대신 <b>그대로 둔다.</b> 그러면 모르는 표기끼리는 글자가 같을 때만 같아진다 —
 * 못 맞히는 쪽이 틀리게 맞히는 쪽보다 낫다.
 *
 * <p>🎯 이 클래스는 순수 함수다. DB 도 네트워크도 필요 없으므로 검사를 붙일 수 있다.
 * 규율: {@code docs/rules/grading-and-difficulty.md} R8
 */
public final class AnswerNormalizer {

    private AnswerNormalizer() {
    }

    /**
     * 비교용 문자열로 바꾼다.
     *
     * @return 정규화 결과. <b>비어 있으면 비교하면 안 된다</b> — {@link #isComparable} 참고
     */
    public static String normalize(String answer) {
        if (answer == null) {
            return "";
        }
        String s = answer;
        s = s.replace("$", "");                                               // $ 제거
        s = s.replaceAll("\\s+", "");                                         // 공백 제거

        // 원문자 → 숫자 (DB 정답 "①" ↔ 유저 제출 "1" 매칭)
        s = s.replace("①", "1").replace("②", "2").replace("③", "3")
             .replace("④", "4").replace("⑤", "5");
        // 괄호형 원문자도 같은 뜻이다 (⑴⑵⑶⑷⑸)
        s = s.replace("⑴", "1").replace("⑵", "2").replace("⑶", "3")
             .replace("⑷", "4").replace("⑸", "5");

        s = s.replaceAll("\\\\frac\\{([^}]*)\\}\\{([^}]*)\\}", "$1/$2");      // \frac{a}{b} → a/b
        s = s.replaceAll("\\^\\{([^}]+)\\}", "^$1");                          // ^{n} → ^n
        s = s.replaceAll("_\\{([^}]+)\\}", "_$1");                            // _{n} → _n

        // 뜻이 확실한 명령어만 기호로 바꾼다
        s = s.replaceAll("\\\\times", "×");
        s = s.replaceAll("\\\\div", "÷");
        s = s.replaceAll("\\\\cdot", "·");
        s = s.replaceAll("\\\\pm", "±");

        // 🔴 여기서 나머지 \word 를 지우지 않는다. 위 주석 참고.

        s = s.replaceAll("[{}]", "");                                         // 중괄호 제거
        return s;
    }

    /**
     * 이 정규화 결과로 채점해도 되는가.
     *
     * <p>🔴 비어 있으면 안 된다. 실제 정답 중 {@code "$"} 와 {@code "$\bigcirc$"} 는
     * 정규화하면 아무것도 안 남는데, 그대로 비교하면 <b>학생이 {@code "$"} 한 글자만
     * 내도 정답</b>이 된다.
     */
    public static boolean isComparable(String normalized) {
        return normalized != null && !normalized.isEmpty();
    }

    /**
     * 두 답이 같은가. <b>비교할 수 없으면 {@code false}</b>.
     *
     * <p>⚠️ 「비교 불가」와 「틀림」은 다른 일이다. 부르는 쪽에서 구분해야 할 때는
     * {@link #isComparable} 로 먼저 확인한다.
     */
    public static boolean matches(String storedAnswer, String userAnswer) {
        String a = normalize(storedAnswer);
        String b = normalize(userAnswer);
        if (!isComparable(a) || !isComparable(b)) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }
}
