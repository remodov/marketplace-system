package ru.vikulinva.orderservice.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.vikulinva.hexagonal.test.HexagonalArchitectureRules;

/**
 * ArchUnit-проверки гексагональной архитектуры. Прогоняем готовый набор
 * правил из библиотеки {@code hexagonal-architecture-test} над целым
 * сервисом (все модули видны в bootstrap classpath).
 */
class HexagonalArchitectureTest {

    @Test
    @DisplayName("Hexagonal layering rules hold for the whole service")
    void hexagonalLayeringHolds() {
        HexagonalArchitectureRules.verify(
            "ru.vikulinva.orderservice",
            "ru.vikulinva.orderservice.domain",
            "ru.vikulinva.orderservice.usecase",
            "ru.vikulinva.orderservice.adapter"
        );
    }
}
