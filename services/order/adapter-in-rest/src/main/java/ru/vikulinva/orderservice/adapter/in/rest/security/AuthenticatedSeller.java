package ru.vikulinva.orderservice.adapter.in.rest.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.util.UUID;

/**
 * Достаёт {@code sellerId} из текущего JWT-токена.
 *
 * <p>Упрощённый маппинг: {@code jwt.sub} → {@link SellerId}. В production
 * используется отдельный claim вроде {@code org_id} / {@code seller_id}.
 * Проверка роли {@code seller} — через {@code @PreAuthorize}.
 */
@Component
public class AuthenticatedSeller {

    public SellerId currentSellerId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT principal");
        }
        return SellerId.of(UUID.fromString(jwt.getSubject()));
    }
}
