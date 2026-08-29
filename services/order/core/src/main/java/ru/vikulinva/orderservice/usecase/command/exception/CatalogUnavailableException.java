package ru.vikulinva.orderservice.usecase.command.exception;

/** Catalog Service не отвечает (timeout / circuit breaker open). */
public final class CatalogUnavailableException extends OrderDomainException {

    public CatalogUnavailableException(Throwable cause) {
        super("SERVICE_DEGRADED", 503, "Catalog service is temporarily unavailable");
        initCause(cause);
    }
}
