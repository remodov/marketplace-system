package ru.vikulinva.payment.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    public record AuthorizeRequest(@NotNull UUID orderId,
                                   @NotNull @Positive BigDecimal amount,
                                   @NotNull String currency) {
    }

    public record PaymentView(UUID id, UUID orderId, BigDecimal amount, String currency, String status) {

        static PaymentView of(Payment payment) {
            return new PaymentView(payment.id(), payment.orderId(), payment.amount(),
                payment.currency(), payment.status().name());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView authorize(@Valid @RequestBody AuthorizeRequest request) {
        return PaymentView.of(service.authorize(request.orderId(), request.amount(), request.currency()));
    }

    @GetMapping("/{id}")
    public PaymentView byId(@PathVariable UUID id) {
        return PaymentView.of(service.byId(id));
    }

    @PostMapping("/{id}/capture")
    public PaymentView capture(@PathVariable UUID id) {
        return PaymentView.of(service.capture(id));
    }

    @PostMapping("/{id}/refund")
    public PaymentView refund(@PathVariable UUID id) {
        return PaymentView.of(service.refund(id));
    }
}
