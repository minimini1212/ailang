package com.example.ailang.global.loader;

import com.example.ailang.domain.problem.entity.Problem;
import com.example.ailang.domain.problem.enums.Difficulty;
import com.example.ailang.domain.problem.enums.ProblemType;
import com.example.ailang.domain.problem.enums.SourceType;
import com.example.ailang.domain.problem.repository.ProblemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 같은 원본 문제가 두 번 저장되지 않는가 — <b>DB 제약으로</b>.
 *
 * <p>🔴 <b>왜 이 검사가 필요한가.</b> {@code PROBLEMS.SOURCE_ID} 의 유일 제약은 적재를
 * 「전부 아니면 전무」에서 벗어나게 하려고 2026-09-09 에 새로 걸었다. 그런데 <b>이 제약을
 * 실제로 확인하는 것이 아무것도 없다.</b> {@code Problem} 엔티티의 {@code unique = true} 를
 * 지워도 어떤 검사도 빨간불이 되지 않고, {@code ddl-auto: update} 는 <b>이미 있는 제약을
 * 지우지 않으므로</b> 개발 DB 에서는 한동안 아무 일도 안 일어난다 — 새로 만든 DB 에서만
 * 조용히 사라진다. 그때 드러나는 증상은 「학생이 같은 문제를 두 번 만난다」와
 * 「정답률 분모가 부풀려진다」이고, 원인까지 되짚기는 매우 어렵다.
 *
 * <p>🎯 이 검사가 잡는 변형: {@code @Column(name = "source_id", length = 50, unique = true)}
 * 에서 {@code unique = true} 를 빼는 것.
 *
 * <p>⚠️ 실제 DB 가 필요하다. 제약을 거는 것도 어기는 것도 DB 가 하는 일이라 가짜로는
 * 재현되지 않는다. 적재된 기출문제가 없으면 <b>조용히 통과하지 않고 건너뛴다.</b>
 */
@SpringBootTest
@DisplayName("기출문제 적재 — 같은 원본은 두 번 들어갈 수 없다")
class SourceIdUniqueTest {

    @Autowired ProblemRepository problemRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @PersistenceContext EntityManager em;

    @Test
    @DisplayName("🔴 이미 있는 원본 id 로 한 건 더 저장하면 DB 가 거부한다")
    void 같은_원본_id_는_두_번_안_들어간다() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Problem existing = tx.execute(status -> {
            List<Problem> found = em.createQuery(
                            "select p from Problem p where p.sourceId is not null order by p.id", Problem.class)
                    .setMaxResults(1)
                    .getResultList();
            return found.isEmpty() ? null : found.get(0);
        });

        Assumptions.assumeTrue(existing != null,
                "원본 id 를 가진 기출문제가 없어 건너뜁니다 — 적재가 안 된 DB 입니다");

        Long chapterId = existing.getChapter().getId();
        String duplicatedSourceId = existing.getSourceId();

        // 🎯 저장은 «실패해야» 성공이다. 실패하는 트랜잭션이므로 별도로 돌려, 다른 검사에
        //    영향이 가지 않게 한다. 실패하면 아무 행도 안 남으므로 뒤처리도 필요 없다.
        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        problemRepository.saveAndFlush(Problem.builder()
                                .chapter(em.getReference(
                                        com.example.ailang.domain.chapter.entity.Chapter.class, chapterId))
                                .difficulty(Difficulty.MEDIUM)
                                .problemType(ProblemType.SHORT_ANSWER)
                                .question("유일 제약 검사용 문제")
                                .answer("1")
                                .explanation("유일 제약 검사용 해설")
                                .sourceType(SourceType.REAL)
                                .sourceId(duplicatedSourceId)
                                .build())))
                .as("같은 원본 id 가 두 번 들어갔다 — 학생이 같은 문제를 두 번 만나고 정답률 분모가 부푼다")
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
