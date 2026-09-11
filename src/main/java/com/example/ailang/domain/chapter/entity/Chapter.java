package com.example.ailang.domain.chapter.entity;

import com.example.ailang.domain.user.enums.Grade;
import com.example.ailang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 챕터(대단원) 엔티티
 * - 학년(grade)별로 수학 대단원을 나타냄 (예: 중1 - 소인수분해, 기본 도형)
 * - orderNum 으로 챕터 표시 순서를 관리 (원본의 question_unit = 1~8)
 * - Problem(문제)의 부모 엔티티
 *
 * <p>🔴 <b>2026-09-09 에 «유형» 에서 «대단원» 으로 바뀌었다.</b> 예전에는 원본의
 * {@code question_topic_name}(「맞꼭지각(1)」 같은 문제 유형)을 그대로 챕터로 썼다.
 * 그래서 챕터가 331개가 되고 그중 200개(60%)가 문제 3개 이하였는데,
 * 난이도 조정은 <b>챕터당 3문제 이상</b> 풀어야 시작하므로
 * <b>그 챕터들은 난이도가 영원히 안 움직였다.</b>
 * 유형은 사라지지 않고 {@code Problem.topic} 으로 옮겨 갔다.
 * 근거: {@code docs/research/chapter-granularity-2026-09-09.md}
 */
@Entity
@Table(
        name = "CHAPTERS",
        // 🔴 같은 학년에 같은 이름의 챕터가 둘 있으면 문제가 갈려 담기고, 통계도 갈린다.
        //    적재는 조회 후 없으면 생성(read-then-write)이라 코드만으로는 못 막는다.
        uniqueConstraints = @UniqueConstraint(
                name = "UK_CHAPTERS_GRADE_TITLE",
                columnNames = {"grade", "title"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Chapter extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chapters_seq")
    @SequenceGenerator(name = "chapters_seq", sequenceName = "CHAPTERS_SEQ", allocationSize = 50)
    private Long id;

    // 이 챕터가 속한 학년 (ELEM_3 ~ HIGH_1)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Grade grade;

    // 챕터명 (예: "분수", "방정식과 부등식")
    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    // 같은 학년 내에서 챕터 표시 순서
    @Column(nullable = false)
    private Integer orderNum;
}
