package com.example.ailang.global.redis;

import java.time.Duration;
import java.util.Optional;

public interface RedisService {
    void save(String key, String value, Duration ttl);
    Optional<String> get(String key);
    void delete(String key);
    boolean hasKey(String key);
}
