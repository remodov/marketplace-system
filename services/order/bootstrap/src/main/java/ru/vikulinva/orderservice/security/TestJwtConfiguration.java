package ru.vikulinva.orderservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Тестовая конфигурация: SecurityFilterChain без OAuth2 Resource Server,
 * чтобы интеграционные тесты могли подкладывать JWT через
 * {@code @WithJwt} / {@code TestHttpHeaders.withSuccessToken()} (см. TS-8).
 *
 * <p>В тестах JWT валидируется через тестовый JwtDecoder, а не через JWK
 * IdP. Это профиль {@code integration-test}.
 */
@Configuration
@EnableMethodSecurity
@Profile("integration-test")
public class TestJwtConfiguration {

    @Bean
    public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
