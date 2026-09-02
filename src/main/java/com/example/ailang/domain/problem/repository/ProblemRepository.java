package com.example.ailang.domain.problem.repository;

import com.example.ailang.domain.problem.entity.Problem;
import com.example.ailang.domain.problem.enums.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 문제 레포지토리
 * - 맞춤형 문제(난이도 기반), 랜덤 문제 조회에 사용
 */
public interface ProblemRepository extends JpaRepository<Problem, Long> {

    // 특정 챕터 + 난이도에 해당하는 기출문제 중 랜덤 1개 조회 (맞춤형 기출문제용)
    @Query(value = "SELECT * FROM PROBLEMS WHERE CHAPTER_ID = :chapterId AND DIFFICULTY = :difficulty AND SOURCE_TYPE = 'REAL' ORDER BY DBMS_RANDOM.VALUE FETCH FIRST 1 ROWS ONLY", nativeQuery = true)
    Optional<Problem> findRandomByChapterIdAndDifficulty(@Param("chapterId") Long chapterId,
                                                         @Param("difficulty") String difficulty);

    // 특정 챕터의 기출문제 중 난이도 무관하게 랜덤 1개 조회 (랜덤 기출문제용)
    @Query(value = "SELECT * FROM PROBLEMS WHERE CHAPTER_ID = :chapterId AND SOURCE_TYPE = 'REAL' ORDER BY DBMS_RANDOM.VALUE FETCH FIRST 1 ROWS ONLY", nativeQuery = true)
    Optional<Problem> findRandomByChapterId(@Param("chapterId") Long chapterId);

    // 특정 챕터의 전체 문제 목록 (관리자용)
    List<Problem> findByChapterId(Long chapterId);

    /**
     * 출처별 문제 수.
     *
     * <p>🔴 적재 여부는 «기출문제가 있는가» 로 판단해야 한다. 전체 건수로 보면,
     * AI 모의문제도 같은 표에 저장되므로 <b>AI 문제가 한 건만 생겨도 기출 적재를
     * 영원히 건너뛴다.</b> ({@code DataLoader} 참고)
     */
    long countBySourceType(SourceType sourceType);

    // 유저 학년에 해당하는 기출문제 중 랜덤 1개 조회 (학년별 랜덤 문제용)
    @Query(value = "SELECT p.* FROM PROBLEMS p JOIN CHAPTERS c ON p.CHAPTER_ID = c.ID WHERE c.GRADE = :grade AND p.SOURCE_TYPE = 'REAL' ORDER BY DBMS_RANDOM.VALUE FETCH FIRST 1 ROWS ONLY", nativeQuery = true)
    Optional<Problem> findRandomByGrade(@Param("grade") String grade);

    // 유저 학년 + 난이도별 기출문제 랜덤 N개 조회 (수준 파악 테스트용)
    @Query(value = "SELECT p.* FROM PROBLEMS p JOIN CHAPTERS c ON p.CHAPTER_ID = c.ID WHERE c.GRADE = :grade AND p.DIFFICULTY = :difficulty AND p.SOURCE_TYPE = 'REAL' ORDER BY DBMS_RANDOM.VALUE FETCH FIRST :limit ROWS ONLY", nativeQuery = true)
    List<Problem> findRandomsByGradeAndDifficulty(@Param("grade") String grade,
                                                  @Param("difficulty") String difficulty,
                                                  @Param("limit") int limit);
}
