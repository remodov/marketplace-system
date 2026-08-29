package ru.vikulinva.notificationservice.time;

import java.time.Instant;

/** Поставщик текущего времени; в тестах подменяется через {@code @MockitoBean}. */
public interface DateTimeService {
    Instant now();
}
