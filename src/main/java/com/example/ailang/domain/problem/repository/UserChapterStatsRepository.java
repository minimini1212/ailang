package com.example.ailang.domain.problem.repository;

import com.example.ailang.domain.problem.entity.UserChapterStats;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 유저 챕터별 학습 통계 레포지토리
 */
public interface UserChapterStatsRepository extends JpaRepository<UserChapterStats, Long> {

    // 특정 유저의 특정 챕터 통계 조회 (없으면 Optional.empty)
    // ⚠️ 읽기 전용이다. 이 결과를 «고쳐 쓰려면» 아래 findForUpdate 를 쓴다.
    Optional<UserChapterStats> findByUserIdAndChapterId(Long userId, Long chapterId);

    /**
     * 고쳐 쓸 목적으로 통계를 잡는다 — 행에 잠금을 건다 (SELECT ... FOR UPDATE).
     *
     * <p>🔴 <b>왜 필요한가.</b> 답안 제출은 통계를 «읽고 고쳐 쓴다». 잠금이 없으면 같은
     * 학생의 동시 제출 두 건이 같은 값을 읽고 각각 +1 해서 <b>하나가 사라진다</b>
     * (lost update). 사라지는 것은 정답률이고, 정답률은 다음 문제의 난이도를 정한다.
     *
     * <pre>
     *   잠금이 없을 때                       잠금이 있을 때
     *   A: read total=4 ┐                    A: lock ── read 4 ── write 5 ── commit
     *   B: read total=4 ┘ 같은 값을 읽는다   B:        (대기) ─────────────── read 5 ── write 6
     *   A: write 5                           🎯 두 건이 «차례로» 반영된다
     *   B: write 5   ← A 의 1건이 사라졌다
     * </pre>
     *
     * <p>🎯 이 잠금은 「이 문제가 첫 제출인가」 판정도 함께 지켜 준다. 잠금을 먼저 잡고
     * 이력을 조회하면, 뒤 요청은 앞 요청이 <b>커밋한 뒤에</b> 읽으므로 같은 문제를
     * 두 번 세지 않는다. 그래서 부르는 쪽은 <b>잠금 → 이력 조회</b> 순서를 지켜야 한다.
     *
     * <p>⚠️ 잠금은 트랜잭션이 끝날 때까지 유지된다. 이 트랜잭션 안에서
     * <b>외부 호출(AI 서버 등)을 하지 않는다</b> — 몇 초 동안 다른 요청을 세워 둔다.
     * 범위는 (학생 1명 × 챕터 1개) 이므로 다른 학생·다른 챕터는 영향이 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserChapterStats s where s.user.id = :userId and s.chapter.id = :chapterId")
    Optional<UserChapterStats> findByUserIdAndChapterIdForUpdate(@Param("userId") Long userId,
                                                                 @Param("chapterId") Long chapterId);

    // 특정 유저의 전체 챕터 통계 목록 조회 (전체 통계 화면용)
    List<UserChapterStats> findByUserId(Long userId);
}
