package ru.vikulinva.orderservice.adapter.in.rest.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;

import java.util.UUID;

/**
 * Достаёт {@code customerId} из текущего JWT-токена.
 *
 * <p>Маппинг: {@code jwt.sub} → {@link CustomerId}. Проверка роли {@code customer}
 * делается отдельно Spring Security ({@code @PreAuthorize}).
 *
 * <p>Доменные объекты не должны знать про Spring Security — отсюда отдельный
 * компонент в адаптере, который контроллер инжектит и вызывает.
 */
@Component
public class AuthenticatedCustomer {

    public CustomerId currentCustomerId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT principal");
        }
        return CustomerId.of(UUID.fromString(jwt.getSubject()));
    }
}
