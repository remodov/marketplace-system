package ru.vikulinva.orderservice.adapter.out.payment.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import ru.vikulinva.hexagonal.OutboundAdapter;
import ru.vikulinva.orderservice.adapter.out.payment.dto.RefundRequest;
import ru.vikulinva.orderservice.adapter.out.payment.dto.RefundResponse;
import ru.vikulinva.orderservice.domain.valueobject.Money;
import ru.vikulinva.orderservice.domain.valueobject.OrderId;
import ru.vikulinva.orderservice.port.out.PaymentPort;
import ru.vikulinva.orderservice.usecase.command.exception.PaymentUnavailableException;

import java.util.UUID;

/**
 * REST-клиент Payment Service. Resilience4j-инстанс {@code payment}.
 * При недоступности или открытом circuit-breaker → {@link PaymentUnavailableException}.
 *
 * <p>Передаёт {@code Idempotency-Key} как HTTP-заголовок — Payment должен
 * быть идемпотентным по нему (повторная заявка с тем же ключом → тот же refundId).
 */
@Component
@OutboundAdapter("Payment Service REST client")
public class PaymentRestClient implements PaymentPort {

    private static final String INSTANCE = "payment";

    private final RestTemplate restTemplate;

    public PaymentRestClient(@Qualifier("paymentRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    @CircuitBreaker(name = INSTANCE, fallbackMethod = "fallback")
    @Retry(name = INSTANCE)
    public UUID requestRefund(OrderId orderId, UUID paymentId, Money amount, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        RefundRequest body = new RefundRequest(
            paymentId,
            orderId.value(),
            amount.amount(),
            amount.currency().getCurrencyCode(),
            idempotencyKey);

        ResponseEntity<RefundResponse> response;
        try {
            response = restTemplate.exchange(
                "/api/v1/refunds",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, headers),
                RefundResponse.class);
        } catch (HttpServerErrorException hsee) {
            // 5xx — Payment временно недоступен; сразу мапим в доменное исключение,
            // чтобы вызывающий код мог откатить транзакцию и не дёргать дальше.
            throw new PaymentUnavailableException(hsee);
        } catch (ResourceAccessException raex) {
            throw new PaymentUnavailableException(raex);
        }

        RefundResponse refund = response.getBody();
        if (refund == null || refund.refundId() == null) {
            throw new IllegalStateException("Payment Service returned empty refundId for order " + orderId);
        }
        return refund.refundId();
    }

    /** Fallback при открытом circuit breaker. */
    @SuppressWarnings("unused")
    private UUID fallback(OrderId orderId, UUID paymentId, Money amount, String idempotencyKey, Throwable t) {
        if (t instanceof PaymentUnavailableException pue) {
            throw pue;
        }
        throw new PaymentUnavailableException(t);
    }
}
