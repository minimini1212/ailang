package com.example.ailang.domain.auth.dto.response;

import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.AuthProvider;
import com.example.ailang.domain.user.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {
    private Long id;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private AuthProvider provider;
    private UserRole role;

    public static UserInfoResponse from(User user) {
        return UserInfoResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .nickname(user.getNickname())
            .profileImageUrl(user.getProfileImageUrl())
            .provider(user.getProvider())
            .role(user.getRole())
            .build();
    }
}
