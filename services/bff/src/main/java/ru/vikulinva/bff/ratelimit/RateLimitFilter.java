package ru.vikulinva.bff.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Лимит частоты на границе: считаем по идентификатору клиента из заголовка,
 * а не по IP — за одним адресом сидит и офис, и мобильный оператор.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CLIENT_HEADER = "X-Client-Id";

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {

        String client = request.getHeader(CLIENT_HEADER);
        if (client == null || client.isBlank()) {
            client = "anonymous";
        }

        RateLimiter.Decision decision = rateLimiter.check(client);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                {"status":429,"title":"Too Many Requests","detail":"Слишком много запросов, попробуйте позже"}
                """);
            return;
        }

        chain.doFilter(request, response);
    }
}
