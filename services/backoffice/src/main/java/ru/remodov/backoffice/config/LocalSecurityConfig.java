package ru.remodov.backoffice.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("local | integration-test")
@EnableMethodSecurity
public class LocalSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
            .anonymous(a -> a.authorities(List.of(
                new SimpleGrantedAuthority("ROLE_moderator"),
                new SimpleGrantedAuthority("ROLE_admin")
            )));
        return http.build();
    }
}
