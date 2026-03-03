package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.problem.dto.request.SubmitAnswerRequest;
import com.example.ailang.domain.problem.dto.response.ConceptResponse;
import com.example.ailang.domain.problem.dto.response.ProblemResponse;
import com.example.ailang.domain.problem.dto.response.SubmitAnswerResponse;

import java.util.List;

/**
 * 문제 서비스 인터페이스
 */
public interface ProblemService {

    // 정답률 기반 맞춤형 문제 조회 (현재 난이도에 맞는 문제 랜덤 반환)
    ProblemResponse getAdaptiveProblem(Long userId, Long chapterId);

    // 난이도 무관 랜덤 문제 조회
    ProblemResponse getRandomProblem(Long userId, Long chapterId);

    // 답안 제출 → 이력 저장 + 통계 업데이트 + 난이도 재계산
    SubmitAnswerResponse submitAnswer(Long userId, Long problemId, SubmitAnswerRequest request);

    // 문제 관련 개념 설명 요청 → FastAPI(Gemini) 호출
    ConceptResponse getConcept(Long problemId, String grade);

    // AI 모의문제 생성 → FastAPI(Gemini) 호출 후 DB 저장
    ProblemResponse getAiProblem(Long userId, Long chapterId);

    // 유저 학년 기반 랜덤 기출문제 조회 (chapterId 없이 바로 문제 제공)
    ProblemResponse getRandomProblemByGrade(Long userId);

    // 유저 수준 파악용 20문제 (LOW 7개 + MEDIUM 7개 + HIGH 6개)
    List<ProblemResponse> getAssessmentProblems(Long userId);
}
