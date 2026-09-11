package com.example.ailang.domain.chapter.repository;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.user.enums.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 챕터 레포지토리
 * - 학년별 챕터 목록 조회에 사용
 */
public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    // 특정 학년의 챕터를 순서대로 조회
    List<Chapter> findByGradeOrderByOrderNum(Grade grade);

    // 학년 + 챕터명으로 단건 조회 (DataLoader에서 챕터 중복 생성 방지용)
    Optional<Chapter> findByGradeAndTitle(Grade grade, String title);
}
