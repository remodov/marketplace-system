package ru.vikulinva.orderservice.domain.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.vikulinva.orderservice.domain.entity.OrderItem;
import ru.vikulinva.orderservice.domain.event.OrderCancelled;
import ru.vikulinva.orderservice.domain.event.OrderCompleted;
import ru.vikulinva.orderservice.domain.event.OrderConfirmed;
import ru.vikulinva.orderservice.domain.event.OrderCreated;
import ru.vikulinva.orderservice.domain.event.OrderDelivered;
import ru.vikulinva.orderservice.domain.event.OrderExpired;
import ru.vikulinva.orderservice.domain.event.OrderPaid;
import ru.vikulinva.orderservice.domain.event.OrderShipped;
import ru.vikulinva.orderservice.domain.valueobject.Address;
import ru.vikulinva.orderservice.domain.valueobject.CancellationReason;
import ru.vikulinva.orderservice.domain.valueobject.CustomerId;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderItemId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.ProductId;
import ru.vikulinva.orderservice.domain.valueobject.Quantity;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты на агрегат {@link Order}. Без Spring (TS-26).
 */
class OrderTest {

    private static final Address ADDRESS = new Address("RU", "Moscow", "Tverskaya 1", "125009", null);

    @Test
    @DisplayName("create: переводит заказ в DRAFT и регистрирует OrderCreated")
    void create_setsDraftAndEmitsEvent() {
        var orderId = OrderId.of(UUID.randomUUID());
        var customer = CustomerId.of(UUID.randomUUID());
        var seller = SellerId.of(UUID.randomUUID());
        var item = item(seller, "1000.00");

        var order = Order.create(orderId, customer, List.of(item),
            Money.zero(Money.RUB), ADDRESS, Instant.parse("2026-04-01T10:00:00Z"));

        assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.getEvents()).hasSize(1).first().isInstanceOf(OrderCreated.class);
        var event = (OrderCreated) order.getEvents().get(0);
        assertThat(event.customerId()).isEqualTo(customer);
        assertThat(event.sellerId()).isEqualTo(seller);
        assertThat(event.itemsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-001: total = sum(items) − discount + shippingFee")
    void total_followsBR001() {
        var seller = SellerId.of(UUID.randomUUID());
        var items = List.of(
            item(seller, "100.00"),
            item(seller, "250.00")
        );

        var order = Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()), items,
            Money.rub(50), ADDRESS, Instant.now());

        // 100 + 250 = 350; without discount; +50 shipping = 400
        assertThat(order.total()).isEqualTo(Money.rub("400.00"));
    }

    @Test
    @DisplayName("BR-014: множественный продавец отклоняется")
    void multiSeller_isRejected() {
        var sellerA = SellerId.of(UUID.randomUUID());
        var sellerB = SellerId.of(UUID.randomUUID());

        assertThatThrownBy(() -> Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            List.of(item(sellerA, "100.00"), item(sellerB, "200.00")),
            Money.zero(Money.RUB), ADDRESS, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("BR-014");
    }

    @Test
    @DisplayName("создание без позиций отклоняется")
    void empty_isRejected() {
        assertThatThrownBy(() -> Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            List.of(),
            Money.zero(Money.RUB), ADDRESS, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one item");
    }

    @Test
    @DisplayName("confirm: DRAFT → PENDING_PAYMENT и регистрирует OrderConfirmed")
    void confirm_movesDraftToPendingPaymentAndEmitsEvent() {
        var order = sampleOrder();
        order.clearDomainEvents();

        order.confirm();

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getEvents()).hasSize(1).first().isInstanceOf(OrderConfirmed.class);
        var event = (OrderConfirmed) order.getEvents().get(0);
        assertThat(event.total()).isEqualTo(order.total());
    }

    @Test
    @DisplayName("confirm: повторный вызов в PENDING_PAYMENT — IllegalStateException")
    void confirm_fromNonDraft_throws() {
        var order = sampleOrder();
        order.confirm();

        assertThatThrownBy(order::confirm)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("BR-013: confirm с суммой ниже 100 RUB отклоняется")
    void confirm_belowMinimum_throws() {
        var seller = SellerId.of(UUID.randomUUID());
        var order = Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            List.of(item(seller, "50.00")),
            Money.zero(Money.RUB), ADDRESS, Instant.now());

        assertThatThrownBy(order::confirm)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BR-013");
    }

    @Test
    @DisplayName("cancel: DRAFT → CANCELLED и регистрирует OrderCancelled")
    void cancel_fromDraft_emitsOrderCancelled() {
        var order = sampleOrder();
        order.clearDomainEvents();

        order.cancel(CancellationReason.of("CHANGED_MIND"));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getEvents()).hasSize(1).first().isInstanceOf(OrderCancelled.class);
        var event = (OrderCancelled) order.getEvents().get(0);
        assertThat(event.previousStatus()).isEqualTo(OrderStatus.DRAFT);
        assertThat(event.reason().code()).isEqualTo("CHANGED_MIND");
    }

    @Test
    @DisplayName("cancel: PENDING_PAYMENT → CANCELLED, событие содержит previousStatus")
    void cancel_fromPendingPayment_capturesPreviousStatus() {
        var order = sampleOrder();
        order.confirm();
        order.clearDomainEvents();

        order.cancel(CancellationReason.of("FOUND_BETTER_PRICE"));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        var event = (OrderCancelled) order.getEvents().get(0);
        assertThat(event.previousStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("cancel: повторная отмена в CANCELLED — IllegalStateException")
    void cancel_alreadyCancelled_throws() {
        var order = sampleOrder();
        order.cancel(CancellationReason.of("CHANGED_MIND"));

        assertThatThrownBy(() -> order.cancel(CancellationReason.of("OTHER")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("markPaid: PENDING_PAYMENT → PAID, OrderPaid содержит paymentId и total")
    void markPaid_emitsOrderPaid() {
        var order = sampleOrder();
        order.confirm();
        order.clearDomainEvents();
        var paymentId = UUID.randomUUID();
        var paidAt = Instant.parse("2026-04-02T12:00:00Z");

        order.markPaid(paymentId, paidAt);

        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.paymentId()).isEqualTo(paymentId);
        assertThat(order.paidAt()).isEqualTo(paidAt);
        var event = (OrderPaid) order.getEvents().get(0);
        assertThat(event.paymentId()).isEqualTo(paymentId);
        assertThat(event.total()).isEqualTo(order.total());
    }

    @Test
    @DisplayName("markPaid из DRAFT — IllegalStateException")
    void markPaid_fromDraft_throws() {
        var order = sampleOrder();
        assertThatThrownBy(() -> order.markPaid(UUID.randomUUID(), Instant.now()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("markShipped: PAID → SHIPPED, событие содержит trackingNumber")
    void markShipped_emitsOrderShipped() {
        var order = sampleOrder();
        order.confirm();
        order.markPaid(UUID.randomUUID(), Instant.parse("2026-04-02T12:00:00Z"));
        order.clearDomainEvents();
        var shippedAt = Instant.parse("2026-04-03T08:00:00Z");

        order.markShipped("TRACK-1", shippedAt);

        assertThat(order.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.shippedAt()).isEqualTo(shippedAt);
        var event = (OrderShipped) order.getEvents().get(0);
        assertThat(event.trackingNumber()).isEqualTo("TRACK-1");
    }

    @Test
    @DisplayName("confirmDelivery: SHIPPED → DELIVERED")
    void confirmDelivery_emitsOrderDelivered() {
        var order = sampleOrder();
        order.confirm();
        order.markPaid(UUID.randomUUID(), Instant.parse("2026-04-02T12:00:00Z"));
        order.markShipped("TRACK-1", Instant.parse("2026-04-03T08:00:00Z"));
        order.clearDomainEvents();
        var deliveredAt = Instant.parse("2026-04-05T15:00:00Z");

        order.confirmDelivery(deliveredAt);

        assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.deliveredAt()).isEqualTo(deliveredAt);
        assertThat(order.getEvents().get(0)).isInstanceOf(OrderDelivered.class);
    }

    @Test
    @DisplayName("expire: PENDING_PAYMENT → EXPIRED")
    void expire_emitsOrderExpired() {
        var order = sampleOrder();
        order.confirm();
        order.clearDomainEvents();

        order.expire(Instant.parse("2026-04-01T10:15:00Z"));

        assertThat(order.status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getEvents().get(0)).isInstanceOf(OrderExpired.class);
    }

    @Test
    @DisplayName("complete: DELIVERED → COMPLETED")
    void complete_emitsOrderCompleted() {
        var order = sampleOrder();
        order.confirm();
        order.markPaid(UUID.randomUUID(), Instant.parse("2026-04-02T12:00:00Z"));
        order.markShipped("TRACK", Instant.parse("2026-04-03T08:00:00Z"));
        order.confirmDelivery(Instant.parse("2026-04-05T15:00:00Z"));
        order.clearDomainEvents();

        order.complete(Instant.parse("2026-04-19T15:00:00Z"));

        assertThat(order.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getEvents().get(0)).isInstanceOf(OrderCompleted.class);
    }

    @Test
    @DisplayName("cancelAfterPayment: PAID → CANCELLED, событие содержит refundId")
    void cancelAfterPayment_emitsRefundId() {
        var order = sampleOrder();
        order.confirm();
        order.markPaid(UUID.randomUUID(), Instant.parse("2026-04-02T12:00:00Z"));
        order.clearDomainEvents();
        var refundId = UUID.randomUUID();

        order.cancelAfterPayment(CancellationReason.of("CHANGED_MIND"), refundId);

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        var event = (OrderCancelled) order.getEvents().get(0);
        assertThat(event.previousStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(event.refundId()).isEqualTo(refundId);
    }

    @Test
    @DisplayName("cancelAfterPayment из DRAFT — IllegalStateException")
    void cancelAfterPayment_fromDraft_throws() {
        var order = sampleOrder();
        assertThatThrownBy(() -> order.cancelAfterPayment(CancellationReason.of("X"), UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PAID");
    }

    @Test
    @DisplayName("clearDomainEvents очищает зарегистрированные события")
    void clearDomainEvents_works() {
        var order = sampleOrder();
        assertThat(order.getEvents()).hasSize(1);

        order.clearDomainEvents();

        assertThat(order.getEvents()).isEmpty();
    }

    private OrderItem item(SellerId seller, String price) {
        return new OrderItem(
            OrderItemId.of(UUID.randomUUID()),
            ProductId.of(UUID.randomUUID()),
            seller,
            Quantity.of(1),
            Money.rub(price)
        );
    }

    private Order sampleOrder() {
        var seller = SellerId.of(UUID.randomUUID());
        return Order.create(OrderId.of(UUID.randomUUID()),
            CustomerId.of(UUID.randomUUID()),
            List.of(item(seller, "100.00")),
            Money.zero(Money.RUB), ADDRESS, Instant.now());
    }
}
