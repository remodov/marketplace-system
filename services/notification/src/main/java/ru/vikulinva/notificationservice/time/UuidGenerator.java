package ru.vikulinva.notificationservice.time;

import java.util.UUID;

/** Поставщик UUID; в тестах подменяется через {@code @MockitoBean}. */
public interface UuidGenerator {
    UUID generate();
}
