package com.example.ailang.domain.user.entity;

import com.example.ailang.domain.user.enums.AuthProvider;
import com.example.ailang.domain.user.enums.UserRole;
import com.example.ailang.domain.user.enums.UserStatus;
import com.example.ailang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "USERS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_seq")
    @SequenceGenerator(name = "users_seq", sequenceName = "USERS_SEQ", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 200)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(length = 100)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(length = 500)
    private String profileImageUrl;

    public void updateOAuthInfo(AuthProvider provider, String providerId, String profileImageUrl) {
        this.provider = provider;
        this.providerId = providerId;
        this.profileImageUrl = profileImageUrl;
    }
}
