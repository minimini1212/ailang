package com.example.ailang.domain.problem.enums;

/**
 * 문제 출처 Enum
 * - REAL: 기출문제 (실제 문제집 기반, RAG로 저장된 데이터)
 * - AI: 모의문제 (Gemini가 학습 내용 기반으로 자체 생성)
 */
public enum SourceType {
    REAL,
    AI
}
