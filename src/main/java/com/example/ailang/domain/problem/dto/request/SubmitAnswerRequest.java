package com.example.ailang.domain.problem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 답안 제출 요청 DTO
 * - chapterId: 통계 업데이트 대상 챕터 식별용
 * - userAnswer: 유저가 입력한 답안
 * - selfJudge: 단답형 전용 자가 채점 결과 (true=맞음, false=틀림)
 *              객관식은 null (서버에서 자동 채점)
 */
@Getter
public class SubmitAnswerRequest {

    @NotNull(message = "챕터 ID를 입력해주세요.")
    private Long chapterId;

    @NotBlank(message = "답안을 입력해주세요.")
    private String userAnswer;

    // 단답형 자가 채점용 (객관식은 null)
    private Boolean selfJudge;
}
