package com.example.ailang.domain.chapter.dto.response;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.enums.Difficulty;
import com.example.ailang.domain.user.enums.Grade;
import lombok.Builder;
import lombok.Getter;

/**
 * 챕터 목록 응답 DTO
 * - 챕터 기본 정보 + 해당 유저의 학습 통계를 함께 반환
 * - 통계가 없으면 (문제를 한 번도 안 푼 경우) stats는 기본값으로 반환
 */
@Getter
@Builder
public class ChapterResponse {

    private Long id;
    private String title;
    private String description;
    private Integer orderNum;
    private Grade grade;

    // 유저의 해당 챕터 통계 (없으면 기본값)
    private StatsInfo myStats;

    @Getter
    @Builder
    public static class StatsInfo {
        private int correctCount;
        private int totalCount;
        private Difficulty currentDifficulty;
        private double correctRate;
    }

    public static ChapterResponse of(Chapter chapter, UserChapterStats stats) {
        StatsInfo statsInfo;

        if (stats == null) {
            // 아직 문제를 한 번도 풀지 않은 경우 기본값으로 세팅
            statsInfo = StatsInfo.builder()
                    .correctCount(0)
                    .totalCount(0)
                    .currentDifficulty(Difficulty.MEDIUM)
                    .correctRate(0.0)
                    .build();
        } else {
            statsInfo = StatsInfo.builder()
                    .correctCount(stats.getCorrectCount())
                    .totalCount(stats.getTotalCount())
                    .currentDifficulty(stats.getCurrentDifficulty())
                    .correctRate(stats.getCorrectRate())
                    .build();
        }

        return ChapterResponse.builder()
                .id(chapter.getId())
                .title(chapter.getTitle())
                .description(chapter.getDescription())
                .orderNum(chapter.getOrderNum())
                .grade(chapter.getGrade())
                .myStats(statsInfo)
                .build();
    }
}
