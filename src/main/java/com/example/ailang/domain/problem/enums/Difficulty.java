package com.example.ailang.domain.problem.enums;

/**
 * 문제 난이도 Enum
 * - 정답률에 따라 난이도를 자동으로 조정할 때 사용
 * - upgrade() / downgrade() 메서드로 다음 난이도를 계산
 */
public enum Difficulty {
    LOW, MEDIUM, HIGH;

    // 난이도 상향: LOW → MEDIUM → HIGH (이미 HIGH면 유지)
    public Difficulty upgrade() {
        return switch (this) {
            case LOW -> MEDIUM;
            case MEDIUM -> HIGH;
            case HIGH -> HIGH;
        };
    }

    // 난이도 하향: HIGH → MEDIUM → LOW (이미 LOW면 유지)
    public Difficulty downgrade() {
        return switch (this) {
            case HIGH -> MEDIUM;
            case MEDIUM -> LOW;
            case LOW -> LOW;
        };
    }
}
