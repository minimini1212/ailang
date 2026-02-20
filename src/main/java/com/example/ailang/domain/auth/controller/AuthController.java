package com.example.ailang.domain.auth.controller;

import com.example.ailang.domain.auth.dto.request.EmailSendRequest;
import com.example.ailang.domain.auth.dto.request.EmailVerifyRequest;
import com.example.ailang.domain.auth.dto.request.LoginRequest;
import com.example.ailang.domain.auth.dto.request.SignUpRequest;
import com.example.ailang.domain.auth.service.AuthService;
import com.example.ailang.domain.auth.service.EmailVerificationService;
import com.example.ailang.global.response.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/email/send")
    public ResponseEntity<ResponseDTO<Void>> sendCode(@Valid @RequestBody EmailSendRequest request) {
        emailVerificationService.sendCode(request.getEmail());
        return ResponseEntity.ok(ResponseDTO.okWithMessage("인증 코드가 발송되었습니다."));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ResponseDTO<Void>> verifyCode(@Valid @RequestBody EmailVerifyRequest request) {
        emailVerificationService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ResponseDTO.okWithMessage("이메일 인증이 완료되었습니다."));
    }

    @PostMapping("/signup")
    public ResponseEntity<ResponseDTO<Void>> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ResponseDTO.created());
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseDTO<Void>> login(@Valid @RequestBody LoginRequest request,
                                                    HttpServletResponse response) {
        authService.login(request, response);
        return ResponseEntity.ok(ResponseDTO.ok());
    }

    @PostMapping("/reissue")
    public ResponseEntity<ResponseDTO<Void>> reissue(HttpServletRequest request, HttpServletResponse response) {
        authService.reissue(request, response);
        return ResponseEntity.ok(ResponseDTO.ok());
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseDTO<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ResponseDTO.okWithMessage("로그아웃 되었습니다."));
    }
}
