package com.example.ailang.global.loader;

/**
 * 적재에서 문제 한 건이 어떻게 됐나.
 *
 * <p>🔴 예전에는 이 전부가 {@code skipped} 카운터 하나였다. 그러면 「몇 건이 안 들어왔다」는
 * 알아도 <b>무엇을 고쳐야 하는지</b>는 알 수 없다 — 답안 파일이 없는 것과, 학년 코드가
 * 처음 보는 값인 것과, 정답 자리가 비어 있는 것은 <b>사람이 할 일이 전부 다르다.</b>
 *
 * <p>규율: {@code CLAUDE.md} 「Persist failures with their kind」
 */
public enum LoadOutcome {

    /** 저장됨 */
    INSERTED("저장"),

    /** 짝이 되는 답안 파일이 없거나 비어 있다 → 답안 자료를 확인한다 */
    NO_ANSWER_FILE("답안 파일 없음"),

    /** {@code answer_bbox} 에 정답으로 표시된 항목이 없다 → 원자료 품질 문제다 */
    NO_ANSWER_TEXT("정답 자리가 비어 있음"),

    /** 학년 코드가 매핑에 없다 → 매핑을 늘려야 할 수 있다 */
    UNKNOWN_GRADE("처음 보는 학년 코드"),

    /** 학년·단원 같은 필수 필드 자체가 없다 → 자료 형식이 바뀌었을 수 있다 */
    MISSING_FIELD("필수 필드 없음"),

    /** 그 밖의 예외 → 코드를 봐야 한다 */
    ERROR("처리 중 오류");

    private final String label;

    LoadOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
