package com.example.ailang.domain.auth.service;

import com.example.ailang.domain.auth.dto.request.LoginRequest;
import com.example.ailang.domain.auth.dto.request.SignUpRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {
    void signUp(SignUpRequest request);
    void login(LoginRequest request, HttpServletResponse response);
    void reissue(HttpServletRequest request, HttpServletResponse response);
    void logout(HttpServletRequest request, HttpServletResponse response);
}
