package com.example.ailang.domain.auth.service;

/**
 * 「몇 번까지 허용하나」 판정. <b>순수 함수다</b> — Redis 도 시계도 필요 없다.
 *
 * <p>🎯 이 한 줄을 따로 꺼낸 이유는 <b>경계에서 한 칸 틀리기 쉬워서</b>다.
 * 세는 값은 «올린 뒤» 의 값이므로 첫 요청이 이미 1 이다. 따라서 5회 허용은
 * {@code count > 5} 이지 {@code count >= 5} 가 아니다. 후자로 쓰면 5회 허용이
 * 조용히 4회가 되고, 학생은 왜 막혔는지 알 수 없다 — 검사가 이 변형을 잡는다.
 *
 * @param max 허용 횟수. 이 횟수«까지»는 통과한다
 */
public record AttemptLimit(int max) {

    public AttemptLimit {
        if (max < 1) {
            // 0 이나 음수는 「제한 없음」이 아니라 「아무도 못 함」이다. 설정 실수를
            // 조용히 「무제한」으로 접으면 상한을 건 의미가 사라지므로 뜰 때 터뜨린다.
            throw new IllegalArgumentException("허용 횟수는 1 이상이어야 합니다: " + max);
        }
    }

    /**
     * @param countAfterIncrement 이번 요청까지 «포함해» 센 횟수 (첫 요청이면 1)
     * @return 이번 요청을 거부해야 하는가
     */
    public boolean isExceeded(long countAfterIncrement) {
        return countAfterIncrement > max;
    }
}
