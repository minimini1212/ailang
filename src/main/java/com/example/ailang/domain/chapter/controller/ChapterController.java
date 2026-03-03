package com.example.ailang.domain.chapter.controller;

import com.example.ailang.domain.chapter.dto.response.ChapterResponse;
import com.example.ailang.domain.chapter.service.ChapterService;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.Grade;
import com.example.ailang.domain.user.service.UserService;
import com.example.ailang.global.response.ResponseDTO;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 챕터 컨트롤러
 * - GET /api/chapters             : 내 학년 챕터 목록 (통계 포함, 인증 필요)
 * - GET /api/chapters?grade=MIDDLE_3 : 특정 학년 챕터 목록 (통계 없음, 비인증 가능)
 */
@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<ChapterResponse>>> getChapters(
            @RequestParam(required = false) String grade,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (grade != null) {
            // grade 파라미터 있음 → 해당 학년 챕터 (통계 없음, 비인증 가능)
            List<ChapterResponse> chapters = chapterService.getChaptersByGrade(Grade.valueOf(grade));
            return ResponseEntity.ok(ResponseDTO.okWithData(chapters));
        }

        // grade 파라미터 없음 → 내 학년 챕터 + 통계 (인증 필요)
        User user = userService.getUserByEmail(userDetails.getEmail());
        List<ChapterResponse> chapters = chapterService.getMyChapters(
                user.getId(), user.getGrade().name());
        return ResponseEntity.ok(ResponseDTO.okWithData(chapters));
    }
}
