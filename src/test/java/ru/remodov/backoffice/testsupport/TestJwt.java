package ru.remodov.backoffice.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

public final class TestJwt {

    private TestJwt() {
    }

    public static JwtRequestPostProcessor asModerator(UUID moderatorId) {
        return jwt()
            .jwt(j -> j.subject(moderatorId.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_moderator"));
    }

    public static JwtRequestPostProcessor asAdmin(UUID adminId) {
        return jwt()
            .jwt(j -> j.subject(adminId.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }
}
