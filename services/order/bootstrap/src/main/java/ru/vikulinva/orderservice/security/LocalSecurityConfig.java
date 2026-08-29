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
 * SecurityFilterChain для локального запуска (профиль {@code local}).
 *
 * <p>Без OAuth2 Resource Server: чтобы поднимать сервис без живого
 * Keycloak. Все запросы — permitAll. Эндпоинты с {@code @PreAuthorize}
 * по-прежнему требуют security context, поэтому для проверки роле-зависимых
 * операций в Postman/curl используй интеграционный профиль с реальным IdP.
 */
@Configuration
@EnableMethodSecurity
@Profile("local")
public class LocalSecurityConfig {

    @Bean
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
