package com.example.ailang.domain.user.controller;

import com.example.ailang.domain.auth.dto.response.UserInfoResponse;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.service.UserService;
import com.example.ailang.global.response.ResponseDTO;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
