package ru.vikulinva.payment.payment;

import java.util.UUID;

public class InvalidPaymentTransitionException extends RuntimeException {

    public InvalidPaymentTransitionException(UUID paymentId, Payment.Status from, Payment.Status to) {
        super("Платёж " + paymentId + ": переход " + from + " → " + to + " не разрешён");
    }
}
