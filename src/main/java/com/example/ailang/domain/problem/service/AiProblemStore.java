package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.chapter.exception.ChapterNotFoundException;
import com.example.ailang.domain.chapter.repository.ChapterRepository;
import com.example.ailang.domain.problem.dto.response.ProblemResponse;
import com.example.ailang.domain.problem.entity.Problem;
import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.enums.Difficulty;
import com.example.ailang.domain.problem.enums.ProblemType;
import com.example.ailang.domain.problem.enums.SourceType;
import com.example.ailang.domain.problem.repository.ProblemRepository;
import com.example.ailang.domain.problem.repository.UserChapterStatsRepository;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.exception.UserNotFoundException;
import com.example.ailang.domain.user.repository.UserRepository;
import com.example.ailang.global.client.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.ailang.domain.user.entity.UserGrades;
/**
 * AI 모의문제의 DB 작업만 담당한다.
 *
 * 🔴 왜 별도 빈인가 — 트랜잭션을 «AI 호출 앞뒤로 짧게» 나누기 위해서다.
 *    AI 호출은 실측 13초다. 그 시간 동안 Oracle 커넥션을 붙잡으면 동시에 열 명만
 *    요청해도 로그인 같은 무관한 요청까지 대기한다 (docs/rules/ai-call-policy.md R1).
 *
 * ⚠️ 같은 클래스 안에서 {@code this.method()} 로 부르면 Spring 프록시를 안 타서
 *    {@code @Transactional} 이 **아무 일도 하지 않는다.** 그래서 클래스를 나눈다.
 *    (한 번 같은 클래스의 protected 메서드로 만들었다가 이 함정에 빠졌다.)
 */
@Component
@RequiredArgsConstructor
public class AiProblemStore {

    private final UserRepository userRepository;
    private final ChapterRepository chapterRepository;
    private final ProblemRepository problemRepository;
    private final UserChapterStatsRepository userChapterStatsRepository;

    /** AI 호출 직전에 DB 에서 꺼내 둔 값. 트랜잭션 밖으로 들고 나가려고 만든다. */
    public record Context(String chapterTitle, Difficulty difficulty, String grade) {
    }

    /**
     * ① AI 호출에 필요한 값만 읽는 짧은 트랜잭션.
     *
     * ⚠️ 엔티티가 아니라 «값» 을 꺼내 나간다. 엔티티를 들고 나가면 트랜잭션이 닫힌 뒤
     *    지연 로딩에서 터진다.
     */
    @Transactional(readOnly = true)
    public Context loadContext(Long userId, Long chapterId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Chapter chapter = chapterRepository.findById(chapterId).orElseThrow(ChapterNotFoundException::new);

        Difficulty difficulty = userChapterStatsRepository
                .findByUserIdAndChapterId(userId, chapterId)
                .map(UserChapterStats::getCurrentDifficulty)
                .orElse(Difficulty.MEDIUM);

        // ⚠️ 학년이 없는 유저(구글 가입)는 여기서 NPE 가 난다. 별도 과제다 (TODOS.md 2절).
        return new Context(chapter.getTitle(), difficulty, UserGrades.requireName(user));
    }

    /** ② 생성된 문제를 저장하는 짧은 트랜잭션. */
    @Transactional
    public ProblemResponse save(Long userId, Long chapterId, Difficulty difficulty,
                                AiServerClient.AiProblemData data) {
        Chapter chapter = chapterRepository.findById(chapterId).orElseThrow(ChapterNotFoundException::new);

        // 기출문제와 같은 표에 넣되 sourceType=AI 로 구분한다.
        // 🔴 이 구분이 빠지면 검증되지 않은 정답이 학생의 난이도를 정하게 된다.
        Problem problem = problemRepository.save(Problem.builder()
                .chapter(chapter)
                .difficulty(difficulty)
                .problemType(ProblemType.valueOf(data.getProblem_type()))
                .question(data.getQuestion())
                .options(data.getOptions())
                .answer(data.getAnswer())
                .explanation(data.getExplanation())
                .sourceType(SourceType.AI)
                .build());

        UserChapterStats stats = userChapterStatsRepository
                .findByUserIdAndChapterId(userId, chapterId)
                .orElse(null);

        return ProblemResponse.of(problem, stats);
    }
}
