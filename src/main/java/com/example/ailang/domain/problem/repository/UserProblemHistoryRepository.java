package com.example.ailang.domain.problem.repository;

import com.example.ailang.domain.problem.entity.UserProblemHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 유저 문제 풀이 이력 레포지토리
 */
public interface UserProblemHistoryRepository extends JpaRepository<UserProblemHistory, Long> {
}
