package ru.remodov.catalog.exception;

public class InvalidCurrencyException extends RuntimeException {
    private final String currency;

    public InvalidCurrencyException(String currency) {
        super("Currency is not supported: " + currency);
        this.currency = currency;
    }

    public String currency() { return currency; }
}
