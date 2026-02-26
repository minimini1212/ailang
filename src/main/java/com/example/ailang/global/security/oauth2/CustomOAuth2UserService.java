package com.example.ailang.global.security.oauth2;

import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.AuthProvider;
import com.example.ailang.domain.user.enums.UserRole;
import com.example.ailang.domain.user.enums.UserStatus;
import com.example.ailang.domain.user.repository.UserRepository;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = new DefaultOAuth2UserService().loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = extractUserInfo(registrationId, oAuth2User.getAttributes());

        User user = userRepository.findByEmail(userInfo.getEmail())
            .map(existing -> {
                if (existing.getProvider() == AuthProvider.LOCAL) {
                    throw new OAuth2AuthenticationException("이미 이메일로 가입된 계정입니다. 이메일 로그인을 이용해주세요.");
                }
                return existing;
            })
            .orElseGet(() -> userRepository.save(User.builder()
                .email(userInfo.getEmail())
                .nickname(userInfo.getName())
                .provider(AuthProvider.GOOGLE)
                .providerId(userInfo.getProviderId())
                .status(UserStatus.ACTIVE)
                .role(UserRole.ROLE_USER)
                .profileImageUrl(userInfo.getProfileImageUrl())
                .build()));

        return new CustomUserDetails(user);
    }

    private OAuth2UserInfo extractUserInfo(String registrationId, Map<String, Object> attributes) {
        if ("google".equals(registrationId)) {
            return new GoogleOAuth2UserInfo(attributes);
        }
        throw new OAuth2AuthenticationException("Unsupported provider: " + registrationId);
    }
}
