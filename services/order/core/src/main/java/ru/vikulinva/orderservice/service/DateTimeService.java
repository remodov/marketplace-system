package ru.vikulinva.orderservice.service;

import java.time.Instant;

/**
 * Поставщик текущего времени. В тестах подменяется через {@code @MockitoBean}
 * для детерминизма (см. test-strategy TS-7).
 */
public interface DateTimeService {

    Instant now();
}
