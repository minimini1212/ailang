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
     * 객관식 정답 칸에 섞여 들어온 <b>자료 잡음</b>을 걷어낸다.
     *
     * <p>🔴 <b>왜 {@link #normalize} 와 따로 두나.</b> 여기서 하는 일은 「표기 차이」가
     * 아니라 「자료가 더러운 것」이고, <b>객관식에서만 참이다</b>. 단답형 채점을 서버로
     * 되돌릴지는 아직 열린 결정인데, 그때 {@code (5)} 를 {@code 5} 로 벗기면
     * <b>순서쌍이나 괄호가 뜻을 가지는 답을 뭉갠다.</b> 그래서 일반 정규화에 안 넣는다.
     *
     * <p>2026-09-16 에 DB 의 객관식 500건을 전부 꺼내 세어 보고 만든 것이다.
     * 정답 칸이 보기 번호가 아닌 것이 <b>70건</b>이었고, 그중 이 함수가 걷어내는 것은
     * 아래 두 모양 <b>14건</b>이다. 나머지 56건은 사람 판단이 필요하다
     * ({@code docs/reports/daily/REPORT_2026-09-16.md} §10).
     *
     * <pre>
     *   (해답)③  →  3     7건.  「(해답)」 은 원본 해설의 머리말이 딸려 온 것이다
     *   (5)      →  5     7건.  괄호로 싼 보기 번호. 본문 보기가 ①~⑤ 다섯 개인 것을 확인했다
     * </pre>
     *
     * <p>🎯 <b>둘 다 「통째로 그 모양일 때만」 걷어낸다.</b> 문자열 «안에» 있는 괄호는
     * 건드리지 않는다 — {@code \left(-\frac{1}{3}\right)} 같은 수식이 정답 칸에 통째로
     * 들어온 건도 있는데, 거기서 괄호를 벗기면 <b>다른 수가 된다.</b>
     *
     * @param normalized {@link #normalize} 를 이미 거친 문자열
     */
    static String stripChoiceNoise(String normalized) {
        if (normalized == null) {
            return "";
        }
        // 「(해답)3」·「[해답]3」 처럼 머리말이 붙은 것만. 뒤에 보기 번호 하나만 남아야 한다.
        String s = normalized.replaceFirst("^[\\(\\[]?해답[\\)\\]]?(?=[1-5]$)", "");
        // 「(5)」 처럼 통째로 괄호에 싸인 보기 번호만.
        s = s.replaceFirst("^\\(([1-5])\\)$", "$1");
        return s;
    }

    /**
     * 객관식 채점용 비교. <b>자료 잡음을 걷어낸 뒤</b> 비교한다.
     *
     * <p>⚠️ 단답형에는 쓰지 않는다 — {@link #stripChoiceNoise} 주석 참고.
     */
    public static boolean matchesChoice(String storedAnswer, String userAnswer) {
        String a = stripChoiceNoise(normalize(storedAnswer));
        String b = stripChoiceNoise(normalize(userAnswer));
        if (!isComparable(a) || !isComparable(b)) {
            return false;
        }
        return a.equalsIgnoreCase(b);
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
