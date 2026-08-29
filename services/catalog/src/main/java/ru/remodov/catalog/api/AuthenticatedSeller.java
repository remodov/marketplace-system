package ru.remodov.catalog.api;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import ru.remodov.catalog.domain.SellerId;

@Component
public class AuthenticatedSeller {

    public SellerId currentSellerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT in security context");
        }
        return SellerId.of(UUID.fromString(jwt.getSubject()));
    }

    public Optional<SellerId> tryCurrentSellerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        try {
            return Optional.of(SellerId.of(UUID.fromString(jwt.getSubject())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(g -> "ROLE_admin".equals(g.getAuthority()));
    }
}
