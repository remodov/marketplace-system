package ru.remodov.backoffice.auth;

import java.util.UUID;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class ModeratorContext {

    static final UUID LOCAL_PLACEHOLDER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public UUID currentModeratorId(@Nullable UUID headerOverride) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedException("JWT subject is not a valid moderator UUID");
            }
        }
        if (headerOverride != null) {
            return headerOverride;
        }
        return LOCAL_PLACEHOLDER;
    }
}
