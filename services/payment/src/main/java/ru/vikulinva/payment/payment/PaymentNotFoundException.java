package ru.vikulinva.payment.payment;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Платёж не найден: " + paymentId);
    }
}
