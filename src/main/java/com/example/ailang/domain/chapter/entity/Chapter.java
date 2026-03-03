package com.example.ailang.domain.chapter.entity;

import com.example.ailang.domain.user.enums.Grade;
import com.example.ailang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 챕터(단원) 엔티티
 * - 학년(grade)별로 수학 단원을 나타냄 (예: 초3 - 분수, 중1 - 방정식)
 * - orderNum으로 챕터 표시 순서를 관리
 * - LearningContent(소단원), Problem(문제)의 부모 엔티티
 */
@Entity
@Table(name = "CHAPTERS")
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
