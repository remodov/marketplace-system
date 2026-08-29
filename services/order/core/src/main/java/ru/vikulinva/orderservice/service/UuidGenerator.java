package ru.vikulinva.orderservice.service;

import java.util.UUID;

/**
 * Поставщик UUID. В тестах подменяется через {@code @MockitoBean}
 * для детерминизма (см. test-strategy TS-7).
 */
public interface UuidGenerator {

    UUID generate();
}
