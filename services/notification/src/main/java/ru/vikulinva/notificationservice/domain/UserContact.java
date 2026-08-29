package ru.vikulinva.notificationservice.domain;

import java.util.List;
import java.util.UUID;

/**
 * Контакт пользователя, полученный из Customer BFF. На Tier A —
 * простой immutable record, без отдельных Value Object'ов под email
 * и push-токены.
 */
public record UserContact(UUID userId, String email, List<String> pushTokens, String locale) {

    public UserContact {
        pushTokens = pushTokens == null ? List.of() : List.copyOf(pushTokens);
    }
}
