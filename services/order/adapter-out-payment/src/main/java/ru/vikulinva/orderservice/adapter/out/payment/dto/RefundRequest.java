package ru.vikulinva.orderservice.adapter.out.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(UUID paymentId,
                              UUID orderId,
                              BigDecimal amount,
                              String currency,
                              String idempotencyKey) {
}
