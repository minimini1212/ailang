package com.example.ailang.domain.problem.repository;

import com.example.ailang.domain.problem.entity.UserChapterStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 유저 챕터별 학습 통계 레포지토리
 */
public interface UserChapterStatsRepository extends JpaRepository<UserChapterStats, Long> {

    // 특정 유저의 특정 챕터 통계 조회 (없으면 Optional.empty)
    Optional<UserChapterStats> findByUserIdAndChapterId(Long userId, Long chapterId);

    // 특정 유저의 전체 챕터 통계 목록 조회 (전체 통계 화면용)
    List<UserChapterStats> findByUserId(Long userId);
}
