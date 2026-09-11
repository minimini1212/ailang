package com.example.ailang.domain.problem.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 문제 관련 개념 설명 응답 DTO
 * - GET /api/problems/{id}/concept 응답에 사용
 * - concept: LLM이 생성한 개념 설명 텍스트
 */
@Getter
@Builder
public class ConceptResponse {

    private String concept;

    public static ConceptResponse of(String concept) {
        return ConceptResponse.builder()
                .concept(concept)
                .build();
    }
}
