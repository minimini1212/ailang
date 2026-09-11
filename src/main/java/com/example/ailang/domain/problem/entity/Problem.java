package com.example.ailang.domain.problem.entity;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.problem.enums.Difficulty;
import com.example.ailang.domain.problem.enums.ProblemType;
import com.example.ailang.domain.problem.enums.SourceType;
import com.example.ailang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 문제 엔티티
 * - 각 챕터에 속한 수학 문제를 나타냄
 * - difficulty(난이도), problemType(유형)으로 분류
 * - 객관식의 경우 options에 JSON 형태로 보기 4개를 저장
 *   예: ["1/2", "1", "4/4", "2/4"]
 * - explanation: 정답 제출 후 유저에게 보여줄 해설
 */
@Entity
@Table(name = "PROBLEMS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Problem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "problems_seq")
    @SequenceGenerator(name = "problems_seq", sequenceName = "PROBLEMS_SEQ", allocationSize = 50)
    private Long id;

    // 문제가 속한 챕터
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    // 난이도: LOW(하), MEDIUM(중), HIGH(상)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    // 문제 유형: 객관식(MULTIPLE_CHOICE) / 단답형(SHORT_ANSWER)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProblemType problemType;

    // 문제 본문 (긴 텍스트이므로 CLOB 사용)
    @Lob
    @Column(nullable = false)
    private String question;

    // 객관식 보기 4개를 JSON 배열로 저장 (단답형이면 null)
    // 예: ["1/2", "1", "4/4", "2/4"]
    @Column(length = 1000)
    private String options;

    // 정답 (객관식: 보기 번호 "1"~"4", 단답형: 정답 문자열)
    @Column(nullable = false, length = 200)
    private String answer;

    // 정답 제출 후 보여줄 해설
    @Lob
    @Column(nullable = false)
    private String explanation;

    // 문제 출처: REAL(기출문제) / AI(모의문제)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SourceType sourceType = SourceType.REAL;

    /**
     * 문제 유형명 (예: 「맞꼭지각(1)」, 「평행선에서 각의 크기 구하기(3)-보조선 2개」).
     *
     * <p>🔴 <b>2026-09-09 신설.</b> 예전에는 이 값이 «챕터 이름» 이었다. 챕터를 대단원
     * 8개로 묶으면서 유형을 여기로 옮겼다 — 안 그러면 331종의 정보가 통째로 사라진다.
     *
     * <p>역할: 개념 설명을 요청할 때 대단원명만 보내면 「기본 도형」처럼 너무 넓어진다.
     * 유형까지 함께 보내야 예전과 같은 정확도가 나온다.
     *
     * <p>AI 모의문제는 원본 유형이 없으므로 {@code null} 이다.
     */
    @Column(length = 200)
    private String topic;

    /**
     * 원본 자료의 문제 id (AI Hub). AI 모의문제는 {@code null}.
     *
     * <p>🔴 <b>2026-09-09 신설.</b> 이 컬럼이 없어서 「어느 문제가 어느 원본에서 왔나」를
     * DB 쪽에서 짚을 수 없었고, 그래서 적재가 <b>전부 아니면 전무</b>였다 —
     * 절반만 들어간 상태에서 나머지를 채우거나 새 학년 자료를 덧붙일 수 없었다.
     *
     * <p>⚠️ 유니크 제약을 건다. 같은 원본이 두 번 들어오면 학생이 같은 문제를 두 번 만나고
     * 정답률 분모가 부풀려진다. Oracle 은 {@code NULL} 을 여러 개 허용하므로
     * AI 문제({@code null})는 제약에 걸리지 않는다.
     */
    @Column(name = "source_id", length = 50, unique = true)
    private String sourceId;
}
