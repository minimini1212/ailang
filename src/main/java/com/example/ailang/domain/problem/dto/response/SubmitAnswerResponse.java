package com.example.ailang.domain.problem.dto.response;

import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.enums.Difficulty;
import lombok.Builder;
import lombok.Getter;

/**
 * 답안 제출 결과 응답 DTO
 * - 정답 여부, 정답, 해설을 반환
 * - 제출 후 난이도가 변경되었다면 updatedDifficulty에 반영
 */
@Getter
@Builder
public class SubmitAnswerResponse {

    // 정답 여부
    private boolean isCorrect;

    // 정답 공개
    private String correctAnswer;

    // 해설
    private String explanation;

    // 제출 후 갱신된 난이도
    private Difficulty updatedDifficulty;

    // 제출 후 갱신된 정답률
    private double updatedCorrectRate;

    public static SubmitAnswerResponse of(boolean isCorrect, String correctAnswer,
                                          String explanation, UserChapterStats stats) {
        return SubmitAnswerResponse.builder()
                .isCorrect(isCorrect)
                .correctAnswer(correctAnswer)
                .explanation(explanation)
                .updatedDifficulty(stats.getCurrentDifficulty())
                .updatedCorrectRate(stats.getCorrectRate())
                .build();
    }
}
