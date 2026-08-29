package ru.vikulinva.orderservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Spring Security + OAuth2 Resource Server для Order Service.
 *
 * <p>Соблюдает auth-patterns-style-guide:
 * <ul>
 *   <li>{@code AUTH-4}: используется стандартный {@code oauth2ResourceServer().jwt()}.</li>
 *   <li>{@code AUTH-7}: роли извлекаются из {@code realm_access.roles} с префиксом {@code ROLE_}.</li>
 *   <li>{@code AUTH-9}: проверка ролей — на каждом endpoint через {@code @PreAuthorize}.</li>
 * </ul>
 *
 * <p>Disabled на профиле {@code integration-test} — тесты не валидируют реальные JWT,
 * а используют {@link TestJwtConfiguration}. См. test-strategy TS-8.
 */
@Configuration
@EnableMethodSecurity
@Profile("!integration-test & !local")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractAuthorities);
        return converter;
    }

    /**
     * Извлекает роли из {@code realm_access.roles} (формат Keycloak), мапит в
     * {@link SimpleGrantedAuthority} с префиксом {@code ROLE_}.
     */
    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmMap)) {
            return Collections.emptyList();
        }
        Object roles = realmMap.get("roles");
        if (!(roles instanceof List<?> roleList)) {
            return Collections.emptyList();
        }
        return ((List<String>) roleList).stream()
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
    }
}
