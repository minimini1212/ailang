package com.example.ailang.domain.problem.dto.response;

import com.example.ailang.domain.problem.entity.Problem;
import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.enums.Difficulty;
import com.example.ailang.domain.problem.enums.ProblemType;
import com.example.ailang.domain.problem.enums.SourceType;
import lombok.Builder;
import lombok.Getter;

/**
 * 문제 응답 DTO
 * - 문제 풀기 화면에서 유저에게 전달하는 정보
 * - 정답(answer)은 포함하지 않음 (제출 후 SubmitAnswerResponse에서 공개)
 * - myStats: 현재 유저의 해당 챕터 통계 (현재 난이도 확인용)
 */
@Getter
@Builder
public class ProblemResponse {

    private Long id;
    private Long chapterId;
    private Difficulty difficulty;
    private ProblemType problemType;
    private SourceType sourceType;
    private String question;

    // 객관식 보기 (단답형이면 null)
    private String options;

    // 현재 유저의 챕터 통계 (난이도 조정 현황 표시용)
    private StatsInfo myStats;

    @Getter
    @Builder
    public static class StatsInfo {
        private Difficulty currentDifficulty;
        private double correctRate;
        private int totalCount;
    }

    public static ProblemResponse of(Problem problem, UserChapterStats stats) {
        StatsInfo statsInfo;

        if (stats == null) {
            statsInfo = StatsInfo.builder()
                    .currentDifficulty(Difficulty.MEDIUM)
                    .correctRate(0.0)
                    .totalCount(0)
                    .build();
        } else {
            statsInfo = StatsInfo.builder()
                    .currentDifficulty(stats.getCurrentDifficulty())
                    .correctRate(stats.getCorrectRate())
                    .totalCount(stats.getTotalCount())
                    .build();
        }

        return ProblemResponse.builder()
                .id(problem.getId())
                .chapterId(problem.getChapter().getId())
                .difficulty(problem.getDifficulty())
                .problemType(problem.getProblemType())
                .sourceType(problem.getSourceType())
                .question(problem.getQuestion())
                .options(problem.getOptions())
                .myStats(statsInfo)
                .build();
    }
}
