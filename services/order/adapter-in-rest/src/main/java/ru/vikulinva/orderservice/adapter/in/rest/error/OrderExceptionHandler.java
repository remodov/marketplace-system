package ru.vikulinva.orderservice.adapter.in.rest.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.ErrorCode;
import ru.vikulinva.orderservice.adapter.in.rest.generated.model.ProblemDetails;
import ru.vikulinva.orderservice.usecase.command.exception.OrderDomainException;

import java.net.URI;

/**
 * Конвертирует доменные исключения в RFC 9457 ProblemDetails, как это
 * описано в OpenAPI-спеке. Контракт `code`/`status` стабильный — клиенты
 * могут писать switch по `code`.
 */
@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(OrderDomainException.class)
    public ResponseEntity<ProblemDetails> handle(OrderDomainException ex, HttpServletRequest request) {
        ProblemDetails body = new ProblemDetails();
        body.setType(URI.create("https://vikulin-va.ru/errors/" + slug(ex.code())));
        body.setTitle(humanTitle(ex.code()));
        body.setStatus(ex.httpStatus());
        body.setCode(ErrorCode.valueOf(ex.code()));
        body.setDetail(ex.getMessage());
        body.setInstance(request.getRequestURI());

        return ResponseEntity
            .status(HttpStatus.valueOf(ex.httpStatus()))
            .contentType(MediaType.valueOf("application/problem+json"))
            .body(body);
    }

    private static String slug(String code) {
        return code.toLowerCase().replace('_', '-');
    }

    private static String humanTitle(String code) {
        // Можно расширить таблицей кодов; пока — упрощённый title.
        return switch (code) {
            case "MULTI_SELLER_NOT_SUPPORTED" -> "Order must contain items from a single seller";
            case "PRODUCT_NOT_FOUND" -> "Product not found";
            case "OUT_OF_STOCK" -> "Item is out of stock";
            case "ORDER_INVALID_STATE" -> "Order is not in a valid state for this action";
            case "IDEMPOTENCY_KEY_CONFLICT" -> "Idempotency-Key reused for a different request";
            case "SERVICE_DEGRADED" -> "External service is temporarily unavailable";
            default -> "Order domain error";
        };
    }
}
