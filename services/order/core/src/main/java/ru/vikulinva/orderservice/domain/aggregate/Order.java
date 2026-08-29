package ru.vikulinva.orderservice.domain.aggregate;

import ru.vikulinva.ddd.AggregateRoot;
import ru.vikulinva.orderservice.domain.entity.OrderItem;
import ru.vikulinva.orderservice.domain.event.DisputeOpened;
import ru.vikulinva.orderservice.domain.event.DisputeResolved;
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
import ru.vikulinva.orderservice.domain.valueobject.Discount;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.domain.valueobject.OrderStatus;
import ru.vikulinva.orderservice.domain.valueobject.SellerId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Корень агрегата «Заказ». Защищает инварианты:
 *
 * <ul>
 *   <li>BR-001: {@code total = sum(items.lineTotal) − discount + shippingFee}.</li>
 *   <li>BR-014: один заказ — один продавец (V1).</li>
 *   <li>BR-013: минимальная сумма для confirm — 100 RUB (проверяется в {@code confirm()}, не здесь).</li>
 * </ul>
 *
 * Создание идёт через фабричный метод {@link #create(OrderId, CustomerId, List, Money, Address)}
 * — внутри регистрирует событие {@link OrderCreated}.
 * Перевод состояния (confirm/pay/ship/...) — отдельные методы, добавляются по мере UC.
 */
public final class Order extends AggregateRoot<OrderId> {

    private final OrderId id;
    private final CustomerId customerId;
    private final SellerId sellerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private Discount discount;
    private final Money shippingFee;
    private final Address shippingAddress;
    private final Instant createdAt;
    private UUID paymentId;
    private Instant paidAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant closedAt;

    private Order(OrderId id,
                   CustomerId customerId,
                   SellerId sellerId,
                   OrderStatus status,
                   List<OrderItem> items,
                   Discount discount,
                   Money shippingFee,
                   Address shippingAddress,
                   Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Order.id");
        this.customerId = Objects.requireNonNull(customerId, "Order.customerId");
        this.sellerId = Objects.requireNonNull(sellerId, "Order.sellerId");
        this.status = Objects.requireNonNull(status, "Order.status");
        this.items = new ArrayList<>(Objects.requireNonNull(items, "Order.items"));
        this.discount = discount;
        this.shippingFee = Objects.requireNonNull(shippingFee, "Order.shippingFee");
        this.shippingAddress = Objects.requireNonNull(shippingAddress, "Order.shippingAddress");
        this.createdAt = Objects.requireNonNull(createdAt, "Order.createdAt");
    }

    /**
     * Создать новый заказ в статусе {@link OrderStatus#DRAFT} и зарегистрировать
     * событие {@link OrderCreated}.
     *
     * <p>Инварианты:
     * <ul>
     *   <li>≥ 1 позиции;</li>
     *   <li>BR-014: все позиции от одного продавца.</li>
     * </ul>
     */
    public static Order create(OrderId id,
                                CustomerId customerId,
                                List<OrderItem> items,
                                Money shippingFee,
                                Address shippingAddress,
                                Instant createdAt) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order requires at least one item");
        }
        SellerId seller = items.get(0).sellerId();
        boolean multiSeller = items.stream().anyMatch(i -> !i.sellerId().equals(seller));
        if (multiSeller) {
            throw new IllegalArgumentException("BR-014: order must contain items from a single seller");
        }

        Order order = new Order(id, customerId, seller, OrderStatus.DRAFT,
            new ArrayList<>(items), null, shippingFee, shippingAddress, createdAt);

        List<OrderCreated.ItemSnapshot> snapshots = items.stream()
            .map(i -> new OrderCreated.ItemSnapshot(
                i.productId().value(), i.quantity().value(), i.unitPrice()))
            .toList();
        order.registerEvent(new OrderCreated(id, customerId, seller, order.total(), snapshots));
        return order;
    }

    /**
     * Restore-конструктор для репозитория (без публикации событий).
     */
    public static Order restore(OrderId id,
                                  CustomerId customerId,
                                  SellerId sellerId,
                                  OrderStatus status,
                                  List<OrderItem> items,
                                  Discount discount,
                                  Money shippingFee,
                                  Address shippingAddress,
                                  Instant createdAt,
                                  UUID paymentId,
                                  Instant paidAt,
                                  Instant shippedAt,
                                  Instant deliveredAt,
                                  Instant closedAt) {
        Order order = new Order(id, customerId, sellerId, status, items, discount,
            shippingFee, shippingAddress, createdAt);
        order.paymentId = paymentId;
        order.paidAt = paidAt;
        order.shippedAt = shippedAt;
        order.deliveredAt = deliveredAt;
        order.closedAt = closedAt;
        return order;
    }

    @Override
    public OrderId getId() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public SellerId sellerId() {
        return sellerId;
    }

    public OrderStatus status() {
        return status;
    }

    public List<OrderItem> items() {
        return Collections.unmodifiableList(items);
    }

    public Discount discount() {
        return discount;
    }

    public Money shippingFee() {
        return shippingFee;
    }

    public Address shippingAddress() {
        return shippingAddress;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public UUID paymentId() { return paymentId; }
    public Instant paidAt() { return paidAt; }
    public Instant shippedAt() { return shippedAt; }
    public Instant deliveredAt() { return deliveredAt; }
    public Instant closedAt() { return closedAt; }

    /**
     * Подтверждение заказа: {@code DRAFT → PENDING_PAYMENT}. Регистрирует
     * {@link OrderConfirmed}.
     *
     * <p>Инварианты:
     * <ul>
     *   <li>Текущий статус — {@link OrderStatus#DRAFT}, иначе {@code IllegalStateException}.</li>
     *   <li>BR-013: сумма заказа ≥ {@link #MIN_CONFIRM_AMOUNT_RUB} ₽.</li>
     *   <li>BR-002: ≥ 1 позиции (гарантировано {@link #create}).</li>
     * </ul>
     */
    public void confirm() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException(
                "Order can only be confirmed from DRAFT, current status: " + status);
        }
        Money current = total();
        if (current.amount().compareTo(MIN_CONFIRM_AMOUNT_RUB) < 0) {
            throw new IllegalStateException(
                "BR-013: order total %s is below minimum %s".formatted(current, MIN_CONFIRM_AMOUNT_RUB));
        }
        this.status = OrderStatus.PENDING_PAYMENT;
        List<OrderConfirmed.ItemSnapshot> snapshots = items.stream()
            .map(i -> new OrderConfirmed.ItemSnapshot(
                i.productId().value(), i.quantity().value(), i.unitPrice()))
            .toList();
        registerEvent(new OrderConfirmed(id, customerId, sellerId, snapshots, current));
    }

    /** BR-013: минимальная сумма заказа для confirm. */
    private static final java.math.BigDecimal MIN_CONFIRM_AMOUNT_RUB = new java.math.BigDecimal("100.00");

    /**
     * Отмена заказа до оплаты. Допустимые исходные статусы — {@code DRAFT}
     * и {@code PENDING_PAYMENT}. Возврат не требуется. Регистрирует
     * {@link OrderCancelled} (с {@code refundId == null}).
     *
     * <p>Отмена из {@code PAID} — отдельный метод {@link #cancelAfterPayment}.
     */
    public void cancel(CancellationReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (status != OrderStatus.DRAFT && status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                "Order can only be cancelled from DRAFT or PENDING_PAYMENT, current: " + status);
        }
        OrderStatus previous = this.status;
        this.status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelled(id, customerId, sellerId, previous, reason));
    }

    /**
     * Отмена заказа после оплаты с инициированным возвратом. Доступно только
     * из статуса {@code PAID} (для SHIPPED/DELIVERED — спор, не отмена).
     * Регистрирует {@link OrderCancelled} с указанным {@code refundId}.
     */
    public void cancelAfterPayment(CancellationReason reason, UUID refundId) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(refundId, "refundId");
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException(
                "cancelAfterPayment requires PAID status, current: " + status);
        }
        OrderStatus previous = this.status;
        this.status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelled(id, customerId, sellerId, previous, reason, refundId));
    }

    /**
     * Оплата подтверждена (вебхук Payment Service): {@code PENDING_PAYMENT → PAID}.
     * Регистрирует {@link OrderPaid}.
     */
    public void markPaid(UUID paymentId, Instant paidAt) {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(paidAt, "paidAt");
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                "Order can only be paid from PENDING_PAYMENT, current: " + status);
        }
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
        this.paidAt = paidAt;
        registerEvent(new OrderPaid(id, customerId, sellerId, paymentId, total(), paidAt));
    }

    /**
     * Передача в доставку (продавец): {@code PAID → SHIPPED}. Регистрирует
     * {@link OrderShipped} с трек-номером.
     */
    public void markShipped(String trackingNumber, Instant shippedAt) {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(shippedAt, "shippedAt");
        if (trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber must not be blank");
        }
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException(
                "Order can only be shipped from PAID, current: " + status);
        }
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = shippedAt;
        registerEvent(new OrderShipped(id, customerId, sellerId, trackingNumber, shippedAt));
    }

    /**
     * Подтверждение получения (покупатель): {@code SHIPPED → DELIVERED}.
     */
    public void confirmDelivery(Instant deliveredAt) {
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        if (status != OrderStatus.SHIPPED) {
            throw new IllegalStateException(
                "Order can only be delivered from SHIPPED, current: " + status);
        }
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
        registerEvent(new OrderDelivered(id, customerId, sellerId, deliveredAt));
    }

    /**
     * Просрочка ожидания оплаты (планировщик): {@code PENDING_PAYMENT → EXPIRED}.
     */
    public void expire(Instant expiredAt) {
        Objects.requireNonNull(expiredAt, "expiredAt");
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                "Order can only expire from PENDING_PAYMENT, current: " + status);
        }
        this.status = OrderStatus.EXPIRED;
        this.closedAt = expiredAt;
        registerEvent(new OrderExpired(id, customerId, sellerId, expiredAt));
    }

    /**
     * Финализация по таймауту (планировщик): {@code DELIVERED → COMPLETED} —
     * через 14 дней после получения, если не было дисптуа.
     */
    public void complete(Instant closedAt) {
        Objects.requireNonNull(closedAt, "closedAt");
        if (status != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                "Order can only be completed from DELIVERED, current: " + status);
        }
        this.status = OrderStatus.COMPLETED;
        this.closedAt = closedAt;
        registerEvent(new OrderCompleted(id, customerId, sellerId, closedAt));
    }

    /**
     * Покупатель открывает спор по полученному заказу: {@code DELIVERED → DISPUTE}.
     * Регистрирует {@link DisputeOpened}.
     */
    public void openDispute(String reason, Instant openedAt) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(openedAt, "openedAt");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("dispute reason must not be blank");
        }
        if (status != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                "Dispute can only be opened from DELIVERED, current: " + status);
        }
        this.status = OrderStatus.DISPUTE;
        registerEvent(new DisputeOpened(id, customerId, sellerId, reason, openedAt));
    }

    /**
     * Оператор закрывает спор: {@code DISPUTE → COMPLETED} (отклонён) либо
     * {@code DISPUTE → REFUNDED} (удовлетворён). При REFUNDED refund-саге
     * слушает подписчик через {@link DisputeResolved}.
     */
    public void resolveDispute(OrderStatus finalStatus, String resolutionNote, Instant resolvedAt) {
        Objects.requireNonNull(finalStatus, "finalStatus");
        Objects.requireNonNull(resolutionNote, "resolutionNote");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        if (status != OrderStatus.DISPUTE) {
            throw new IllegalStateException(
                "Dispute can only be resolved from DISPUTE, current: " + status);
        }
        if (finalStatus != OrderStatus.COMPLETED && finalStatus != OrderStatus.REFUNDED) {
            throw new IllegalArgumentException(
                "Dispute resolution must end in COMPLETED or REFUNDED, got: " + finalStatus);
        }
        this.status = finalStatus;
        this.closedAt = resolvedAt;
        registerEvent(new DisputeResolved(id, customerId, sellerId, finalStatus, resolutionNote, resolvedAt));
    }

    /**
     * BR-001: сумма заказа = сумма позиций − скидка + доставка.
     */
    public Money total() {
        Money itemsSum = items.stream()
            .map(OrderItem::lineTotal)
            .reduce(Money::add)
            .orElseThrow(() -> new IllegalStateException("Order has no items"));
        Money afterDiscount = (discount == null) ? itemsSum : discount.applyTo(itemsSum);
        return afterDiscount.add(shippingFee);
    }
}
