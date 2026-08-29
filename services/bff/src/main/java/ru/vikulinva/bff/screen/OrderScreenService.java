package ru.vikulinva.bff.screen;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Сборка экрана заказа. Заказ нужен первым — из него известны товары; карточки
 * товаров и статус платежа запрашиваются параллельно, а не по очереди.
 */
@Service
public class OrderScreenService {

    private final RestClient order;
    private final RestClient catalog;
    private final RestClient payment;

    public OrderScreenService(RestClient.Builder builder, DownstreamProperties props) {
        this.order = builder.clone().baseUrl(props.order()).build();
        this.catalog = builder.clone().baseUrl(props.catalog()).build();
        this.payment = builder.clone().baseUrl(props.payment()).build();
    }

    @SuppressWarnings("unchecked")
    public OrderScreen assemble(UUID orderId) {
        Map<String, Object> orderBody = order.get()
            .uri("/api/v1/orders/{id}", orderId)
            .retrieve()
            .body(Map.class);

        List<Map<String, Object>> lines = (List<Map<String, Object>>) orderBody.getOrDefault("items", List.of());

        // TODO шаг 13: собрать экран.
        // Заказ уже прочитан — из него известны товары. Осталось добрать карточки
        // товаров и статус платежа. Обрати внимание: эти два похода независимы,
        // и экран не обязан ждать их по очереди.
        throw new UnsupportedOperationException("Шаг 13: экран ещё не собирается");
    }

    @SuppressWarnings("unchecked")
    private OrderScreen.Item toItem(Map<String, Object> line) {
        UUID productId = UUID.fromString(String.valueOf(line.get("productId")));
        Map<String, Object> card = catalog.get()
            .uri("/api/v1/products/{id}", productId)
            .retrieve()
            .body(Map.class);
        return new OrderScreen.Item(
            productId,
            String.valueOf(card.get("title")),
            Integer.parseInt(String.valueOf(line.getOrDefault("quantity", 1))),
            new BigDecimal(String.valueOf(card.getOrDefault("price", "0")))
        );
    }

    @SuppressWarnings("unchecked")
    private String paymentStatus(UUID orderId) {
        try {
            Map<String, Object> body = payment.get()
                .uri("/api/v1/payments/by-order/{id}", orderId)
                .retrieve()
                .body(Map.class);
            return String.valueOf(body.get("status"));
        } catch (RuntimeException e) {
            // Платежа может не быть — заказ ещё не оплачивали. Это не ошибка экрана.
            return "NONE";
        }
    }
}
