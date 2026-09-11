package com.example.ailang.domain.chapter.service;

import com.example.ailang.domain.chapter.dto.response.ChapterResponse;
import com.example.ailang.domain.user.enums.Grade;

import java.util.List;

/**
 * 챕터 서비스 인터페이스
 */
public interface ChapterService {

    // 내 학년에 해당하는 챕터 목록 조회 (통계 포함)
    List<ChapterResponse> getMyChapters(Long userId, String grade);

    // 특정 학년의 챕터 목록 조회 (통계 없음, 비인증 접근 가능)
    List<ChapterResponse> getChaptersByGrade(Grade grade);
}
