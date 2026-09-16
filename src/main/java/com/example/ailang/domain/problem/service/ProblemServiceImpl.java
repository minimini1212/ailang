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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import com.example.ailang.domain.user.entity.UserGrades;
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
    // 통계 행 «생성» 만 별도 트랜잭션으로 한다 — 동시 제출의 유일 제약 위반 때문 (아래 submitAnswer)
    private final UserChapterStatsCreator userChapterStatsCreator;

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

        // 🔴 순서가 규칙이다 — 잠금을 «먼저» 잡는다.
        //
        //    같은 학생의 동시 제출 두 건이 이 아래를 겹쳐서 실행하면 세 가지가 깨진다.
        //      ① 통계 행이 없을 때 둘 다 만들어 유일 제약 위반 → 학생이 500 을 받는다
        //      ② 둘 다 같은 값을 읽고 각각 +1 → 한 건이 사라진다 (lost update)
        //      ③ 같은 문제인데 둘 다 「첫 제출」로 읽어 정답률에 두 번 센다
        //
        //    잠금을 잡은 뒤에 이력을 조회하면 셋이 함께 닫힌다. 뒤 요청은 앞 요청이
        //    «커밋한 뒤에» 이력을 읽으므로 ③ 도 자연히 없어진다.
        //
        //      A: 잠금 ── 이력조회(없음) ── 이력저장 ── 통계+1 ── commit
        //      B:        (대기) ─────────────────────────────────── 이력조회(있음) ──
        //                                                           이력저장 · 통계는 그대로
        //
        //    ⚠️ 이 잠금은 트랜잭션이 끝날 때까지 유지된다. 그래서 이 메서드 안에서
        //       외부 호출(AI 서버 등)을 하지 않는다 — 하면 다른 요청을 몇 초씩 세워 둔다.
        UserChapterStats stats = getOrCreateStatsForUpdate(userId, chapter.getId());

        // 🎯 이력은 «사실» 이므로 언제나 남긴다. 반복 제출이어도 남긴다.
        //    ⚠️ 「첫 제출인가」 판정은 잠금을 잡은 «뒤» 에 해야 한다. 앞에서 하면
        //       동시 요청 둘이 같은 답을 얻어, 잠금이 있어도 두 번 세어진다.
        boolean firstAttempt = !userProblemHistoryRepository
                .existsByUserIdAndProblemId(userId, problemId);

        userProblemHistoryRepository.save(UserProblemHistory.builder()
                .user(user)
                .problem(problem)
                .userAnswer(request.getUserAnswer())
                .isCorrect(isCorrect)
                .build());

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
     * 고쳐 쓸 (학생 × 챕터) 통계를 «잠금까지 잡아» 가져온다. 없으면 만들고 다시 잡는다.
     *
     * <p>🔴 삽입은 {@link UserChapterStatsCreator} 가 <b>별도 트랜잭션</b>에서 한다.
     * 같은 트랜잭션에서 유일 제약 위반을 잡으면 그 트랜잭션은 롤백밖에 못 하게 되므로,
     * 「남이 먼저 만들었으면 그 행을 쓴다」를 그 안에서 할 수 없다.
     *
     * <p>🔴 <b>그래서 잡는 자리가 여기다.</b> 2026-09-11 에는 {@code Creator} 안에서 잡았는데,
     * 삼켜도 그 트랜잭션은 이미 「롤백 전용」이라 <b>반환하면서 커밋할 때 다시 터졌다</b>
     * ({@code UnexpectedRollbackException}). 학생이 보는 것은 똑같은 500 이었다.
     * 실패를 삼키는 자리는 <b>그 실패가 일어난 트랜잭션 밖</b>이어야 한다 —
     * {@code REQUIRES_NEW} 덕분에 이 바깥 트랜잭션은 그 실패에 물들지 않는다.
     *
     * <p>⚠️ 두 번째 조회까지 비어 있는 경우는 «있을 수 없는» 상태다. 만들기가 성공했거나
     * 제약 위반이 났거나 둘 중 하나이고, 제약 위반이면 다른 트랜잭션이 이미 커밋한 것이다.
     * 조용히 넘기지 않고 터뜨린다 — 그 상태로 진행하면 정답률이 말없이 사라진다.
     */
    private UserChapterStats getOrCreateStatsForUpdate(Long userId, Long chapterId) {
        Optional<UserChapterStats> locked =
                userChapterStatsRepository.findByUserIdAndChapterIdForUpdate(userId, chapterId);
        if (locked.isPresent()) {
            return locked.get();
        }

        try {
            userChapterStatsCreator.create(userId, chapterId);
        } catch (DataIntegrityViolationException e) {
            // 🎯 실패가 아니다. 동시 제출이 «먼저» 만들었다는 뜻이고, 원하던 결과
            //    (행이 존재함) 는 이미 이뤄졌다. 아래에서 그 행을 잠가 잡는다.
            log.debug("통계 행이 이미 있음 (동시 제출) - userId={}, chapterId={}", userId, chapterId);
        }

        return userChapterStatsRepository.findByUserIdAndChapterIdForUpdate(userId, chapterId)
                .orElseThrow(() -> new IllegalStateException(
                        "통계 행을 만든 직후에 찾지 못했다 - userId=" + userId + ", chapterId=" + chapterId));
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
        // 객관식: 서버가 채점한다.
        // 🔄 2026-09-16: matches 가 아니라 matchesChoice 다. 정답 칸에 「(해답)③」·「(5)」
        //    처럼 자료 잡음이 섞인 건이 14건 있었고, 그동안 «무엇을 골라도 오답» 이었다.
        return AnswerNormalizer.matchesChoice(problem.getAnswer(), request.getUserAnswer());
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
        String grade = UserGrades.requireName(user);

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
                .findRandomByGrade(UserGrades.requireName(user))
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
