package com.example.ailang.domain.problem.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 단답형 정답 공개 응답 DTO (자가채점 전 정답 확인용, 이력 저장 없음)
 */
@Getter
@Builder
public class AnswerRevealResponse {

    private String answer;
    private String explanation;

    public static AnswerRevealResponse of(String answer, String explanation) {
        return AnswerRevealResponse.builder()
                .answer(answer)
                .explanation(explanation)
                .build();
    }
}
