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
import com.example.ailang.domain.problem.exception.AnswerNotRevealableException;
import com.example.ailang.domain.problem.exception.ProblemNotFoundException;
import com.example.ailang.domain.problem.exception.ProblemNotInChapterException;
import com.example.ailang.domain.problem.exception.SelfJudgeRequiredException;
import com.example.ailang.domain.problem.repository.ProblemRepository;
import com.example.ailang.domain.problem.repository.UserChapterStatsRepository;
import com.example.ailang.domain.problem.repository.UserProblemHistoryRepository;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.exception.UserNotFoundException;
import com.example.ailang.domain.user.repository.UserRepository;
import com.example.ailang.global.client.AiServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 문제 서비스 구현체
 */
@Slf4j
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
        Difficulty wanted = stats != null ? stats.getCurrentDifficulty() : Difficulty.MEDIUM;

        // 🔴 그 난이도가 이 챕터에 «없을 수 있다». 실측(2026-09-09)에서 24칸 중 7칸이 비었다 —
        //    난이도가 단원별로 몰려 있어서다(입체도형은 전부 상, 정수와 유리수는 전부 하).
        //    예전에는 여기서 404 가 났다. 학생이 그 단원을 처음 열면 통계가 없어
        //    중(MEDIUM) 으로 찾는데 0건이기 때문이다.
        //    ⚠️ 난이도를 «바꿔치기» 하는 것이 아니다. 응답에는 실제로 준 문제의 난이도가
        //       그대로 실려 나가므로 화면이 학생에게 사실대로 보여 줄 수 있다.
        Problem problem = null;
        for (Difficulty candidate : DifficultyFallback.order(wanted)) {
            problem = problemRepository
                    .findRandomByChapterIdAndDifficulty(chapterId, candidate.name())
                    .orElse(null);
            if (problem != null) {
                if (candidate != wanted) {
                    log.info("[맞춤문제] 챕터 {} 에 {} 문제가 없어 {} 로 대신합니다",
                            chapterId, wanted, candidate);
                }
                break;
            }
        }
        // 세 난이도 모두 0건이면 그 챕터에 기출문제가 진짜로 없는 것이다.
        if (problem == null) {
            throw new ProblemNotFoundException();
        }

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

        // 🔴 두 개의 id 가 오면 관계를 증명한 뒤에 쓴다.
        //    예전에는 problemId 가 chapterId 에 속하는지 확인하지 않아서, 학생이 통계를
        //    올릴 챕터를 마음대로 고를 수 있었다 (안 푼 챕터에 정답을 쌓는 식으로).
        if (!problem.getChapter().getId().equals(chapter.getId())) {
            throw new ProblemNotInChapterException();
        }

        boolean isCorrect = grade(problem, request);

        // 풀이 이력 1건 저장 (USER_PROBLEM_HISTORY에 INSERT)
        // 🎯 이력은 «사실» 이므로 언제나 남긴다. 반복 제출이어도 남긴다.
        boolean firstAttempt = !userProblemHistoryRepository
                .existsByUserIdAndProblemId(userId, problemId);

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

        // 🔴 통계에는 «문제당 첫 제출» 만 반영한다.
        //    제출 응답이 정답을 알려주므로(설계), 중복 제출을 막지 않으면
        //    ① 틀린 답을 내서 정답을 알아내고 ② 정답을 반복 제출해서
        //    정답률을 원하는 값으로 만들 수 있다. 객관식만으로도 된다.
        //    이력은 위에서 이미 남겼다 — 통계(요약)에만 안 넣는 것이다.
        if (firstAttempt) {
            stats.recordAnswer(isCorrect);
        }

        return SubmitAnswerResponse.of(isCorrect, problem.getAnswer(), problem.getExplanation(), stats);
    }

    /**
     * 정답 여부를 정한다.
     *
     * <p>🔴 클라이언트가 보낸 판정을 그대로 쓰는 자리는 단답형 하나뿐이고, 그것도
     * 값이 없으면 «틀림» 이 아니라 «요청 거부» 다. 「모른다」를 값으로 접지 않는다.
     */
    private boolean grade(Problem problem, SubmitAnswerRequest request) {
        if (problem.getProblemType() == ProblemType.SHORT_ANSWER) {
            // 단답형: 학생 자가 채점 (수학 단답형은 2/4·1/2·0.5 가 다 맞는 답이라
            //        문자열 비교가 어렵다 — docs/rules/grading-and-difficulty.md §4)
            // 🔴 값이 없으면 오답으로 기록하지 않는다. 프론트 버그가 학생 정답률을 깎는다.
            if (request.getSelfJudge() == null) {
                throw new SelfJudgeRequiredException();
            }
            return request.getSelfJudge();
        }
        // 객관식: 서버가 채점한다
        return AnswerNormalizer.matches(problem.getAnswer(), request.getUserAnswer());
    }

    @Override
    // ---- 단답형 정답 공개 (자가채점용, 이력 저장 없음) ----
    //
    // 🔴 «단답형만» 이다. 예전에는 유형을 안 봐서 객관식 정답도 제출 전에 나갔다.
    //    객관식은 서버가 채점하므로 정답을 미리 줄 이유가 없다.
    //
    // ⚠️ 「이미 제출했는지」는 확인하지 않는다 — 확인하면 안 된다.
    //    단답형 자가채점은 «정답 공개 → 학생이 판단 → 제출» 순서라(Assessment.tsx),
    //    제출 여부를 요구하면 그 흐름이 통째로 막힌다.
    public AnswerRevealResponse revealAnswer(Long problemId) {
        Problem problem = problemRepository.findById(problemId).orElseThrow(ProblemNotFoundException::new);

        if (problem.getProblemType() != ProblemType.SHORT_ANSWER) {
            throw new AnswerNotRevealableException();
        }
        return AnswerRevealResponse.of(problem.getAnswer(), problem.getExplanation());
    }

    @Override
    // ---- 개념 설명 (FastAPI 호출) ----
    public ConceptResponse getConcept(Long problemId, String grade) {
        Problem problem = problemRepository.findById(problemId).orElseThrow(ProblemNotFoundException::new);

        // 문제의 단원명(+유형) + 문제 본문 + 유저 학년을 FastAPI에 전달해 개념 설명 요청
        //
        // 🔴 유형을 «반드시» 붙인다. 2026-09-09 에 챕터를 유형 331개에서 대단원 8개로
        //    묶었는데, 챕터명만 보내면 예전에 「맞꼭지각(1)」이 가던 자리에 「기본 도형」이
        //    간다 — 개념 설명이 그만큼 뭉뚱그려진다. 유형은 Problem.topic 에 남겨 뒀다.
        String topic = problem.getTopic();
        String chapterTitle = problem.getChapter().getTitle();
        String context = (topic == null || topic.isBlank())
                ? chapterTitle
                : chapterTitle + " · " + topic;

        // FastAPI에 문제 본문 + 학년 + 단원·유형 전달 → Gemini 개념 설명 생성
        String concept = aiServerClient.requestConcept(problem.getQuestion(), grade, context);

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
