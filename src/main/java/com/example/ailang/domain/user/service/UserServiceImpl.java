package com.example.ailang.domain.user.service;

import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.Grade;
import com.example.ailang.domain.user.exception.UserNotFoundException;
import com.example.ailang.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(UserNotFoundException::new);
    }

    @Override
    @Transactional
    public void completeAssessment(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.completeAssessment();
    }

    /**
     * 🧭 <b>진단 완료 표시는 건드리지 않는다.</b> 학년을 바꾼 학생의 예전 진단이 새 학년에도
     * 유효한지는 «제품 판단»이라 여기서 혼자 정하지 않는다 — TODOS 8절의 결정 대기 항목이다.
     * 지금 자동으로 끄면, 학년을 한 번 잘못 골랐다 되돌린 학생의 진단까지 지워진다.
     */
    @Override
    @Transactional
    public User updateGrade(Long userId, Grade grade) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.updateGrade(grade);
        return user;
    }
}
