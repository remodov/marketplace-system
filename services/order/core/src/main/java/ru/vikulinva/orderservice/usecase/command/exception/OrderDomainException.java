package ru.vikulinva.orderservice.usecase.command.exception;

/**
 * Базовое доменное исключение Order Service. Несёт стабильный машинный код
 * (соответствует enum ErrorCode из OpenAPI / §13 Errors), HTTP-статус и
 * человекочитаемое сообщение.
 *
 * <p>Конкретные исключения наследуют этот класс с прибитым {@code code} и
 * {@code httpStatus}.
 */
public abstract class OrderDomainException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    protected OrderDomainException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
