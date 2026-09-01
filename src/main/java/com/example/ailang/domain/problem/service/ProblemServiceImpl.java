package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.chapter.exception.ChapterNotFoundException;
import com.example.ailang.domain.chapter.repository.ChapterRepository;
import com.example.ailang.domain.problem.dto.request.SubmitAnswerRequest;
import com.example.ailang.domain.problem.dto.response.AnswerRevealResponse;
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
import org.springframework.transaction.annotation.Propagation;
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
    private final AiServerClient aiServerClient;      // FastAPI 호출용
    private final AiProblemStore aiProblemStore;      // AI 문제의 DB 작업 (트랜잭션 분리)

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

        // 정답 여부 판단
        // - 객관식(MULTIPLE_CHOICE): 서버에서 자동 채점 (정규화 후 비교)
        // - 단답형(SHORT_ANSWER): 유저 자가 채점 결과(selfJudge) 사용
        boolean isCorrect;
        if (problem.getProblemType() == ProblemType.SHORT_ANSWER) {
            // 단답형: selfJudge 없으면 false 처리 (프론트에서 반드시 전달해야 함)
            isCorrect = Boolean.TRUE.equals(request.getSelfJudge());
        } else {
            // 객관식: 정규화 후 자동 비교
            isCorrect = normalizeAnswer(problem.getAnswer())
                    .equalsIgnoreCase(normalizeAnswer(request.getUserAnswer()));
        }

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
    // ---- 단답형 정답 공개 (자가채점용, 이력 저장 없음) ----
    public AnswerRevealResponse revealAnswer(Long problemId) {
        Problem problem = problemRepository.findById(problemId).orElseThrow(ProblemNotFoundException::new);
        return AnswerRevealResponse.of(problem.getAnswer(), problem.getExplanation());
    }

    /**
     * 정답 정규화: LaTeX 표기와 일반 표기 모두 허용
     * - $ 제거, 공백 제거
     * - \frac{a}{b} → a/b
     * - ^{n} → ^n, _{n} → _n (중괄호 제거)
     * - \times → ×, \div → ÷, \cdot → ·, \pm → ±
     * - 나머지 LaTeX 명령어 제거 (\word)
     * - 남은 중괄호 제거
     */
    private String normalizeAnswer(String answer) {
        String s = answer;
        s = s.replaceAll("\\$", "");                                          // $ 제거
        s = s.replaceAll("\\s+", "");                                         // 공백 제거
        // 원문자 → 숫자 변환 (DB 정답 "①" ↔ 유저 제출 "1" 매칭)
        s = s.replace("①", "1").replace("②", "2").replace("③", "3")
             .replace("④", "4").replace("⑤", "5");
        s = s.replaceAll("\\\\frac\\{([^}]*)\\}\\{([^}]*)\\}", "$1/$2");      // \frac{a}{b} → a/b
        s = s.replaceAll("\\^\\{([^}]+)\\}", "^$1");                          // ^{n} → ^n
        s = s.replaceAll("_\\{([^}]+)\\}", "_$1");                            // _{n} → _n
        s = s.replaceAll("\\\\times", "×");
        s = s.replaceAll("\\\\div", "÷");
        s = s.replaceAll("\\\\cdot", "·");
        s = s.replaceAll("\\\\pm", "±");
        s = s.replaceAll("\\\\[a-zA-Z]+", "");                               // 나머지 LaTeX 명령어 제거
        s = s.replaceAll("[{}]", "");                                         // 중괄호 제거
        return s;
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
    // ---- AI 모의문제 생성 (FastAPI 호출 후 DB 저장) ----
    //
    // 🔴 트랜잭션을 «AI 호출 앞뒤로» 나눈다.
    //    AI 호출은 2026-09-01 실측으로 **13초** 걸린다. 트랜잭션 안에서 부르면 그동안
    //    Oracle 커넥션을 붙잡고 있어, 동시에 열 명만 요청해도 로그인 같은 무관한 요청까지
    //    대기한다 (docs/rules/ai-call-policy.md R1).
    //
    // ⚠️ NOT_SUPPORTED 가 필요한 이유: 이 클래스에 @Transactional(readOnly = true) 가
    //    걸려 있어서, 아무것도 안 붙이면 «읽기 전용 트랜잭션»이 AI 호출 내내 열려 있게 된다.
    //    여기서 명시적으로 트랜잭션을 쓰지 않겠다고 선언해야 실제로 안 열린다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProblemResponse getAiProblem(Long userId, Long chapterId) {
        // ① 짧은 읽기
        AiProblemStore.Context ctx = aiProblemStore.loadContext(userId, chapterId);

        // ② 트랜잭션 밖에서 AI 호출 (느리고, 자주 실패한다)
        AiServerClient.AiProblemData data = aiServerClient.requestAiProblem(
                ctx.chapterTitle(), ctx.difficulty().name(), ctx.grade());

        // ③ 짧은 쓰기
        return aiProblemStore.save(userId, chapterId, ctx.difficulty(), data);
    }
}
