package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.chapter.exception.ChapterNotFoundException;
import com.example.ailang.domain.chapter.repository.ChapterRepository;
import com.example.ailang.domain.problem.dto.request.SubmitAnswerRequest;
import com.example.ailang.domain.problem.dto.response.ConceptResponse;
import com.example.ailang.domain.problem.dto.response.ProblemResponse;
import com.example.ailang.domain.problem.dto.response.SubmitAnswerResponse;
import com.example.ailang.domain.problem.entity.Problem;
import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.entity.UserProblemHistory;
import com.example.ailang.domain.problem.enums.Difficulty;
import com.example.ailang.domain.problem.enums.ProblemType;
import com.example.ailang.domain.problem.enums.SourceType;
import com.example.ailang.domain.problem.exception.ProblemNotFoundException;
import com.example.ailang.domain.problem.repository.ProblemRepository;
import com.example.ailang.domain.problem.repository.UserChapterStatsRepository;
import com.example.ailang.domain.problem.repository.UserProblemHistoryRepository;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.exception.UserNotFoundException;
import com.example.ailang.domain.user.repository.UserRepository;
import com.example.ailang.global.client.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 문제 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemServiceImpl implements ProblemService {

    private final ProblemRepository problemRepository;
    private final UserChapterStatsRepository userChapterStatsRepository;
    private final UserProblemHistoryRepository userProblemHistoryRepository;
    private final ChapterRepository chapterRepository;
    private final UserRepository userRepository;
    private final AiServerClient aiServerClient;    // FastAPI 호출용

    @Override
    // ---- 맞춤형 문제 조회 ----
    public ProblemResponse getAdaptiveProblem(Long userId, Long chapterId) {
        // 현재 유저의 챕터 통계 조회 (없으면 null → 기본 난이도 MEDIUM 적용)
        UserChapterStats stats = userChapterStatsRepository
                .findByUserIdAndChapterId(userId, chapterId)
                .orElse(null);

        // 통계가 없으면 초기 난이도 MEDIUM, 있으면 현재 난이도 사용
        String difficulty = stats != null ? stats.getCurrentDifficulty().name() : "MEDIUM";

        // 현재 난이도에 맞는 문제를 랜덤으로 1개 조회
        Problem problem = problemRepository
                .findRandomByChapterIdAndDifficulty(chapterId, difficulty)
                .orElseThrow(ProblemNotFoundException::new);

        return ProblemResponse.of(problem, stats);
    }

    @Override
    // ---- 랜덤 문제 조회 ----
    public ProblemResponse getRandomProblem(Long userId, Long chapterId) {
        // 난이도 무관하게 챕터 내 랜덤 문제 1개 조회
        Problem problem = problemRepository
                .findRandomByChapterId(chapterId)
                .orElseThrow(ProblemNotFoundException::new);

        UserChapterStats stats = userChapterStatsRepository
                .findByUserIdAndChapterId(userId, chapterId)
                .orElse(null);

        return ProblemResponse.of(problem, stats);
    }

    @Override
    @Transactional
    // ---- 답안 제출 ----
    public SubmitAnswerResponse submitAnswer(Long userId, Long problemId, SubmitAnswerRequest request) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Problem problem = problemRepository.findById(problemId).orElseThrow(ProblemNotFoundException::new);
        Chapter chapter = chapterRepository.findById(request.getChapterId()).orElseThrow(ChapterNotFoundException::new);

        // 정답 여부 판단 (대소문자, 공백, LaTeX $ 기호 무시)
        // 예: "$ 7 $" vs "7" → 둘 다 정답 처리
        String normalizedCorrect = problem.getAnswer().replaceAll("[$\\s]", "");
        String normalizedUser    = request.getUserAnswer().replaceAll("[$\\s]", "");
        boolean isCorrect = normalizedCorrect.equalsIgnoreCase(normalizedUser);

        // 풀이 이력 1건 저장 (USER_PROBLEM_HISTORY에 INSERT)
        userProblemHistoryRepository.save(UserProblemHistory.builder()
                .user(user)
                .problem(problem)
                .userAnswer(request.getUserAnswer())
                .isCorrect(isCorrect)
                .build());

        // 챕터 통계 getOrCreate 패턴:
        // - 기존에 이 챕터를 푼 적 있으면 → 기존 통계 레코드 사용
        // - 처음 푸는 챕터면 → 새 통계 레코드 생성 후 저장
        UserChapterStats stats = userChapterStatsRepository
                .findByUserIdAndChapterId(userId, request.getChapterId())
                .orElseGet(() -> userChapterStatsRepository.save(
                        UserChapterStats.builder().user(user).chapter(chapter).build()
                ));

        // 정답 여부 기록 + 난이도 재계산 (엔티티 내부 메서드에서 처리)
        stats.recordAnswer(isCorrect);

        return SubmitAnswerResponse.of(isCorrect, problem.getAnswer(), problem.getExplanation(), stats);
    }

    @Override
    // ---- 개념 설명 (FastAPI 호출) ----
    public ConceptResponse getConcept(Long problemId, String grade) {
        Problem problem = problemRepository.findById(problemId).orElseThrow(ProblemNotFoundException::new);

        // 문제의 챕터명 + 문제 본문 + 유저 학년을 FastAPI에 전달해 개념 설명 요청
        String chapterTitle = problem.getChapter().getTitle();

        // FastAPI에 문제 본문 + 학년 + 챕터명 전달 → Gemini 개념 설명 생성
        String concept = aiServerClient.requestConcept(problem.getQuestion(), grade, chapterTitle);

        return ConceptResponse.of(concept);
    }

    @Override
    // ---- 유저 수준 파악용 20문제 (LOW 7 + MEDIUM 7 + HIGH 6) ----
    public List<ProblemResponse> getAssessmentProblems(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        String grade = user.getGrade().name();

        // 난이도별로 나눠서 조회 후 합치기 → 균형 잡힌 수준 파악 가능
        List<Problem> low    = problemRepository.findRandomsByGradeAndDifficulty(grade, "LOW",    7);
        List<Problem> medium = problemRepository.findRandomsByGradeAndDifficulty(grade, "MEDIUM", 7);
        List<Problem> high   = problemRepository.findRandomsByGradeAndDifficulty(grade, "HIGH",   6);

        List<Problem> all = new java.util.ArrayList<>();
        all.addAll(low);
        all.addAll(medium);
        all.addAll(high);

        // 문제 순서를 섞어서 난이도 순서가 티 나지 않게 반환
        java.util.Collections.shuffle(all);

        return all.stream()
                .map(p -> ProblemResponse.of(p, null))
                .toList();
    }

    @Override
    // ---- 유저 학년 기반 랜덤 기출문제 조회 ----
    public ProblemResponse getRandomProblemByGrade(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        // JWT에서 가져온 유저의 학년으로 바로 랜덤 문제 조회
        Problem problem = problemRepository
                .findRandomByGrade(user.getGrade().name())
                .orElseThrow(ProblemNotFoundException::new);

        // 해당 문제의 챕터에 대한 유저 통계 조회 (없으면 null)
        UserChapterStats stats = userChapterStatsRepository
                .findByUserIdAndChapterId(userId, problem.getChapter().getId())
                .orElse(null);

        return ProblemResponse.of(problem, stats);
    }

    @Override
    @Transactional
    // ---- AI 모의문제 생성 (FastAPI 호출 후 DB 저장) ----
    public ProblemResponse getAiProblem(Long userId, Long chapterId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Chapter chapter = chapterRepository.findById(chapterId).orElseThrow(ChapterNotFoundException::new);

        // 현재 유저의 난이도 조회 (없으면 기본 MEDIUM)
        UserChapterStats stats = userChapterStatsRepository
                .findByUserIdAndChapterId(userId, chapterId)
                .orElse(null);
        String difficulty = stats != null ? stats.getCurrentDifficulty().name() : "MEDIUM";

        // FastAPI에 모의문제 생성 요청 (챕터명 + 난이도 + 학년 전달)
        AiServerClient.AiProblemData data = aiServerClient.requestAiProblem(
                chapter.getTitle(), difficulty, user.getGrade().name());

        // AI 생성 문제를 DB에 저장 (기출문제와 동일한 테이블, sourceType=AI로 구분)
        Problem problem = problemRepository.save(Problem.builder()
                .chapter(chapter)
                .difficulty(Difficulty.valueOf(difficulty))
                .problemType(ProblemType.valueOf(data.getProblem_type()))
                .question(data.getQuestion())
                .options(data.getOptions())
                .answer(data.getAnswer())
                .explanation(data.getExplanation())
                .sourceType(SourceType.AI)
                .build());

        return ProblemResponse.of(problem, stats);
    }
}
