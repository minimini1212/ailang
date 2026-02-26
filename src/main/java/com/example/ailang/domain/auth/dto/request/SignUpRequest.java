package com.example.ailang.domain.auth.dto.request;

import com.example.ailang.domain.user.enums.Grade;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignUpRequest {
    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 8, max = 50)
    private String password;

    @NotBlank @Size(min = 2, max = 20)
    private String nickname;

    @NotNull(message = "학년을 선택해주세요.")
    private Grade grade;
}
