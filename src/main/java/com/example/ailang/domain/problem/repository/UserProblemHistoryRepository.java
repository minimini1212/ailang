package com.example.ailang.domain.problem.repository;

import com.example.ailang.domain.problem.entity.UserProblemHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 유저 문제 풀이 이력 레포지토리
 */
public interface UserProblemHistoryRepository extends JpaRepository<UserProblemHistory, Long> {

    /**
     * 이 학생이 이 문제를 이미 낸 적 있나.
     *
     * <p>🔴 이 표는 오래 «쓰기만 하고 읽지 않는» 표였다. 조회 메서드가 하나도 없어서
     * 「이 학생이 이 문제를 풀었나」를 물을 방법 자체가 없었고, 그래서 같은 문제를
     * 반복 제출해 정답률을 올리는 것을 막을 수 없었다.
     */
    boolean existsByUserIdAndProblemId(Long userId, Long problemId);
}
