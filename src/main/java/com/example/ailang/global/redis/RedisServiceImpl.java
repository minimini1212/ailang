package com.example.ailang.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void save(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(key));
    }

    @Override
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    @Override
    public long increment(String key, Duration ttlOnFirst) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value == null) {
            // 🔴 여기서 0 을 돌려주면 「셀 수 없었다」가 「아직 한 번도 안 했다」가 되어
            //    제한이 통째로 열린다. 못 셌으면 못 셌다고 말한다.
            throw new IllegalStateException("요청 횟수를 셀 수 없습니다: " + key);
        }
        if (value == 1L) {
            // 처음 생긴 키에만 만료를 건다. 매번 걸면 계속 두드리는 쪽이 만료를 밀어낸다.
            stringRedisTemplate.expire(key, ttlOnFirst);
        }
        return value;
    }
}
