package com.example.ailang.domain.auth.service;

public interface EmailVerificationService {
    void sendCode(String email);
    void verifyCode(String email, String code);
}
