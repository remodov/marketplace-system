package ru.vikulinva.orderservice.domain.valueobject;

import java.util.Objects;

/**
 * Причина отмены заказа. {@code code} нормализуется (uppercase) и используется
 * аналитикой/событиями; {@code comment} — необязательный свободный текст
 * пользователя (≤ 500 символов).
 */
public record CancellationReason(String code, String comment) {

    public CancellationReason {
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        code = code.toUpperCase();
        if (comment != null && comment.length() > 500) {
            throw new IllegalArgumentException("comment must be ≤ 500 chars");
        }
    }

    public static CancellationReason of(String code) {
        return new CancellationReason(code, null);
    }
}
