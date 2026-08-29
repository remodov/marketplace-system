package ru.vikulinva.bff.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Счётчик запросов на клиента в минуту. Живёт в Redis, а не в памяти процесса:
 * инстансов шлюза несколько, и лимит должен быть общим, иначе он умножается на
 * их число.
 */
@Component
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Decision check(String client) {
        String key = "rate:" + client + ":" + (System.currentTimeMillis() / WINDOW.toMillis());
        Long used = redis.opsForValue().increment(key);
        if (used != null && used == 1L) {
            redis.expire(key, WINDOW);
        }
        long count = used == null ? 1L : used;
        long remaining = Math.max(0, properties.requestsPerMinute() - count);
        return new Decision(count <= properties.requestsPerMinute(), remaining, WINDOW.toSeconds());
    }

    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {}
}
