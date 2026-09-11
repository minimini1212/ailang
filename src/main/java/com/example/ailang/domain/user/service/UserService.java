package com.example.ailang.domain.user.service;

import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.Grade;

public interface UserService {
    User getUserByEmail(String email);
    void completeAssessment(Long userId);

    /**
     * 학년을 설정하거나 바꾼다. 바뀐 유저를 돌려준다.
     *
     * <p>🔴 <b>구글 가입 계정에는 이것이 «유일한» 학년 입력 경로다.</b> 가입 때 학년을
     * 받지 않으므로, 이 길이 없으면 학생은 「학년을 먼저 설정해 주세요」(400)를 받고도
     * 갈 곳이 없다.
     */
    User updateGrade(Long userId, Grade grade);
}
