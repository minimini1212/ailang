package com.example.ailang.domain.chapter.service;

import com.example.ailang.domain.chapter.dto.response.ChapterResponse;
import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.chapter.repository.ChapterRepository;
import com.example.ailang.domain.problem.entity.UserChapterStats;
import com.example.ailang.domain.problem.repository.UserChapterStatsRepository;
import com.example.ailang.domain.user.enums.Grade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 챕터 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChapterServiceImpl implements ChapterService {

    private final ChapterRepository chapterRepository;
    private final UserChapterStatsRepository userChapterStatsRepository;

    @Override
    public List<ChapterResponse> getChaptersByGrade(Grade grade) {
        return chapterRepository.findByGradeOrderByOrderNum(grade).stream()
                .map(chapter -> ChapterResponse.builder()
                        .id(chapter.getId())
                        .title(chapter.getTitle())
                        .description(chapter.getDescription())
                        .orderNum(chapter.getOrderNum())
                        .grade(chapter.getGrade())
                        .myStats(null)
                        .build())
                .toList();
    }

    @Override
    public List<ChapterResponse> getMyChapters(Long userId, String gradeStr) {
        // 문자열 "ELEM_3"을 Grade enum으로 변환
        Grade grade = Grade.valueOf(gradeStr);

        // 학년에 해당하는 챕터 목록을 순서대로 조회
        List<Chapter> chapters = chapterRepository.findByGradeOrderByOrderNum(grade);

        // 유저의 전체 챕터 통계를 한 번에 조회해 Map으로 변환 (N+1 방지)
        Map<Long, UserChapterStats> statsMap = userChapterStatsRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(s -> s.getChapter().getId(), s -> s));

        // 챕터별로 통계를 매핑해서 응답 생성
        return chapters.stream()
                .map(chapter -> ChapterResponse.of(chapter, statsMap.get(chapter.getId())))
                .toList();
    }
}
