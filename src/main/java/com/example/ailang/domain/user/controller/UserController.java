package com.example.ailang.domain.user.controller;

import com.example.ailang.domain.auth.dto.response.UserInfoResponse;
import com.example.ailang.domain.user.dto.request.UpdateGradeRequest;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.service.UserService;
import com.example.ailang.global.response.ResponseDTO;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ResponseDTO<UserInfoResponse>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithData(UserInfoResponse.from(user)));
    }

    /**
     * 학년 설정·변경.
     *
     * <p>🔴 <b>왜 이 엔드포인트가 필요한가</b> — 구글 가입은 학년을 받지 않는다. 그래서
     * 학년을 쓰는 기능 다섯이 그 계정에 「학년을 먼저 설정해 주세요」(400)를 돌려주는데,
     * 정작 <b>설정할 길이 없었다.</b> 화면이 그 400 을 받아 여기로 보낼 수 있게 연다.
     *
     * <p>🔴 <b>대상은 언제나 «인증된» 나다.</b> 본문이나 경로의 id 를 믿지 않는다.
     *
     * <p>🧭 바뀐 내 정보를 그대로 돌려준다 — 화면이 저장한 사용자 정보를 갱신하려고
     * 곧바로 다시 조회하는 왕복을 없앤다.
     */
    @PatchMapping("/me/grade")
    public ResponseEntity<ResponseDTO<UserInfoResponse>> updateMyGrade(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateGradeRequest request) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        User updated = userService.updateGrade(user.getId(), request.getGrade());
        return ResponseEntity.ok(ResponseDTO.okWithData(UserInfoResponse.from(updated)));
    }

    // 진단 테스트 완료 처리
    @PatchMapping("/me/assessment")
    public ResponseEntity<ResponseDTO<Void>> completeAssessment(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getEmail());
        userService.completeAssessment(user.getId());
        return ResponseEntity.ok(ResponseDTO.ok());
    }
}
