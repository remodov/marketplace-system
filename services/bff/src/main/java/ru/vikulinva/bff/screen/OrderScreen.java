package ru.vikulinva.bff.screen;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Экран заказа целиком: то, что показывает мобильный клиент на одной странице.
 * Собирается из трёх сервисов, но клиент об этом не знает — и не платит за это
 * тремя круговыми задержками.
 */
public record OrderScreen(UUID orderId,
                          String status,
                          BigDecimal total,
                          String paymentStatus,
                          List<Item> items) {

    public record Item(UUID productId, String title, int quantity, BigDecimal price) {}
}
