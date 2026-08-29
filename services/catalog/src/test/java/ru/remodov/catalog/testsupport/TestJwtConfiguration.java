package ru.remodov.catalog.testsupport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.remodov.catalog.config.KeycloakJwtAuthenticationConverter;

@Configuration
@Profile("integration-test")
@EnableMethodSecurity
public class TestJwtConfiguration {

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return new FakeJwtDecoder();
    }

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));
        return http.build();
    }
}
