package com.example.ailang.domain.chapter.controller;

import com.example.ailang.domain.chapter.dto.response.ChapterResponse;
import com.example.ailang.domain.chapter.service.ChapterService;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.Grade;
import com.example.ailang.domain.user.service.UserService;
import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;
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

import com.example.ailang.domain.user.entity.UserGrades;
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
            // 🔴 Grade.from 이 어휘를 지킨다. 예전에는 Grade.valueOf 를 날것으로 불러
            //    ?grade=중1 같은 요청이 500 으로 나갔다 — 학생 입력이 원인인데 우리 잘못처럼 보였다.
            List<ChapterResponse> chapters = chapterService.getChaptersByGrade(Grade.from(grade));
            return ResponseEntity.ok(ResponseDTO.okWithData(chapters));
        }

        // grade 파라미터 없음 → 내 학년 챕터 + 통계 (인증 필요)
        //
        // 🔴 이 경로 전체가 permitAll 이라 «로그인 안 한 사람도 여기까지 온다».
        //    예전에는 바로 userDetails.getEmail() 을 불러 NPE → 500 이 났다.
        //    ⚠️ permitAll 을 좁히는 것으로는 못 고친다 — 같은 경로가 파라미터 유무로
        //       두 가지 일을 하고, 그중 하나는 «정말로» 비로그인에 열려 있어야 한다.
        //       그래서 인증 여부를 «여기서» 본다.
        if (userDetails == null) {
            throw new ApplicationException(ErrorCode.LOGIN_REQUIRED);
        }
        User user = userService.getUserByEmail(userDetails.getEmail());
        List<ChapterResponse> chapters = chapterService.getMyChapters(
                user.getId(), UserGrades.requireName(user));
        return ResponseEntity.ok(ResponseDTO.okWithData(chapters));
    }
}
