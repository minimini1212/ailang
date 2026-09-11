package com.example.ailang.domain.problem.controller;

import com.example.ailang.domain.problem.dto.request.SubmitAnswerRequest;
import com.example.ailang.domain.problem.dto.response.AnswerRevealResponse;
import com.example.ailang.domain.problem.dto.response.ConceptResponse;
import com.example.ailang.domain.problem.dto.response.ProblemResponse;
import com.example.ailang.domain.problem.dto.response.SubmitAnswerResponse;
import com.example.ailang.domain.problem.service.ProblemService;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.service.UserService;
import com.example.ailang.global.response.ResponseDTO;

import java.util.List;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.ailang.domain.user.entity.UserGrades;
/**
 * 문제 컨트롤러
 * - GET  /api/problems/adaptive?chapterId={id} : 정답률 기반 맞춤형 문제
 * - GET  /api/problems/random?chapterId={id}   : 랜덤 문제 (난이도 무관)
 * - POST /api/problems/{id}/submit             : 답안 제출
 * - GET  /api/problems/{id}/concept            : 문제 관련 개념 설명 (LLM)
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final UserService userService;

    // 정답률 기반 맞춤형 문제 조회 (현재 난이도에 맞는 문제 랜덤 반환)
    @GetMapping("/adaptive")
    public ResponseEntity<ResponseDTO<ProblemResponse>> getAdaptiveProblem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long chapterId) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                problemService.getAdaptiveProblem(user.getId(), chapterId)));
    }

    // 난이도 무관 랜덤 문제 조회
    @GetMapping("/random")
    public ResponseEntity<ResponseDTO<ProblemResponse>> getRandomProblem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long chapterId) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                problemService.getRandomProblem(user.getId(), chapterId)));
    }

    // 답안 제출: 이력 저장 + 통계 업데이트 + 결과 반환
    @PostMapping("/{problemId}/submit")
    public ResponseEntity<ResponseDTO<SubmitAnswerResponse>> submitAnswer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long problemId,
            @RequestBody @Valid SubmitAnswerRequest request) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                problemService.submitAnswer(user.getId(), problemId, request)));
    }

    // 문제 관련 개념 설명 요청: FastAPI(Gemini)를 통해 학년 수준에 맞는 개념 설명 반환
    @GetMapping("/{problemId}/concept")
    public ResponseEntity<ResponseDTO<ConceptResponse>> getConcept(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long problemId) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                problemService.getConcept(problemId, UserGrades.requireName(user))));
    }

    // 유저 수준 파악용 20문제 (LOW 7 + MEDIUM 7 + HIGH 6, 섞인 순서로 반환)
    @GetMapping("/assessment")
    public ResponseEntity<ResponseDTO<List<ProblemResponse>>> getAssessmentProblems(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                problemService.getAssessmentProblems(user.getId())));
    }

    // 유저 학년 기반 랜덤 기출문제 조회 (chapterId 없이 바로 문제 제공)
    @GetMapping("/random-by-grade")
    public ResponseEntity<ResponseDTO<ProblemResponse>> getRandomProblemByGrade(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                problemService.getRandomProblemByGrade(user.getId())));
    }

    // 단답형 정답 공개 (자가채점 전 확인용, 이력 저장 없음)
    @GetMapping("/{problemId}/answer")
    public ResponseEntity<ResponseDTO<AnswerRevealResponse>> revealAnswer(
            @PathVariable Long problemId) {
        return ResponseEntity.ok(ResponseDTO.okWithData(
                problemService.revealAnswer(problemId)));
    }

    // AI 모의문제 조회: 현재 난이도 기반으로 Gemini가 생성한 새 문제 반환
    @GetMapping("/ai-generated")
    public ResponseEntity<ResponseDTO<ProblemResponse>> getAiProblem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long chapterId) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                problemService.getAiProblem(user.getId(), chapterId)));
    }
}
