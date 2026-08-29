package ru.vikulinva.orderservice.adapter.out.payment.dto;

import java.util.UUID;

public record RefundResponse(UUID refundId, String status) {
}
