package ru.vikulinva.notificationservice.provider.contact;

import ru.vikulinva.notificationservice.domain.UserContact;

import java.util.Optional;
import java.util.UUID;

/**
 * Клиент Customer BFF: достаёт контакты пользователя для отправки.
 */
public interface ContactClient {

    /** {@link Optional#empty()} если пользователь не найден / нет контактов. */
    Optional<UserContact> findContact(UUID userId);
}
