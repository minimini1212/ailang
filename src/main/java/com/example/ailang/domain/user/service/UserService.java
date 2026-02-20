package com.example.ailang.domain.user.service;

import com.example.ailang.domain.user.entity.User;

public interface UserService {
    User getUserByEmail(String email);
}
