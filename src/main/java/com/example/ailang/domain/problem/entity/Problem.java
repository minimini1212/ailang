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
}
