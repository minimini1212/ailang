package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.chapter.repository.ChapterRepository;
import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.repository.UserChapterStatsRepository;
import com.example.ailang.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * (학생 × 챕터) 통계 행을 <b>만드는</b> 자리. 트랜잭션을 따로 쓰는 것이 요점이다.
 *
 * <p>🔴 <b>왜 별도 클래스인가.</b> 답안 제출은 통계 행이 없으면 만든다. 그 「없으면 만든다」가
 * 조회 → 삽입 두 걸음이라, 같은 학생의 동시 제출 두 건이 <b>둘 다 없다고 읽고 둘 다 삽입</b>
 * 한다. {@code USER_CHAPTER_STATS (user_id, chapter_id)} 는 유일 제약이므로 뒤쪽이 깨진다 —
 * 학생은 <b>답안을 냈는데 500</b> 을 받는다.
 *
 * <p>제약 위반 자체는 잡을 수 있지만, <b>같은 트랜잭션 안에서는 잡아도 회복이 안 된다.</b>
 * JPA 는 플러시가 실패한 시점에 영속성 컨텍스트를 버려야 하는 상태로 표시하고, 그 트랜잭션은
 * 롤백만 가능해진다. 그래서 삽입을 <b>별도 트랜잭션</b>({@code REQUIRES_NEW})에서 한다.
 *
 * <h2>🔴 여기서 한 번 틀렸다 — 2026-09-15 에 검사가 잡았다</h2>
 *
 * 별도 트랜잭션까지는 맞았는데, <b>{@code catch} 를 이 메서드 «안» 에 두었다.</b>
 * 그러면 이렇게 된다:
 *
 * <pre>
 *   saveAndFlush 실패 → 이 트랜잭션이 「롤백 전용」으로 표시된다
 *   catch 로 삼킨다   → 예외는 사라진다. 여기까지는 의도대로다
 *   메서드가 정상 반환 → 스프링이 «커밋을 시도한다»
 *                     → 롤백 전용이므로 UnexpectedRollbackException
 *                     → 부르는 쪽으로 터져 나간다. 학생은 여전히 500 을 본다
 * </pre>
 *
 * <b>예외 종류만 바뀌고 증상은 그대로였다.</b> 「고쳤다」고 적힌 채 2026-09-11 부터
 * 2026-09-15 까지 남아 있었고, 실제 DB 로 동시 제출을 재 보기 전까지 아무도 몰랐다.
 * 재현과 전말: {@code docs/reports/daily/REPORT_2026-09-15.md}
 *
 * <p>🎯 <b>일반화한 규칙: 실패를 삼키는 자리는 그 실패가 일어난 트랜잭션 «밖» 이어야 한다.</b>
 * 그래서 이 메서드는 이제 아무것도 삼키지 않는다. 제약 위반은 그대로 던지고,
 * 이 트랜잭션은 깨끗이 롤백된다 — 바깥 트랜잭션은 {@code REQUIRES_NEW} 로 잠시 밀려나
 * 있었으므로 아무 영향이 없다. 삼키는 일은 부르는 쪽이 한다
 * ({@code ProblemServiceImpl.getOrCreateStatsForUpdate}).
 *
 * <pre>
 *   A: 조회(없음) ─ create ─────────── 성공
 *   B: 조회(없음) ─ create ─ 제약위반 ─→ 던진다 ─→ 부르는 쪽이 잡는다 ─→ 다시 조회
 *                                                                    ─→ A 가 만든 행을 쓴다
 * </pre>
 *
 * <p>🔴 이 트랜잭션 안에서는 난이도·정답률을 건드리지 않는다. 여기서 하는 일은
 * 「빈 통계 행을 존재하게 만드는 것」 하나뿐이다 — 값을 채우는 것은 잠금을 잡은
 * 바깥 트랜잭션의 몫이다({@code UserChapterStatsRepository.findByUserIdAndChapterIdForUpdate}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserChapterStatsCreator {

    private final UserChapterStatsRepository userChapterStatsRepository;
    private final UserRepository userRepository;
    private final ChapterRepository chapterRepository;

    /**
     * 통계 행을 만든다. <b>이미 있으면 제약 위반을 던진다</b> — 그것이 정상 경로다.
     *
     * <p>⚠️ 엔티티가 아니라 <b>id</b> 를 받는다. 별도 트랜잭션은 영속성 컨텍스트가 다르므로,
     * 바깥에서 관리되던 엔티티를 그대로 넘기면 준영속 상태가 섞인다.
     *
     * <p>⚠️ 이름이 {@code createIfAbsent} 였다. 「없으면」을 이 안에서 처리하는 것처럼
     * 읽혀서 실제로 그렇게 구현했다가 위의 문제가 났다. 하는 일 그대로 {@code create} 다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         이미 있을 때. 부르는 쪽에서 <b>잡아서</b> 다시 조회한다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(Long userId, Long chapterId) {
        // saveAndFlush: 제약 위반을 «이 트랜잭션이 끝나기 전에» 일으켜야 한다.
        //               save 만 하면 커밋 시점에 터지는데, 그때는 이미 프록시가
        //               커밋을 시작한 뒤라 예외의 종류가 달라진다.
        userChapterStatsRepository.saveAndFlush(UserChapterStats.builder()
                .user(userRepository.getReferenceById(userId))
                .chapter(chapterRepository.getReferenceById(chapterId))
                .build());
        log.debug("통계 행 생성 - userId={}, chapterId={}", userId, chapterId);
    }
}
