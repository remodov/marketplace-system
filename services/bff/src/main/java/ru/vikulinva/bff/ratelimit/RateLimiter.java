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
        // TODO шаг 13: счётчик запросов клиента в текущем окне.
        // Ключ должен сам протухать вместе с окном — чистить его отдельной job'ой
        // не нужно. И считать надо на каждого клиента, а не на всех сразу.
        return new Decision(true, properties.requestsPerMinute(), WINDOW.toSeconds());
    }

    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {}
}
