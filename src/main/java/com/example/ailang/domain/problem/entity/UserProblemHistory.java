package com.example.ailang.domain.problem.entity;

import com.example.ailang.domain.user.entity.User;
import com.example.ailang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 유저 문제 풀이 이력 엔티티
 * - 유저가 문제를 풀 때마다 한 건씩 저장
 * - UserChapterStats의 정답률 계산 기반 데이터
 * - 풀이 시각은 BaseTimeEntity의 createdAt 활용
 */
@Entity
@Table(name = "USER_PROBLEM_HISTORY",
        indexes = @Index(name = "idx_history_user_solved", columnList = "user_id, created_at DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserProblemHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_problem_history_seq")
    @SequenceGenerator(name = "user_problem_history_seq", sequenceName = "USER_PROBLEM_HISTORY_SEQ", allocationSize = 50)
    private Long id;

    // 문제를 푼 유저
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 풀었던 문제
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    // 유저가 제출한 답안
    @Column(nullable = false, length = 200)
    private String userAnswer;

    // 정답 여부
    @Column(nullable = false)
    private boolean isCorrect;
}
