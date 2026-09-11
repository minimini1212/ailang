package com.example.ailang.domain.problem.entity;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.problem.enums.Difficulty;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 유저별 챕터 학습 통계 엔티티
 * - 유저가 챕터의 문제를 풀 때마다 누적 정답/총 풀이 수를 업데이트
 * - currentDifficulty: 정답률 기반으로 자동 조정되는 현재 난이도
 * - (user_id, chapter_id) 조합은 유일 (한 유저당 한 챕터에 하나의 통계)
 * - 마지막 업데이트 시각은 BaseTimeEntity의 updatedAt 활용
 */
@Entity
@Table(name = "USER_CHAPTER_STATS",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "chapter_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserChapterStats extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_chapter_stats_seq")
    @SequenceGenerator(name = "user_chapter_stats_seq", sequenceName = "USER_CHAPTER_STATS_SEQ", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    // 누적 정답 수
    @Column(nullable = false)
    @Builder.Default
    private int correctCount = 0;

    // 누적 총 풀이 수
    @Column(nullable = false)
    @Builder.Default
    private int totalCount = 0;

    // 현재 적용 중인 난이도 (초기값: MEDIUM)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Difficulty currentDifficulty = Difficulty.MEDIUM;

    /**
     * 답안 제출 후 통계를 업데이트하고 난이도를 재계산
     * - 정답률 80% 이상 → 난이도 상향
     * - 정답률 50% 미만 → 난이도 하향
     * - 3문제 미만은 데이터 부족으로 난이도 변경 안 함
     */
    public void recordAnswer(boolean correct) {
        if (correct) {
            this.correctCount++;
        }
        this.totalCount++;
        recalculateDifficulty();
    }

    private void recalculateDifficulty() {
        // 3문제 이상 풀어야 난이도 조정 시작
        if (this.totalCount < 3) {
            return;
        }

        double correctRate = (double) this.correctCount / this.totalCount * 100;

        if (correctRate >= 80) {
            this.currentDifficulty = this.currentDifficulty.upgrade();
        } else if (correctRate < 50) {
            this.currentDifficulty = this.currentDifficulty.downgrade();
        }
        // 50 <= correctRate < 80: 현재 난이도 유지
    }

    // 현재 정답률 반환 (소수점 포함, 0.0 ~ 100.0)
    public double getCorrectRate() {
        if (this.totalCount == 0) return 0.0;
        return (double) this.correctCount / this.totalCount * 100;
    }
}
