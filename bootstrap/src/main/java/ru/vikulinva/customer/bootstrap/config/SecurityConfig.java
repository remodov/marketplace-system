package ru.vikulinva.customer.bootstrap.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@Profile("!integration-test & !local")
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/v1/customers", "/v1/customers/email-verifications/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
