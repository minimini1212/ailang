package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.problem.dto.request.SubmitAnswerRequest;
import com.example.ailang.domain.problem.entity.Problem;
import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.enums.ProblemType;
import com.example.ailang.domain.problem.repository.UserChapterStatsRepository;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.AuthProvider;
import com.example.ailang.domain.user.enums.Grade;
import com.example.ailang.domain.user.enums.UserRole;
import com.example.ailang.domain.user.enums.UserStatus;
import com.example.ailang.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 학생이 <b>동시에</b> 두 건을 제출했을 때 통계가 깨지지 않는가.
 *
 * <p>🔴 <b>이 검사는 실제 DB 가 필요하다.</b> 여기서 보려는 세 가지가 전부 DB 가 하는 일이라
 * 가짜로는 재현되지 않는다 — 유일 제약, 행 잠금, 커밋 순서. 그래서 순수 로직 검사들과 달리
 * Oracle 이 떠 있어야 돌고, 없으면 조용히 통과하지 않고 건너뛴다({@code Assumptions}).
 * 통과한 것처럼 보이는 것이 안 돈 것보다 나쁘다.
 *
 * <p>2026-09-11 에 고친 세 가지를 못 박는다. 고칠 당시에는 재 볼 수단이 없어
 * <b>「고쳤지만 확인 못 함」</b> 상태로 남아 있었다:
 * <ol>
 *   <li>통계 행이 없을 때 둘 다 만들어 유일 제약 위반 → 학생이 <b>500</b> 을 받는다</li>
 *   <li>둘 다 같은 값을 읽고 각각 +1 → 한 건이 사라진다 (lost update)</li>
 *   <li>같은 문제인데 둘 다 「첫 제출」로 읽어 정답률에 두 번 센다</li>
 * </ol>
 *
 * <p>⚠️ 이 검사는 <b>진짜 행을 쓴다</b>. 검사 전용 학생을 새로 만들고 끝나면 지운다 —
 * 기존 학생의 통계를 건드리면 정답률이 오염되고, 그 오염은 난이도로 번진다.
 */
@SpringBootTest
@DisplayName("답안 제출 — 같은 학생의 동시 제출")
class SubmitAnswerConcurrencyTest {

    @Autowired ProblemService problemService;
    @Autowired UserRepository userRepository;
    @Autowired UserChapterStatsRepository statsRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @PersistenceContext EntityManager em;

    private TransactionTemplate tx;
    private Long userId;
    private Long chapterId;
    private List<Long> shortAnswerProblemIds;

    @BeforeEach
    void setUp() {
        // 🔬 기본값(3줄)이면 «어느 트랜잭션이 터졌는지» 가 잘려 안 보인다.
        //    2026-09-15 에 이 검사가 잡은 결함이 정확히 그 정보에 달려 있었다 —
        //    예외 이름만 보면 원인을 못 찾고, 프록시 호출 스택을 봐야 안다.
        org.assertj.core.api.Assertions.setMaxStackTraceElementsDisplayed(60);
        tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            // 같은 챕터에 단답형이 2개 이상 있는 챕터를 고른다.
            // 🎯 단답형을 쓰는 이유: 자가채점이라 정답 여부가 입력대로 정해진다.
            //    객관식이면 정규화 결과에 따라 정답 여부가 달라져 셈이 흔들린다.
            List<Chapter> chapters = em.createQuery("select c from Chapter c order by c.id", Chapter.class)
                    .getResultList();
            for (Chapter c : chapters) {
                List<Problem> problems = em.createQuery(
                                "select p from Problem p where p.chapter.id = :cid and p.problemType = :t order by p.id",
                                Problem.class)
                        .setParameter("cid", c.getId())
                        .setParameter("t", ProblemType.SHORT_ANSWER)
                        .setMaxResults(2)
                        .getResultList();
                if (problems.size() == 2) {
                    chapterId = c.getId();
                    shortAnswerProblemIds = problems.stream().map(Problem::getId).toList();
                    break;
                }
            }

            User user = User.builder()
                    .email("concurrency-test-" + UUID.randomUUID() + "@example.invalid")
                    .nickname("동시제출검사")
                    .provider(AuthProvider.LOCAL)
                    .status(UserStatus.ACTIVE)
                    .role(UserRole.ROLE_USER)
                    .grade(Grade.MIDDLE_1)
                    .build();
            userRepository.save(user);
            userId = user.getId();
        });

        Assumptions.assumeTrue(chapterId != null,
                "단답형 문제가 2개 이상인 챕터가 없어 건너뜁니다 — 적재가 안 된 DB 입니다");
    }

    @AfterEach
    void tearDown() {
        if (userId == null) {
            return;
        }
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from UserProblemHistory h where h.user.id = :uid")
                    .setParameter("uid", userId).executeUpdate();
            em.createQuery("delete from UserChapterStats s where s.user.id = :uid")
                    .setParameter("uid", userId).executeUpdate();
            em.createQuery("delete from User u where u.id = :uid")
                    .setParameter("uid", userId).executeUpdate();
        });
    }

    @Test
    @DisplayName("🔴 서로 다른 문제 2건을 동시에 내도 500 이 안 나고, 2건 다 세어진다")
    void 서로_다른_문제_2건_동시제출() throws Exception {
        // 🔴 잡는 변형: getOrCreateStatsForUpdate 의 잠금을 빼거나, 삽입을 별도 트랜잭션에서
        //    빼는 것. 통계 행이 아직 없는 상태에서 둘이 동시에 들어오므로 둘 다 만들려 하고,
        //    유일 제약이 뒤쪽을 깬다 — 학생은 답을 냈는데 500 을 받는다.
        //    그리고 잠금이 없으면 둘 다 total=0 을 읽고 각각 1 을 써서 1 로 끝난다.
        List<Throwable> failures = submitConcurrently(
                shortAnswerProblemIds.get(0), shortAnswerProblemIds.get(1));

        assertThat(failures)
                .as("동시 제출이 예외를 냈다 — 학생에게는 500 으로 보인다")
                .isEmpty();

        UserChapterStats stats = loadStats();
        assertThat(stats.getTotalCount())
                .as("두 건이 각각 세어져야 한다 (1 이면 한 건이 사라진 것 — lost update)")
                .isEqualTo(2);
        assertThat(stats.getCorrectCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("🔴 같은 문제 2건을 동시에 내면 1건만 세어진다")
    void 같은_문제_2건_동시제출() throws Exception {
        // 🔴 잡는 변형: 「첫 제출인가」 판정을 잠금 앞으로 옮기는 것.
        //    앞에 두면 둘 다 「이력 없음」을 읽어 둘 다 첫 제출로 세므로, 같은 문제를
        //    두 번 내서 정답률을 올릴 수 있다. 잠금이 있어도 순서가 틀리면 새어 나간다.
        Long sameProblemId = shortAnswerProblemIds.get(0);
        List<Throwable> failures = submitConcurrently(sameProblemId, sameProblemId);

        assertThat(failures).as("동시 제출이 예외를 냈다").isEmpty();

        UserChapterStats stats = loadStats();
        assertThat(stats.getTotalCount())
                .as("통계에는 문제당 첫 제출만 들어간다 (2 면 반복 제출로 정답률을 올릴 수 있다)")
                .isEqualTo(1);
        assertThat(stats.getCorrectCount()).isEqualTo(1);
    }

    /** 두 건을 최대한 겹쳐서 보낸다. 래치로 출발선을 맞춘다. */
    private List<Throwable> submitConcurrently(Long firstProblemId, Long secondProblemId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLine = new CountDownLatch(1);
        List<Future<Throwable>> futures = new ArrayList<>();

        for (Long problemId : List.of(firstProblemId, secondProblemId)) {
            futures.add(pool.submit(() -> {
                try {
                    startLine.await(5, TimeUnit.SECONDS);
                    problemService.submitAnswer(userId, problemId, request(chapterId));
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
        }
        startLine.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> future : futures) {
            Throwable thrown = future.get(30, TimeUnit.SECONDS);
            if (thrown != null) {
                failures.add(thrown);
            }
        }
        pool.shutdown();
        return failures;
    }

    /** DTO 에 생성자가 없다. 운영 코드를 검사 때문에 고치지 않으려고 필드에 직접 넣는다. */
    private SubmitAnswerRequest request(Long targetChapterId) {
        SubmitAnswerRequest request = new SubmitAnswerRequest();
        ReflectionTestUtils.setField(request, "chapterId", targetChapterId);
        ReflectionTestUtils.setField(request, "userAnswer", "검사용 답안");
        ReflectionTestUtils.setField(request, "selfJudge", Boolean.TRUE);
        return request;
    }

    private UserChapterStats loadStats() {
        return statsRepository.findByUserIdAndChapterId(userId, chapterId)
                .orElseThrow(() -> new AssertionError("통계 행이 만들어지지 않았다"));
    }
}
