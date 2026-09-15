package com.example.ailang.global.redis;

import java.time.Duration;
import java.util.Optional;

public interface RedisService {
    void save(String key, String value, Duration ttl);
    Optional<String> get(String key);
    void delete(String key);
    boolean hasKey(String key);

    /**
     * 키의 값을 1 올리고 «올린 뒤의 값» 을 돌려준다. 키가 없으면 1 이 된다.
     *
     * <p>횟수 제한에 쓴다. 「읽고 → 더하고 → 쓰기」로 직접 세면 동시 요청 둘이 같은 값을
     * 읽어 한 번만 세어지므로, 제한을 우회할 수 있다. Redis 의 {@code INCR} 은 한 번에
     * 일어나므로 그 틈이 없다.
     *
     * @param ttlOnFirst 이 키가 «이번에 처음 생겼을 때만» 걸 만료 시간.
     *                   매번 다시 걸면 계속 요청하는 쪽이 만료를 영원히 미룰 수 있다.
     * @return 올린 뒤의 값
     * @throws IllegalStateException 값을 못 받았을 때. 🔴 「모른다」를 0 으로 접지 않는다 —
     *         셀 수 없는데 통과시키면 제한이 없는 것과 같다.
     */
    long increment(String key, Duration ttlOnFirst);
}
