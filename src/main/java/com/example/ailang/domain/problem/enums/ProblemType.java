package com.example.ailang.domain.problem.enums;

/**
 * 문제 유형 Enum
 * - MULTIPLE_CHOICE: 객관식 (4지선다, options 필드에 JSON으로 보기 저장)
 * - SHORT_ANSWER: 단답형 (숫자 또는 식 직접 입력)
 */
public enum ProblemType {
    MULTIPLE_CHOICE, SHORT_ANSWER
}
