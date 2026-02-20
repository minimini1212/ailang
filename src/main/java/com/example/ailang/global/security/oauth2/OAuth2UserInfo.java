package com.example.ailang.global.security.oauth2;

public interface OAuth2UserInfo {
    String getEmail();
    String getName();
    String getProfileImageUrl();
    String getProviderId();
}
