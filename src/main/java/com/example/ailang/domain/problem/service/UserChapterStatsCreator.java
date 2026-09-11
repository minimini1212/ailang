package com.example.ailang.domain.problem.service;

import com.example.ailang.domain.chapter.repository.ChapterRepository;
import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.repository.UserChapterStatsRepository;
import com.example.ailang.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * (학생 × 챕터) 통계 행을 «없으면 만드는» 자리. 트랜잭션을 따로 쓰는 것이 요점이다.
 *
 * <p>🔴 <b>왜 별도 클래스인가.</b> 답안 제출은 통계 행이 없으면 만든다. 그 「없으면 만든다」가
 * 조회 → 삽입 두 걸음이라, 같은 학생의 동시 제출 두 건이 <b>둘 다 없다고 읽고 둘 다 삽입</b>
 * 한다. {@code USER_CHAPTER_STATS (user_id, chapter_id)} 는 유일 제약이므로 뒤쪽이 깨진다 —
 * 학생은 <b>답안을 냈는데 500</b> 을 받는다.
 *
 * <p>제약 위반 자체는 잡을 수 있지만, <b>같은 트랜잭션 안에서는 잡아도 회복이 안 된다.</b>
 * JPA 는 플러시가 실패한 시점에 영속성 컨텍스트를 버려야 하는 상태로 표시하고, 그 트랜잭션은
 * 롤백만 가능해진다. 그래서 삽입을 <b>별도 트랜잭션</b>({@code REQUIRES_NEW})에서 하고,
 * 실패하면 «남이 먼저 만들었다»는 뜻이므로 그 행을 쓰면 된다.
 *
 * <pre>
 *   A: 조회(없음) ─ 삽입 ───────── 성공
 *   B: 조회(없음) ─ 삽입 ─ 제약위반 ─→ 삼킨다 ─→ 다시 조회 ─→ A 가 만든 행을 쓴다
 *                         🎯 여기서 죽지 않는다. 학생은 500 을 안 본다
 * </pre>
 *
 * <p>⚠️ 「만들었다」와 「이미 있었다」를 구분해 돌려주지 않는다. 부르는 쪽이 알 필요가 없고,
 * 구분하려면 그것 자체가 또 하나의 경쟁이 된다. 부르는 쪽은 <b>이 호출 뒤에 다시 조회</b>한다.
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
     * 통계 행이 없으면 만든다. 이미 있으면(동시 요청이 먼저 만들었으면) 아무 일도 안 한다.
     *
     * <p>⚠️ 엔티티가 아니라 <b>id</b> 를 받는다. 별도 트랜잭션은 영속성 컨텍스트가 다르므로,
     * 바깥에서 관리되던 엔티티를 그대로 넘기면 준영속 상태가 섞인다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfAbsent(Long userId, Long chapterId) {
        try {
            // saveAndFlush: 제약 위반을 «이 자리에서» 받아야 한다.
            //               save 만 하면 커밋 시점에 터져서 여기서 잡을 수 없다.
            userChapterStatsRepository.saveAndFlush(UserChapterStats.builder()
                    .user(userRepository.getReferenceById(userId))
                    .chapter(chapterRepository.getReferenceById(chapterId))
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 🎯 실패가 아니다. 동시 요청이 먼저 만들었다는 뜻이고, 원하던 결과는 이미 이뤄졌다.
            log.debug("통계 행이 이미 있음 (동시 제출) - userId={}, chapterId={}", userId, chapterId);
        }
    }
}
