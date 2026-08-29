package ru.remodov.catalog.exception;

public class InvalidPriceException extends RuntimeException {
    public InvalidPriceException(String detail) {
        super(detail);
    }
}
